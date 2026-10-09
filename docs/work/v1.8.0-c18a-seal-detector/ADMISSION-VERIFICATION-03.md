# C18a-SEAL-ADMISSION-03 — detector admission and owned-worker verification

Date: 2026-10-09. Scope:
[preparation and corrective task](ADMISSION-TASK-03.md).
Status: scoped source independently reviewed, integrated and normally pushed;
independent evidence-record review complete. This is development evidence,
not item delivery or completed reviewer cleanup.

## Source and preparation

Preparation `771543514c0ccf6f09f22b62ba1d36e9a6d9b7f9` is committed/pushed before
source authoring. Original source `e456ffc0eab1c0752fa5e6e57b4c63ddedbac964`
adds one 380-line main-source GameTest class. Independent original review finds
a Medium exceptional worker-cleanup issue; passing original cohorts do not
dispose of it. Corrective scope `a2e3c476de0188c93c0de85bf005d4a0e02e3251`
is committed/pushed before the correction. Merge c047d805 preserves that history.

Corrected tested source is `452fd2a0dc12895c2e2b955c80115457459888bb`; complete
src tree is `f085b52691c4f72814566841f2d0ff69394a99e3`. Its exact source diff
against corrective scope contains only:

- [SealDetectorAdmissionGameTests.java](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/instrument/SealDetectorAdmissionGameTests.java), 407 lines /12 annotated cases.
- [SealDetectorAdmissionWorkerGateTest.java](../../../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/instrument/SealDetectorAdmissionWorkerGateTest.java), 104 lines /4 unit cases.

No existing production code, registry, build input, asset, ID, schema, unlock,
provenance or ledger changes. Seven Gradle input blobs match the preparation
base. Test code in the instrument package reaches existing package-private
contracts without a production seam; it is included in normal main/sources
JARs. Those identities change; the API JAR does not. No Claude is called.

## Observed boundaries and finding disposition

Every fixture uses owned ordinary embedded connected survival players and the
actual registered item. A native admitted control asserts exact serialized
component keys/nested arguments, requester-only replies and cooldown. The
cooldown and captured replies are then cleared before testing refusal.

Nine direct actual item-entry cases cover count two, captured hand-stack
replacement, spectator, dead, removed, missing connection, foreign Level,
stale same-UUID object and off-thread admission. Refusal precedes poisoned
selected-position/face/hit-location getters, yields FAIL, and leaves replies,
cooldown, both held identities/full data and atmosphere metrics unchanged.
Direct item admission is not a real-client or native caller refusal certificate.

Three fresh uninstalled local-service cases cover terminal/idempotent closure,
thread refusal and post-query actual held-identity replacement. The local
manager's empty-to-present metrics transition witnesses its supply query before
a controlled context getter changes the real held object. These fixtures do
not replace the installed runtime; that callback is not a native client/provider
event. Only the documented instrumented getters are directly poisoned, not
every possible context accessor. Local closure is not installed stop/restart.

The original helper's surviving worker could throw while surrounding automatic
cleanup still closed local handles and restored cells. Independent compiled
helper diagnostics and an external unwinding witness reproduce the ordering;
they do not execute Minecraft cleanup bodies or reproduce a normal detector
hang. Original e456ffc0 remains CHANGES_REQUESTED in its immutable report.

Correction makes both actual close methods check one shared fixture-owned
WorkerGate before any actor/channel, service/manager or cell cleanup. Unresolved
workers fail and retain the owned fixture; automatic exceptional-world recovery
is not claimed. Wait/join/tick bounds are unchanged. No forced stop, event hook,
poller or production seam. Four unit cases execute actual helper/gate completion,
callable failure/cause, survivor resource-cleanup refusal with explicit release/
join before later cleanup, and live-worker replacement refusal/terminal reuse.
Their external resource witness is not itself actual world cleanup.

Independent corrected actual-diff review finds no additional Critical, High,
Medium or Low source finding. Compiled actual Fixture/LocalReader closers call
the same owned gate before mutation; both full unit stages execute the four
cases. The original Medium is addressed only for the declared retention-before-
cleanup contract, not automatic recovery or installed lifecycle certification.

Source is integrated/normally pushed at
`b001316102d064545fb81d9f4f2141ae52931118`. Complete src and seven Gradle inputs
match the actual tested 452fd2a0 candidate; the merge SHA is not rerun. Original
and corrected source-review worktree/HEAD interests and owned processes are
released before integration. User-owned AGENTS.md remains unchanged/excluded.

## Actual command results

All hosted results bind to committed fixed source, not an uncommitted snapshot
or a later merge. Both drives exceed 10 GiB before sustained commands; Java 17
and owned D: TEMP/TMP/java.io.tmpdir are used. Full child argv, UTC intervals,
raw outputs, source/build-input/artifact identities and separate unit XML are
retained. No timeout, assertion or service budget is relaxed.

| Actual command | Original Root e456ffc0 | Corrected Root 452fd2a0 | Original independent e456ffc0 | Corrected independent 452fd2a0 |
| --- | --- | --- | --- | --- |
| Forced uncached `gradlew.bat clean build` | Exit 0; 2,179 JUnit /378 XML /0 F/E/S | Exit 0; 2,183 /379 /0 F/E/S | Exit 0; 2,179 /378 /0 F/E/S | Exit 0; 2,183 /379 /0 F/E/S |
| Forced uncached explicit `gradlew.bat test` | Exit 0; fresh 2,179 /378 /0 F/E/S | Exit 0; fresh 2,183 /379 /0 F/E/S | Exit 0; fresh 2,179 /378 /0 F/E/S | Exit 0; fresh 2,183 /379 /0 F/E/S |
| `gradlew.bat runData`, twice | Exit 0 each; written 841 then 0 | Exit 0 each; written 0 then 0 | Exit 0 each; written 841 then 0 | Exit 0 each; written 841 then 0 |
| Separate `git diff --exit-code` after each | Both exit 0 /empty | Both exit 0 /empty | Both exit 0 /empty | Both exit 0 /empty |
| Unfiltered `gradlew.bat runGameTestServer` | Exit 0; all 565 required pass | Exit 0; all 565 required pass | Exit 0; all 565 required pass | Exit 0; all 565 required pass |
| Content ledger `--require-accepted` | Exit 0 | Exit 0 | Exit 0 | Exit 0 |
| Bootstrap provenance | Exit 0; mechanical | Exit 0; mechanical | Exit 0 with `--require-approved-review` | Exit 0 with `--require-approved-review` |
| Strict repository `--require-approved-identity` | **Exit 1**, 44 PASS /1 FAIL | **Exit 1**, 44 PASS /1 FAIL | **Exit 1**, 44 PASS /1 FAIL | **Exit 1**, 44 PASS /1 FAIL |
| Whitespace /committed scope checks | Exit 0 | Exit 0 | Exit 0 | Exit 0 |

Root forced tasks use `--no-daemon --offline --no-build-cache --rerun-tasks`;
native uses `--no-daemon --offline --no-build-cache`. The independent cohort's
exact per-command settings are in its report. Native completion uses the actual
required-test terminal marker, not scheduling or inherited JUnit XML. Each
native suite schedules 565 tests across 147 batches, including 12 admission
cases. Each latest log retains 62 ERROR headers /zero FATAL, unwaived; repeated
console/debug copies are not extra events. Strict Markdown traversal stops at
256 errors, not a complete remaining-debt count.

First DataGen can write 841 byte-identical generated files from an empty
HashCache while still leaving an empty tracked diff. The immutable original
independent report incorrectly says its first run wrote zero. Its separate
corrected-cohort erratum identifies the original report/manifest and exact raw
lines; it does not change original findings, source identity, exits or results.
No original sealed file is modified. Counts are not borrowed between cohorts.

Independent corrected build/test each genuinely execute all tasks; no cached
JUnit result is used. Its artifact hashes/bytes match Root's three endpoint
identities. Original reviewer wrong-class javap setup failures, corrected
reviewer's packaging-quoting/presentation/read failures and missing exploratory
child-exit/timing boundaries remain explicit in their reports. A later successful
packaging inventory does not retroactively turn the failed attempt into success.

## Artifact identity and evidence

Corrected Root endpoint artifacts, captured before cleanup:

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| Main | 6,049,561 | `865ba5e264cc19a964959ab7ed9c0c0d05b7c9ac9d091ef2e919d285c9a9f019` |
| Sources | 2,879,064 | `d4c2cc5e3d1a21b83857ac5aac36bf97b648858045e363e93a432a901a746415` |
| API | 51,052 | `aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b` |

Main contains the GameTest and eight nested classes, sources contains its main
source, API contains none. These are not packaged S1/S2 or R1 certification.

[Original independent evidence](ADMISSION-INDEPENDENT-03-ORIGINAL.zip) is
immutable: 2,670,319 compressed bytes /1,988 ZIP files /14,773,990 uncompressed
bytes; archive SHA-256 `3ff42fa84c8388c71b76895aaa12a73dd848c76fc92b847f390ee01de99a8b88`.
Its report SHA-256 is `75916e43b3dfd6db48a8b28b6181db2907f9f962b7565690c07a2a16018fc0be`;
the manifest has 1,987 payload entries plus itself. Both hashes/CRC and size
budgets are mechanically checked before packaging.

[Corrected independent evidence and original-counter erratum](ADMISSION-INDEPENDENT-03.zip):
2,878,713 compressed bytes /2,027 ZIP files /15,159,736 uncompressed bytes;
archive SHA-256 `502a9a5fad8735237a0065c7ad28335a9c9d48651631fdd4e0ca7386e3320cb8`.
Report SHA-256 `9afc8530b24435aeb6791978bd3d370d660652fe30abd13cc3e7c44d2929e6f4`;
`ORIGINAL-REPORT-ERRATUM-01.md` SHA-256
`13597ddc308f52318951252368ee0b0fd571154c927139859c214ed70e309f71`.
All payload hashes/CRC and size budgets are checked; original sealed files stay
unchanged.

[Root original/corrected evidence](ADMISSION-ROOT-03.zip): 1,782,654 compressed
bytes /72 ZIP files /13,225,793 uncompressed bytes; archive SHA-256
`652bb7853b4151133498418a64a71370ec9ececc7850a9d226d4e72d13d020dc`.
Report SHA-256 `09267e5ce426e564ae66dcf1fd950d1814efaca464549779f1fab449251728b8`.
The pre-seal five-file record check inspects 616 links, retains 49 inherited
errors and introduces none. Its hashes bind that exact pre-seal checkpoint,
before this archive-link paragraph/evidence-state update; it is not the final
record-review digest. These three archives total 7,331,686 compressed bytes and
43,159,519 uncompressed bytes, within the slice's evidence budget.

## Independent record review and publication boundary

[Record review evidence](ADMISSION-RECORD-REVIEW-03.zip) contains only the
fresh review's manifest-covered retained scope: 6,826,655 compressed bytes,
135 files /9,832,063 uncompressed bytes, archive SHA-256
`5c97d0618962970c506c7b91e9315be6d49b20ae79dd973e119083cecc69bc69`.
Report SHA-256 `778faee0419f58cf6741f9f7290470483f2b05cfac39a09ec4f2febe3af10477`;
manifest SHA-256 `92e3007455ca0fd1a64c6c74274097c0692e34a9331862f2c3a7d3ba25b5a14b`.
All 134 payload hashes and archive CRC/relative paths are checked. These four
archives total 14,158,341 compressed /52,991,582 uncompressed bytes, excluding
unsealed temporary outputs; no whole temporary checkout is packaged.

The review identifies no new scoped record finding. Its five exact preimages
at Main b0013161 are preserved in PREIMAGES.json and the report: task
`806f4bdd6ba913f23581c9012b7dbe8343a0977ce474f32f5f0da73ff038f63e`, verification
`e9eaf0b49ebc5fbe92c1474d3451891fd82cba34fe6811d0603a63bdbe68e435`, implementation log
`aa365ccc6d7c6b1053b46b9574be54fe69c8f66539b4892cac9d5739d6748c3b`, current status
`aa335b143bfedc614d04e2a9eff3e30db7221bcc243bbe62595c1e5e7cf0b0cc`, plan
`1b2c4d0fc7e5ba8842f6841e105a982198ecd15df9e79398c0aa0729788f4f24`.
Subsequent edits only record this review/packaging, precise review attribution
and execution limitations. They are not substituted for reviewed preimages.

The reviewer reruns approved ledger/bootstrap successfully, strict exit 1 with
44 PASS /one inherited failure, and final whitespace exit 0. The unchanged
checker inspects 617 links with no introduced target-error multiplicities.
Its private clone additionally lacks 13 excluded inherited untracked archives,
so its 62 inherited focused errors are not a new Main error count. Long-path
checkout/extraction and presentation failures are retained; only its owned
clone inputs are repaired using per-command core.longpaths, not global settings.
No Gradle/native/client command is newly executed by this records-only review.

Its single native PowerShell cleanup launch exits 1 before loading the script
due to execution policy. No preflight/deletion body runs, no target is removed,
and no retry/bypass/Root takeover occurs. The unsealed owned _tmp remains:
1,184,148,902 logical bytes /10,277 files. Its whole working leaf is therefore
over the evidence budget; only the bounded retained scope is admissible above.
The earlier interrupted reviewer has no final report or manifest; that separate
leaf and its temporary export are untouched and not credited or packaged.

Root's first inline packaging command fails through the pyenv batch shim with
SyntaxError and creates no archive. A separate explicit installed Python 3.13.15
launch exits 0; no interpreter or execution-policy configuration changes.
Publication observations belong to the task's separate declared Root leaf,
not the sealed source/review leaves. All review read/HEAD/process interests
are released before record publication. User AGENTS.md remains excluded.

Root cleanup succeeds once after source-pin/clean/tracked-input, ended-process,
absolute-containment and reparse checks; only its build/run-data/.gradle and two
TEMP directories are removed. Original independent cleanup removes only its
five ended disposable outputs once; corrected independent removes only its
three exclusively owned outputs once. Source-world inputs, committed source,
sealed evidence, user work and prior refused targets remain untouched. Their
sealed payloads retain no whole source/build/world/JAR copies; compact manifests
remain relative. This does not claim cleanup of the later record reviewer.

## Open obligations

Full detector Level/chunk/cell/query-order, installed precedence/lifecycle,
unlock/acquisition, reload/unload, protection mods, packaged/restart, true-client
V1/V2, compatibility/performance and all v1.8 G0–G9 remain open. Content ledger
stays 186 PLANNED /154 REVIEW; no full item delivery. ADR-068 stays PROPOSED,
R-021 OPEN; physical hatch/hook/save writer and unchosen owner policies remain
disabled. The prior slice's Low preparation-log timing deviation is retained,
not retrospectively waived. Acceptance cursor stays v1.0 under ADR-060; active
development stays v1.8 IN_PROGRESS / IMPLEMENTING. A scoped development test
milestone does not satisfy the version's Required Gates.
