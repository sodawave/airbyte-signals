/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workers.general.puente;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-worker journal used when no durable writer is supplied and by connector-level tests.
 */
public class InMemoryPuenteJournalWriter implements PuenteJournalWriter {

  private final List<PuenteJournalEntry> entries = new ArrayList<>();
  private final Set<String> acceptedKeys = new HashSet<>();

  @Override
  public synchronized boolean hasAccepted(final String sourceFillId, final UUID destinationId) {
    return acceptedKeys.contains(key(sourceFillId, destinationId));
  }

  @Override
  public synchronized void append(final PuenteJournalEntry entry) {
    entries.add(entry);
    if ("accepted".equals(entry.status())) {
      acceptedKeys.add(key(entry.sourceFillId(), entry.destinationId()));
    }
  }

  public synchronized List<PuenteJournalEntry> entries() {
    return List.copyOf(entries);
  }

  private static String key(final String sourceFillId, final UUID destinationId) {
    return sourceFillId + ":" + destinationId;
  }

}
