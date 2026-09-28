/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.airbyte.api.client.generated.ConnectionApi;
import io.airbyte.api.client.invoker.generated.ApiException;
import io.airbyte.api.client.model.generated.PuenteAcceptedFillRequest;
import io.airbyte.api.client.model.generated.PuenteAcceptedFillResponse;
import io.airbyte.api.client.model.generated.PuenteJournalRead;
import io.airbyte.commons.json.Jsons;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiPuenteJournalWriterTest {

  private final ConnectionApi api = mock(ConnectionApi.class);
  private final UUID connectionId = UUID.randomUUID();
  private final UUID destinationId = UUID.randomUUID();
  private final ApiPuenteJournalWriter writer = new ApiPuenteJournalWriter(api, connectionId);

  @Test
  void usesTargetedAcceptedFillLookup() throws Exception {
    when(api.hasAcceptedPuenteFill(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PuenteAcceptedFillResponse().accepted(true));

    assertTrue(writer.hasAccepted("fill-1", destinationId));

    final ArgumentCaptor<PuenteAcceptedFillRequest> request = ArgumentCaptor.forClass(PuenteAcceptedFillRequest.class);
    verify(api).hasAcceptedPuenteFill(request.capture());
    assertEquals(connectionId, request.getValue().getConnectionId());
    assertEquals("fill-1", request.getValue().getSourceFillId());
    assertEquals(destinationId, request.getValue().getDestinationId());
  }

  @Test
  void mapsCompleteJournalEntry() throws Exception {
    final OffsetDateTime origin = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    writer.append(new PuenteJournalEntry(
        connectionId, "fill-1", destinationId, "rejected", "unsupported_symbol",
        Jsons.jsonNode(Map.of("symbol", "DAX")), Jsons.jsonNode(Map.of("symbol", "GER40")),
        Jsons.jsonNode(Map.of("accepted", false)), Jsons.jsonNode(List.of("rejected")),
        origin, origin.plusNanos(1), origin.plusNanos(2), origin.plusNanos(3), 0L, 2));

    final ArgumentCaptor<PuenteJournalRead> request = ArgumentCaptor.forClass(PuenteJournalRead.class);
    verify(api).appendPuenteJournal(request.capture());
    final PuenteJournalRead operation = request.getValue();
    assertEquals(connectionId, operation.getConnectionId());
    assertEquals(destinationId, operation.getDestinationId());
    assertEquals(PuenteJournalRead.StatusEnum.REJECTED, operation.getStatus());
    assertEquals("unsupported_symbol", operation.getReason());
    assertEquals("DAX", operation.getSourceRecord().get("symbol"));
    assertEquals("GER40", operation.getOutboundRequest().get("symbol"));
    assertEquals(false, operation.getDestinationResponse().get("accepted"));
    assertEquals(List.of("rejected"), operation.getLogs());
    assertEquals(2, operation.getAttempt());
  }

  @Test
  void wrapsApiFailures() throws Exception {
    when(api.hasAcceptedPuenteFill(org.mockito.ArgumentMatchers.any())).thenThrow(new ApiException("lookup failed"));
    doThrow(new ApiException("append failed")).when(api).appendPuenteJournal(org.mockito.ArgumentMatchers.any());

    assertThrows(IllegalStateException.class, () -> writer.hasAccepted("fill-1", destinationId));
    assertThrows(IllegalStateException.class, () -> writer.append(new PuenteJournalEntry(
        connectionId, "fill-1", destinationId, "accepted", null, null, null, null,
        Jsons.jsonNode(List.of()), null, OffsetDateTime.now(), null, OffsetDateTime.now(), null, 1)));
  }

}
