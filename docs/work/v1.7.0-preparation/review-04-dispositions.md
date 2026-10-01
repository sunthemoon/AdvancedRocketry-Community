# v1.7.0 contract review round 4 — dispositions

Round 4 reviewed revision 4 at `1e7d6c8` and found 0 Critical, 0 High, 0 Medium,
5 Low and 2 Info findings. Verdicts: all seven ADRs (053–059) ACCEPT. Every
round-3 finding is resolved. The Lows were recommended before the contracts
freeze; each answer is its own commit. The reviewer's confirmation round at
`30ab77b` found all five resolved, kept every verdict, and added one Info
(R4C-I1). Only ADR-054 and the reference models change.

| ID | Severity | Answer | Verification | Commit |
|---|---|---|---|---|
| R4-L1 | Low | `endpoint retire` settles its tombstone at once, and `tombstone settle <id>` settles one whose chunk save never succeeds. Neither then holds an endpoint place forever. Both are audited and, for that endpoint, give up the one-lost-write audit guarantee | `evict-before-saved-absence` shows what is given up | `8f05410` |
| R4-L2 | Low | `dispatched_through` is 0 from registration, so every tombstone records it. `resolve` returns a frozen entry above it, and only an evicted tombstone leaves entries unprovable. When the copy's persisted `next_seq` is at or below the value, `resolve` audits `SOURCE_ROLLBACK` | `Transit` now retires S on removal and freezes a crash-restored retired copy: 72,618 / 24,011 / 27,578 states (1,385 / 33,057 with cap eviction). The reviewer's `ESCROW SAVE_S REMOVE_S FLUSH_L CRASH` now returns the payload (named cut). Mutations `retired-source-registers-again`, `resolve-returns-delivered-entries` and `resolve-without-rollback-audit` are caught | `f7a4e33` |
| R4-L3 | Low | An ID frozen for its contents stays frozen for good. It has no index record or tombstone and counts toward no limit. `endpoint purge` is folded into `endpoint retire`, which is now refused while the endpoint's chunk is loaded | — | `de90066` |
| R4-L4 | Low | While saves are off no stub prunes. Once records and stubs reach 256, escrow stops with `TRANSIT_LIMIT` until saving resumes. `status` shows stubs, and the C13 operator guide item lists this | — | `a3fa20d` |
| R4-L5 | Low | A mutation search that hits its state limit reports INCONCLUSIVE and fails the run | 26 mutations caught, controls clean | `d372340` |
| R4-I1 | Info | Verified claims; no change | — | — |
| R4-I2 | Info | The evidence archive (`root-checks.zip`) is committed with the acceptance | — | — |
| R4C-I1 | Info | Confirmation round: `transfer purge <source> <seq>` also removes a quarantined outbox entry that has no record. It needs S's chunk to be loaded, and it destroys the payload with an audit line | — | this commit |
