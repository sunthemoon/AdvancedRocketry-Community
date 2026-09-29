# V150-STATION-03 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `37e4be6296093b9ab41020e3e8f4be5439146676` (STATION-01/02).
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag, real-client result or human Gate approval.
**Independent diff review has not been performed for this slice.**

## Implemented scope and design

Contract: [ADR-040](../../decisions/ADR-040-STATION-REGIONS-AND-MIGRATION.md),
"Stable geometry and expansion policy" and "Runtime authority and checked mutation".

- `/arce station expand` issues a server-side confirmation. The player must be
  a real server player, the station owner or a permission-level-2 operator,
  standing inside the station's current region in Space with their current
  chunk loaded. The reply names the station UUID, current and target bounds,
  and warns that blocks already in the added area join the station. It cannot
  be undone. `/arce station expand confirm <station_id>` completes it.
- The server derives the only allowed target from the station cell (centered
  768 square). No coordinates are accepted, no terrain is scanned, no blocks are
  moved or removed, and `hasChunkAt` is used so no chunk is loaded.
- Confirmations (`StationExpansionConfirmations`) are bound to actor UUID,
  observed immutable `StationState` and the registry instance. There is one per
  player (a new request replaces it) and at most 128 overall; capacity rejects a
  new player. They last 200 server ticks, are consumed by any confirm attempt,
  are purged in issue order and are cleared on logout and server stop. A confirm
  rechecks authority, position, loaded chunk, permission and exact state.
- Checked candidate commit (`StationRegistrySavedData.checkedExpand`): encode
  the complete registry with only that station substituted, validate it through
  the current-schema migrator and decoder, then stage, force, read back and
  atomically replace the file. Only then publish through
  `StationRegistryModel.replaceExpanded`, which requires the live state to still
  equal the observed state. The live registry is never mutated before success.
  - Failure before replacement, or a replacement reported as failed while the
    file still holds other valid content, returns `WRITE_FAILED`. The old region
    stays and a later autosave cannot write the rejected growth.
  - A replacement reported as failed but verified on disk is published.
  - If the file cannot be read back after a failed replacement, the result is
    `OUTCOME_UNKNOWN`. Further expansion is disabled until restart and the
    registry is marked dirty so ordinary saves reassert the acknowledged
    (unexpanded) authority. Other station access continues.
- The staging/force/readback/atomic-move routine was extracted unchanged from
  `AtomicSavedData` into `CheckedSavedDataFile` and is shared by both. Discovery
  saves keep their behavior (`AtomicSavedDataTest` 6/6).
- Ordinary station creation, membership, transfer and deletion still use the
  old `flush` and its exception behavior; none were routed through the new API.
- No new packet, public API change or translation key. Messages use literal
  English like the existing station commands. The mod description in
  `gradle.properties` was updated under
  [provenance](../../provenance/v1.5.0-development-metadata.md).

## Files and tests

Production: `CheckedSavedDataFile` (new), `AtomicSavedData`,
`StationLimits`, `StationState.withExpandedRegion/expanded`,
`StationRegistryModel.replaceExpanded`, `StationRegistrySavedData`,
`StationAccessAction.EXPAND`, `StationExpansionCode/Result/Confirmations/Service`
(new), `StationManager`, `StationCommands`, logout listener registration, and
`StationExpansionGameTests` (new). Exact hashes are in `implementation-files.json`.

New JUnit (16):

- `StationExpansionConfirmationsTest` (5): one-shot use, 200-tick boundary and
  earlier-tick rejection, no transfer to another player/station/authority, one
  per player, 128 cap, purge and clear.
- `StationExpansionServiceTest` (3): owner/operator allowed; member, invitee and
  outsider rejected; authority/Space/chunk/region order; expanded is idempotent.
- `StationCheckedExpansionTest` (8): success on disk and reload, gap lookup,
  neighbor unchanged; stale and repeated requests write nothing; failure before
  replacement; refused atomic move keeps old bytes and a later save stays at 512,
  then retry succeeds; replaced-despite-error is published; unreadable result
  quarantines; blocked registry; exact-expansion guard in the model.

New GameTest `localOwnerAndOperatorExpandOnlyThroughConfirmedCheckedCommit`
on the real Forge server:

- member/invitee/outsider rejected; Overworld request rejected;
- an unloaded gap chunk is rejected and still unloaded afterwards;
- after test-side loading, the gap position is outside the station;
- confirm without request, from another (operator) player, for another station,
  after consumption and after a logout event are all rejected;
- a membership change between request and confirm is rejected as stale;
- the owner's confirm expands the station with an unchanged loaded-chunk count,
  intact platform, the former gap resolved to the station and the checked file
  on disk containing the expansion;
- a repeated request reports already expanded;
- the registered command rejects the console and lets a local operator expand
  a second station through `expand` and `expand confirm`.

Its audit lines in the passing run: request ISSUED 5, UNAUTHORIZED 3,
NOT_IN_SPACE 1, CHUNK_UNLOADED 1, NOT_IN_STATION 1, ALREADY_EXPANDED 1;
confirm EXPANDED 2, NO_CONFIRMATION 4, CONFIRMATION_MISMATCH 1, STATION_CHANGED 1.
The console rejection happens in Brigadier before the service, so it has no audit line.

## Commands actually executed

Java `C:/Program Files/Java/jdk-17.0.7`; Gradle with
`--offline --no-daemon --console=plain`.

| Command | Result |
|---|---|
| `gradlew compileJava` | Exit 0 (existing deprecation warnings only) |
| `gradlew test --tests '*station.*' --tests '*persistence.migration.*'` | Exit 0; 89 tests, 0 failures/errors/skips |
| `gradlew clean build test runData runGameTestServer` (run 01) | Exit 0, 169 s; 959 JUnit / 173 suites passed; all 238 required GameTests passed |
| same (run 02, after the description edit) | Exit 1, 43 s; `ModMetadataTest` pinned the old description sentence; DataGen/GameTests not reached |
| same (run 03, literal updated to the new exact sentence) | Exit 0, 164 s; 959 JUnit / 173 suites, 0 failures/errors/skips; all 238 required GameTests passed |
| `git diff --exit-code -- src/generated` (after runs 01 and 03) | Exit 0; no tracked or untracked generated change |
| `git diff --check` | Exit 0 |
| `validate_repository.py --require-approved-identity`, `validate_v1plus_planning.py`, `validate_bootstrap_provenance.py`, planning regression suite | Exit 0; see [Evidence archive](#evidence-archive) |

Run 02's failure was a stale test literal, not a product defect; the assertion
still checks the exact new sentence and was not weakened. Its log and XML are
retained. The GameTest log keeps 15 ERROR lines, all from the missing
`server.properties` and existing deliberate energy/precision/satellite failure
injections; zero FATAL.

## Artifacts

Development build `1.20.1-1.5.0-dev` from run 03, not a release candidate:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| main | 2661760 | `7d5953061da585e16ada3ba9e7f25fe38dac02b9b1ed947244ad812fd1935c0c` |
| API | 48469 | `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf` |
| sources | 1208275 | `e280df014df493805671e8acbb03936abadea79d59c3033d6eaa8cee0449611a` |

The API JAR is byte-identical to the STATION-01/02 and v1.4 API JAR.

## Evidence archive

A packaging script read the evidence directory without changing it:

```text
C:/Users/Administrator/AppData/Local/Temp/arce-v150-station-expansion-cc05bea54bc943d5bc174b001c7e8b37
```

Before writing, it asserted that the 20 tested implementation files are exactly
the changed `src/main`, `src/test` and `gradle.properties` paths and equal the
committed bytes, that the three JARs match the table above, that runs 01 and 03
have 959/173 passing XML, 238 passing GameTests and exit 0, and that run 02
exited 1 with one failure.

`root-checks.zip` (537 members, SHA-256
`feda5de574b4c4123583d2ee1721cb753e58dc0210c01cb241a97331426add24`) holds all
three Gradle logs, their JUnit XML, the GameTest latest/debug logs, source and
artifact hashes, the JUnit/audit summary and `packaging/archive_evidence.py`.
`root-checks-files.json` indexes it. `root-checks.json` and
`development-artifacts.json` summarize the results; `source-identity.json`
binds every other staged file, `links.json` lists validated local Markdown
targets and `SHA256SUMS.txt` covers this directory.

After the documentation edits, these were rerun, with logs in the packaging
directory's `out/` folder (not in the ZIP):
`validate_repository.py --require-approved-identity` exit 0 (45 passed),
`validate_v1plus_planning.py` exit 0, `validate_bootstrap_provenance.py` exit 0,
planning regression suite exit 0 (15 tests), `git diff --check` exit 0.

## Not covered; Gate status and risks

- Native dedicated-server expansion, same-world restart readback and nonempty
  native teams belong to STATION-04; no native process ran for this slice.
- No real client, multiplayer or long-load test. FakePlayers exercise the
  service and command path, not a networked client.
- Process kill or power loss during the replace is not tested; the injected
  cases cover caught failures, a refused atomic move and an unreadable result.
- Expansion adopts any existing blocks in the added ring as station territory;
  this is the accepted ADR-040 tradeoff, stated in the warning.
- Independent diff review is pending. v1.5 G0-G9 remain open; nothing here
  approves a Gate.

Rollback: revert this commit. Expanded records are valid schema 2 and remain
readable by STATION-01/02 code, which keeps the 768 region but offers no command.
