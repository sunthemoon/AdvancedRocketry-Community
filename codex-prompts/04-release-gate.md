# Codex Task 04 — Execute the release gate

Read:

- `AGENTS.md`
- `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`
- current version document
- `docs/templates/RELEASE-EVIDENCE-TEMPLATE.md`
- all current work/test/manual/performance reports

This is a verification task, not a feature task.

Actions:

1. Verify repository is clean and commit/build identity is known.
2. Run the full required build/test/DataGen/GameTest suite.
3. Verify dedicated-server, persistence/recovery, security, performance, manual and provenance evidence.
4. Inspect the built JAR and calculate SHA-256.
5. Verify version, tag plan, README status, Known Issues and license statements.
6. Fill `docs/releases/<version>/RELEASE-EVIDENCE.md`.
7. Update Gate status truthfully.
8. Do not hide or fix unrelated failures in this same task; report them as blockers.
9. Recommend either `BLOCKED` or `READY_FOR_HUMAN_APPROVAL`.
10. Do not create or move a Git tag unless the human explicitly performs/approves that separate step.
