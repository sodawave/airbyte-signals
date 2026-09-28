# Agent instructions — Puente on Airbyte

## What this repo is

- **Product:** Puente, implemented inside the native Airbyte repository. It replicates fills from a source connector to a destination connector. It does not broker, advise, or custody.
- **Method:** BMad Method at the workspace root (`_bmad/`, skills under `.agents/skills/` and `.claude/skills/`).
- **Code:** existing native Airbyte modules (`airbyte-*`, including connectors under `airbyte-integrations/`). Extend those modules in place.
- BMAD is the development process. Do not invent a parallel methodology, agent set, workflow, or implementation tree.

## Session autoload

Before taking a task, read, in order:

1. `docs/OVERVIEW.md`
2. `docs/ADR.md`
3. `README.md`

If a file is missing, say so. Do not continue silently.

Cursor CLI then follows `IGNITE.md`. Claude Desktop does not. Its ignition is the project conversation. See ADR-010.

## Branches

Work branch, then evidence, then human OK, then merge to `dev`. Do not develop on `dev` or `master` unless the human explicitly agrees.

- `feat/*`, `fix/*`, `chore/*`, `docs/*` — one branch per task, cut from `dev`
- `dev` — integration
- `master` — stable. Merge from `dev` only when the human asks

The stable branch is `master`, not `main`.

## Language

- Chat with the human in Spanish.
- Technical documentation and BMAD artifacts (ADR, overview, specs) are written in English.

## Working tree

- Product code belongs in the existing native Airbyte modules. Follow each module's current layout and conventions.
- Rejected parallel root directories are forbidden: do not create `src/`, `protocol/`, `cdk/`, `core/`, `connectors/`, `platform/`, or any alternate product tree alongside the native Airbyte modules.
- Method files stay at the repo root (`_bmad/`, `_bmad-output/`, `docs/OVERVIEW.md`, `docs/ADR.md`, `AGENTS.md`, `IGNITE.md`).
- Do not replace an inherited Airbyte `README.md` or the existing Airbyte `docs/` directories.
- Do not install BMAD under any Airbyte module.
- Do not rewrite `.claude/skills/my-prs`.

## Quality

- Follow spec, plan, implement, verify with evidence.
- Prefer tests for behavior. The paper sync tests (DAX to GER40, DJ to WS30, excluded future, desfase) are the bar for the core.
- Never claim a test passed without running it in this session.

## Authorship

- **Owner:** Sodawave. Conception and architecture belong to the human.
- **Forbidden on commits and PRs:** `Co-authored-by: Cursor`, `cursoragent@cursor.com`, `Made-with: Cursor`, or any AI authorship trailer.

## Project

Puente overlays the Airbyte connector model: templates, entities, a saved crosswalk, and a sync that writes an operation log. Paper connectors run without keys. Alpaca, Bybit, Kraken, and Binance run with user keys. Paid quota counts a live destination accept only.
