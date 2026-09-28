/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable operation journal row for one Puente fill decision or destination attempt.
 */
public record PuenteJournalEntry(
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
