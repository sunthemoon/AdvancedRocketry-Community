# v1.5 review closure (C1)

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`. Base: `130a744`.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`. This record
is not a Gate approval.

Two independent reviews are handled here. Both unmodified reports, with their
evidence, are archived in this directory.

## 1. Review of WARP-03, WARP-04 and the first review fixes

Reviewed commits: `0416455`, `615a5f1` and `1a5763a`. The reviewer:
- worked read-only on `git archive` exports;
- reran all three full builds, reproducing the claimed counts;
- ran 28 single-change mutations;
- probed registry capacity;
- rechecked evidence integrity (0 problems).

Archive: `independent-review-warp.zip`. The report is complete. An earlier draft
of this record was written while the reviewer was still working and used
provisional finding numbers; the numbers below are the report's own.

**Verdicts:**
- `0416455`: accept with changes.
- `615a5f1`: accept with changes.
- `1a5763a`: **reject as committed**, because of R1 (High).

There are no Critical findings: 1 High, 2 Medium, 5 Low and 8 Info.

| ID | Severity | Finding | Handling |
|---|---|---|---|
| R1 | High | The per-type heap factor from the F1 fix set satellite missions to 1x: a registry above about 1,190 missions could no longer be written or read | Every managed type uses 8x again, the value every type had before `1a5763a`. The per-declaration guard from F1 still refuses a tiny file that declares a huge element, before allocation. Capacity tests through the checked writer and the bounded reader: satellites, 6,144 missions (3.71 MB raw, heap ratio 5.02); the transfer journal, 64 records of 2,048-block rockets (11.25 MB raw, ratio 6.89); stations, the existing 4,096-station test (ratio 6.49 in the review) |
| R2 | Medium | Rechecks and wiring pinned by no test (M02, M03, M08-M12, M16, M22, M27 survived; the M16/M27 assertions were vacuous); the target-removal test missing | A test now kills each mutation; see "Mutation checks" below. The Overworld and off-thread calls are made while the whole allowance is unused and name a position inside the station. New GameTest `aTargetRemovedDuringTheCountdownAborts` runs a private `StationWarpService` on a private catalog and removes the target mid-countdown (abort `WARP_TARGET_UNAVAILABLE`). New `StationRegistrySavedData.acceptsWarpEnergy()` is pinned by `aQuarantinedRegistryRefusesWarpEnergy` |
| R3 | Medium (pre-existing) | The satellite registry outgrows its own 4 MiB bound before its record limits (`save` throws before 8,192 missions) | Not changed here: it predates these commits and is outside the warp slice. Tracked in the implementation log (segment 9, C5), to be fixed or carried to v1.6 as a known issue |
| R4 | Low | Energy accepted by a core was destroyed when a fold refused a new balance entry; a station at 0 FE could never recharge near the bound | The 256 KiB headroom is exactly 4,096 entries of 64 bytes, and every other growth stops before it, so a new balance entry is now admitted up to the 4 MiB bound itself. Growth admission moved into `StationStorageBudget` (also R13, R15). The storage-bound test now expects the new entry to be credited: a behaviour change, not a weaker assertion; the refusal path is unit-tested on the budget. ADR-044 revision 4 §2/§3 records the rule and the remaining refusal case |
| R5 | Low | A departure from a station kept the pre-warp `current_body` | `RocketFlightData.atStationOrbit`; a station-sourced countdown records the station's current orbit. The docked-rocket GameTest asserts it, and `rocketBuiltOnAStationRecordsItsOrbitBody` covers rockets built on a station |
| R6 | Low | `/arce station warp status` folded credits and could be run by command blocks, so it could dirty the registry every tick | `status` no longer folds; it shows `energy=<folded> FE pending=<pending> FE`. The rejections GameTest charges a core, flushes, runs `status` and asserts the registry is not dirty and the credit is still pending |
| R7 | Low | Rule 2 (an unclassified record blocks every warp) can block warps permanently when a record's Level is gone, and operators have no way out | Partly handled: the transfer-journal diagnostic line lists up to four unclassified records with both ends and the operator step. Recovery that moves past an unclassifiable record, and an audited abandon path, are assigned to the MIG-01 recovery matrix (segment 7, C3) |
| R8 | Low | Growth admission proven only for reserve, commit and invite-then-accept | `everyGrowthPathIsRefusedOnItsOwnAtTheBound`: at the bound, `reserve`, `commit`, `addMember`, `invite`, `acceptInvitation`, `transferOwnership` and a relocation to a 126-character orbit ID are each refused, and the registry is unchanged. `invitationsAloneAreRefusedAtTheStorageBound` covers `invite` alone |
| R9 | Info | A rocket GameTest (`earthmoonroundtripconservesfuelandblockedpadreturnssource`) failed in 2 of about 33 runs | Not reproduced in this closure's runs; watched in C5 |
| R10 | Info | The CHANGELOG overstated F1 | Reworded: only declarations the file could not hold are refused before allocation |
| R11 | Info | ADR-044 §7 said the core uses machine-casing textures; the top references `minecraft:block/crying_obsidian` | ADR-044 revision 4 §7 states it (a reference, not a copied asset) |
| R12 | Info | Sources JARs are not reproducible from `git archive` (94 bytes differ; most likely line endings) | Recorded. The main and API JARs reproduce. Not changed in v1.5 |
| R13 | Info | Refused mutations inflated the running growth bound | Growth is recorded only after the mutation succeeds; `checksDoNotInflateTheBoundAndRecordedGrowthDoes` |
| R14 | Info | ADR-044 was edited in place while accepted | ADR-044 is now revision 4 with a review-history entry; its `amends` line notes that ADR-040's 4,096-station count holds only for small records |
| R15 | Info | `StationRegistrySavedData` (599 lines) and `RocketEntity` (504) are over 500 lines | Growth admission extracted to `StationStorageBudget`; the registry is 595 lines. `RocketEntity` unchanged (under 800; no ADR needed) |
| R16 | Info | Failed focused runs were not retained | This closure retains its failed full run, the invalid first mutation attempt and every mutation log. Two earlier focused compile/test failures in this session (an escaped regex in a Java string; a journal overlap rejection in a test fixture) happened before this evidence directory was used and were not retained |

## Mutation checks

Paths in this section and in the command table are inside `root-checks.zip`.

Each JUnit mutation was applied alone, and the focused tests were run
(`mutations/`). All six were killed by the intended assertions:
- M06 (`invite` unchecked);
- M18 (relocation unchecked);
- M20 (`transferOwnership` unchecked);
- M22 (quarantine accepted);
- R4 (a balance entry limited to the growth limit);
- R13 (a check that inflates the bound).

A first attempt with a shell driver is retained in
`mutations-attempt-01/`. Two of its mutations did not apply, because
multi-line arguments broke on Windows, so its "SURVIVED" rows are invalid.

The GameTest mutations ran as two combined runs; each mutation targets a
different test (`gametest-mutations/`).
- **Set A** failed exactly its six targets, each with its own message:
  - M02: "A de-opped operator warped";
  - M03: both countdowns committed in tick 21144;
  - M10: "A disabled warp committed";
  - M11: "Confirmation differs";
  - M12: "must record the station's orbit";
  - M27: "An Overworld position at the station's x/z was accepted".
- **Set B** (M16) failed "An off-thread call with allowance left was accepted".

M08 and M09 are killed by `serviceWiringUsesLiveAndSettledSetsAsSpecified`.

## 2. Contract review of ADR-045 and ADR-046

Reviewed: commit `2900e66`. Archive: `independent-review-contracts.zip`.

**Verdicts:** both accept with changes. There are 2 High findings (both in
ADR-046), 9 Medium, 7 Low and 2 Info.

All 16 required changes are applied:
- **ADR-045 revision 2:**
  - authority moved to rule 2;
  - unbinding, warp-interlock checkpoints, a deletion guard and named
    transitions, handed to v1.7 as constraints;
  - the relation to ADR-039 and v1.7, with storage left open;
  - a leaf-level permission check, bounded coordinates, an audit line and an
    unstable surface;
  - rule 4 stated precisely;
  - the ADR-040 pad invariant cited;
  - the extended test list.
- **ADR-046 revision 2:**
  - the amendment of ADR-039, with an owner, reason, reconsideration version
    (v1.8.0) and recovery condition;
  - a normative expected-outcome table and the A1/S1/V2 report;
  - the protocol claim scoped, with an ORBIT-03 carve-out and all four channels
    to be pinned;
  - the completed matrix, including the flight C2S path and BUILD/VISIT;
  - the scoped chunk invariant;
  - rate bounds, including vanilla `detectRateSpam`, which was checked with
    `javap`;
  - member removal by UUID;
  - the localization waiver;
  - the presentation rules.

Both ADRs are **ACCEPTED**. ADR-039 carries an amendment note. PORTING_MATRIX
gains rows:
- station/warp controls: `IN_PROGRESS`;
- control screens: `DEFERRED`, reconsidered by v1.8.0;
- space elevator: `IN_PROGRESS`, a v1.5 contract and v1.7 logistics.

The warp and star-system row is `IN_PROGRESS`. The validator, matrix, `uuid`
removal and protocol pins are implemented in C2.

## Commands actually executed

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `./gradlew clean build test runData runGameTestServer` (full run 01) | exit 1: `adeoppedoperatorscountdownaborts` failed. `PlayerList.op` grants the GameTest server's operator level, which is 0; the test now adds an explicit level-4 ops entry. All other GameTests passed | `root-gradle-01.log`, `run-01/` |
| 2 | `./gradlew test` (storage, checked-update, motion-rule and capacity tests) | exit 0 | `focused-unit-01.log` |
| 3 | shell mutation driver | invalid: 2 of 5 mutations did not apply | `mutations-attempt-01/` |
| 4 | `python c1_mutate.py` (6 JUnit mutations) | 6 killed | `mutations/` |
| 5 | `./gradlew test --tests *RocketTransferJournalCapacityTest` | exit 0; ratio 6.89 < 8 | `focused-unit-02.log` |
| 6 | `./gradlew clean build test runData runGameTestServer` (full run 02) | exit 0: 1,032 JUnit tests, 0 failures, 0 errors, 0 skipped; all 254 required GameTests passed; `git diff --exit-code -- src/generated` exit 0 | `root-gradle-02.log`, `junit-summary.json`, `run-02/`, `generated-diff.log`, `artifacts.json` |
| 7 | `python c1_gt_mutate.py` (GameTest mutation sets A and B) | exit 1 each, with exactly the targeted tests failing | `gametest-mutations/` |
| 8 | repository validators, `unittest` and `git diff --check` (before staging) | see `validation.log` | `validation.log` |

## Evidence archive

`root-checks.zip` holds the Gradle logs, JUnit XML, GameTest logs, hashes and the
packaging script. The two review archives are listed in `evidence-archives.json`,
with their exclusions: the reviewers' repository exports and their re-extracted
copies of committed evidence.
