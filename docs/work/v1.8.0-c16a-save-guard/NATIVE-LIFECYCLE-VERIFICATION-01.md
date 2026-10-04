# Existing guard lifecycle diagnostic verification

Date: 2026-10-04. Owner and sole integrator: Root.
Version: v1.8.0, IN_PROGRESS / IMPLEMENTING. All Required Gates remain open.

## Delivered source and boundary

The bounded copy-only diagnostic script and tests are committed and pushed at
`a382e36e4e1ce5949bb9cc9c9ab3517ebfd9d0ce`. The separately reviewed opt-in
unload observer and stricter event gate are committed and pushed at
`23bec1eb1452e8c8ef37c004606aaed3a2394063`.
The two-file copied-spawn setup and its task record are committed and pushed
at `f945dc1c33f09199da9d47663e891acf1cf4a475`; its native outcome is still FAIL.

Only one new Java class is added. It logs the actual fixed overworld chunk
(11, 11) Unload event on the server thread when release-test hooks are enabled.
It does not mutate resources/world data, load chunks, cancel events, clear
denials or change save behavior. Its marker means unload **begin**, not completed
BlockEntity callbacks. Under ordinary inline event dispatch and queued console
execution, the later command cannot interleave the same-thread continuation;
foreign reentrant/erroring listeners remain unproven.

Source23 names the 2,995 declared build/tool inputs: 2,992 preceding inputs plus
the observer and changed script/test postimages. It is not a whole-repository
snapshot. This probe does not deliver real GuardTickets, first-save writers,
full native codecs, resource consumers, physical hatches or ordinary player
removal/drop conservation. R-021 is open and unaccepted; the factual ADR
disclosure proposal remains PROPOSED, without a save-policy change.

## Actual Java build and artifact

Java 17.0.7, Forge 47.4.10; fresh C/D space checks exceed the recorded
10,000,000,000-byte threshold before build, GameTest and native launches.

| Actual command | Result |
|---|---|
| `gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G clean build test runData` | exit 0, 202.700690 seconds; 1,765 JUnit cases / 330 suites, zero failures/errors/skips; 771 generated files, zero repeat writes |
| Same flags with `runGameTestServer` | exit 0, 230.578916 seconds; all 464 required tests pass |
| `git diff --exit-code -- src/generated` | exit 0 |
| Focused Python regression after the copied-spawn setup | 114 cases pass, exit 0 / 2.769800 seconds; only the two tool postimages differ from Source23 |
| Bounded repository wrapper with the sole owner-private Git inventory exclusion | exit 0, 307.631619 seconds; 45 passed, zero pending/warnings/failures |
| Client-import, strict ledger, planning, provenance, machine-resource, artifact and whitespace checks | exit 0 |
| Ledger `--closure`; original global `git diff --exit-code` | each exits 1; original failures preserved |

Both GameTest logs retain 61 ERROR headers. No blanket clean-log waiver or
release qualification is inferred. The original outer build collector exits 1
**after both Gradle commands exit 0**, because it selected the wrong native log
location. A separate corrected capture exits 0 using `build/gametest`; the
original collector and its transcribed failure remain intact.

Artifacts are retained externally at
`D:/ARCE-Task-Evidence/v1.8.0/guard-lifecycle-01/artifacts23-01/`:

| Artifact | SHA-256 |
|---|---|
| Main, 5,443,263 bytes | `5fd2a23dd02c8c4977efe9c03628edfe4c517bc1d3d02ba0e846aa89649aeee9` |
| Sources, 2,622,232 bytes | `4ab8254184549cef0ca0edbdb3d256ee8215ecf2470a7036b453baddfa8053a3` |
| API, 51,045 bytes | `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da` |

Only the observer class/source member is added to the non-API archives. All
preceding members and the whole API artifact remain byte-identical. No full
source, JAR, copied runtime/world or region export belongs in the compact packet.

## Preserved failed native cohorts

Native01, native02 and native03 name separate actual copied-world attempts, not source
versions or successful retries. The original historical Tank06 source world,
runtime libraries and compatibility fixture remain unchanged. Each fresh
runtime receives only a copied world and pinned main/fixture JAR. Native commands
operate only on its fixed target and marker cells. Console save feedback is not
terrain durability proof; stopped compressed records are inspected separately.

| Attempt | Actual outcome | What remains unproven |
|---|---|---|
| Native01, preceding K3 main JAR `26356660…` | exit 1 / 20.909501 seconds; initial and post-removal refusal observed, but restored-culprit assertion fails after immediate reacquisition | Actual unload, live restoration, clean stop and second restart cycle |
| Native02, new Source23 main JAR `5fd2a23d…` | exit 1 / 79.807475 seconds; unchanged 60-second deadline expires without a fresh unload-begin marker | No reacquisition/restoration, `stop`, second cycle or callback completion |
| Native03, same Source23 main JAR with separately reviewed tool setup | exit 1 / 88.472856 seconds; exact copied-spawn success is observed, but unchanged unload deadline again expires without the event | No reacquisition/restoration, clean stop or second cycle; original START conflict does not establish the remaining cause |

Independent audits freshly extract each failed cohort's fixed stopped record:
5,898 bytes, SHA
`d9b170538589303c92cef7a2430407ed20f790c23df4e80da30388434ea54ea8`.
It is exactly equal to that cohort's captured source record. All four typed
resource roots and the AIR marker remain. These narrow post-abort observations
do not turn either failed live lifecycle exercise into PASS.

Native02 records 24 commands and no literal unload marker in stdout/latest/debug.
Its initial and post-removal save windows contain three and two ERROR pairs;
the complete stdout has six EventBus/ChunkMap pairs. Its packaged observer and
enabled hook property are verified. Absence of the marker alone does not assign
a unique runtime cause or establish successful event registration/callback.

## Separate copied-spawn setup

The [setup amendment](COPIED-SPAWN-SETUP-AMENDMENT-01.md) is a separate tooling
boundary. Pinned primary inspection identifies the retained spawn START-ticket
halo as a fixture eligibility conflict. It relocates only the copied world's
spawn after the explicit forced target is loaded. The original source world,
real event requirement, total unload deadline and all restoration/byte/resource
assertions are unchanged. Source review passes 32 focused controls, with no
introduced source finding. The actual fresh native03 result remains FAIL.
Its setup success and following barrier are captured before mutations. Both
explicit save windows have three ERROR pairs. Its independent
[stopped-record/log audit](lifecycle-01/reviews/NATIVE-FAILURE-03.zip) freshly
extracts the same 5,898 bytes, four typed roots and AIR marker, measures seven
ERROR pairs in each complete stream, and passes ten separate controls. Its
[primary follow-up](lifecycle-01/reviews/LIFECYCLE-PRIMARY-03.zip) passes seven
controls: the original START explanation alone is insufficient; the periodic
save stack does not prove the unload continuation. Other ticket/future/observer
conditions remain unobserved. Successful setup is not unloading or restarting.

The new two tool inputs have their own SETUP03 manifest. All other 2,993 named
Source23 inputs are unchanged, including Java/generated/artifact inputs. Native
input postchecks preserve the original world, libraries and main/fixture JARs.
No new Gradle execution is claimed for a Python-only setup change.
The original primary investigation's Medium fixture eligibility finding is
preserved in its [own packet](lifecycle-01/reviews/LIFECYCLE-PRIMARY-02.zip).
The [source review](lifecycle-01/reviews/COPIED-SPAWN-SOURCE-01.zip) qualifies
only its separately bound setup revision, not the still-failing lifecycle.
The [new root cohort packet](lifecycle-01/ROOT-NATIVE03-COMPACT-01.zip) preserves
the actual new commands/results, raw logs, manifests and source-stage receipts.

## Remaining work and cleanup

First-save/managed-entry defense, real family lifecycle, hash/frame/native codecs,
cross-store/crash recovery, charged FE and ordinary drop conservation, physical
hatches/controllers/lathe, C16b-d, C17-C19, clients/GPU/multiplayer/performance
and inherited release acceptance remain open. The ledger still has 186 PLANNED
units and 154 REVIEW assets. G0-G9 are not satisfied.

The cleanup tool rejected Root's checked PowerShell-only cleanup request before
OS execution. No alternate deletion mechanism was attempted. Stopped owned
runtime copies remain local, excluded from Git packets; historical input worlds
and sealed evidence are preserved. This storage debt is not marked resolved.

The owner subsequently requests C temporary-script cleanup and moves future
temporary outputs to the project's parent. New helpers/publication evidence
use `D:/GitHub/ARCE-Task-Evidence`. Read-only checks identify 11 direct script
files in two older Root directories, 24,930 bytes total. The exact-path/hash/
verified-backup/literal-file-removal command is rejected before execution.
No copy or C deletion from that command is claimed; these scripts and other
unallocated C task directories remain. No other-agent files/processes or
sealed originals are deleted. The cleanup failure is preserved separately.
See its [exact authority/precheck/disposition packet](lifecycle-01/C-CLEANUP-DISPOSITION-01.zip).

Evidence root: `D:/ARCE-Task-Evidence/v1.8.0/guard-lifecycle-01/`. The
[Source23 compact packet](lifecycle-01/ROOT-SOURCE23-COMPACT-01.zip) contains
actual commands/logs/XML/results and input pins, without runtime or artifact
exports. Independent [build evidence](lifecycle-01/reviews/BUILD23-EVIDENCE-01.zip),
[unload source](lifecycle-01/reviews/UNLOAD-SOURCE-01.zip) and original failed
[native01](lifecycle-01/reviews/NATIVE-FAILURE-01.zip)/
[native02](lifecycle-01/reviews/NATIVE-FAILURE-02.zip) audits are separate exact
copies of frozen originals. The [source/native packet index](lifecycle-01/INDEX.md)
records their exact byte counts, SHA-256 and scoped checksum list. Later standalone
documentation-review/publication receipts are outside that immutable index.
The additive [companion locator](lifecycle-01/COMPANION-LOCATORS-01.md) resolves
two historical cross-report references to their already indexed archive members,
without rewriting any sealed original.
