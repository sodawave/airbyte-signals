/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.config.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.db.Database;
import io.airbyte.db.ExceptionWrappingDatabase;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * Append-only persistence for Puente replication decisions and destination attempts.
 */
public class PuenteOperationJournalPersistence {

  private static final Table<Record> JOURNAL = DSL.table(DSL.name("puente_operation_journal"));
  private final ExceptionWrappingDatabase database;

  public PuenteOperationJournalPersistence(final Database database) {
    this.database = new ExceptionWrappingDatabase(database);
  }

  public boolean hasAccepted(final String sourceFillId, final UUID destinationId) throws IOException {
    return database.query(ctx -> ctx.fetchExists(
        ctx.selectOne()
            .from(JOURNAL)
            .where(DSL.field("source_fill_id", String.class).eq(sourceFillId))
            .and(DSL.field("destination_id", UUID.class).eq(destinationId))
            .and(DSL.field("status", String.class).eq("accepted"))));
  }

  public void append(final JournalRecord entry) throws IOException {
    database.transaction(ctx -> {
      JournalRecord persisted = withComputedDesfase(entry);
      JsonNode policy = null;
      if ("accepted".equals(persisted.status())) {
        final JSONB policyJson = ctx.select(DSL.field("puente_policy", JSONB.class))
            .from(DSL.table("connection"))
            .where(DSL.field("id", UUID.class).eq(persisted.connectionId()))
            .forUpdate()
            .fetchOne(DSL.field("puente_policy", JSONB.class));
        policy = json(policyJson);
        final boolean alreadyAccepted = ctx.fetchExists(ctx.selectOne()
            .from(JOURNAL)
            .where(DSL.field("source_fill_id", String.class).eq(persisted.sourceFillId()))
            .and(DSL.field("destination_id", UUID.class).eq(persisted.destinationId()))
            .and(DSL.field("status", String.class).eq("accepted")));
        if (alreadyAccepted) {
          persisted = withStatus(persisted, "duplicate", "already_accepted");
        } else if (isExhaustedLivePolicy(policy)) {
          persisted = withStatus(persisted, "rejected", "quota_exhausted");
        }
      }

      final int inserted = ctx.insertInto(JOURNAL)
          .columns(
              DSL.field("id"),
              DSL.field("connection_id"),
              DSL.field("source_fill_id"),
              DSL.field("destination_id"),
              DSL.field("status"),
              DSL.field("reason"),
              DSL.field("source_record"),
              DSL.field("outbound_request"),
              DSL.field("destination_response"),
              DSL.field("logs"),
              DSL.field("origin_at"),
              DSL.field("received_at"),
              DSL.field("sent_at"),
              DSL.field("replica_at"),
              DSL.field("desfase_ms"),
              DSL.field("attempt"))
          .values(
              entry.id(),
              persisted.connectionId(),
              persisted.sourceFillId(),
              persisted.destinationId(),
              persisted.status(),
              persisted.reason(),
              jsonb(persisted.sourceRecord()),
              jsonb(persisted.outboundRequest()),
              jsonb(persisted.destinationResponse()),
              jsonb(persisted.logs()),
              persisted.originAt(),
              persisted.receivedAt(),
              persisted.sentAt(),
              persisted.replicaAt(),
              persisted.desfaseMs(),
              persisted.attempt())
          .onConflictDoNothing()
          .execute();
      if (inserted == 1 && "accepted".equals(persisted.status()) && isFiniteLivePolicy(policy)) {
        ctx.execute("""
                    UPDATE connection
                    SET puente_policy = jsonb_set(
                      puente_policy,
                      '{liveQuota}',
                      to_jsonb((puente_policy ->> 'liveQuota')::bigint - 1)
                    )
                    WHERE id = ?
                    """, persisted.connectionId());
      }
      return null;
    });
  }

  public List<JournalRecord> list(final UUID connectionId) throws IOException {
    return database.query(ctx -> ctx.select()
        .from(JOURNAL)
        .where(DSL.field("connection_id", UUID.class).eq(connectionId))
        .orderBy(DSL.field("replica_at").desc())
        .limit(200)
        .fetch(record -> new JournalRecord(
            record.get("id", UUID.class),
            record.get("connection_id", UUID.class),
            record.get("source_fill_id", String.class),
            record.get("destination_id", UUID.class),
            record.get("status", String.class),
            record.get("reason", String.class),
            json(record.get("source_record", JSONB.class)),
            json(record.get("outbound_request", JSONB.class)),
            json(record.get("destination_response", JSONB.class)),
            json(record.get("logs", JSONB.class)),
            record.get("origin_at", OffsetDateTime.class),
            record.get("received_at", OffsetDateTime.class),
            record.get("sent_at", OffsetDateTime.class),
            record.get("replica_at", OffsetDateTime.class),
            record.get("desfase_ms", Long.class),
            record.get("attempt", Integer.class))));
  }

  private static JSONB jsonb(final JsonNode value) {
    return value == null ? null : JSONB.valueOf(Jsons.serialize(value));
  }

  private static JsonNode json(final JSONB value) {
    return value == null ? null : Jsons.deserialize(value.data());
  }

  private static boolean isFiniteLivePolicy(final JsonNode policy) {
    return policy != null
        && "live".equals(policy.path("mode").asText())
        && policy.hasNonNull("liveQuota");
  }

  private static boolean isExhaustedLivePolicy(final JsonNode policy) {
    return isFiniteLivePolicy(policy) && policy.path("liveQuota").asLong() <= 0;
  }

  private static JournalRecord withComputedDesfase(final JournalRecord entry) {
    final Long desfase = entry.originAt() == null || entry.replicaAt() == null
        ? null
        : entry.replicaAt().toInstant().toEpochMilli() - entry.originAt().toInstant().toEpochMilli();
    return new JournalRecord(
        entry.id(), entry.connectionId(), entry.sourceFillId(), entry.destinationId(), entry.status(), entry.reason(),
        entry.sourceRecord(), entry.outboundRequest(), entry.destinationResponse(), entry.logs(), entry.originAt(),
        entry.receivedAt(), entry.sentAt(), entry.replicaAt(), desfase, entry.attempt());
  }

  private static JournalRecord withStatus(final JournalRecord entry, final String status, final String reason) {
    return new JournalRecord(
        entry.id(), entry.connectionId(), entry.sourceFillId(), entry.destinationId(), status, reason,
        entry.sourceRecord(), entry.outboundRequest(), entry.destinationResponse(), entry.logs(), entry.originAt(),
        entry.receivedAt(), entry.sentAt(), entry.replicaAt(), entry.desfaseMs(), entry.attempt());
  }

  public record JournalRecord(
                              UUID id,
                              UUID connectionId,
                              String sourceFillId,
                              UUID destinationId,
                              String status,
                              String reason,
                              JsonNode sourceRecord,
                              JsonNode outboundRequest,
                              JsonNode destinationResponse,
                              JsonNode logs,
                              OffsetDateTime originAt,
                              OffsetDateTime receivedAt,
                              OffsetDateTime sentAt,
                              OffsetDateTime replicaAt,
                              Long desfaseMs,
                              int attempt) {}

}
