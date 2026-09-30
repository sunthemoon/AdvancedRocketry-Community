# Review of STAR-02/03, the capacity fix and WARP-02, and its fixes

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Reviewed commits:
- `c5e213b` (STAR-02/03);
- `30b9b11` (bounded-codec capacity fix, ORBIT-04 measurement);
- `9969550` (WARP-02).

Fix base: `615a5f1` (WARP-04). Development slice only: v1.5 and inherited G0-G9
remain `IN_PROGRESS`. This record is not a Gate approval.

## Independent review

An independent reviewer read the three commits against ADR-041/043/044 and
AGENTS.md. It worked read-only, on `git archive` exports only.
- It reran the full `clean build test runData runGameTestServer` at each commit
  and reproduced the claimed counts:
  - 975 JUnit / 239 GameTests at `30b9b11`;
  - 981 / 240 at `c5e213b`;
  - 1,001 / 240 at `9969550`.

  Generated data was unchanged at each commit.
- It ran 27 single-change mutations; 21 were caught.
- It rechecked the evidence integrity of all three packets and found 0 problems.

The unmodified report, its evidence, scripts and probe tests are in
`independent-review.zip`. The reviewer's re-extracted copies of already committed
evidence (`prev-review/`) are excluded.

**Verdicts:** all three commits are **accept with changes**, with no Critical or
High findings.

## Findings and handling

| ID | Severity | Finding | Handling in this fix |
|---|---|---|---|
| F1 | Medium | `30b9b11` applied the 8x heap quota to every managed type, so a 34-byte crafted rocket-journal file made the pre-start reader allocate up to 1.18 GB (an `OutOfMemoryError`, not `OVERSIZED_DATA`) | The heap factor is per type: stations 8 (measured), the others their earlier 1. A new bounded accounter refuses any single declared array or list before allocation when it exceeds what can still follow: the raw quota, and the unread compressed bytes times the deflate maximum of 1032:1, at most 4 accounted bytes per raw byte. A new test writes tiny files declaring huge byte, int, long and list elements for every managed type. It expects `OVERSIZED_DATA` whenever the declaration exceeds the backed bound or the quota, and fail-closed otherwise |
| F2 | Medium, pre-existing | The 4 MiB station-registry bound was not enforced on growth: about 1,856 fully populated stations made every ordinary save throw | Creating, committing, adding a member or invitation, transferring ownership, and relocating to a longer orbit identifier are refused once the encoded registry would leave less than 256 KiB (the balance headroom). A running upper bound (the last exact measurement plus the maximum growth since) avoids re-encoding on every mutation. The new `StationRegistryStorageBoundTest` fills the registry with fully populated records until growth is refused, then checks that the encoded size stays within the limit, that creation is refused, that a fold of new balances fits in the headroom (under 4 MiB), that the next new entry is refused, and that the result reloads. ADR-044 §2 now states the growth admission instead of the wrong "4,096 records fit" premise |
| F3 | Low | `foldWarpCredits` could leave a partial, unsaved fold on invalid input | The map is validated as a whole before any credit (null key or value, negative, above the cap, more than 4,096 entries); tested |
| F4 | Low | Registry-level `checkedRelocation` did not check the target or the confirmed cost, and nothing said so | Javadoc and ADR-044 §3 state that these are the WARP-03 commit step's obligations (implemented and GameTested in WARP-03) |
| F5 | Low | Candidate verification and the first root-shape guard were not proven by tests | A package-private candidate seam: five corruptions (another balance, own balance, another station, the reservation, the balance list) are each refused before any write, on both the relocation and the growth path. The migrator's own message "schema 3 cannot carry warp_energy" is asserted |
| F6 | Low | ADR-043 tests were partial | Added: the packaged planner routes Earth to Mars but cannot reach Tau Ceti e's orbit or a station there (`UNSUPPORTED_ROUTE`); a v1.5 survey mission to Tau Ceti e ends in a pending discovery, and the star is refused as a target; exactly 16 systems load and a 17th is rejected. A data-pack reload GameTest is not possible (the GameTest server cannot swap packs); reloads are covered by `PlanetaryReloadTest` through the real reload lifecycle |
| F7 | Low | The raw-bound test did not assert the diagnostic, and its comment was wrong | It now asserts `OVERSIZED_DATA`; the comment is corrected |
| F8 | Low | The cooldown after a failed gravity write was untested | The rule is `StationGravityService.startsCooldown`; a test pins it for every code |
| F9 | Info | Failed focused-run logs were not captured | Acknowledged; later packets retain every failed full run |
| F10 | Info | Encoding relies on server-thread confinement | Documented on `StationRegistrySavedData` |
| F11 | Info | Epoch and shape rules were tied to "current" | They use `WARP_REGISTRY_SCHEMA_VERSION = 4`, so a future root 5 cannot misclassify root 4 |
| F12 | Info | The packaged example bodies count toward the 128-body cap; the v1.4 providers no longer run | Recorded here. A data pack that already had 121 or more bodies must drop two. Frozen per-version DataGen is the project pattern |

## Commands actually executed

| Command | Result |
|---|---|
| focused `gradlew test` (storage bound, warp, star systems, reload, cooldown rule, bounded IO) | Exit 1 twice, then exit 0. The first run failed the tiny-file test for a 1 MiB-class type (its quota is below the file-backed bound, so the declaration is allocated and then fails closed; the test now asserts exactly the two refusal guarantees). The second failed the new planner control (the packaged graph has no Earth-Moon route; Mars is used). The final run passed: 69 tests in 10 classes |
| `clean build test runData runGameTestServer` run 01 | Exit 0, 3 m 6 s; `:test` executed, 1,020 JUnit / 184 suites, 0 failures; all 248 GameTests passed; `src/generated` unchanged after runData |

Main JAR `f4411b039074cc384ab00ed24cb1e785705ded98e39aa37c856f60b7c87d5b4b`; the API JAR is
byte-identical to the previous slices (`97d1aaad…`).

## Evidence archive

`root-checks.zip` holds the fix run's Gradle log, JUnit XML, GameTest logs,
hashes and packaging script. `independent-review.zip` holds the review, as
described above. Member indexes, `source-identity.json`, `links.json` and
`SHA256SUMS.txt` bind this directory and the staged tree.

## Residual risks

- A declaration small enough to be backed by the file can still be allocated
  before an EOF, bounded by the smaller of the type's quota and about 4 MB for a
  tiny file.
- Growth admission encodes the registry when the running bound reaches the limit,
  on the server thread, during a player's command. This is measured only
  indirectly: the storage-bound test fills the registry with full records until
  growth is refused, in about 10 s of test time, including every mutation.
