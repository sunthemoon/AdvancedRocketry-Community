# v1.0.0 security and provenance review

**Independent final-candidate review is outstanding.** This is an evidence map,
not a finding-free security certification or G0/G6 approval.

Supporting records:

- [Community provenance](../../provenance/v1.0.0-stable-core.md) identifies new
  community-authored changes without importing another mod's code/assets.
- [Transaction recovery](../../work/v1.0.0-transaction-recovery/VERIFICATION.md)
  retains the reproduced block/entity duplication boundary and staged recovery.
- [Legacy passenger recovery](../../work/v1.0.0-legacy-passenger/VERIFICATION.md)
  records duplicate-authority reconciliation and retained repair warnings.
- [Readiness](../../work/v1.0.0-passenger-readiness/VERIFICATION.md) and
  [logout cleanup](../../work/v1.0.0-passenger-logout/VERIFICATION.md) cover
  bounded UUID-only retries, entity-readiness decisions and transient cleanup.

Review the actual candidate diff and rerun relevant cases for sender, distance,
permissions, state, replay/rate limits and loaded-chunk validation on every C2S
entry. Exercise malformed/oversized packet and NBT inputs without reflecting
huge payloads into logs. Check server/client side isolation, duplicate/loss
invariants, future-schema refusal and limits on scans, queues and caches.

Inspect the final JAR and sources for stable metadata, original license
notices, complete provenance, duplicate/missing resources and unintended
credentials, private logs, worlds or local paths. Keep optional release-test
hooks opt-in and distinguish them from player-authorized gameplay.

Any confirmed Critical/High remains release-blocking; Medium requires review
and an explicit disposition. The reports above do not prove absence of such
findings across the final candidate. See [SECURITY.md](../../../SECURITY.md).
