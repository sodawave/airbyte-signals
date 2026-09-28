/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.PuenteConnectionPolicy;
import io.airbyte.config.PuenteCrosswalkRow;
import io.airbyte.protocol.models.AirbyteMessage;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a frozen Puente crosswalk immediately before a record is offered to a destination.
 *
 * <p>
 * No symbol, factor, or scale is inferred here. A record without a saved row is skipped.
 */
public class PuenteReplicationPolicy {

  public static final String FILL_STREAM = "fills";
  private static final String SOURCE_FILL_ID = "source_fill_id";
  private static final String SOURCE_SYMBOL = "symbol";
  private static final String ORIGIN_AT = "origen_en";
  private static final String QUANTITY = "quantity";
  private static final String DESTINATION_ID = "_puente_destination_id";
  private static final String ACK_FIELD = "_puente_ack";

  private final UUID connectionId;
  private final UUID destinationId;
  private final PuenteConnectionPolicy policy;
  private final PuenteJournalWriter journal;
  private final int attempt;
  private final Map<String, PuenteCrosswalkRow> rows;
  private final Map<String, PreparedFill> pending = new ConcurrentHashMap<>();

  public PuenteReplicationPolicy(final UUID connectionId,
                                 final UUID destinationId,
                                 final PuenteConnectionPolicy policy,
                                 final PuenteJournalWriter journal) {
    this(connectionId, destinationId, policy, journal, 1);
  }

  public PuenteReplicationPolicy(final UUID connectionId,
                                 final UUID destinationId,
                                 final PuenteConnectionPolicy policy,
                                 final PuenteJournalWriter journal,
                                 final int attempt) {
    this.connectionId = connectionId;
    this.destinationId = destinationId;
    this.policy = policy;
    this.journal = journal;
    this.attempt = attempt;
    this.rows = new HashMap<>();
    policy.getRows().forEach(row -> {
      final boolean mapped = row.getDestinationSymbol() != null;
      final boolean excluded = row.getExclusionReason() != null;
      if (mapped == excluded) {
        throw new IllegalArgumentException("Each Puente row must define exactly one destination or exclusion");
      }
      if (rows.putIfAbsent(row.getSourceSymbol(), row) != null) {
        throw new IllegalArgumentException("Duplicate Puente source symbol: " + row.getSourceSymbol());
      }
    });
  }

  /**
   * Returns empty after journaling a deterministic skip or duplicate.
   */
  public Optional<PreparedFill> prepare(final AirbyteMessage message) {
    if (message.getRecord() == null || !FILL_STREAM.equals(message.getRecord().getStream())) {
      return Optional.of(new PreparedFill(message, null, null, null, null, null, null));
    }

    final OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
    final JsonNode source = Jsons.clone(message.getRecord().getData());
    if (source == null || !source.isObject()) {
      append("skipped", "invalid_record_data", null, source, null, null, null, receivedAt, null, receivedAt);
      return Optional.empty();
    }
    final String fillId = text(source, SOURCE_FILL_ID);
    final String symbol = text(source, SOURCE_SYMBOL);
    final OffsetDateTime originAt = originAt(source);

    if (source.hasNonNull(ORIGIN_AT) && originAt == null) {
      append("skipped", "invalid_origin_timestamp", fillId, source, null, null, null, receivedAt, null, receivedAt);
      return Optional.empty();
    }
    if (fillId == null) {
      append("skipped", "missing_fill_id", null, source, null, null, originAt, receivedAt, null, receivedAt);
      return Optional.empty();
    }
    if (journal.hasAccepted(fillId, destinationId)) {
      append("duplicate", "already_accepted", fillId, source, null, null, originAt, receivedAt, null, receivedAt);
      return Optional.empty();
    }

    final PuenteCrosswalkRow row = rows.get(symbol);
    if (row == null) {
      append("skipped", "missing_map", fillId, source, null, null, originAt, receivedAt, null, receivedAt);
      return Optional.empty();
    }
    if (row.getExclusionReason() != null) {
      append("skipped", row.getExclusionReason(), fillId, source, null, null, originAt, receivedAt, null, receivedAt);
      return Optional.empty();
    }
    if (row.getDestinationSymbol() == null) {
      append("skipped", "missing_destination_symbol", fillId, source, null, null, originAt, receivedAt, null, receivedAt);
      return Optional.empty();
    }
    if (policy.getMode() == PuenteConnectionPolicy.Mode.LIVE
        && policy.getLiveQuota() != null
        && policy.getLiveQuota() <= pending.size()) {
      append("skipped", "quota_exhausted", fillId, source, null, null, originAt, receivedAt, null, receivedAt);
      return Optional.empty();
    }

    final AirbyteMessage outbound = Jsons.clone(message);
    final ObjectNode outboundData = (ObjectNode) outbound.getRecord().getData();
    outboundData.put("source_symbol", symbol);
    outboundData.put("symbol", row.getDestinationSymbol());
    outboundData.put("canonical_instrument", row.getCanonicalInstrument());
    outboundData.put("contract_factor", row.getContractFactor());
    outboundData.put("scale", row.getScale());
    outboundData.put(DESTINATION_ID, destinationId.toString());
    if (source.hasNonNull(QUANTITY)) {
      outboundData.put(QUANTITY, source.get(QUANTITY).asDouble() * row.getContractFactor() * row.getScale());
    }
    return Optional.of(new PreparedFill(
        outbound,
        fillId,
        source,
        Jsons.clone(outboundData),
        originAt,
        receivedAt,
        null));
  }

  /**
   * Registers a fill before writing it to destination STDIN. A second in-flight copy is suppressed.
   */
  public synchronized boolean registerPending(final PreparedFill prepared) {
    if (!prepared.isFill()) {
      return true;
    }
    if (pending.containsKey(prepared.fillId())) {
      final OffsetDateTime replicaAt = OffsetDateTime.now(ZoneOffset.UTC);
      append("duplicate", "already_in_flight", prepared.fillId(), prepared.sourceRecord(), prepared.outboundRequest(), null,
          prepared.originAt(), prepared.receivedAt(), prepared.sentAt(), replicaAt);
      return false;
    }
    if (policy.getMode() == PuenteConnectionPolicy.Mode.LIVE
        && policy.getLiveQuota() != null
        && policy.getLiveQuota() <= pending.size()) {
      final OffsetDateTime replicaAt = OffsetDateTime.now(ZoneOffset.UTC);
      append("skipped", "quota_exhausted", prepared.fillId(), prepared.sourceRecord(), null, null,
          prepared.originAt(), prepared.receivedAt(), null, replicaAt);
      return false;
    }
    pending.put(prepared.fillId(), prepared);
    return true;
  }

  public PreparedFill markSent(final PreparedFill prepared) {
    if (!prepared.isFill()) {
      return prepared;
    }
    final PreparedFill sent = prepared.withSentAt(OffsetDateTime.now(ZoneOffset.UTC));
    pending.replace(prepared.fillId(), prepared, sent);
    return sent;
  }

  /**
   * Consumes a Puente acknowledgment carried in a protocol-compatible destination state message.
   */
  public boolean acknowledge(final AirbyteMessage message) {
    if (message.getType() != AirbyteMessage.Type.STATE
        || message.getState() == null
        || message.getState().getData() == null
        || !message.getState().getData().has(ACK_FIELD)) {
      return false;
    }

    final JsonNode acknowledgment = message.getState().getData().get(ACK_FIELD);
    if (!destinationId.toString().equals(text(acknowledgment, "destination_id"))) {
      return false;
    }

    final String fillId = text(acknowledgment, SOURCE_FILL_ID);
    final PreparedFill prepared = fillId == null ? null : pending.remove(fillId);
    if (prepared == null) {
      return false;
    }

    final JsonNode response = acknowledgment.has("response") ? acknowledgment.get("response") : acknowledgment;
    if ("accepted".equals(text(acknowledgment, "status"))) {
      accepted(prepared, response);
    } else {
      rejected(prepared, text(acknowledgment, "reason"), response);
    }
    return true;
  }

  private void accepted(final PreparedFill prepared, final JsonNode response) {
    if (!prepared.isFill()) {
      return;
    }
    final OffsetDateTime replicaAt = OffsetDateTime.now(ZoneOffset.UTC);
    append("accepted", null, prepared.fillId(), prepared.sourceRecord(), prepared.outboundRequest(),
        response, prepared.originAt(), prepared.receivedAt(), prepared.sentAt(), replicaAt);
    if (policy.getMode() == PuenteConnectionPolicy.Mode.LIVE) {
      if (policy.getLiveQuota() != null) {
        policy.setLiveQuota(policy.getLiveQuota() - 1);
      }
    }
  }

  public void rejected(final PreparedFill prepared, final Exception error) {
    if (!prepared.isFill()) {
      return;
    }
    pending.remove(prepared.fillId(), prepared);
    rejected(
        prepared,
        "destination_delivery_failed",
        Jsons.jsonNode(Map.of(
            "accepted", false,
            "error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())));
  }

  public void rejectPending(final String reason) {
    pending.values().forEach(prepared -> {
      if (pending.remove(prepared.fillId(), prepared)) {
        rejected(prepared, reason, Jsons.jsonNode(Map.of("accepted", false, "reason", reason)));
      }
    });
  }

  private void rejected(final PreparedFill prepared, final String reason, final JsonNode response) {
    if (!prepared.isFill()) {
      return;
    }
    final OffsetDateTime replicaAt = OffsetDateTime.now(ZoneOffset.UTC);
    append("rejected", reason == null ? "destination_rejected" : reason, prepared.fillId(), prepared.sourceRecord(), prepared.outboundRequest(),
        response,
        prepared.originAt(), prepared.receivedAt(), prepared.sentAt(), replicaAt);
  }

  private void append(final String status,
                      final String reason,
                      final String fillId,
                      final JsonNode source,
                      final JsonNode outbound,
                      final JsonNode response,
                      final OffsetDateTime originAt,
                      final OffsetDateTime receivedAt,
                      final OffsetDateTime sentAt,
                      final OffsetDateTime replicaAt) {
    final Long desfaseMs = originAt == null ? null : replicaAt.toInstant().toEpochMilli() - originAt.toInstant().toEpochMilli();
    journal.append(new PuenteJournalEntry(connectionId, fillId, destinationId, status, reason, source, outbound, response,
        Jsons.jsonNode(List.of(reason == null ? status : status + ": " + reason)),
        originAt, receivedAt, sentAt, replicaAt, desfaseMs, attempt));
  }

  private static String text(final JsonNode node, final String field) {
    return node.hasNonNull(field) && !node.get(field).asText().isBlank() ? node.get(field).asText() : null;
  }

  private static OffsetDateTime originAt(final JsonNode source) {
    if (!source.hasNonNull(ORIGIN_AT)) {
      return null;
    }
    final JsonNode value = source.get(ORIGIN_AT);
    if (value.isNumber()) {
      if (!value.canConvertToLong()) {
        return null;
      }
      try {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(value.asLong()), ZoneOffset.UTC);
      } catch (final DateTimeException e) {
        return null;
      }
    }
    try {
      return OffsetDateTime.parse(value.asText());
    } catch (final DateTimeParseException e) {
      return null;
    }
  }

  public record PreparedFill(
                             AirbyteMessage message,
                             String fillId,
                             JsonNode sourceRecord,
                             JsonNode outboundRequest,
                             OffsetDateTime originAt,
                             OffsetDateTime receivedAt,
                             OffsetDateTime sentAt) {

    public boolean isFill() {
      return fillId != null;
    }

    public PreparedFill withSentAt(final OffsetDateTime sentAt) {
      return new PreparedFill(message, fillId, sourceRecord, outboundRequest, originAt, receivedAt, sentAt);
    }

  }

}
