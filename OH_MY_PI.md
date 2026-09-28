# Pi (Oh My Pi)

Follow **[`AGENTS.md`](AGENTS.md)** for this workspace.

## Session autoload

At session start, before any task, read `docs/OVERVIEW.md`, `docs/ADR.md`, and `README.md` (see AGENTS.md).

## BMAD bindings

- Method root: `_bmad/`
- Pi skills: `.agents/skills/` (installed with tool id `pi`)

## Process

1. Cursor CLI starts at `IGNITE.md`. Do not re-forge adopted ADRs.
2. Cycle after that: **architecture spine + memlog → spec or epics/stories → implement → evidence**. Stable branch is `master`; integration is `dev`.
3. Honor spine **Binds / Prevents / Deferred / Rejected**.
4. Chat in Spanish; technical documentation and formal BMAD artifacts are in English.
5. Implement only in existing native `airbyte-*` modules. Rejected parallel root directories (`src/`, `protocol/`, `cdk/`, `core/`, `connectors/`, `platform/`) are forbidden.

## CLI shortcuts / macros

Prefer invoking installed BMAD skill names directly (e.g. `bmad-help`, `bmad-spec`, `bmad-build`) rather than custom one-off prompt macros. If you add local Pi aliases, keep them as thin wrappers over those skills and document them under `docs/`.
