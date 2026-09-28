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
      ctx.insertInto(JOURNAL)
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
              entry.connectionId(),
              entry.sourceFillId(),
              entry.destinationId(),
              entry.status(),
              entry.reason(),
              jsonb(entry.sourceRecord()),
              jsonb(entry.outboundRequest()),
              jsonb(entry.destinationResponse()),
              jsonb(entry.logs()),
              entry.originAt(),
              entry.receivedAt(),
              entry.sentAt(),
              entry.replicaAt(),
              entry.desfaseMs(),
              entry.attempt())
          .execute();
      if ("accepted".equals(entry.status())) {
        ctx.execute("""
                    UPDATE connection
                    SET puente_policy = jsonb_set(
                      puente_policy,
                      '{liveQuota}',
                      to_jsonb(GREATEST((puente_policy ->> 'liveQuota')::bigint - 1, 0))
                    )
                    WHERE id = ?
                      AND puente_policy ->> 'mode' = 'live'
                      AND (puente_policy ->> 'liveQuota')::bigint > 0
                    """, entry.connectionId());
      }
      return null;
    });
  }

  public List<JournalRecord> list(final UUID connectionId) throws IOException {
    return database.query(ctx -> ctx.select()
        .from(JOURNAL)
        .where(DSL.field("connection_id", UUID.class).eq(connectionId))
        .orderBy(DSL.field("replica_at").desc())
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
