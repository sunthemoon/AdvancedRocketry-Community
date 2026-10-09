# C19 diagnostic checkpoint68

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Main source remains unchanged.

## Committed candidate and exact scope

[Task64](G4-MALFORMED-ADR-TASK-64.md) produces
d605be33f9b1e25f28241caabe75789b767c0e66, normally pushed on
fix/v1.8.0-g4-malformed-adr, parent Main0686abab94b65ebf458b2b5fcbdbca185de507e7.
It is independent of the unintegrated bootstrap806 chain. Exactly two files
change: scripts/validate_v002_g4_applicability.py and
tests/test_validate_v002_g4_applicability.py,32 insertions/3 deletions.
Three validator lines require string enums before membership and convert
parser RecursionError into the existing labeled rejection channel. No enum,
reviewer, lifecycle, approval, depth/node/byte or execution-budget relaxation.
All14 original methods and23 original assertion sites are unchanged;3 new
methods exercise38 inputs, not38 extra reported unittest methods.

Script14821 bytes/SHA-2567cf7419c81af41fac0dfe0bf176c096ecbe68b83795b914dfd0cf41015b7d318;
tests9899 bytes/SHA-256fc721cb95ab33ca35b1b6254dfa78e94a16cd92a1eceaae123fa81bbe5719dca.
Committed blobs equal the tested/reviewed raw postimages. Actual committed run
uses clean d605be33, not an inferred rerun. HEAD:src remains
a7b7d83a67fb02403f5aaee9c2d21987a06b1f6a, matching Main and bootstrap806;
this is scope evidence, not new Gradle/native qualification.

## Actual results and retained failure

Original64 starts a full17 invocation on the unchanged base script with added
tests. Raw output reports14 errors, but the capture launcher then fails on a
stale output filename. No original child exit receipt is retained or recovered.
Original helper/raw outputs/bindings stay sealed. Explicit observer successor
[Task65](G4-CAPTURE-REVISION-TASK-65.md) fixes only the new capture references
and its own fresh runtime/bindings; the original180-second ceiling stays fixed.

| Distinct invocation | Runtime source | Original wait exit | Seconds | Summary |
| --- | --- | ---: | ---: | --- |
| New negative baseline65 | Main0686, added tests only | 1 | 0.3253314 | 17/errors14 |
| Corrected module | Main0686, two-file postimage | 0 | 0.3207308 | 17/OK |
| Existing repository integration subset | Same postimage | 0 | 0.5051402 | 20/OK |
| Independent review66 module | Same postimage | 0 | 0.3212923 | 17/OK |
| Actual committed module | Clean d605be33 | 0 | 0.3195708 | 17/OK |

New baseline reproduces12 unhashable list/dict failures and recursion in both
records. Original14 tests remain unchanged/passing; this is a diagnostic bug
reproduction, not an explanation of earlier full-command timeout. All successor
streams are complete, source/helper/executable/scoped-index bindings stable,
no timeout/kill/cap error. Root waits for the independent final binding and
completed report, checks explicit cached scope/stat/whitespace, commits and
normally pushes the two-file source. No Main source integration or Gate change.

[Review66](G4-MALFORMED-ADR-REVIEW-TASK-66.md) finds no scoped source issue and
independently runs all17 methods. Root reads the complete report/commands/receipt,
rehashes its exact sealed inventory and checks postimage/commit correspondence.
Its actual TEMP/TMP is a fresh C: Temp directory, whereas Task66 assigned a D:
evidence sibling. Original records and empty-directory cleanup are preserved;
the additive [runtime-scope audit69](G4-REVIEW-RUNTIME-SCOPE-TASK-69.md) confirms
Medium R69-01. Root reads/rechecks the correction; full review66 write-scope
compliance is not claimed or restored. No source read, old-helper/test execution,
runtime/PID query or cleanup occurs in the audit.
Source/test results do not erase this command-scope deviation.

## Earlier manual-evidence observation

[Task60](MANUAL-PHASE-OBSERVATION-TASK-60.md) runs two unchanged cases at clean
bootstrap806 with sys.setprofile: owned wait0/2.8540849 seconds,2/OK, complete
streams and stable declared bindings. Both setUp phases start4 processes and
take0.309/0.294 seconds. Happy body starts21/takes1.618 seconds; dirty-worktree
body starts3/takes0.231 seconds. Each ready_session is about0.085 seconds/0
starts. Nested timings overlap, not additive whole costs. No old-timeout cause,
whole-module timing, speedup or lifetime-memory qualification is inferred.

[Independent61](MANUAL-FIXTURE-SOURCE-TASK-61.md) statically identifies137 manual
methods plus1 CLI method,112 full-session users and20 helper-only cases.
Its Low organization finding suggests a separately verified lighter fixture,
not removal of real repository/physical-file/staged validation defenses.
No implementation follows from static counts. Root reads/rechecks its report.

## Sealed external evidence

All leaves are below D:/GitHub/ARCE-Task-Evidence/v1.8.0. No sealed helper runs
after sealing and no old packet is edited.

| Leaf | Files / bytes | REPORT SHA-256 | Manifest SHA-256 |
| --- | --- | --- | --- |
| c19-manual-phase-root-20261010-60 | 14 /145668 | e967df37aa6f11d5aa4fc78361b01cc2a1cb6f212c12068830ad3959dcd9a641 | 2642ce9318963f6a5b4fb51d3981946bd40cda1dc51c3dc328f0b7fcb3d4e4a0 |
| c19-manual-fixture-source-review-20261010-61 | 3 /24256 | 369871da65cb5fc333552474fdd069e144ad620a6d4a9a3fb771057d010ff7b2 | c844c02722e80ce0a5276ff5ecdec1e5f7a2805d860f8d91b51756190706a869 |
| c19-g4-malformed-root-20261010-64 | 13 /159564 | f47660850539639d41614276448a6bd77a6a39d094fa1c68ad8187650648eace | 7ad159848fc096c34d632492b82e6027da0cc02f347fa41da1ba9baeaeb7069a |
| c19-g4-malformed-capture-revision-root-20261010-65 | 36 /472013 | 8c6225c3659b5ee5469d1ed333a24f40a435c00efb9a18ab8fb3a80c00586047 | 3f479562adb6cbf26176bad940605267224c8a1da223d24183a21b49fb93d975 |
| c19-g4-malformed-review-20261010-66 | 22 /158325 | 49a8a231fb932e559e5633ae9a3f25874852b2229f6d99d785a6e30d475751a0 | 50134651bbd31f541b4800a1ccd047fe9c560fa298e657b453ce127afe4a528a |
| c19-g4-review-runtime-scope-20261010-69 | 6 /37711 | eea72b8ba0f2b05f2c630ed3e2957fe8f8e680a45a0e8ac9ea0dce1e7fe34095 | a41e1838465621de7b84530056bd35ba42f0f79c57c4936f798d1c133b281b2d |

Root67 verifies all six exact inventories and four successor receipts/eight
streams. Three original small task contracts are retained late before status
annotations; Task66 bytes match the peer's original contract hash, not an ABA or
historical raw-capture proof. Root67 is sealed as17 files/45693 bytes: REPORT
SHA-256e05d8bf3c1abf4060a61e1261618ba29ebc65c6b55a0f5f7121c1f99c24bc8fd,
manifestcefb61ac386e27500e1f02911e08b865ffa03a75371f7cdaf2e3f200e8f99ce4.
Its scoped record check precedes these final hash annotations; final actual Git
results are retained separately in Root70, not a sealed helper rerun.
Root/user AGENTS.md remains untouched at c2448e9357ec77d062ab52ecefbb24724fb5c767fb4955a4cd23ef0efbd8ff09;
unknown inherited untracked material is not staged, acquired or cleaned.

## Remaining v1.8 obligations

R32-02 is addressed in the committed focused candidate only; Main integration
and whole qualification remain open. R32-03 filesystem/delegation coverage is
separate. Full Python180 failure, strict Markdown failure, Root53 null-exit/
incomplete streams/retained TEMP, native62 ERROR, bootstrap806 size disposition
and standard/whole qualification stay open. No old receipt or Gate is repaired.
No new Gradle/full strict/server/restart/client/GPU/multiplayer/resource run.
True sleep, frozen observations, ADR-069 dual-window measurements, machines/save,
progression/assets/audio/soak/restarts and all G0-G9 remain current v1.8 work.
Main actual qualification SHA, acceptance cursor and186 PLANNED/154 REVIEW are
unchanged. Continue current C19 evidence/diagnostics and qualification, not a
later version or a claim that C19/v1.8 is complete.
