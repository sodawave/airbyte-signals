/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.PuenteConnectionPolicy;
import io.airbyte.config.PuenteCrosswalkRow;
import io.airbyte.protocol.models.AirbyteMessage;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.airbyte.protocol.models.AirbyteStateMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PuentePaperFlowIntegrationTest {

  private static final UUID CONNECTION_ID = UUID.fromString("30d8761f-b3ca-448f-b878-a09bdc03c16f");
  private static final UUID DESTINATION_ID = UUID.fromString("ea75c03d-0d16-4857-93c7-7ad838647ed7");

  @Test
  void sourceFixturesFlowThroughSavedPolicyDestinationAcknowledgmentsAndReplay() throws Exception {
    final Path fixtures = Path.of(
        "..", "airbyte-integrations", "connectors", "source-paper", "source_paper", "fixtures", "fills.json")
        .normalize();
    final JsonNode sourceFills = Jsons.deserialize(Files.readString(fixtures));
    final InMemoryPuenteJournalWriter journal = new InMemoryPuenteJournalWriter();
    final PuenteReplicationPolicy policy = new PuenteReplicationPolicy(
        CONNECTION_ID,
        DESTINATION_ID,
        new PuenteConnectionPolicy()
            .withMode(PuenteConnectionPolicy.Mode.PAPER)
            .withRows(List.of(
                row("DAX", "DE40", "GER40", null),
                row("DJ", "US30", "WS30", null),
                row("ES", "US500", null, "future_not_supported"))),
        journal);
    final Map<String, JsonNode> destinationLedger = new LinkedHashMap<>();
    final List<String> destinationSymbols = new ArrayList<>();

    sourceFills.forEach(fill -> policy.prepare(record(fill)).ifPresent(prepared -> {
      assertTrue(policy.registerPending(prepared));
      final JsonNode outbound = prepared.message().getRecord().getData();
      destinationSymbols.add(outbound.get("symbol").asText());
      final String key = prepared.fillId() + ":" + DESTINATION_ID;
      final boolean inserted = destinationLedger.putIfAbsent(key, outbound) == null;
      assertTrue(policy.acknowledge(acknowledgment(prepared.fillId(), inserted)));
    }));

    assertEquals(List.of("GER40", "WS30"), destinationSymbols);
    assertFalse(destinationSymbols.contains("ES"));
    assertEquals(2, destinationLedger.size());
    assertEquals(2, journal.entries().stream().filter(entry -> "accepted".equals(entry.status())).count());
    assertTrue(journal.entries().stream()
        .anyMatch(entry -> "paper-es-1".equals(entry.sourceFillId())
            && "skipped".equals(entry.status())
            && "future_not_supported".equals(entry.reason())));
    journal.entries().forEach(entry -> {
      if (entry.originAt() != null) {
        assertEquals(
            entry.replicaAt().toInstant().toEpochMilli() - entry.originAt().toInstant().toEpochMilli(),
            entry.desfaseMs());
      }
    });

    sourceFills.forEach(fill -> assertTrue(policy.prepare(record(fill)).isEmpty()));

    assertEquals(2, destinationLedger.size());
    assertEquals(2, journal.entries().stream().filter(entry -> "duplicate".equals(entry.status())).count());
    assertEquals(2, journal.entries().stream().filter(entry -> "accepted".equals(entry.status())).count());
  }

  private static PuenteCrosswalkRow row(final String source,
                                        final String canonical,
                                        final String destination,
                                        final String exclusion) {
    return new PuenteCrosswalkRow()
        .withSourceSymbol(source)
        .withCanonicalInstrument(canonical)
        .withDestinationSymbol(destination)
        .withExclusionReason(exclusion)
        .withContractFactor(1D)
        .withScale(1D);
  }

  private static AirbyteMessage record(final JsonNode fill) {
    return new AirbyteMessage()
        .withType(AirbyteMessage.Type.RECORD)
        .withRecord(new AirbyteRecordMessage()
            .withStream(PuenteReplicationPolicy.FILL_STREAM)
            .withEmittedAt(0L)
            .withData(fill));
  }

  private static AirbyteMessage acknowledgment(final String fillId, final boolean inserted) {
    return new AirbyteMessage()
        .withType(AirbyteMessage.Type.STATE)
        .withState(new AirbyteStateMessage().withData(Jsons.jsonNode(Map.of(
            "_puente_ack", Map.of(
                "source_fill_id", fillId,
                "destination_id", DESTINATION_ID.toString(),
                "status", "accepted",
                "response", Map.of(
                    "accepted", true,
                    "persisted", true,
                    "inserted", inserted,
                    "replica_at", OffsetDateTime.now().toString()))))));
  }

}
