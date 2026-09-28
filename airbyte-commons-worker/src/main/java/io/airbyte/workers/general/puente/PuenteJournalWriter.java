/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import java.util.UUID;

/**
 * Journal boundary used by replication policy. Implementations must preserve every attempt.
 */
public interface PuenteJournalWriter {

  boolean hasAccepted(String sourceFillId, UUID destinationId);

  void append(PuenteJournalEntry entry);

}
