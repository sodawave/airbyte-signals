---
title: 'Puente native Airbyte paper replication'
type: 'feature'
created: '2026-09-28'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'feadfd4ebf42150a01ee5dc4b611db0175e1513e'
context:
  - '{project-root}/docs/OVERVIEW.md'
  - '{project-root}/docs/ADR.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The current fork tip no longer contains Airbyte's OSS control plane, and the rejected implementation recreated protocol, runtime, persistence, and UI beside Airbyte. Puente must instead be a native extension of the last complete public Airbyte platform.

**Approach:** Keep `master` at `6d0ea5f58ff0db4b5dc9f2a8064db87935333655`, establish `dev` from the last complete revision `feadfd4ebf42150a01ee5dc4b611db0175e1513e` (latest complete tag: `v0.40.32` at `47deb482f2af3c25ba6f9295f16eaebfdff6af35`), and implement one paper-to-paper Puente slice through native connector contracts, connection persistence, replication workers, APIs, and `airbyte-webapp`.

## Boundaries & Constraints

**Always:** Store entity symbol maps in native source/destination configurations; persist resolved crosswalk rows, exclusions, contract factors, and scale policy on the native connection; exclude unsupported assets before destination write; make destination writes idempotent by source fill plus destination; journal every replicated or skipped fill with timestamps and persisted `desfase_ms`; decrement live quota only after destination acceptance; keep paper unlimited; use Spanish UI and English technical artifacts.

**Never:** Reuse rejected `/workspace/{protocol,cdk,core,connectors,platform}` product code; build a parallel server, protocol, persistence layer, Next app, or visual imitation; mutate `master`; infer same-symbol fallback, contract factors, or mappings at sync time; send excluded ES fills to the destination.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Mapped fill | DAX or DJ paper fill with saved row | GER40 or WS30 market replica using saved factor/scale | Journal accepted response and desfase; duplicate is idempotent |
| Excluded fill | ES row marked excluded | No destination write and no quota charge | Journal skip reason and desfase |
| Missing map | Symbol has no saved row | No destination write | Journal deterministic skip; never same-ticker fallback |
| Destination reject | Mapped fill rejected by connector | Preserve request and response | No quota charge; retry retains attempt history |

</frozen-after-approval>

## Code Map

- `airbyte-config/config-models/.../StandardSync*.yaml` -- add typed Puente connection and runtime input while retaining native connection identity.
- `airbyte-db/db-lib/.../configs/migrations` and `airbyte-config/config-persistence/...` -- persist Puente JSON and operation journal using existing Flyway/jOOQ repository patterns.
- `airbyte-api/src/main/openapi/config.yaml`, `airbyte-commons-server/.../handlers`, `airbyte-server/.../apis` -- expose native crosswalk, journal, and quota APIs.
- `airbyte-commons-worker/.../DefaultReplicationWorker.java` -- apply saved policy immediately before destination `accept`.
- `airbyte-integrations/connectors/source-paper` and `destination-paper` -- native SPEC/CHECK/DISCOVER/READ and WRITE actors with deterministic fixtures and destination idempotency.
- `airbyte-webapp/src/pages` and `airbyte-webapp/src/core` -- native entity map, connection crosswalk, quota, and journal surfaces.
- `_bmad/`, agent/tool files, `docs/OVERVIEW.md`, `docs/ADR.md` -- non-destructive BMAD overlay after platform lineage is established.

## Tasks & Acceptance

**Execution:**
- [x] Establish and document platform lineage and non-destructive BMAD overlay.
- [x] Add native Puente config models, migration/repository, API schemas/controllers, and generated bindings.
- [x] Add paper connectors and worker policy/journal integration.
- [x] Add actual Airbyte frontend forms, crosswalk table, quota indicator, and operation journal.
- [x] Add focused model, persistence, worker, connector-contract, API, and frontend tests.

**Acceptance Criteria:**
- Given native paper entities and a saved connection, when one sync reads DAX, DJ, and ES fills, then only DAX→GER40 and DJ→WS30 reach the destination and ES is journaled as excluded.
- Given the same source fill is replayed, when the destination receives it again, then no second replica or live quota debit occurs.
- Given any journal row with an origin timestamp, when replication or skip completes, then stored `desfase_ms` equals `replica_en - origen_en`.
- Given a user opens the native Airbyte frontend, when they edit an entity or connection and inspect a sync, then maps, saved rows, factors/scales, quota, and journal data are visible in Spanish.

## Implementation Notes

- Registered native paper connector definitions/specs and initialized the resolved DAX/DJ/ES paper policy when those entities are connected.
- Persisted policy on `connection` and append-only operation attempts in `puente_operation_journal`; accepted live attempts atomically decrement persisted quota.
- Applied policy in `DefaultReplicationWorker` immediately before destination acceptance and persisted accepted, rejected, duplicate, excluded, and missing-map outcomes through the native config API.
- Correlated protocol-compatible destination state acknowledgments by source fill and native destination ID; accepted journal rows and live quota debit now occur only after the paper destination commits its SQLite transaction.
- Added Spanish crosswalk, scale, quota, and journal controls to the native connection settings page.

## Spec Change Log

- 2026-09-28: Implemented the approved native paper-to-paper slice and focused verification coverage.

## Review Triage Log

## Design Notes

Connection policy is explicit JSON persisted with `StandardSync`; runtime receives the same frozen snapshot through `StandardSyncInput`. The worker makes a deterministic decision before `destination.accept`. Destination acknowledgements, not attempted writes, drive accepted journal state and live quota.

## Verification

**Commands:**
- `./gradlew :airbyte-config:config-models:generateJsonSchema2Pojo :airbyte-commons-worker:test :airbyte-config:config-persistence:test` -- native models, worker policy, and persistence pass.
- `./gradlew :airbyte-integrations:connectors:source-paper:integrationTest :airbyte-integrations:connectors:destination-paper:integrationTest` -- connector contracts and paper flow pass.
- `./gradlew :airbyte-commons-worker:test --tests io.airbyte.workers.general.DefaultReplicationWorkerTest.puenteWaitsForDestinationAcknowledgmentBeforeAccepting --tests io.airbyte.workers.general.puente.PuenteReplicationPolicyTest --tests io.airbyte.workers.general.puente.PuentePaperFlowIntegrationTest` -- acknowledgment ordering, rejection response retention, fixture mapping, exclusion, desfase, and replay idempotency pass.
- `pnpm --dir airbyte-webapp test --runInBand` -- native Puente UI tests pass.
- `git diff master -- protocol cdk core connectors platform` -- rejected parallel product trees remain absent.
