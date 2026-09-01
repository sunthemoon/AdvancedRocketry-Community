# CURRENT_VERSION

```yaml
current_version: v0.8.0
status: IN_PROGRESS
next_action: Implement the bounded satellite definition catalog and pure mission model
last_updated: 2026-09-01
prerequisite_version: v0.7.0
prerequisite_status: PASSED
prerequisite_merge_commit: b75e301f6cd77cfc1c1ade0e9b16c485f736c93b
base_commit: 6b33eaf0be1a01b7feceefe785d8fba7e9717b02
work_branch: codex/v0.8.0-progression-satellites
build: 1.20.1-0.8.0-dev
```

The immutable v0.7.0 acceptance, PR checks, merge identity, and exact
post-merge JAR reproduction are recorded in
[`../releases/v0.7.0/GATE-STATUS.md`](../releases/v0.7.0/GATE-STATUS.md).
ADR-010 fixes the bounded logical-satellite, SavedData, scheduler, and
exact-once claim design. Implementation is isolated to the v0.8.0 branch.
