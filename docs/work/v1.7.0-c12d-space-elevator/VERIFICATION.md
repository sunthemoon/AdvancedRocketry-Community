# V170-ELEV-01 (C12d) space elevator

Date: 2026-10-02. Scope: ADR-059, the space elevator on the ADR-054 §11
transit ledger, with the ADR-044 warp interlock and the deletion guard. No
release Gate is claimed.

Base: `d75260c` (the C12c evidence commit). Commits on
`codex/v1.7.0-endgame-systems`, one layer each:

| Commit | Layer |
|---|---|
| `3e2b1d4` | `CargoEndpointBlockEntity` and `CargoStorage` extracted from the railgun (no behaviour change: 1,340 JUnit tests and 326 GameTests passed before the commit) |
| `4cf1e01` | `ElevatorPair`, the `elevator_pairs` root section, `ElevatorRules` and the elevator codes |
| `aa51488` | The `ElevatorStationGuard` port: warp request, confirmation and commit, and station deletion |
| `7c4fff3` | Anchor and terminal, bind and unbind, cargo, rides, menu, screen, tether, commands, DataGen content, two COMMON values |
| `5a12181` | Three GameTests |
| `7492ca7` | Implementation log and CHANGELOG |

The evidence run used `7492ca7`. The
[implementation log](../v1.7.0-implementation-log.md) ("C12d progress")
records every decision for the C12 review.

## Delivered

| Area | Contract | Result |
|---|---|---|
| ADR-045 open decisions | ADR-059 §1 | Pairs in the endgame root (`elevator_pairs`, ≤ 1,024, ≤ 512 bytes each), the anchor's Level key stored at bind, a random `pair_id`, one pair per station, anchor, terminal and anchor column |
| Endpoints | §2 | Anchor: a 5 × 2 × 5 structure-only multiblock on a surface Level; terminal: a block in a committed station region, not within 2 blocks of the pad (`TERMINAL_ON_PAD`); both with input 4, receive 9 and 500,000 FE (≤ 20,000 per tick); the 3 × 3 platform on top |
| Bind | §3 | From the terminal's menu, in the section's order (ADR-045 rules as `ELEVATOR_RULE <rule>`, terminal, anchor, owner, cardinality, `WARP_PENDING`, admission); a barrier flush before success; the cardinality checked again inside the write |
| Validity at use | §3 | Re-derived at every action: ADR-045 rules 1, 3, 4, 5, the stored Level key (`PAIR_LEVEL_CHANGED`), both endpoints ACTIVE; a failure changes nothing and keeps the pair |
| Unbind | §4 | Station owner, anchor owner or operator, from either menu or `/arce endgame elevator unbind <station_id>`, whatever the validity; never loads the far end; cancels the pair's rides; a barrier flush |
| Warp interlock and deletion | §5 | Request, confirmation and commit refuse with `ELEVATOR_BOUND`; deletion with `ELEVATOR_REFERENCES` also for cargo of the region; both fail closed (`ENDGAME_UNAVAILABLE`) without the guard or the root; the station module imports no endgame class |
| Cargo | §6 | The whole input buffer, to the other end of the pair, valid at escrow; 20,000 FE × percent, 200 ticks; endpoint-addressed delivery that completes after an unbind; the elevator redirect rule |
| Access | §7 | Ride and ship: station owner, members, operators, with the anchor owner a station member (`ANCHOR_OWNER_NOT_MEMBER`); bind: station owner or operator; unbind: station owner, anchor owner, operator |
| Rides | §8 | Request order, ≤ 4 per endpoint and 64 on the server, one `elevator_arrival` ticket (distance 0, 300 ticks) at the server-derived arrival, a 100-tick countdown with its cancellations, one commit per tick with the energy (50,000 FE × percent), the `FULL` arrival chunk (≤ 100 more ticks, then `ARRIVAL_UNLOADED`), the arrival endpoint, two free blocks, bounds, zones and the `TELEPORT` event; the debit only once the rider stands at the arrival; cooldowns of 200 ticks after a commit and 100 after a cancellation |
| Menus, visuals, audit | §9 | Device view with the pair state and failing rule, the far end, the anchor selection, settings and counts; a tether beam while the pair is valid; bind, unbind, refusals, escrow, ride request, cancel and commit audited; `/arce endgame elevator inspect <station_id>` |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `7492ca7` | Exit 0, 278 s. **329 required GameTests** passed (326 earlier, 3 elevator). `git status --porcelain --untracked-files=no` after DataGen: empty |
| `gradlew test --rerun --no-daemon` | Exit 0, 124 s. **1,350 JUnit tests / 248 suites**, 0 failures, errors or skips |
| API jar against the C12c run | SHA-256 `24b6527e…5218ab` in both: no public API change |
| Ten mutations of the elevator rules (`scripts/mutations.py`), elevator tests each | **10 of 10 killed**; the files were restored byte for byte |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the earlier runs.

### Mutation checks

| Mutation | Killed by |
|---|---|
| E1 a non-operator binds another owner's anchor | `ElevatorTest.aBindReportsTheFirstFailureInItsOrder` |
| E2 validity ignores a remapped Level | `validityIsReDerivedAndAFailureNamesItsRule` |
| E3 access ignores an anchor owner who left the station | `membersRideAndShipStrangersDoNotAndUnbindIsWide` |
| E4 no limit of pending rides per endpoint | `aRideRequestAndItsCommitFollowTheirOrders` |
| E5 a commit waits forever for an unloaded arrival | the same |
| E6 the cargo cost without its percent | `costsFollowTheEnergyPercentWithinTheBuffer` |
| E7 deletion ignores cargo of the region | `theStationGuardFailsClosed`, `ElevatorGuardTest` |
| E8 two pairs on one anchor column | `thePairsSectionKeepsOnePairPerStationEndpointAndColumn` |
| E9 the anchor owner cannot unbind | `membersRideAndShipStrangersDoNotAndUnbindIsWide` |
| E10 a warp allowed while the endgame root is down | `theStationGuardFailsClosed`, `ElevatorGuardTest` |

## Tests added

- `ElevatorTest` (8): the bind order step by step, validity with the
  failing rule named, ride and ship access and unbind authority, the ride
  request and commit orders (`WAIT`, `ARRIVAL_UNLOADED`), the costs and
  their bounds, the guard decisions, the pairs section's one-pair rules and
  bound, and the root round trip (byte-stable, pins, a pair naming no
  endpoint refused, an unknown key refused).
- `ElevatorGuardTest` (1): the guard on a service root, failing closed
  before start, a pair pinning warps and deletion, cargo inside or outside
  the station's region, and a removed destination's tombstone.
- `CommonConfigTest`: the two elevator values and their defaults (54 in
  total).
- `ElevatorGameTests` (3), each in its own batch, with an anchor in the
  Overworld (Earth) and a terminal on a station orbiting Earth:
  - bind; the warp guard and `/arce station admin delete` refused while
    bound; cargo up (80,000 FE left of 100,000) and down through
    registration, claim and the saved move; an unbind while cargo is in
    flight, after which the cargo still arrives, the warp is allowed and the
    deletion still waits for the cargo of the region; pruning;
  - a stranger's ride refused (`UNAUTHORIZED`); a member's ride up after the
    countdown, with exactly one `elevator_arrival` ticket while pending and
    none after (50,000 FE debited); a ride down cancelled at its commit by a
    block above the arrival platform, at no cost and without a ticket; a ride
    down after the cooldowns;
  - a pair whose stored Level no longer matches its body: shipping refused
    (`PAIR_LEVEL_CHANGED`), the pair kept, a player's break of the paired
    anchor refused, unbind still possible; then, with the switch off, an
    arrival still claimed and moved, a new shipment refused
    (`SYSTEM_DISABLED`) and unbind still possible.

## Not verified

- `ARRIVAL_UNLOADED` on a running server: the pre-load ticket always loaded
  the arrival chunk in time (the decision is a unit test).
- Remapping a body's Level through a real catalog reload (simulated by a
  stored Level key that differs; the rule itself is a unit test).
- The menus, the screen and the tether on a real client (V1 is `[H]`), and
  riders who disconnect, die or step off during a countdown (the cancellation
  path is the same as for the obstruction, and is covered there).
- Elevator crash cuts with a forced stop during a countdown and right after
  a commit (S2, C13).

Next: the C12 independent review of C12a to C12d, then C13.
