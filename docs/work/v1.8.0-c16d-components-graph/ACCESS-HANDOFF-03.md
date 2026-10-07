# C16d graph access handoff 03

Task: CL16D-GRAPH-ACCESS-03, `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c16d-components-graph/TASK-03.md`
(TASK hash not recorded: hash commands were not permitted).
Author: delegated Claude worker, model Opus 5.5 (`claude-opus-5-5`), owner's interactive Claude
Code session, 2026-10-08. Not Root, no nested delegation. No verdict, acceptance or Gate is
claimed.

## 1. Scope delivered

Three new files in the assigned worktree
`D:/GitHub/arce-v180-claude-graph-access-20261008` (branch
`codex/v1.8.0-claude-graph-access-20261008`), under `docs/work/v1.8.0-c16d-components-graph/`:

| File | Content |
| --- | --- |
| `ACCESS-PROPOSAL-03.md` | vocabulary, producers P1-P5, bindings, monotone limits, soundness argument, dependencies, R1 disposition |
| `ACCESS-TEST-DESIGN-03.md` | A0 synthetic cases AX01-AX15 and A1 observations AO1-AO11, all unrun |
| `ACCESS-HANDOFF-03.md` | this file |

No other path was written. Nothing was written to the evidence leaf
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16d-graph-access-claude-author-20261008-01/`; Root captures
exact bytes, sizes and hashes.

## 2. Design summary

- `cap:station:<B>` is retired and split:
  - `cap:station_exists:<B>`: an owned committed station with orbit body B exists;
  - `cap:station_aboard:<B>`: the actor stands in its region with a docked owned rocket.
- `cap:orbit:<B>` is withdrawn. No survival path admits an Orbit source or destination.
- P1 kit creation emits existence only.
- P3 boarding is the only first producer of aboard. It needs:
  - an admitted Station-kind quote whose orbit anchor is B;
  - existence;
  - blueprint parts and fuel;
  - discovery of B;
  - static seat >= 1 and pad-footprint predicates.
- P2 surface routes emit only `cap:surface`.
- P4 warp needs aboard at the source and emits aboard + existence at the target.
- The graph actor is a single non-operator survival owner. Membership, operator and developer
  travel are not producers.
- Station-local producers bind to aboard. "Anywhere in Space" producers OR over aboard.
- Monotone semantics keep pre-warp contexts derived, with a `relocated_by_warp` note.
  Simultaneous two-context rows are rejected.

R1 disposition: **addressed in proposed text, not closed** (AP §9). Closure needs:

- a different-agent review;
- Root adoption;
- A-DEP-1..4;
- the A1 observations.

## 3. Proofs and limitations

- The soundness argument (AP §7) maps each P3/P4 slot to the source checks read in §5 below. It
  is a documentary argument, not an executed proof. No model check or script was run.
- Runtime states are deliberately A1-only, not graph slots:
  - registry operational, journal, rate limit, reservations;
  - chunk loading, in-motion rule;
  - live destination Level;
  - navigation caps.
- Inferences not verified by a read:
  - `PlanetarySurfaceResolver.find(..., false)` treated as "no arrival requirement", by
    analogy with the planner's `arrival` flag. The resolver itself was not read.
  - Whether `TravelTarget.Orbit` is wire-encodable (`TravelTargetWireCodec` not read). AO4 says
    to record a decode rejection if so.
  - The warp `StationLocalActor` locate codes. The guessed file path did not exist and was not
    searched further.
  - The black-hole generator binding, taken from CONTRACT-02 §5.3, not re-read.
  - Whether `StationState` can be built in plain JUnit (A-DEP-1).
- Effective route data was not located: Grep/Glob under `src/main/resources/data` found no
  route JSON. A-DEP-4 leaves enumeration to Root's catalog binding.
- Not settled: R2-R5, steel bootstrap, tag/root/portal witnesses, energy rows, the full
  contract.

## 4. Impacts on the successor contract (for Root; no edit made)

- CONTRACT-02 §5.1 rows 2-3, §5.2 items 1-3 and §5.3 are replaced by AP §3-§5.
- Every row slot naming `cap:station:*` or `cap:orbit:*` must be renamed or removed.
- TEST-DESIGN-02 X01, X02, X03 and X09 access expectations are superseded by AX01, AX07,
  AX02c and AX10. X10 is kept as AO3. Other rows are unchanged.
- New validator error codes:
  - `unsupported_capability:orbit`
  - `orbit_target_quote`
  - `retired_capability:station`
  - `simultaneous_context_unsupported`
- New row-disable reasons: `no_seat`, `station_pad_footprint`.
- New blocker form: `quote:<code>`.
- New notes: `relocated_by_warp`, `owner_station_limit`, `synthetic_seed`.
- New dependencies: A-DEP-1 (synthetic station lookup for the pure planner, extends G-OPEN-7),
  A-DEP-2 (blueprint seats/footprint), A-DEP-3 (observed station departure/refuel; until then
  station-source rows fail `binding_unfrozen`, which blocks the Tau Ceti chain), A-DEP-4
  (effective route enumeration).
- Open questions: A-OPEN-1 (membership), A-OPEN-2 (navigation `MAX_QUOTES` vs launch
  admission), A-OPEN-3 (warp-core producer/placement).
- No production, registry, recipe, asset, save, protocol, ADR, ledger or status change is
  proposed by this leaf.

## 5. Actual reads (Read/Grep/Glob only)

Instructions and inputs:

- TASK-03 (main checkout).
- Memory notes `v18-claude-worker-protocol`, `codex-shares-the-tree`.
- AGENTS.md of the assigned worktree, as loaded at session start.
- In the worktree: OWNER-DECISIONS-02 and TASK-02 (full).
- REPORT-02 (full; SHA not verified by me).
- CONTRACT-02 lines 340-444.
- TEST-DESIGN-02 lines 55-94.
- Globs of the contract directory, the REPORT-02 leaf and the worktree directory.

Source, paths relative to `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`:

| File | Lines read |
| --- | --- |
| `station/content/StationDeploymentKitItem.java` | full |
| `station/service/StationManager.java` | 60-104 |
| `station/service/StationCreationService.java` | 40-89 |
| `station/service/StationAccessService.java` | full |
| `rocket/server/PlanetaryFlightAdmission.java` | full |
| `rocket/server/RocketFlightService.java` | 150-284, plus grep hits 402-415, 445-457 |
| `rocket/server/RocketNavigationService.java` | 35-94 |
| `rocket/flight/RocketTargetFlightPlanner.java` | 30-154 |
| `rocket/server/RocketTransferService.java` | 68-107 |
| `rocket/transfer/RocketLandingPadSelector.java` | 100-139 |
| `station/warp/StationWarpService.java` | 150-194, plus grep hits 104, 181-182, 294-299, 451, 474-483 |
| `docs/decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md` | 160-175 |

Searches:

- `instanceof TravelTarget.Station` across `src/main/java`;
- `teleportTo|changeDimension`;
- `SafeCelestialTravel` uses and class header;
- `passenger` in `rocket/server`;
- `destinationStation|...` in `rocket`.

Failed or empty lookups, kept as observed:

- Grep path `station/warp/StationLocalActor.java`: "Path does not exist".
- Grep path `docs/adr`: "Path does not exist". The ADR was then found under `docs/decisions`.
- Grep for route JSON keys under `src/main/resources/data` (`**/routes/**`): no matches.
- Glob `src/main/resources/data/**/route*/**/*.json`: no files.

## 6. Deviations from TASK-03 (disclosed, not waived)

- Not read: the main checkout's live AGENTS.md as a separate file; PROJECT-CONFIG, PRODUCT,
  docs 01/04/05/06/14/16/17; the v1.8 version file; HANDOFF-02; CONTRACT-02 and TEST-DESIGN-02
  outside the ranges above. The time and cost caps were the reason. Proposals depending on
  those documents' rules (e.g. naming policy for new public IDs) need Root's check.
- Git status/branch/HEAD/worktrees were not checked with Git. The session-start snapshot
  reported this branch, a clean status and HEAD subject `2e397a49 docs(v1.8): register bounded
  direct Claude dispatch`. Whether a codex.exe process was running was not checked.
- Wall-clock time was not measured. The 15-minute cap may have been exceeded.
- Client-reported spend at handoff authoring was about USD 1.7-1.9, under the USD 4.00 cap. The
  exact final figure comes from the client.
- Hashes, byte counts and line-ending checks of the three files were not computed (hash
  commands forbidden). The Write tool emits LF. Root should verify each file is <= 24 KiB.

## 7. Unrun tests and commands

Unrun:

- every AX and AO case;
- JVM/javac/Gradle (`clean build`, `test`, `runData`, `runGameTestServer`);
- servers and clients;
- Python checks;
- `git diff --check`;
- seals.

No shell was used. No acceptance, freeze or Required Gate (G0-G9) is claimed or delegated.

## 8. Release

Writes: only the three files above in the assigned worktree. No Git object, index, HEAD, branch,
stash or worktree operation. No process started; no cache, archive, network, install or
credential use; no other agent. All read interests in the main checkout, the contract worktree,
the REPORT-02 leaf and this worktree are released. HEAD/index interests are released (none were
taken).

Handoff path:
`D:/GitHub/arce-v180-claude-graph-access-20261008/docs/work/v1.8.0-c16d-components-graph/ACCESS-HANDOFF-03.md`.
