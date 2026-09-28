# Ignite — Cursor CLI entry

This file is how a session starts in Cursor CLI. It is not how Claude Desktop starts. Claude Desktop ignites from the project conversation. That conversation already produced `docs/OVERVIEW.md` and `docs/ADR.md`. Do not run the idea debate again.

## Cursor CLI

1. Read `docs/OVERVIEW.md`, `docs/ADR.md`, and `README.md`.
2. Treat every Adopted ADR as closed. Do not re-forge ADR-001.
3. Continue the cycle from here: architecture spine and memlog, then spec or stories, then implementation, then evidence.
4. New product work is a branch cut from integration branch `dev`, merged back to `dev`. Do not commit on stable branch `master`.

## Claude Desktop

Do not use this file as the entry point. The project conversation is the ignition. Overview and ADR are written from that analysis. After they exist, continue the same cycle.

## Rules

- This file stays after the first ADR. It is the Cursor CLI protocol, not a one-time bootstrap.
- Chat with the human in Spanish. Technical documentation and formal artifacts stay English.
- Implement only in existing native `airbyte-*` modules, including connectors under `airbyte-integrations/`.
- Rejected parallel root directories (`src/`, `protocol/`, `cdk/`, `core/`, `connectors/`, `platform/`) are forbidden.
- Do not install BMAD inside an Airbyte module.
