# V140-MIG-02 — authentic development-world upgrade and content restoration

## Scope and identities

2026-09-28. Base/production commit:
`a9143c2b62d5daab412cefedcee198f1fd032588`, branch
`codex/v1.4.0-planetary-expansion`. This slice adds a bounded copy-only migration
runner, storage oracles and 17 Python tests. No production Java, resource,
public API, network or save-schema change is included. The three v1.4 build
artifacts remain byte-identical to MIG-01 after clean build.

The source is the retained SAT development world at
`C:/Users/Administrator/AppData/Local/Temp/arce-v130-sat-1790482375436/server-final/world`,
not a renamed pre-planet v1.4 fixture. Its original 48-member evidence manifest
and all five final captured world files were verified against the historical
run. The newly inventoried complete original contains 34 files / 13,580,236
bytes and is unchanged after both attempts. Its existing celestial store was
not among those original five archived files; the complete inventory made now
does not retrospectively enlarge the historical archive.

| Native input | Bytes | SHA-256 |
|---|---:|---|
| v1.3 host | 2,352,333 | `d1f9f564ed1dcd1df7ac0648b4091a07dfa9a02f63b36b827f36aad591224475` |
| historical consumer, retained throughout | 72,583 | `e58de48ff038ff42fa1173bfd210d9e8321cfcfa66849ba1c13a0e31c1c02a47` |
| v1.4 host | 2,624,223 | `576eaaf8a5e23df1b049fd5fa5029f1cd6d6ffcfd0cf50bd5980fc280c31d475` |

The v1.3 host matches its development handoff. Full v1.4 identities, including
API and sources, are in [development-artifacts.json](development-artifacts.json).
These are not stable release candidates or accepted Required Gates.

## Scenario and actual observations

The final four owned packaged JVMs exit 0. Every phase checks the exact two
installed mod JARs, host/status identity, zero connected players, startup
catalogs, configuration and saved authority. No original world is booted or
edited; a complete original copy and prepared old-runtime backup are retained.

1. **Legacy copy:** actual v1.3 boots the copied world. The historical terminal
   at (264,101,264), two claimed missions, custom satellite and account remain
   unchanged. A Moon station and six-block rocket are newly created by the old
   runtime on the copy, not represented as objects from the original archive.
   All seven rocket/assembler cells are checked for air before placement at
   (520,100,520). The rocket contains 17 diamonds, 64 iron ingots and 2000 fuel.
   Exact implicit schema-1 Earth/Moon/Space JSON bytes from the old JAR remain
   active as overrides; no binding ledger exists before upgrade.
2. **Upgrade and removal:** v1.4 adopts six stable body bindings, retaining the
   old identities and legacy Moon defaults. Mars and Venus missions start
   before discovery; Mars is claimed and gains a station. A probe pack filters
   the two celestial definitions and four dependent route resources and trims
   satellite allowed targets. Both listeners actually accept: four bodies,
   five route definitions and two satellite definitions. Mars surface arrival,
   arrival at its existing station, new Mars-station creation and new Venus
   research are refused. Two Venus claims return CATALOG_UNAVAILABLE; persisted
   status is CLAIM_PENDING_DISCOVERY, with its captured fee already applied.
3. **Absent-content restart and restoration:** the removed state survives a
   clean restart, including both stations and all six binding reservations.
   The same refusals still hold. Restoring the same content IDs publishes six
   bodies / nine route definitions and two satellite definitions. Production
   tick replay completes Venus discovery before any subsequent manual claim.
   Repeated Mars/Venus claims then return ALREADY_CLAIMED.
4. **Restored restart:** final restart and further repeated claims preserve the
   completed missions, balances, discoveries, native terminal, rocket/cargo/
   fuel and exact station records. No transfer journal entry is introduced by
   rejected travel. All closed-world captures contain exactly one rocket.

Historical account balance/earned/spent remain **337/348/11**; Earth discovery
timestamp remains **461**. The new owner remains **40/240/200** through pending
and completed states. Mission UUIDs, owner/satellite bindings, definition,
duration, captured reward/fee and timestamps are compared, not only totals.
Restoration adds Venus discovery without fabricating a visit. Station UUIDs,
owners, orbit bodies, regions and remaining saved fields survive unchanged.
The terminal retains 9000 FE, its amethyst shard and bound control chip.

Saved-authority comparisons exclude only the two validated advancing scheduler
clock fields. Live checkpoints read SavedData and use actual rocket report/SNBT
receipts; they explicitly omit native regions. Exact terminal and complete
RocketEntityData readback occurs after each clean stop, with unchanged strict
Anvil bounds. Physical Minecraft dimension registrations are not removed by
the celestial-resource filter. The existing pack filter mechanism is described
in the [Minecraft snapshot notes](https://www.minecraft.net/nl-nl/article/minecraft-snapshot-22w11a).

## Commands and short checks

All commands use Java 17.0.7 and Python through
`D:/python/pyenv/pyenv-win/shims/python.bat`, with `PYTHONUTF8=1` and `-B`.

```text
gradlew.bat clean build test runData runGameTestServer --offline --no-daemon --console=plain
python -B -m unittest tests.test_v140_migration_smoke tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v
python -B scripts/run_v140_migration_smoke.py <fresh-libraries-only-server>
  --source-world <retained-v1.3-world> --source-evidence <original-runtime-final>
  --baseline-jar <exact-v1.3-host> --host-jar <frozen-v1.4-host>
  --fixture-jar <exact-historical-consumer> --evidence-dir <new-evidence>
  --java <jdk17>/bin/java.exe --accept-eula
git diff --exit-code -- src/generated
git diff --check
python -B scripts/validate_repository.py --require-approved-identity
python -B scripts/validate_v1plus_planning.py
python -B scripts/validate_bootstrap_provenance.py
```

Root build exits 0 in 2m39s: **922 JUnit results / 167 suites restored FROM-CACHE**,
not a fresh Java execution; **236 required GameTests actually execute and pass**.
DataGen writes no generated-resource changes. Root's **43 Python tests execute
and pass**, including 17 new migration checks. Build, test and runtime diagnostics
are retained rather than described as zero-warning logs. This is finite regression,
not a full acceptance, load or reference-hardware performance campaign.
The repository check passes 45 checks, planning validates 11 version plans and
provenance passes. These run before final evidence documentation; the separate
staged-file/link/archive check covers the added evidence and document changes.

Independent actual-source review and a separate 43-test execution pass with
unchanged script/test input hashes. A separate raw audit checks all 269 final
manifest files, native authority, commands and restoration ordering without
using the new runner's acceptance predicates. The final report finds no unresolved
correctness issue in this Python slice. It does not independently rerun Java.
Report SHA-256: `b8fe1bcc1cb19496153acdb72e5f9bcc31324d6796bfe96e1c710b73450f3475`.
Its original implicit-schema and parent-key findings are corrected; its initial
helper assumptions about retained route count and exact EULA text are preserved
alongside corrected successful audits. Original review snapshots and logs are
included in the independent archive rather than overwritten.
Root's archive finalizer initially checked a generated identity link before
creating that file. The original helper and reproduced failure log are retained;
the corrected ordering creates the output entries before checking links. This
does not change tests, native observations or production artifacts.

## Preserved failures and diagnostic classification

The first native attempt passes its v1.3 process but fails while scanning a
live entity-region file at the upgraded checkpoint. Chunk (20,-3) declares an
allocation ending at 90112 while the captured active file ends at 87350. The
complete bounded zlib payload ends at that file boundary; the strict decoder
expects a closed, fully allocated region. The owned failed upgraded process is
aborted (exit 1). Original logs, commands, file bytes and receipt remain under
`native/` in the archive. This is a harness observation-boundary failure, not a
passing native phase or a production data-loss finding.

The correction defers native region decoding to clean stopped worlds and names
the narrower live SavedData projection. It does not manufacture an observed
terminal value, relax the region decoder, enlarge timeouts or change budgets.
The second attempt uses a fresh copy and passes all four clean processes.

Only the absent-content startup contains an expected project ERROR:
`ARCE_STATION_UNKNOWN_ORBIT_BODY station=bca68d47-9961-4f2d-b798-26ffab48a74e`.
The exact one-line/one-phase diagnostic is checked; unrelated errors still fail.
No native FATAL, client-linkage failure or project warning is reported. Bracketed
warning counts are 15/11/10/10; these omit the initial unbracketed Log4j warning.
The raw logs retain all messages. The upgrade phase includes Forge's mod-version
difference warning for the 1.3-to-1.4 change; no performance claim follows from
the absence of a timing warning.

## Evidence and limits

- [Root checks](root-checks.json), [logs and JUnit XML](root-checks.zip),
  [archive inventory](root-checks-files.json).
- [Native summary](native-summary.json), [both native attempts](native-migration.zip),
  [archive inventory](native-migration-files.json). Final manifest: 269 members.
- [Independent reports and checks](independent-review.zip),
  [archive inventory](independent-review-files.json).
- [Staged source identity](source-identity.json), [local link check](links.json),
  [complete evidence checksums](SHA256SUMS.txt).
- Root raw directory:
  `C:/Users/Administrator/AppData/Local/Temp/arce-v140-mig-worlds-608e947bd8c34249bc523f49858267b8/`.
- Final independent directory:
  `C:/Users/Administrator/AppData/Local/Temp/arce-v140-mig02-script-review-1b048a93afbb497496a2545b8e78d23a/`.

This is a representative authentic v1.3 **development** fixture, not every
historical subsystem or a released-world migration Gate. New old-runtime
witnesses are explicitly distinct from historical content. First adoption
cannot reconstruct unknown older custom Level mappings. No real player visit,
terminal interaction, player station-membership check, GPU/client/network,
remote, arbitrary power loss, forced process interruption or long-load claim is
made. Native interruption work remains MIG-03; full acceptance stays deferred
under ADR-018. All v1.4 Required G0-G9 remain open and status is IN_PROGRESS.
