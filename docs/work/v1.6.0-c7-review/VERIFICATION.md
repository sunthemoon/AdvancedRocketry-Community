# C7 independent review: findings, dispositions and fixes

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`. Reviewed
range: `2752776..bbc0f53` (C7a `4b744c8`, C7b `7e736b5`, C7c `bbc0f53`). The
fixes are committed on top of `d15dfcc` (C8a-2). No Gate, candidate or tag.

## Round 1

An independent, read-only reviewer read the contracts, the three verification
records and all C7 satellite code and tests. It checked the vanilla and Forge
behaviour it relied on against the mapped Forge jar, and ran the satellite,
scan and protocol-pin unit tests in its own copy (23 suites, 76 tests, 0
failures). Its report and log are archived in `independent-review.zip` (the
reviewer's copy of the repository and any Minecraft sources are excluded).

**Verdict: accept with required changes.** Critical 0, High 1, Medium 5, Low 12.

## Dispositions

| Finding | Disposition |
|---|---|
| **C7-H1** A non-`data` launch replay consumed the package without the barrier flush | **Fixed.** The replay flushes a dirty registry before answering `IDEMPOTENT`; a failed flush keeps the package. A failure-injection GameTest blocks the flush, presses Launch twice (package kept both times), then shows that the durable replay consumes the package and that the satellite is on disk. This also tests the flush-before-extraction order (C7-M4) |
| **C7-M1** Receivers crashed the server on a blocked registry | **Fixed.** `SolarLinks` returns "unavailable, output 0" and leaves the links untouched when the registry is blocked; the manager catches any failure of a check or release. The scheduler pass skips a blocked registry instead of logging an error every 20 ticks. A GameTest in a dedicated batch installs a blocked registry (a future root) and checks the receiver, breaking it, a scan, `blank-chip` and a launch |
| **C7-M2** View pacing counted broadcasts, not ticks | **Fixed.** Composition is paced on server ticks, at most once per 5 ticks whatever the clicks. A GameTest calls the pacer 50 times in one tick twice and checks exactly one send, then one more after 5 ticks |
| **C7-M3** A scan tick could crash the server; long biome IDs threw | **Fixed.** A failing job is dropped and logged, and the manager guards the scan tick. A biome ID longer than 128 characters is `OTHER`. Unit test with a 129-character ID |
| **C7-M4** Untested behaviour | **Fixed** with the GameTests above and: an unloaded-chunk scan (every cell `UNKNOWN`, and the far chunk still not loaded afterwards); the survey chip's `use()` starting a scan that the production server tick finishes; the builder's `DEFINITION_NOT_FOUND` (via a primary component defined by the test mod with no definition) and `OUTPUT_BLOCKED`; a builder menu of an older catalog generation refusing to assemble |
| **C7-M5** Contract deviations recorded only in verification notes | **Recorded** as ADR-049 revision 4 (items 8–11 added) and ADR-050 revision 4 (save epoch, derived counters, required `target_body`, blocked-registry behaviour, rate limits delivered with C8a). Both stay **PROPOSED** until the second review round |
| C7-L1 `blank-chip` guards | **Fixed.** It refuses while the registry is blocked and reads the raw `satellite_id` of an undecodable chip; refused if that satellite is registered. GameTests for both |
| C7-L2 Component catalog not cleared | **Fixed.** It and the resource tables are cleared when the server stops |
| C7-L3 Builder data stat truncated | **Fixed.** Sent as two 16-bit halves (menu data count 12) |
| C7-L4 Launch did not re-check the primary's definition | **Fixed.** Refused with `DEFINITION_NOT_FOUND`; recorded in ADR-049 revision 4, item 9 |
| C7-L5 Refused decommission flushed | **Fixed.** Only a removal flushes. A UI confirmation is left to the v1.8 UI batch |
| C7-L6 Target selection carried over between chips | **Fixed.** A chip or payload that selects another definition starts at its first target (GameTest) |
| C7-L7 Ambiguous codes | **Recorded** in ADR-049 revision 4, item 7 (`MISSION_BUSY` reused, `IDEMPOTENT` when there is no link) |
| C7-L8 Class sizes and layering | **Scheduled.** C8a-1 splits the registry's retention, budgets and instances into their own classes (implementation-log class-size plan); the scan job's result type moves out of the network package with that work |
| C7-L9 Roots skipped slot checks | **Fixed** for the receiver (control chips only). The builder keeps catalog-independent load checks on purpose: a component removed by a data pack must not quarantine the root; assembly re-checks every slot |
| C7-L10 Scan duration and Level height | **Recorded** in ADR-049 revision 4, item 6 |
| C7-L11 Automation could insert chips into the builder | **Fixed.** Refused (GameTest); recorded in item 1 |
| C7-L12 Operator unlink and receiver access | **Recorded** in item 7 |

## Tests added or changed

- New GameTests (`SatelliteReviewGameTests`):
  - `aFailedLaunchFlushKeepsThePackageUntilTheReplayIsDurable` (batch
    `satellite_flush_failure`);
  - `aBlockedRegistryFailsClosedWithoutCrashing` (batch `satellite_blocked`);
  - `aScanOfUnloadedChunksIsUnknownAndLoadsNothing`;
  - `theSurveyChipStartsAScanThatTheServerTickFinishes`;
  - `theViewIsSentAtMostOncePerFiveTicks`;
  - `builderReportsABlockedOutputAMissingDefinitionAndStaleMenus`;
  - `aNewChipStartsAtTheFirstTargetAndUnreadableChipsStayBound`.
- `SatelliteRegistryFixture` swaps the registry for the two dedicated batches
  and restores and saves the original afterwards.
- New unit test: `SurveyScanJobTest.aBiomeIdTheResultCannotCarryIsShownAsOther`.
- Test data: `src/adapterTest/.../satellite_components/orphan_survey_primary.json`.
- No existing assertion was removed or loosened.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew test` for the satellite, scan, resource, network-pin and config tests, then `runGameTestServer` (development runs) | All passed on their first run: 278 required GameTests (seven new). These outputs were not captured into the evidence directory; this table is their only record |
| `gradlew clean build test runData runGameTestServer --console=plain` | Exit 0, 3m59s. **1,116 JUnit tests / 209 suites executed**, 0 failures. **278 required GameTests** passed. DataGen rewrote nothing (generated diff empty) |

The log has 18 ERROR lines and 0 FATAL: the 15 known intentional lines of C7
plus three new intentional ones, `Satellite idle launch operation failed`. Two
come from the injected flush failures and one from the launch on the blocked
registry. The blocked batch also logs one expected
`ARCE_SATELLITE_RECEIVER_RELEASE_SKIPPED` warning.

Repository validators, run on the staged tree after packaging (log in
`packaging/out/validation.log`): `validate_repository.py
--require-approved-identity`, `validate_v1plus_planning.py`,
`validate_bootstrap_provenance.py`, `python -m unittest tests.test_v1plus_planning`
and `git diff --cached --check` exit 0.

## Status

C7 closes only when the second review round accepts these fixes and the two
revision-4 amendments. Until then ADR-049 and ADR-050 stay at revision 3 as
accepted, with revision 4 **PROPOSED**.
