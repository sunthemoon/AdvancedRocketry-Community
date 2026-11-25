# v1.8.0 migration report — C6 draft

**DRAFT; native accounting results recorded, broader campaign migration actuals PENDING.** Test identity is C6,
`c8fa16e16be87554f26c402546733df6555d15e6`, host JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
This is the procedure and evidence table, not an assertion that a world is safe
to upgrade. [PROJECT-CONFIG](../../../PROJECT-CONFIG.md) requires 1.x migration;
direct 1.12.2 world loading is not promised.

## Identity and input census

| Field | Required recording | C6 completion |
|---|---|---|
| Source world | Previous stable and beta/RC build/commit/JAR; archive SHA-256; played or reconstructed provenance | K02/K03 inputs are delivered, with reconstructed origins retained ([W431](../../work/v1.8.0-root-queue/records/W431.md), [W436](../../work/v1.8.0-root-queue/records/W436.md)); final A03/B01/E10/I03 source-to-result census and broader upgrade outcomes remain PENDING. |
| Target runtime | Candidate above; exact Java, Forge, mods, data packs and configuration hashes | PENDING |
| Saved data | Every managed key and independent schema; DataVersion; future/downgrade handling | PENDING final complete schema inventory |
| Network/API | Protocol pin and fixture-mod/API version; client/server same JAR identity | PENDING; [network pins](../../../src/test/resources/network-protocols.txt) are the source, not a guessed single protocol number |
| Registry mappings | All old IDs, placed blocks, item stacks, block-entity roots and inventories | PENDING; I03 needs K02's all-old-ID v1.7 archive/manifest |
| Resource state | Items, fluid by type, energy where applicable, rockets/seats/passengers, stations, research, missions/receipts | PENDING before/after census |

[ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md)
uses additive stable IDs. Presentation/behaviour replacement is not permission
to rename or delete an old registry identity. I03 must show every placeholder
remains usable and unchanged in identity. Any actual rename/removal needs an
explicit Missing Mapping/migration plan before release. Recipe changes do not
rewrite player inventories. Missing dependencies must be diagnosed, not silently
discarded; D10 tests the API provider/mod subset.

## Copy-only upgrade procedure

1. Stop the source server and confirm its JVM has exited. Preserve a complete
   consistent world plus mods/configuration/data packs, source JAR and hashes.
   Never migrate an original fixture or treat an in-flight copy as a backup.
2. Copy it to a new disposable target. Record pre-upgrade file/ID/state census
   and disk space. Do not combine individual files from different world times.
3. Launch the copied world on the pinned candidate/runtime. Archive full logs,
   migration diagnostics and before/after managed schemas and record counts.
4. Verify identities, resource totals and continued representative gameplay;
   “world opens” alone is insufficient. Save/stop, confirm exit, restart twice
   on the same copy, and compare after each start.
5. Repeat migration/loading to prove documented idempotence or safe refusal.
   On failure retain the failed copy and source hashes. Never overwrite the
   original or repair it in place to make a test pass.

The [campaign plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md)
§§5–6 supplies logs/state/media layout; the
[quality budgets](../../17-V1PLUS-QUALITY-BUDGETS.md) §7 supplies the required
stable→candidate, beta/RC→candidate, repeat migration and interrupted-transaction
cases. The historical v1.7 development world is not a previously approved stable
release; label its origin accurately.

## Candidate result matrix

| Case | Planned evidence | Result / actual / receipts |
|---|---|---|
| Previous stable → C6 | Source stable fixture identity plus A03/B01/E10 as applicable; preserve distinction between stable and development fixtures | PENDING |
| Previous beta/RC → C6 | A03 Beta copy and appropriate retained candidate fixture; original hashes unchanged | PENDING |
| v1.7 all old IDs/placeholders → C6 | I03: all IDs/roots/items and representative use, two restarts | PENDING |
| Current C6 save → reopen twice | A17/I03, J22 and native per-family restarts | Native selected-family restarts recorded in [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) (R632); A17/I03 broader identity/resource checks PENDING. |
| Interrupted transaction → recover | A04/G04/H03/J22; exact forced-stop boundary, full before/after census | J22 attempt 2 remains 74 PASS / 4 FAILED; K02 has passing attempts for 89 sealed cuts ([W461/R664](../../work/v1.8.0-root-queue/records/W461.md)) and 7 PASS / 3 FAIL flight cuts ([W465/R667](../../work/v1.8.0-root-queue/records/W465.md)); unmatched cuts, passenger/client and A04/G04/H03 campaign checks remain PENDING. |
| Ledger root 2 → accounting root 3 | `accounting-root2-upgrade`, four selected cases including stop after replacement; preserve accounting/proof fields | PASS in C6 attempt 2; [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) section 12.2. KI-V180-UPGRADE-01 is accepted/published without code change (R680). |
| Repeated upgrade/load | Same copied world, duplicate credits/migration counts checked | PENDING |
| Unsupported/future/invalid root | R-031 and accounting families; exact refusal scope and file changes | Selected r042-R031_FUTURE and r042-R031_INVALID families PASS in C6 attempt 2; [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) sections 9/12. Complete cross-store/schema census remains PENDING. |
| Missing provider/mod and reinstall | D10 copy-only refusal/recovery; no broad modpack claim | PENDING |
| Old JAR reads newer journal/root | `cargo-G2-REUSE`, `w336-downgrade`, `accounting-root3-old-jar`; retain failed byte-preservation assertions | All three FAILED on C6 (R615/R618/R652). R679 signs only the residual; [C6 results](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) and [the sign-off](../../work/v1.8.0-root-queue/records/R044-SIGNOFF-DRAFT.md) retain the failures. |

## Refusal, backups and downgrade boundaries

Per [C6 report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §12,
the activated accounting format uses ledger root 3; an older root migrates under
checked writes. The complete inventory of other schemas remains to be archived;
root 3 is not a world-wide schema number.

Root decision R680 (2026-11-24, under the
[owner's delegation](../../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261124.md)
of the [owner packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md))
accepts KI-V180-UPGRADE-01 in [KNOWN-ISSUES](KNOWN-ISSUES.md): interruption before
replacement can leave `.…migrating-<uuid>` staging files and a backup directory.
Incomplete backup directories count toward the fixed five-directory cap. A
still-unmigrated world at that cap refuses startup with `ARCE-BETA-2004`; an
already migrated world is not blocked by that cap. C6's four-case
`accounting-root2-upgrade` family passed in attempt 2; the broader upgrade
campaign, source-fixture comparisons and full schema inventory remain PENDING.

Published operational handling under R680 is stop first, preserve a complete backup, move
older migration-backup directories outside the world rather than deleting them,
and move stale staging files only after migration is verified. Migration backups
are **not complete-world backups** and cannot be restored alone.

R-044's downgrade residual is accepted with the
[R-044 sign-off](../../work/v1.8.0-root-queue/records/R044-SIGNOFF-DRAFT.md) §5
unchanged (Root decision R679, signed by Root on the owner's behalf on
2026-11-24); KI-V180-DOWNGRADE-01 in [KNOWN-ISSUES](KNOWN-ISSUES.md) gives the
backup and downgrade instruction. Historical old-JAR startup changed `level.dat` and
`level.dat_old` before refusing; exit code 0 alone does not prove successful
loading—operators must inspect `ARCE-BETA-2001`. Signing a limitation neither
passes the failed checks nor enables admission. At the journal-schema-3 boundary
(a), returning to v1.8 requires the complete pre-downgrade backup; reopening the
rewritten world without it remains unsupported. At the ledger-root-3 boundary
(b), the signed text also supports reopening the refused world on v1.8 without
restoration: C6's two later starts logged no ERROR and retained root 3 and the
held legacy operation (R652). Returning to an older version always requires the
complete pre-upgrade backup and loses later v1.8 progress. Any restoration replaces
the entire stopped-world folder, never selected data/region/player files. There
is no general single-file rollback or power-loss guarantee.

Native commands/times/hashes and measured refusal/recovery observations are
indexed by the C6 report and R044 sign-off. These are not substitutes for the
remaining stable/beta/v1.x fixture campaigns, I03 repeated migration and complete
ID/schema census, independent migration review and final acceptance: **PENDING**.
R682 changes no ID or schema and removes no retained history; deferred
admission does not disable existing-ledger migration. R691's four narrow
protection signatures do not accept the six unsigned risk scopes or G5.
