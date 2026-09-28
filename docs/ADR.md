# Puente architecture decisions

## ADR-001 — Extend the complete OSS Airbyte platform

**Status:** Adopted.

`master` remains at `6d0ea5f58ff0db4b5dc9f2a8064db87935333655`. Development is based on `feadfd4ebf42150a01ee5dc4b611db0175e1513e`, the immediate parent of the commit that deleted Airbyte's platform code. The last complete tagged release is `v0.40.32` at `47deb482f2af3c25ba6f9295f16eaebfdff6af35`.

Puente extends Airbyte's actors, connection configuration, workers, persistence, API, and webapp. It must not recreate those layers beside Airbyte.

## ADR-002 — Native connector entities

**Status:** Adopted.

A broker template is an Airbyte source or destination connector. A broker entity is a configured Airbyte source or destination actor. Its editable native configuration contains the account's symbol-to-canonical map, supported asset classes, and tradable symbols.

## ADR-003 — Persist a resolved connection policy

**Status:** Adopted.

An Airbyte connection stores resolved rows containing source symbol, canonical instrument, destination symbol or exclusion reason, contract factor, and scale. Syncs consume this frozen policy. They never infer a same-symbol fallback, query a model, or recalculate the crosswalk per fill.

## ADR-004 — Exclude before destination write

**Status:** Adopted.

The replication worker evaluates the saved row before forwarding a record. Missing and excluded rows produce journaled skips and no destination call or quota debit.

## ADR-005 — Replicate fills idempotently

**Status:** Adopted.

Only fills are replicated. The idempotency key is source fill id plus destination actor id. Destination acceptance, not an attempted write, marks an operation accepted and decrements live quota. Paper entities have unlimited quota.

## ADR-006 — Persist the operation journal

**Status:** Adopted.

Each accepted, rejected, duplicate, or skipped fill retains native input, outbound body, destination response, origin/received/sent/replica timestamps, logs, attempts, and persisted `desfase_ms = replica_en - origen_en` when origin time is available.

## ADR-007 — Use the actual Airbyte frontend

**Status:** Adopted.

Entity maps, connection crosswalk and scale, live quota, and operation journal are Spanish surfaces inside `airbyte-webapp`. No separate Next.js application or visual imitation is permitted.

## ADR-008 — Non-destructive BMAD overlay

**Status:** Adopted.

BMAD workflow files may be added after establishing the native platform base. They must not replace Airbyte's README, inherited docs, existing skills, or product code.
