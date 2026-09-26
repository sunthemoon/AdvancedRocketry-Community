# V120-MIG-03B2 — Rolling saved-world fixture refresh

- Status: READY_FOR_REVIEW; owner: delegated Rolling fixture worker; reviewer: root integration.
- Branch: `codex/v1.2.0-rolling-fixture`; base: `3dbe6884e6d8bff3c676c4f7cebee0824b507602`.
- Outcome: retain a bounded Rolling controller/ports chunk from an actual candidate-artifact
  save/restart scenario, with source hash checks and semantic tamper tests.
- Scope: Rolling restart harness metadata, fixture extractor, tests, task-specific evidence.
- Non-goals: Java changes, historical region reconstruction, cross-version Rolling migration,
  full version acceptance, long load tests, remote servers or real-client validation.
- Dependencies: existing Rolling S2 harness and current root worktree JAR
  `6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a`.
- Verification: fresh loopback 56062 baseline, short durable-save/forced-stop/recovery,
  final save/restart; source region hash, bounded NBT and resource/identity checks;
  fixture tampering unit tests. Root checkout is read-only.
- Writes: delegated script/test files, `fixtures/rolling`, `rolling-restart`, this task
  directory and own worktree `build` only. No commits or central plan/status edits.
- Evidence: real baseline and Rolling S2 passed; fixture extracted and verified; 16
  fixture tests passed. [Report](../ROLLING-WORLD-FIXTURE.md) distinguishes this
  regenerated candidate world from the unavailable historical Rolling region.
- Unfinished: independent review/integration, full version acceptance, historical
  upgrade and arbitrary crash-cut scenarios. No commits made.
