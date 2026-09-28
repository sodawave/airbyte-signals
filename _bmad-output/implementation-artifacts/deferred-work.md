- source_spec: `/workspace/airbyte-native/_bmad-output/implementation-artifacts/spec-puente-native-airbyte.md`
  summary: Verify or eliminate the crash-after-paper-commit window across ephemeral destination containers.
  evidence: The native platform journal prevents normal replay, but a process or node loss after the destination SQLite commit and before the acknowledgment is journaled may require deployment-level persistent storage or connector-side recovery state to prove end-to-end idempotency.
