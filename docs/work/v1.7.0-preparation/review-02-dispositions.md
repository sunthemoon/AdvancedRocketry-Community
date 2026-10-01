# v1.7.0 contract review round 2 — dispositions

Round 2 reviewed revision 2 at `1b3923a` and found 0 Critical, 2 High, 3 Medium,
10 Low and 4 Info findings. Verdicts: ADR-053, ADR-055, ADR-056, ADR-057 and
ADR-059 ACCEPT; ADR-054 and ADR-058 ACCEPT WITH REQUIRED CHANGES. Every round-1
finding was resolved except R1-H3, R1-M11, R1-L8 and R1-I1 (partially) and R1-M9
(not resolved); their remaining parts are the round-2 findings below. Each
answer is its own commit; revision 3 is the result.

| ID | Severity | Answer | Verification | Commit |
|---|---|---|---|---|
| R2-H1 | High | Removing an endpoint (or finding it `MISSING`) retires its ID; a block entity with a retired ID is `ENDPOINT_RETIRED` and inert anywhere, its contents frozen until `endpoint resolve`; owner and operator redirects need a durable retirement, are barrier flushes and follow the route rule; "moves again" and the removal drop apply only to records acknowledged at this endpoint; `PushReaction.BLOCK` and movers' non-movable tags as defence in depth; `endpoint retire` for lost endpoints, with the remaining ADR-051 §9-class residual stated | New two-destination `Redirect` model (removal, mover pickup and placement, crash restore, redirect): with retirement every interleaving delivers exactly once (1,789 states, two crashes); without it the mover path duplicates with no crash | `e802904` |
| R2-H2 | High | A source removal makes registered, not yet durable records durable with a barrier flush in the same tick | Model applies the barrier and counts only unregistered entries as destroyed; fault-free test asserts delivered + destroyed = payloads; new named cut and mutation | `0e6da59` |
| R2-M1 | Medium | Outside stations a field affects its owner and players who trust that owner through their own `field trust` command (≤ 32, kept across death) | Consent vectors | `435dac1` |
| R2-M2 | Medium | A failed settlement stays applied in memory and is retried (`REMOVAL_SETTLEMENT_PENDING`); every settled record is logged with its outcome; `transfer resettle` applies it if the server stopped before any successful write | — | `f7585ba` |
| R2-M3 | Medium | Stated worst cases (endpoint 384 B, record 2.5 KiB) and lower caps (2,048 endpoints, 256 records, tombstone pressure 4,096): accounted maximum about 2.4 MiB, every cap reachable together below the 3 MiB growth threshold; reference load 1,024 endpoints, 256 records, 1,024 tombstones | — | `217cedd` |
| R2-L1 | Low | Tombstones are removed only after the absence is read back in a load tag after a start | The transit model observes absence only through a reload; no unaudited duplicate remains with a lost write | `e802904`, `217cedd` |
| R2-L2 | Low | A removal runs a barrier only when something must be settled; bulk removals of busy endpoints come only from operator tools | — | `f251862` |
| R2-L3 | Low | P99 covers ticks without an endgame flush; flush ticks have their own budget | — | `217cedd` |
| R2-L4 | Low | Pair validity re-derived before the pre-load ticket; 100-tick wait after a cancelled ride | — | `f251862` |
| R2-L5 | Low | `ownerId()` defined; `actorId()` added before API 1.8 freezes | — | `f251862` |
| R2-L6 | Low | Fields inside stations capped at 1.00 g | Value vectors | `435dac1` |
| R2-L7 | Low | The migrator's epoch check becomes type-aware | — | `f251862` |
| R2-L8 | Low | Owner redirect route rule and write class | — | `e802904` |
| R2-L9 | Low | Spawn protection on dedicated servers only, as vanilla | — | `f251862` |
| R2-L10 | Low | Unbinding is never refused by the barrier spacing | — | `f251862` |
| R2-I1 | Info | Verified claims; no change | — | — |
| R2-I2 | Info | `check_mutations.py` committed: 14 mutations, exit 0 only when all are caught and the control is clean | All 14 caught | `c2cb649` |
| R2-I3 | Info | Vectors' purpose line updated; the evidence archive is committed with the acceptance | — | `c2cb649` |
| R2-I4 | Info | ADR-054 §14 states that restoring an older endgame file is outside the guarantee | — | `c2cb649` |
