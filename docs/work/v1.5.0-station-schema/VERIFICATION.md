# V150-STATION-01 / V150-STATION-02 verification

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Base: `a0e1d8fd90bc1731a3de5c34b8d3fdf20d35ab03`.
Development slice only: v1.5 and inherited G0-G9 remain `IN_PROGRESS`.
No release candidate, tag, real-client result or human Gate approval.

**Resumed and archived:** the maintainer paused advancement and later resumed
it on 2026-09-30. Runtime checks below completed before the pause and were not
rerun. Before archival, all 27 tested implementation files and the three
development JARs were confirmed byte-identical to the checked-in tree; see
[Evidence archive](#evidence-archive). This packet is bound to the commit that
adds it on top of the base above.

## Implemented scope and design

- Centered 512/768-square station records retain UUID, owner/team/invitations,
  cell, pad, orbit, creation time and environment. New/default stations remain
  512-square; this slice exposes no expansion or orbit-changing operation.
- Station root schema is independently 3 / `v1.5.0-orbital-station`, records 2,
  reservations 1. Other managed roots remain 2 / `v0.9.0-beta`.
- Root 1 or Beta-root-2 migration validates original record-1 geometry and all
  identity/type/list bounds before copying and changing versions. Extra tags and
  historical nonzero-byte vacuum values survive migration without normalization.
  Ordinary later saves retain the previous known-fields-only policy.
- Pre-start migration backs up managed primaries and present `_old` files,
  stages/validates upgrades, and requires atomic replacement with no non-atomic
  fallback. Manifest schema 2 records source/target per primary, not a false
  global schema1-to2 claim. Current files neither remigrate nor create backups.
- Caught staging/commit failures preserve or restore original bytes; incomplete
  rollback identifies the backup for manual recovery. File-by-file interruption
  can leave complete mixed old/new roots for the next preflight, not a cross-file
  power-loss transaction. The existing five-backup cap remains.
- Late runtime loading cannot silently bypass migration backups. Quarantined
  raw data stays intact; player break/place/multiplace in Space is denied even
  for operators. Other Levels and valid unowned-gap behavior are unchanged.
- At 4,096 committed stations, commit rejects without losing its reservation.
  Restoration admits the independently bounded 64 reservations. Ordinary station
  flush semantics remain unchanged; checked expansion commit belongs to 03.

Contracts: [ADR-040](../../decisions/ADR-040-STATION-REGIONS-AND-MIGRATION.md),
[development exception](../../decisions/ADR-039-V150-DEVELOPMENT-BASELINE-EXCEPTION.md).
No public API/network change, imported art or copied upstream implementation.
The MDK-derived metadata adjustment is recorded in
[provenance](../../provenance/v1.5.0-development-metadata.md).

## Files and new tests

Production changes are restricted to station model/persistence/access, the
existing managed migration service/type selector, bounded diagnostics and
development metadata. `StationRegistryPayload` separates pure collection
validation from SavedData loading, avoiding migrator/loader recursion.
`WorldDataMigrationService` remains a single pre-start transaction coordinator
(492 lines), not a new framework. Exact implementation identities are in
`implementation-files.json` in the root Temp evidence directory below.

New tests:

- `StationRegionBoundaryTest`: both sizes, inclusive negative/extreme edges,
  indexed gaps/conflicts, invalid geometry, deletion/reuse and independent caps.
- `StationSchemaMigrationTest` plus synthetic `migrations/v150/stations-v2.snbt`:
  exact legacy conversion, future/mixed/typed/size bounds, preservation and
  preflight-only upgrade. Nonempty member/invitation and unknown-body cases here
  are synthetic, not substituted for native team coverage.
- `StationWorldMigrationTest`: byte-exact mixed-version backup and per-file
  manifest, unchanged other authorities, staging/readback/runtime failures,
  unsupported atomic move, post-replacement rollback, simulated interrupted
  batch and incomplete restore reporting. The interruption uses an injected
  Java `Error`, not an actual killed native process.
- `StationProtectionGameTests`: real Forge event objects with scoped FakePlayers
  exercise break, placement and multi-placement for normal/operator policies,
  other Levels and a valid gap. It restores the original in-memory registry
  synchronously; no corrupt registry is saved in the GameTest world.
- Existing loader/migrator/preservation/access/diagnostic tests retain their
  assertions with the independent station versions. `ModMetadataTest` tracks
  exact v1.5 identity/description literals, not an unrestricted version match.

## Commands actually executed

Java `C:/Program Files/Java/jdk-17.0.7`; Python shim
`D:/python/pyenv/pyenv-win/shims/python.bat`, `PYTHONUTF8=1`.
Every Gradle command used `--offline --no-daemon --console=plain`.

| Command | Observed result |
|---|---|
| `gradlew.bat compileJava` | Exit 0, 16s; initial production compilation |
| `gradlew.bat test --tests '*station.*' --tests '*persistence.migration.*' --tests '*BetaOperationalReportTest'` | Exit 0, 32s; 76 tests / 12 suites, no failures/errors/skips |
| Independent nine-class targeted `test`, additionally `--no-build-cache` | Exit 0, 24s; 61 tests / 9 suites actually executed, no failures/errors/skips |
| `gradlew.bat clean build runData runGameTestServer` (first attempt) | Exit 1, 43s; 943 tests, one stale v1.4 metadata assertion failed; DataGen/GameTest not reached |
| `gradlew.bat clean build test runData runGameTestServer` (corrected) | Exit 0, 3m49s; 943 JUnit tests / 170 suites executed, 0 failures/errors/skips; all 237 required GameTests passed |
| `git diff --exit-code -- src/generated` | Exit 0; all 253 tracked generated files unchanged, no new generated files |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 passed, no pending/warnings/failures |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0; 11 plans / 33-input inventory |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0; historical bootstrap mechanical provenance remains valid |
| `python -B -m unittest discover -s tests -p test_v1plus_planning.py -v` | Exit 0; 15 tests passed in 16.419s |
| Temp `collect_checks.py` | Exit 0; JUnit/artifact/source/resource hashes, 27 unchanged API classes |
| Temp `native_station_check.py` | Exit 0; two native clean processes, each exit 0; exact old-world migration/restart checks below |
| `git diff --check` | Exit 0 |

The final Gradle run had 20 executed tasks and 7 compile-cache hits. JUnit and
GameTests were executed, not restored test results. DataGen processed the
existing 34 current resources and wrote zero files. The first failed full-run
log/XML are retained; only the three outdated metadata literals were corrected.

The full GameTest log retains 15 ERROR lines: initial missing `server.properties`
and the existing deliberate precision/energy/satellite failure injections; no
FATAL line. Its 2,163ms / 43-tick startup-lag warning is retained, not dismissed
as a reference-performance result. Native processes have zero ERROR/FATAL and
zero project warnings; loader/offline-fixture warnings are retained (16 / 10).

## Finite native migration and restart

The source was the retained genuine v1.4 MIG-02 `server-final/world`, not a
synthetic NBT world. Its station bytes match the prior
`native-final/restored-restart/state` capture and its old host is pinned to
SHA-256 `576eaaf8a5e23df1b049fd5fa5029f1cd6d6ffcfd0cf50bd5980fc280c31d475`.
The harness inventories/copies the complete bounded source and checks the source
inventory again after both processes. The original is unchanged.

1. First v1.5 start: `ARCE-BETA-1002`, managed **4**, migrated **1**. Both old
   station identities/regions/pads/orbits are inspected through live commands.
   After `save-all flush` and clean stop, the station payload equals the original
   except root version/epoch and record versions. Other present authorities
   match except the independently validated satellite clock.
2. One backup contains exactly all four present managed primaries; this native
   source contains no `_old` companions (unit fixtures cover them). Unique manifest membership, file lengths, every
   source/backup hash and per-file schemas are checked, not just present rows.
3. Second v1.5 start: `ARCE-BETA-1001`, migrated **0**, backup **none**. Live
   inspection and saved station payload remain the same; the original backup
   tree is byte-identical and no additional backup is created.

The frozen historical consumer JAR is retained to avoid unrelated missing-mod
registry changes. Its identity and actual Forge status mod set are checked.
Generic bounded copy/NBT/process/status/log helpers are reused; historical
v090/v100 root-schema/manifest oracles are neither invoked nor loosened.

This world has two stations and empty teams/invitations/reservations. It does
not prove nonempty native teams, expanded-region operation, all docked/inbound
rocket cases, crash cuts, power-loss durability, real clients or a load campaign.
Those remaining station-operation/native cases belong to 03/04. An unavailable
orbit ID is retained by codec tests; no native unknown-orbit result is claimed.

## Artifacts

Development build `1.20.1-1.5.0-dev`, not a frozen release candidate:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| main | 2632344 | `119671d32d133311a4d227f42edcd15d8912a8f63f1c0a606b36f8071011b3cb` |
| API | 48469 | `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf` |
| sources | 1197309 | `d8cc369d59d09e4693a4d60e7246914fb369c7726e7d75ab6fbe9b2c7cc83b08` |

API JAR bytes and all 27 class bodies are unchanged from the v1.4 handoff.
Current public protocol sources are unchanged.

## Independent review and evidence

Independent source/key-test review and native-oracle readback are retained in
the reviewer Temp directory below. One pre-execution evidence finding identified that
validating only listed manifest rows would not detect omitted rows. The harness
was corrected to require exact expected membership, uniqueness and lengths
before its first native run. No production defect was demonstrated by that
finding. Independent `native-readback.json` / `.log` record Python exit 0 and
successful exact membership/source/backup/schema readback, not a second execution
of the dedicated server. The initial `REVIEW.md` predates this readback and still
lists that finding; its final addendum/checksum inventory was interrupted by
the requested pause. Preserve both boundaries rather than rewriting the raw report.

Existing root evidence:

```text
C:/Users/Administrator/AppData/Local/Temp/arce-v150-station-schema-04389c908f4b406ebc03badf69358576
```

Key files: `root-gradle-01.log` / `full-junit-01-failed/` (retained failure),
`root-gradle-02.log` / `full-junit-02/` (passing full run), `focused-junit/`,
`gametest-latest.log`, `gametest-debug.log`, `artifacts.json`,
`implementation-files.json`, `generated-files.json`, `junit-summary.json`,
validation logs, `native_station_check.py`, and `native/` (both process receipts,
commands/status/logs, before/after NBT, exact backup and source inventory).

Existing reviewer evidence:

```text
C:/Users/Administrator/AppData/Local/Temp/arce-v150-station-schema-review-3c51e146d3d54e2f828b5959d7f8ea66
```

Key files: `REVIEW.md`, `targeted-tests.log`, `command-result.json`,
`test-results/`, `inspected-file-hashes.json`, original/revised native harnesses,
oracle probes and `native-readback.json` / `.log`. The independent readback
observed 69 unchanged source files and identical upgraded/restarted station SHA-256
`2e9fef1644ae2ed7a00ce80ecda54432af8d19d019edd8110875a3ddeb4065c4`.

## Evidence archive

After resume, a separate packaging script read both Temp directories without
modifying them and wrote this packet. No build, test or server was run; no
evidence was regenerated. Before writing, it asserted that:

- all 27 tested implementation files equal the working tree and are exactly the
  changed `src/main`, `src/test` and `gradle.properties` paths since the base;
- the three `build/libs` JARs equal the artifact table above;
- the retained XML totals are 943/170 passing, 943 with one failure (first run),
  76 focused and 61 independent, and the Gradle logs contain their result markers;
- the native summary passes both phases, the independent readback passes, and
  the root harness, reviewer's revised harness and readback all share SHA-256
  `9c992bd8f8d9d7fa25eaf2ad27040cf6e42c51f52be7b8e8e027e11fd21590d8`.

| Archive | Members | Contents |
|---|---:|---|
| `root-checks.zip` | 383 | Root Temp logs, JSON, JUnit XML (passing, failed and focused), GameTest logs, harness scripts and `packaging/archive_evidence.py` |
| `native.zip` | 37 | `native/`: before/after NBT, backup and manifest, both phase receipts/commands/status/logs, source inventory, summary |
| `independent-review.zip` | 142 | The complete reviewer directory, including the unmodified `REVIEW.md` and `native-readback.*` |

Each archive has a `*-files.json` member index (path, bytes, SHA-256).
`evidence-archives.json` records the source directories and the root exclusions:
`station-server/` (server libraries, mods and the disposable world copy; its
logs equal the retained `native/restart` logs), `__pycache__/` and `native/`
(archived separately). `root-checks.json` and `development-artifacts.json`
summarize the asserted results. `source-identity.json` binds every other staged
file to its committed blob, `links.json` lists validated local Markdown targets,
and `SHA256SUMS.txt` covers this directory.

After archiving and the status-document edits, the document-level checks were
rerun: `validate_repository.py --require-approved-identity` (exit 0, 45 passed),
`validate_v1plus_planning.py` (exit 0), `validate_bootstrap_provenance.py`
(exit 0) and the planning regression suite (exit 0, 15 tests in 23.9s). Their
logs are in the packaging directory's `out/` folder and are not in the ZIPs.

The reviewer's addendum was never written because the pause interrupted it. The
unmodified `REVIEW.md` still lists finding 1 as open. The reviewer's own later
`native-readback.json`/`.log` show that finding resolved: the corrected oracle
rejects empty, missing-row, duplicate-row and wrong-size manifests. Root did not
write an addendum on the reviewer's behalf.

## Gate status, risks and rollback

01/02 are a bounded development implementation, not the complete station
capability or v1.5. Local expansion/checked commit, orbit effects, stars, warp,
multiplayer, visual and performance acceptance remain open. No G0-G9 is marked
passed. The acceptance cursor and all prior approvals are unchanged.

Ordinary station creation/member/deletion saves retain historical failure
semantics; this does not claim those cross-chunk operations are atomic. Build
protection covers player events, not comprehensive automation/explosion/fluid
protection. Whole-world backup is still required before upgrade. For downgrade,
stop the world and restore that complete pre-upgrade backup with the old build;
do not edit schema numbers or combine partially restored authorities.

Next is [V150-STATION-03](../v1.5.0-implementation-log.md#v150-station-03), the
checked local expansion operation, followed by its native 04 cases. Full-content
acceptance remains deferred under ADR-018 without waiving these finite checks.
