# V150-GOV-01 / V150-CON-01 preparation verification

Date: 2026-09-28. Scope: development baseline, station foundation contract,
documentation examples and a planning-test fixture correction. **No v1.5
production feature, world migration, candidate or Required Gate is delivered here.**

Baseline: `6f530ac7db4bf0e06be6d6aaef35e6ca31a5651b`;
branch: `codex/v1.5.0-orbital-station-warp`.
[Implementation log](../v1.5.0-implementation-log.md) is the canonical task tree.
Inherited G0-G9 stay open. No upstream imports or public API changes.

## Decisions and unchanged runtime

[ADR-039](../../decisions/ADR-039-V150-DEVELOPMENT-BASELINE-EXCEPTION.md)
accepts only v1.5 development from the committed v1.4 handoff, separately from
inherited acceptance. [ADR-040](../../decisions/ADR-040-STATION-REGIONS-AND-MIGRATION.md)
freezes station identity/geometry, explicit legacy migration, local confirmed
expansion and checked candidate authority. It does not freeze the full warp
protocol or implement those rules.

Root recorded acceptance under the maintainer's standing direction to follow
recommended solutions after independent source/draft review; this is not a
new numbered approval message. Independent review supplies findings and scoped
verification, not maintainer authority or Gate approval. Active development is
v1.5; the earliest unfinished release cursor remains v1.0.

Production Java/resources/build/scripts are unchanged against the baseline.
The runtime remains `1.20.1-1.4.0-dev`; API 1.7 has the same 27 class bytes and
all three rebuilt JARs exactly match the v1.4 handoff. See
[baseline-checks.json](baseline-checks.json) for full identities and command data.
No candidate identity is assigned to v1.5.

## Independent findings and dispositions

Unchanged original source inventory and draft/revised reports are retained in
[independent-review.zip](independent-review.zip), with source/output identity in
[independent-review.json](independent-review.json). The original inventory
report SHA-256 is `6594cb5c9b3529a388530fcfc028496b8eda4e4507136ad7340696f996c267f5`;
initial contract report is `511236c82221d50504f01c9859cffc42d1bc2077d8110ec826c0f96ce6c825f2`;
revised core report is `8d362153d85ff55279cec8f79a5bc6cf35ebfb2907db3d3d0327983a45cd408c`.
Final substantive report is `061d5c5b2f0c039e3d88b448867cf125e0a65b5141c37cf6d6b4922f2da25f41`.
Both contract findings are resolved. Independent reruns passed 15 planning tests
(18.031s), 12 documentation examples and the 11-plan/33-input validator; artifact
readback confirmed three JARs / 27 API classes / 34 generated entries without
running a JVM. Its original final link scan retains five evidence targets not
yet packaged at that time; final assembly checks resolve those separately.
The [assembly review](assembly-review.zip) records the separate packet readback;
root attaches that unchanged report and rechecks the resulting packet/index.
Its report SHA-256 is `f0f97e8a48ba3931ec748af64538257cf70e6f60e315211debd1105360fffbc8`.
The final snapshot-scoped check passed: 17 initially staged files, eight packet
checksums, 351 archive content members, four reports and 111 document links.
The later attachment adds evidence only, not another runtime result.
One original inventory-manifest defect is retained: relative names erroneously
start with `7a99/` because of Windows short/long Temp path handling. The archived
manifest is **not valid as-is**. Its original bytes/hashes remain unchanged;
the independent assembly receipt applies exactly that prefix mapping and the
root ZIP's separate member manifest uses the actual archive names. The initial
failed nested-manifest lookup is retained in the assembly evidence.
The root Temp directory continued assembly after `root-checks.zip` was frozen:
`final_readback.py` gained checks for the later report attachment. An attempted
all-files comparison with that live Temp directory therefore also failed and is
retained. The frozen root ZIP is bound by its member manifest, not a promise that
its working Temp directory never changes. The later readback/attachment helpers
are preserved in the assembly ZIP; both original reviewer directories stay
immutable and are checked against their archived bytes.
The assembly evidence also retains an interim short-check failure when the
declared attachment note was not yet staged. Final root readback stages that
note and the attachment, checks every final packet hash against both working
bytes and Git index blobs, and requires an empty unstaged diff.

| Finding | Disposition |
|---|---|
| Checked persistence could introduce new exceptions into creation's post-commit rollback, which only releases reservations | Expansion uses an isolated immutable candidate commit; old ordinary flush callers do not silently inherit throwing semantics. Any wider writer conversion must enumerate/adapt/test affected callers first. This is a contract repair, not a runtime fix. |
| Local/operator request and confirmation lifecycle were ambiguous | Both owner/operator must be actual players in the committed Space region with their current-position chunk loaded; no console/remote bypass. Actor/state/lifecycle binding, 200-tick lifetime, 128-entry cap and logout/stop cleanup are explicit. |
| Unavailable registry currently resembles a valid unowned gap at the build boundary | First runtime slice must deny Space placement/break while authority is blocked and preserve data. Preparation does not fix that source behavior. |
| Existing effective gravity/sky and docked/in-flight rocket authority do not follow an orbit mutation | Separate ORBIT/WARP contracts and implementation remain required; no orbit mutator or metadata-only warp substitute. |
| Evidence collector counted the legacy generated-resource directory | Corrected to the actual build output `src/generated/v1.4/resources`, 34 entries; the directory is explicit in JSON. Initial logs with 29 legacy entries remain retained. |

## Tests added or corrected

[examples.json](examples.json) and [check_examples.py](check_examples.py) add
12 standard-library documentation checks: inclusive/negative/max-coordinate
geometry, nearest-cell selection, 256-column gap, allowed widths, legacy field
preservation and version-only changes, reservation independence, current
idempotence, invalid/mixed/future/epoch cases and off-center/pad rejection.

Examples are normalized JSON projections, not NBT decoders, authentic world
fixtures, migration/backups, permissions, runtime or performance evidence.
They deliberately do not validate every production field, UUID/list/byte bound
or demonstrate actual crash safety.

The existing planning unittest fixture copied canonical docs but omitted the
already-linked `compat-test-mod/README.md`. The first run had **2 failures / 14
tests**, while the real-repository validator passed. The fixture now copies the
actual guide and adds a negative deletion test. No validator rule/assertion was
removed or relaxed. The corrected **15 tests passed**; the original failure log
is retained, not overwritten.

## Commands actually executed

Windows, Java `C:/Program Files/Java/jdk-17.0.7`, Python via
`D:/python/pyenv/pyenv-win/shims/python.bat`, `PYTHONUTF8=1`, offline Gradle cache.

| Command | Result |
|---|---|
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon --console=plain` | Exit 0, 2m31s; 17 tasks executed / 9 from cache / 1 up-to-date |
| `python -B docs/work/v1.5.0-preparation/check_examples.py` | Exit 0, 12 tests, 0.003s |
| `python -B -m unittest discover -s tests -p test_v1plus_planning.py -v` | First exit 1, 2 failures/14; corrected exit 0, 15 tests, 18.223s |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed, no pending/warnings/failures |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0, 11 plans / 33-input inventory, no original bundle reads |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0, accepted historical bootstrap mechanical evidence intact |
| Temp `collect_baseline.py` | Exit 0; production and generated diffs empty, exact 3 JARs / 27 API classes, 34 current generated entries |

The Gradle command executed **236 required GameTests**, all passed, in the
disposable `build/gametest` world after clean. JUnit contains **922 results /
167 suites**, zero failures/errors/skips, but `:test FROM-CACHE`: these JUnit
tests were **not newly executed** in preparation. DataGen wrote **zero** files.

The retained build log has **15 ERROR lines**, **0 FATAL lines**, intentional
Precision/Satellite failure-injection diagnostics and initial missing
`server.properties`; it is not an error-free-log claim. Startup also reports
**2,656 ms / 53 ticks behind**. Passing functional checks do not turn that into
performance acceptance. Early source exploration used several nonexistent
guessed filenames/directories and then corrected them through tracked inventory;
one patch context did not apply before a corrected patch. No missing source or
failed edit was treated as implemented behavior.

## Evidence and boundaries

[root-checks.zip](root-checks.zip) retains command logs, initial/corrected planning
tests, original build diagnostics, all 167 JUnit XML files, helpers and artifact/
resource identities. [evidence-archives.json](evidence-archives.json) records
source directories, exact member manifests and archive hashes;
[SHA256SUMS.txt](SHA256SUMS.txt) binds this preparation packet. Changed-file/link,
checksum and staged-index readback accompany final assembly; `git diff
--exit-code` applies after intentionally staging the scoped changes, not as a
claim that preparation made no edits.

Independent execution is limited to short Python/document/Git checks and
readback; it did not rerun root Gradle/JVM/native commands. No dedicated native
server migration, real-client V1/V2, remote or long-load checks were run here.
Historical v1.4 native evidence is baseline context, not a new v1.5 result.

Known implementation risks remain assigned to STATION-01..04 and later ORBIT,
STAR/WARP/UI/MIG tasks. In particular, no tested migration, checked station
operation, effective orbit environment, multi-star catalog or recoverable warp
exists merely because a contract was accepted. All G0-G9 remain unapproved;
the next implementation leaf is **V150-STATION-01**, not v1.6 or release tagging.
