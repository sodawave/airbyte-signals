# Google Antigravity / Antigravity CLI

Follow **[`AGENTS.md`](AGENTS.md)** for this workspace.

## Session autoload

At session start, before any task, read `docs/OVERVIEW.md`, `docs/ADR.md`, and `README.md` (see AGENTS.md).

## BMAD bindings

| Surface | Path |
|---------|------|
| Method root | `_bmad/` |
| Google Antigravity skills | `.agent/skills/` |
| Antigravity CLI (AGY) / shared skills | `.agents/skills/` |

## Process

1. Start planned work with `bmad-help`.
2. Cycle: **idea debate → ADR (`docs/ADR.md`) → architecture spine + memlog → spec or epics/stories → implement → evidence**.
3. Before inventing architecture, read `_bmad-output/planning-artifacts/**/ARCHITECTURE-SPINE.md` and `.memlog.md`.
4. Respect context limits: prefer targeted reads over dumping entire trees; keep formal artifacts in `_bmad-output/`.
5. Chat in Spanish; technical documentation and formal BMAD artifacts are in English.

## Execution flows

- Planning agents and skills come from installed BMM/CIS/TEA modules — do not fork parallel agent definitions here.
- Implementation stays in existing native `airbyte-*` modules, including connectors under `airbyte-integrations/`. The stable branch is `master`; integration is `dev`.
- Rejected parallel root directories (`src/`, `protocol/`, `cdk/`, `core/`, `connectors/`, `platform/`) are forbidden.
