# Final campaign checklists B: expansion kernel

Status: planned, not executed. Covers B01-B07 of
[FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md), section 4.2. The v1.1.1
maintenance document creates no additional campaign row and does not discharge
these deferred tests.

## Shared execution requirements

Use the candidate, authentication, driver markers, restart protocol, result
metadata and section-6 evidence naming specified in
`CAMPAIGN-CHECKLISTS-A.md`. P1 pins C4 (TASK-W396 names
`659bbcb6a`), not a historical v1.1 artifact. All rows use ordinary disposable
worlds, **not O2's marked world**; production switches stay off. No JVM test
properties are required. Default combination: Java 17, Minecraft 1.20.1,
Forge **47.4.10**, **JEI absent**. Record any additional 47.4.23 or JEI
15.56.0.205 run as a separate cell. DS has a real client on the development PC;
V1 requires its real GPU; V2 requires two independently authenticated accounts,
not FakePlayers. No test is run by writing this document.

P2 supplies **a v1.0 world**, **the reference workload world of docs/17
section 4**, and immutable fixture hashes. Derived scenes and valid/invalid
packs need a P2 manifest with paths, positions, UUIDs and before-state; sources
do not provide a single ready-made campaign fixture for every row. Use copies.
The version sources are [v1.1.0](../../../versions/V1.1.0-EXPANSION-KERNEL.md)
and [v1.1.1](../../../versions/V1.1.1-MAINTENANCE.md).

## B01 - Continue a v1.0 world after upgrade

**Header:** Phase 3; DS; development PC, one real client/account A; ordinary
world, switches off, no properties. **Source:** v1.1 section 10.1.

**Preconditions:** Copy P2 **a v1.0 world**, including a fueled Earth/Moon
rocket, station and satellite/research state; baseline Forge/JEI absent.
Capture the source's target IDs, flight journal, station orbit/rights, mission
and resource projections before first C4 load. This is distinct from A03's Beta.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Start the copied world on C4. | Supported upgrade; stable identities and retained original data. |
| 2 | [H] Travel Earth to Moon through the production rocket console. | Correct named target and one authoritative fuel debit; no default-Earth fallback. |
| 3 | [H] Return through the same production flow. | Earth/Moon behavior remains usable with intact cargo/seats. |
| 4 | [H] Travel to the existing authorized station. | Same station UUID, membership and orbit identity. |
| 5 | [H] Continue the saved satellite mission/claim flow. | Captured mission/reward state retained; no second award for an old claim. |
| 6 | [H] Save/stop the copied server. | Clean exit and complete saved projections. |
| 7 | [H] Reopen the same copy. | No repeated migration/resource reset. |
| 8 | [H] Compare the saved subsystem projections. | IDs and resource balances reconcile, including legitimate processing. |
| 9 | [H] Repeat steps 6 to 8 for the second restart. | Idempotent second reopen. |
| 10 | [H] Rehash the original and backup. | Neither source was changed. |

**Evidence to keep:** `phase3-ds/B01/` source/copy manifests, before/after
target/schema projections, each open log and exit, flight/station/claim receipts,
resource ledgers, client screenshots and original-hash comparison.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B02 - Two orbit contexts inside shared Space

**Header:** Phase 2 V1 and phase 3 DS; development PC, real GPU client/account
A and station owners defined in the fixture; ordinary world, switches off,
no properties. **Sources:** v1.1 sections 10.2 and 11.2.

**Preconditions:** P2 v1.0-world-derived copy with two stations in the same
shared Space Level, one orbiting Earth and one the Moon; baseline/JEI absent.
Use different fixture owners where ownership limits require it. Manifest gives
the two station UUIDs, disjoint regions, members, positions and orbit-body values.
Do not assume that Space's Level ID alone determines either body's environment.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Enter the Earth-orbit station as an authorized account. | Station context names Earth; region/environment agree with its record. |
| 2 | [H] Open its station/rocket interface. | Correct station and orbit body, not the Moon station's display. |
| 3 | [H] Enter the Moon-orbit station as its authorized account. | Same physical Space dimension but different station/orbit context. |
| 4 | [H] Open that station's interface. | Correct Moon orbit information and independent permissions. |
| 5 | [H] Attempt a request with the other station's non-member fixture account. | Permissions do not leak between the two regions. |
| 6 | [H] Save/stop the server. | Both station records preserved. |
| 7 | [H] Reopen and revisit each region. | Correct context and rights at each station. |
| 8 | [H] Repeat steps 6 to 7 for the second restart. | No cross-station state after reload. |
| 9 | [P] Review the two orbit displays against server records. | V1 visuals agree with the correct station's body; station climate is not a planet surface hazard. |

**Evidence to keep:** `phase3-ds/B02/` region/context/rights projections and
restart logs; phase-2 screenshots of each body's label/sky and the permission
response. This does not replace B05/A06's actual two-client results.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B03 - Atomic route reload and rejection

**Header:** Phase 3; DS; development PC, one real client/account A and console
operator; ordinary copy, switches off, no properties. **Source:** v1.1 section 10.3.

**Preconditions:** P2 v1.0-world-derived copy; baseline/JEI absent; a known
valid route pack and a separately hashed invalid candidate pack, plus originals.
Manifest supplies pack paths, route IDs, expected cost/reachability changes and
the deliberately invalid field. No exact pack name or numeric route change is
specified by section 10.3. Do not fabricate one as an acceptance requirement.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the active route catalog and current quote. | Baseline generation, target and server cost available. |
| 2 | [H] Install the valid route pack on the disposable copy. | Only intended fixture files change. |
| 3 | [H] Issue `reload` on the server console. | Valid route/catalog candidate is published. |
| 4 | [H] Reopen the destination interface. | Quote/reachability follows the new valid route data. |
| 5 | [H] Replace that pack with the invalid candidate. | Recorded invalid fixture, not an arbitrary production-pack edit. |
| 6 | [H] Issue `reload` again. | Reload rejection with bounded diagnostic; previous valid catalog remains active. |
| 7 | [H] Request the same destination quote. | Last valid target/cost retained; no empty catalog or fallback destination. |
| 8 | [H] Restore the valid pack. | A valid on-disk pack is present before restart. |
| 9 | [H] Issue `reload`. | Recovery to valid definitions without data loss. |
| 10 | [H] Run two clean restarts of this same copy using checklist A's protocol. | Route state and persisted targets remain correct after each. |

**Evidence to keep:** `phase3-ds/B03/` pack bytes/hashes, catalog/quote
projections, all reload diagnostics, client feedback and restart comparisons.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B04 - Restart a player's in-flight target

**Header:** Phase 3; DS; development PC, one real client/account A; ordinary
copy, switches off, no properties. **Source:** v1.1 section 10.4; section 8
also requires old in-flight transactions to finish under their original semantics.

**Preconditions:** P2 v1.0-world-derived copy, legal fueled rocket owned by A,
recorded stable target ID and route; baseline/JEI absent. Use the current C4
schema/protocol, not the historical flight=2/protocol=6 numbers as a C4 pin.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Select the fixture destination through A's rocket console. | Stable typed target and authoritative quote recorded. |
| 2 | [H] Confirm launch. | Durable active plan/target and fuel ledger created. |
| 3 | [H] Capture the in-flight phase, target and journal. | Before-state identifies the exact checkpoint. |
| 4 | [H] Save/stop while that flight is still active. | Normal restart case, not a claimed power-loss cut. |
| 5 | [H] Restart that same world. | Target ID and original plan semantics retained. |
| 6 | [H] Reconnect A. | Authorized recovery and one rocket/seat authority. |
| 7 | [H] Observe completion of the recovered flight. | Intended destination, not automatic Earth or another session's target; one debit. |
| 8 | [H] Save/stop and restart the settled world once more. | Second restart preserves destination, assets and resources without replay. |

**Evidence to keep:** `phase3-ds/B04/` selected/active/recovered target
projections, journal/fuel/seat comparisons, disconnect/rejoin logs and screenshots.
An ordinary flight is not evidence for upgrading a historical in-flight fixture;
that missing migration fixture must be reported separately.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B05 - Concurrent independent target selections

**Header:** Phase 4, sitting 1; V2; authenticated real accounts A/B and two
clients; ordinary world, switches off, no properties. **Source:** v1.1 section 10.5.

**Preconditions:** Baseline/JEI absent; P2-derived scene with a separate legal
rocket and permitted destination per account, distinct target IDs and enough
fuel; manifest binds each account, rocket, menu and target. O5 supplies concurrent
input to the two signed-in windows; no FakePlayer substitutes.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Open A's rocket destination interface. | A's rocket/menu identity recorded. |
| 2 | [P] Open B's distinct rocket interface. | B's independent rocket/menu identity recorded. |
| 3 | [P] Select the two different targets concurrently on the coordinated cue. | Each client retains its own choice and quote; log the actual input/receipt times. |
| 4 | [P] Confirm A's launch. | A's plan contains A's target and costs. |
| 5 | [P] Confirm B's launch. | B's plan contains B's target, unaffected by A. |
| 6 | [H] Compare both plans and arrivals against the initial account map. | No target/session/fuel/seat crossover. |

**Evidence to keep:** `phase4-v2/sitting-1/` both videos/logs, coordinated
step table and server plan/arrival projections. Sources give no millisecond
concurrency tolerance; record observed simultaneity rather than invent one.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B06 - Readable destinations, locked routes and test content

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary copy,
switches off, no properties. **Sources:** v1.1 sections 11.1, 11.3 and 11.4.

**Preconditions:** P2 v1.0-world-derived scene with Earth/Moon travel, one
unknown/removed destination, one locked route and the data-pack-only `test_mars`
body of v1.1 section 6.9; baseline/JEI absent. Record its actual namespaced ID
from the pack, not the later built-in Mars. English/Chinese, scales 2/3/4 and
windowed/fullscreen follow the campaign V1 environment.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Open the destination interface on the Earth/Moon route. | Readable names rather than raw internal identity as the player-facing label. |
| 2 | [H] Select the unknown/removed destination fixture. | Readable refusal, no automatic fallback or asset loss. |
| 3 | [H] Select the locked-route fixture. | Lock and reason visible; no unauthorized launch. |
| 4 | [H] Install the valid `test_mars` data-only pack. | No Java enum or production code change. |
| 5 | [H] Issue `reload`. | Valid test body/routes appear through data. |
| 6 | [H] Reopen the destination interface. | Test body discoverable and explicitly identified as test content. |
| 7 | [H] Complete the old Earth/Moon selection/launch flow. | No visible or functional regression. |
| 8 | [P] Review all language/scale/window cells. | Names and refusal/test labels remain readable; no critical clipping. |

**Evidence to keep:** Phase-2 screenshots of valid/unknown/locked/test
destinations, pack diff/hash, reload logs and old-route server receipts.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## B07 - BodyContext cache and full-workload regression

**Header:** Phase 5; REF; accepted 4-vCPU/8-GiB Debian 12 node, normal user,
workload accounts/equivalent under O7; ordinary world, switches off, no properties.
**Sources:** v1.1 sections 13.1 and 13.4; [quality budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md) section 4.

**Preconditions:** P2 **reference workload world of docs/17 section 4** and
the two-station orbit scene from B02; baseline/JEI absent, heap <=4 GiB, RSS/tick
sampling plus a cache/query profiling method. Root must identify the comparable
baseline and meaning of significant regression; the version gives no percentage.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Record the full workload's MSPT/RSS/GC and query/cache profile. | Same load as the documented reference, not an empty world. |
| 2 | [H] Query context repeatedly in both station regions. | Correct per-region body; cache avoids per-tick full-table scans. |
| 3 | [H] Apply the fixture's valid context/catalog invalidation. | Subsequent query reflects the new generation, not stale context. |
| 4 | [H] Repeat the profile after invalidation. | Cache recovers without monotonic growth or unbounded scanning. |
| 5 | [H] Compare the complete regression interval with the matched baseline. | Record absolute budgets and baseline deltas; mean <=25 ms, P95 <=50 ms, P99 <=100 ms, no sustained RSS above 5.5 GiB. |

**Evidence to keep:** `phase5-ref/<run>/B07/` workload/environment record,
raw samples, cache invalidation/query traces, baseline identity and calculated
deltas. No numerical claim of no significant regression without a defined comparison.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## Open questions for Root

- **B01/B04:** P2 must name the v1.0 fixture and any historical in-flight
  variant, with schema/target projections. Section 10 supplies no world path,
  coordinates or flight-stop timing. Do not equate a new C4 flight with historical
  in-flight migration coverage.
- **B02:** Supply the two-station region/owner manifest and supported setup
  method; a single account's ownership limit must not be bypassed silently.
- **B03/B06:** Supply valid/invalid route packs, unknown/locked-route cases and
  data-only `test_mars` pack against C4. The later built-in Mars does not prove
  the original data-only extension requirement.
- **B05:** Confirm O5's concurrency operator/input arrangement. The sources
  supply no numerical overlap tolerance; keep precise observed receipt times.
- **B07:** Name the cache observation method, invalidation fixture, matched
  regression baseline and review rule for significant degradation. Absolute
  reference budgets still apply even while that comparison is unresolved.
