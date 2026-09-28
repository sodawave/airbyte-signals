/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.airbyte.commons.json.Jsons;
import io.airbyte.config.PuenteConnectionPolicy;
import io.airbyte.config.PuenteCrosswalkRow;
import io.airbyte.protocol.models.AirbyteMessage;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.airbyte.protocol.models.AirbyteStateMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PuenteReplicationPolicyTest {

  private static final UUID CONNECTION_ID = UUID.randomUUID();
  private static final UUID DESTINATION_ID = UUID.randomUUID();
  private static final String ORIGIN = "2026-01-01T00:00:00Z";

  private InMemoryPuenteJournalWriter journal;
  private PuenteConnectionPolicy config;
  private PuenteReplicationPolicy policy;

  @BeforeEach
  void setup() {
    journal = new InMemoryPuenteJournalWriter();
    config = new PuenteConnectionPolicy()
        .withMode(PuenteConnectionPolicy.Mode.PAPER)
        .withRows(List.of(
            row("DAX", "DE40", "GER40", null, 2D, 0.5D),
            row("DJ", "US30", "WS30", null, 1D, 3D),
            row("ES", "US500", null, "future_not_supported", 1D, 1D)));
    policy = new PuenteReplicationPolicy(CONNECTION_ID, DESTINATION_ID, config, journal);
  }

  @Test
  void mapsSavedRowsAndNeverInfersMissingSymbols() {
    final var dax = policy.prepare(fill("dax-1", "DAX", 4D)).orElseThrow();
    assertEquals("GER40", dax.message().getRecord().getData().get("symbol").asText());
    assertEquals(4D, dax.message().getRecord().getData().get("quantity").asDouble());

    final var dj = policy.prepare(fill("dj-1", "DJ", 2D)).orElseThrow();
    assertEquals("WS30", dj.message().getRecord().getData().get("symbol").asText());
    assertEquals(6D, dj.message().getRecord().getData().get("quantity").asDouble());

    assertTrue(policy.prepare(fill("unknown-1", "GER40", 1D)).isEmpty());
    assertEquals("missing_map", journal.entries().get(0).reason());
  }

  @Test
  void excludesBeforeDestinationAndPersistsDesfase() {
    assertTrue(policy.prepare(fill("es-1", "ES", 1D)).isEmpty());

    final PuenteJournalEntry entry = journal.entries().get(0);
    assertEquals("skipped", entry.status());
    assertEquals("future_not_supported", entry.reason());
    assertEquals(
        entry.replicaAt().toInstant().toEpochMilli() - OffsetDateTime.parse(ORIGIN).toInstant().toEpochMilli(),
        entry.desfaseMs());
  }

  @Test
  void acceptedReplayIsIdempotentAndLiveQuotaDebitsOnlyAfterAcceptance() {
    config.setMode(PuenteConnectionPolicy.Mode.LIVE);
    config.setLiveQuota(2L);

    final var first = policy.prepare(fill("dax-live-1", "DAX", 1D)).orElseThrow();
    policy.rejected(first, new IllegalStateException("paper reject"));
    assertEquals(2L, config.getLiveQuota());
    assertFalse(journal.hasAccepted("dax-live-1", DESTINATION_ID));

    final var retry = policy.prepare(fill("dax-live-1", "DAX", 1D)).orElseThrow();
    assertTrue(policy.registerPending(retry));
    assertEquals(2L, config.getLiveQuota());
    assertFalse(journal.hasAccepted("dax-live-1", DESTINATION_ID));

    assertTrue(policy.acknowledge(acknowledgment("dax-live-1", "accepted", null)));
    assertEquals(1L, config.getLiveQuota());
    assertTrue(journal.hasAccepted("dax-live-1", DESTINATION_ID));
    assertTrue(journal.entries().get(1).destinationResponse().get("persisted").asBoolean());

    assertTrue(policy.prepare(fill("dax-live-1", "DAX", 1D)).isEmpty());
    assertEquals(List.of("rejected", "accepted", "duplicate"),
        journal.entries().stream().map(PuenteJournalEntry::status).toList());
  }

  @Test
  void destinationRejectionPreservesResponseAndDoesNotDebitQuota() {
    config.setMode(PuenteConnectionPolicy.Mode.LIVE);
    config.setLiveQuota(1L);

    final var prepared = policy.prepare(fill("rejected-1", "DAX", 1D)).orElseThrow();
    assertTrue(policy.registerPending(prepared));
    assertTrue(policy.acknowledge(acknowledgment("rejected-1", "rejected", "unsupported_symbol")));

    final PuenteJournalEntry entry = journal.entries().get(0);
    assertEquals("rejected", entry.status());
    assertEquals("unsupported_symbol", entry.reason());
    assertFalse(entry.destinationResponse().get("accepted").asBoolean());
    assertFalse(entry.destinationResponse().get("persisted").asBoolean());
    assertEquals(1L, config.getLiveQuota());
  }

  private static PuenteCrosswalkRow row(final String source,
                                        final String canonical,
                                        final String destination,
                                        final String exclusion,
                                        final double factor,
                                        final double scale) {
    return new PuenteCrosswalkRow()
        .withSourceSymbol(source)
        .withCanonicalInstrument(canonical)
        .withDestinationSymbol(destination)
        .withExclusionReason(exclusion)
        .withContractFactor(factor)
        .withScale(scale);
  }

  private static AirbyteMessage fill(final String id, final String symbol, final double quantity) {
    return new AirbyteMessage()
        .withType(AirbyteMessage.Type.RECORD)
        .withRecord(new AirbyteRecordMessage()
            .withStream(PuenteReplicationPolicy.FILL_STREAM)
            .withEmittedAt(0L)
            .withData(Jsons.jsonNode(Map.of(
                "source_fill_id", id,
                "symbol", symbol,
                "quantity", quantity,
                "origen_en", ORIGIN))));
  }

  private static AirbyteMessage acknowledgment(final String fillId, final String status, final String reason) {
    final Map<String, Object> value = new java.util.HashMap<>();
    value.put("source_fill_id", fillId);
    value.put("destination_id", DESTINATION_ID.toString());
    value.put("status", status);
    value.put("response", Map.of("accepted", "accepted".equals(status), "persisted", "accepted".equals(status)));
    if (reason != null) {
      value.put("reason", reason);
    }
    return new AirbyteMessage()
        .withType(AirbyteMessage.Type.STATE)
        .withState(new AirbyteStateMessage().withData(Jsons.jsonNode(Map.of("_puente_ack", value))));
  }

}
