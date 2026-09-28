/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.db.instance.configs.migrations;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores the frozen Puente connection policy and append-only operation attempts.
 */
public class V0_40_32_001__AddPuentePolicyAndJournal extends BaseJavaMigration {

  private static final Logger LOGGER = LoggerFactory.getLogger(V0_40_32_001__AddPuentePolicyAndJournal.class);

  @Override
  public void migrate(final Context context) {
    LOGGER.info("Running migration: {}", getClass().getSimpleName());
    try (final DSLContext ctx = DSL.using(context.getConnection())) {
      ctx.alterTable("connection")
          .addColumnIfNotExists(DSL.field("puente_policy", SQLDataType.JSONB.nullable(true)))
          .execute();

      ctx.createTableIfNotExists("puente_operation_journal")
          .column("id", SQLDataType.UUID.nullable(false))
          .column("connection_id", SQLDataType.UUID.nullable(false))
          .column("source_fill_id", SQLDataType.VARCHAR(256).nullable(true))
          .column("destination_id", SQLDataType.UUID.nullable(false))
          .column("status", SQLDataType.VARCHAR(32).nullable(false))
          .column("reason", SQLDataType.VARCHAR(256).nullable(true))
          .column("source_record", SQLDataType.JSONB.nullable(true))
          .column("outbound_request", SQLDataType.JSONB.nullable(true))
          .column("destination_response", SQLDataType.JSONB.nullable(true))
          .column("logs", SQLDataType.JSONB.nullable(false))
          .column("origin_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(true))
          .column("received_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
          .column("sent_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(true))
          .column("replica_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
          .column("desfase_ms", SQLDataType.BIGINT.nullable(true))
          .column("attempt", SQLDataType.INTEGER.nullable(false))
          .column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false).defaultValue(DSL.currentOffsetDateTime()))
          .constraints(
              DSL.constraint("puente_operation_journal_pkey").primaryKey("id"),
              DSL.constraint("puente_operation_journal_connection_fkey").foreignKey("connection_id")
                  .references("connection", "id").onDeleteCascade())
          .execute();

      ctx.createIndexIfNotExists("puente_journal_connection_replica_idx")
          .on("puente_operation_journal", "connection_id", "replica_at")
          .execute();
      ctx.execute("""
                  CREATE UNIQUE INDEX IF NOT EXISTS puente_journal_accepted_fill_idx
                  ON puente_operation_journal(source_fill_id, destination_id)
                  WHERE status = 'accepted'
                  """);
    }
  }

}
