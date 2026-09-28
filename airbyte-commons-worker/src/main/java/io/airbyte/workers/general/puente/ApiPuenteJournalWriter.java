/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.api.client.generated.ConnectionApi;
import io.airbyte.api.client.invoker.generated.ApiException;
import io.airbyte.api.client.model.generated.ConnectionIdRequestBody;
import io.airbyte.api.client.model.generated.PuenteJournalRead;
import io.airbyte.commons.json.Jsons;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists worker journal events through Airbyte's native config API.
 */
public class ApiPuenteJournalWriter implements PuenteJournalWriter {

  private final ConnectionApi connectionApi;
  private final UUID connectionId;

  public ApiPuenteJournalWriter(final ConnectionApi connectionApi, final UUID connectionId) {
    this.connectionApi = connectionApi;
    this.connectionId = connectionId;
  }

  @Override
  public boolean hasAccepted(final String sourceFillId, final UUID destinationId) {
    try {
      return connectionApi.listPuenteJournal(new ConnectionIdRequestBody().connectionId(connectionId))
          .getOperations()
          .stream()
          .anyMatch(operation -> operation.getDestinationId().equals(destinationId)
              && sourceFillId.equals(operation.getSourceFillId())
              && operation.getStatus() == PuenteJournalRead.StatusEnum.ACCEPTED);
    } catch (final ApiException e) {
      throw new IllegalStateException("Could not read Puente operation journal", e);
    }
  }

  @Override
  public void append(final PuenteJournalEntry entry) {
    try {
      connectionApi.appendPuenteJournal(new PuenteJournalRead()
          .id(UUID.randomUUID())
          .connectionId(entry.connectionId())
          .sourceFillId(entry.sourceFillId())
          .destinationId(entry.destinationId())
          .status(PuenteJournalRead.StatusEnum.fromValue(entry.status()))
          .reason(entry.reason())
          .sourceRecord(asMap(entry.sourceRecord()))
          .outboundRequest(asMap(entry.outboundRequest()))
          .destinationResponse(asMap(entry.destinationResponse()))
          .logs(Jsons.convertValue(entry.logs(), List.class))
          .originAt(entry.originAt())
          .receivedAt(entry.receivedAt())
          .sentAt(entry.sentAt())
          .replicaAt(entry.replicaAt())
          .desfaseMs(entry.desfaseMs())
          .attempt(entry.attempt()));
    } catch (final ApiException e) {
      throw new IllegalStateException("Could not append Puente operation journal", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(final JsonNode value) {
    return value == null ? null : Jsons.convertValue(value, Map.class);
  }

}
