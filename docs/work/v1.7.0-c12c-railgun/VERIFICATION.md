# V170-RAIL-01 (C12c) railgun cargo launcher

Date: 2026-10-02. Scope: ADR-056, the railgun on the ADR-054 §11 transit
ledger of C12b. No release Gate is claimed.

Base: `34ce112` (the C12b evidence commit). Commits on
`codex/v1.7.0-endgame-systems`:

| Commit | Content |
|---|---|
| `05695e9` | The railgun: route rule and quote, launch order, storage, block entity, block, menu, screen, redirect rule, effects, pattern, DataGen content, two COMMON values |
| `c363171` | Five GameTests on a running server |
| `5f370a5` | Implementation log and CHANGELOG |
| `7b5193e` | A unit-test case found by the mutation checks (below) |
| `7dc17f8`, `d2f30ec` | Two GameTest fixture fixes found by the first evidence run (below) |

The evidence run used `d2f30ec`. The
[implementation log](../v1.7.0-implementation-log.md) ("C12c progress")
records every decision for the C12 review.

## Delivered

| Area | Contract | Result |
|---|---|---|
| Scope | ADR-056 §1 | Items only, between two railguns; a destination is one of the owner's railgun endpoints, never a position, player, entity or chunk |
| Structure and state | §2 | 3 × 6 × 3 structure-only pattern (casing base, iron barrel, casing muzzle), four rotations; input 4 slots (insert-only for automation), receive 9 slots (extract-only), 1,000,000 FE buffer taking ≤ 50,000 FE per tick; the ledger's source and destination state; destination, `auto`, redstone mode, minimum stack size 1..64 |
| Route rule and classes | §3 | `NO_TARGET`, `TARGET_FOREIGN` (operators may select any owner's railgun), `BODY_UNAVAILABLE`, `ROUTE_OUT_OF_SYSTEM` from the index and the live catalog, no chunk read; `LOCAL` `min(25,000 + 10 d, 250,000)` FE and `min(max(20 + d/64, 20), 200)` ticks, `ORBITAL` 250,000 FE and 600 ticks, times `energyPercent` (10..400); the C10 vectors match |
| Launch | §4 | The first input stack of the minimum size, escrowed with its cost in one tick after the source reconciles; at most one launch per railgun per 20 ticks and `endgame.railgun.launchesPerTick` (≤ 4) on the server, round-robin; refusals change nothing and keep the section 4 order |
| Arrival | §5 | Claims while the destination is loaded and has room; an unloaded destination leaves the record `ARRIVED` and nothing is loaded; a removed destination leaves `DESTINATION_MISSING` until an owner or operator redirect (to another of the owner's railguns in the system, or back to the source) or a purge |
| Menu, visuals, audit | §6 | Device view with buffers, energy, destination (label, body, Level, position, class, cost, travel), settings, outbox, in-transit, incoming and receipt counts, last code; buttons for destination, launch, auto, redstone and minimum stack ±1/±16; a launch flash and streak and an arrival flash as block events, at most 8 per client; escrow, settings and refusals audited |
| Endpoint rules | ADR-054 §9, §9.1 | Registration on a saved tag, freeze of a returning retired copy, the busy break rule for non-operators, removal settlement from live state, a position-conflict copy that is never attached and settles nothing |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `d2f30ec` | Exit 0, 307 s. **326 required GameTests** passed (321 earlier, 5 railgun). `git status --porcelain --untracked-files=no` after DataGen: empty |
| `gradlew test --rerun --no-daemon` | Exit 0, 127 s. **1,340 JUnit tests / 246 suites**, 0 failures, errors or skips |
| API jar against the C12b run | SHA-256 `24b6527e…5218ab` in both: no public API change |
| Eight mutations of the pure railgun rules (`scripts/mutations.py`), railgun tests each, at `7b5193e` | **8 of 8 killed**; the files were restored byte for byte |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the earlier runs.

### Mutation checks

| Mutation | Killed by |
|---|---|
| R1 the route rule's owner check removed | `RailgunTest.theRouteRuleRefusesInItsOrderAndClassifiesTheRoute` |
| R2 the star-system check removed | the same |
| R3 `LOCAL` without the same body | the same, after `7b5193e` |
| R4 the local travel time without its 200-tick cap | `theQuotesMatchTheReferenceVectors` |
| R5 the source itself accepted as a launch destination | the route rule test |
| R6 the minimum stack size ignored | `thePayloadIsTheFirstStackOfTheMinimumSize` |
| R7 a launch to a destination whose registration is not durable | `theFirstRefusalWins` |
| R8 the energy checked after the outbox | `theFirstRefusalWins` |

The first mutation run (kept in `root-checks.zip` under
`attempt-01-r3-survived`) found R3 alive: no test had two places of one
Level on different bodies, as two stations of the Space Level orbiting
different bodies are. `7b5193e` adds that case and the LOCAL case of one
Level and body; the second run kills all eight.

### First evidence run (failed, kept)

The first run at `7b5193e` (kept under `attempt-02-failed`) failed two
GameTests; no production code changed:
- `cargoTravelsAndMovesOnlyAfterTheDestinationSavedIt` stayed `UNFORMED`.
  Rerun with the failing cells named, it showed lava from around the shared
  test origin flowing into the air cells beside the barrel. `7dc17f8` builds
  the test railguns solid (casing in those cells, which the pattern allows).
- `anAwaitingMarkerKeepsItsChunkDirty` (C11R-L5) assumed no chunk save in its
  first 25 ticks; vanilla's own between-tick save registered the marker
  first. `d2f30ec` runs its dirty-chunk check in the placement tick and keeps
  the "nothing registers without a save" check while no save of the chunk is
  recorded (`ChunkSaveWatcher`).

Two full GameTest runs after the fixes passed (`after-fix`), then the
evidence run above.

## Tests added

- `RailgunTest` (5): the nine C10 quote vectors and the bounds, the integer
  square root against `BigInteger` up to the ends of the range, the route rule
  in its order and the route classes (including two stations of one Level),
  the payload selection and minimum stack size, and the refusal order.
- `CommonConfigTest`: the two railgun values and their defaults (52 in total).
- `RailgunGameTests` (5), each in its own batch:
  - a `LOCAL` transfer: escrow of the stack and the quoted cost, registration
    and the move to the receive buffer each checked against every real and
    posted save of the two chunks (at least 40 ticks old), release,
    acknowledgement and pruning, removal and retirement, unchanged tickets;
  - refusals (`TARGET_FOREIGN`, `INSUFFICIENT_ENERGY`, `SYSTEM_DISABLED`)
    that change nothing, the busy break refused for a player, a destination
    removed in flight with the system disabled, `DESTINATION_MISSING` in the
    list, a stranger's redirect refused and the operator's redirect home
    claimed and moved at the source;
  - an `ORBITAL` route to a station (250,000 FE, 600 ticks) whose chunk
    unloads: the record waits `ARRIVED`, the ledger loads nothing, and the
    claim follows the reload; then a warp to Cygnus X-1 and
    `ROUTE_OUT_OF_SYSTEM` back to the planet; unchanged tickets;
  - two sources into one destination, with the console `transfer list`;
  - the menu (selection, a launch refused with `NO_PAYLOAD`, a stranger's
    public view that presses and takes nothing) and the owner's
    `endpoint resolve` of a returning frozen copy, which hands its
    never-registered payload back to the input.

## Not verified

- The screen and the effects on a real client (V1 is `[H]`).
- Railgun crash cuts with a forced stop (S2, C13).
- Railguns at the server's launch cap and their cost (C13).

Next: C12d, the ADR-059 space elevator.
