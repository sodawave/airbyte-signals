# Claude Code

Follow **[`AGENTS.md`](AGENTS.md)** for this workspace.

## Session autoload

Imported into every session via memory imports:

@docs/OVERVIEW.md
@docs/ADR.md
@README.md

## Process (generic)

Cursor CLI starts at `IGNITE.md`. Claude Desktop starts from the project conversation, not from `IGNITE.md`. ADR-001 is already adopted. Do not re-forge it.

1. **BMAD required** at repo root (`_bmad/`, `.claude/skills/` and `.agents/skills/`). Never install BMAD under an Airbyte module.
2. Cycle after the adopted ADRs: **architecture spine + memlog → spec or epics/stories → implement → evidence**. Stable branch is `master`. Tasks branch from `dev`.
3. Before inventing architecture: read `_bmad-output/planning-artifacts/**/ARCHITECTURE-SPINE.md` and `.memlog.md`. Obey **Binds / Prevents / Deferred / Rejected**. Do not reopen closed paths without a new adopted spine decision.
4. Chat in **Spanish**; technical documentation and BMAD formal artifacts are in **English**.
5. Implement only in existing native `airbyte-*` modules. Rejected parallel root directories (`src/`, `protocol/`, `cdk/`, `core/`, `connectors/`, `platform/`) are forbidden.

## Skills

BMAD skills live under `.claude/skills/` (installed via `npx bmad-method install`). Prefer those over ad-hoc process inventions.

## Authorship (canonical)

Follow **Authorship (canonical)** in [`AGENTS.md`](AGENTS.md). Never add Cursor/`cursoragent` co-author or Made-with trailers. Project + human own conception and architecture.
