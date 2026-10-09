# Corrected passive observation source and development qualification 08

Later [contract/wire disposition 18](DRIVER-REVIEW-STATUS-18.md) replaces the
pending Tasks16/17 state below and records an additional nested-receiver
preflight boundary. This checkpoint's narrowed review and actual regressions
remain historical evidence at 5b451320, not universal serializer closure.

Date: 2026-10-09. Status: implemented-unverified for the complete observation leaf.
The source is isolated and normally pushed, not merged into Main. No production
sleep implementation or twenty-case observation command has been executed.
This supersedes [checkpoint 07](SOURCE-REVIEW-STATUS-07.md) only for the corrected
source/review/short-regression state; original commits, findings and receipts stay.

## Source correction and complete independent review

Fixed source: **5b45132000ade1e3c8d130a6b8ccb29f07535124**, with additive
9f21a3daab7e6f3b38029742979a328306146fcf. Source base remains b9bec789978de0df67c93e976239d6d36e41532b.
Only SleepBoundaryObservationFixture.java and SleepBoundaryTrace.java are added
in adapterTest, 497/498 lines. The old e72b41/0c05cc27 cohorts remain immutable.
Root reads the complete corrected source, delta and final independent report.

The [complete source review](OBSERVATION-SOURCE-INDEPENDENT-06.zip) finds no
remaining material source defect within this narrowed candidate. Historical
Medium F01/F02 are source-fixed: counter-only capped JSON accounting precedes
attachment, metadata/chat preflight precedes serialization, final allocation is
bounded, metrics explicitly name their prefix, and acquired invocation-marker
finally covers trace/fixture initialization. Limits are not enlarged. Unsupported
native chat remains explicit incomplete capture, not successful evidence.

The [author evidence](OBSERVATION-SOURCE-05.zip) preserves three successful
compiles; the independent review separately performs three successful compiles.
Neither source-only executor runs tests or the twenty native rows. The intermediate
9f21a3da commit is not compiled alone. All original failures/limits are retained.

## Actual Root and independent standard qualification

The [Root qualification](OBSERVATION-ROOT-QUALIFICATION-11.zip) and
[fresh independent qualification](OBSERVATION-INDEPENDENT-QUALIFICATION-12.zip)
both use fixed 5b451320, not a moving Main. Each executes forced, uncached commands
with installed JDK 17.0.7 /Gradle 8.8 /Forge 47.4.10 after disk preflight.
Root reads both complete reports and verifies all compact manifest-covered hashes,
safe case-unique paths, archive CRC and payload equality. Native XML is not emitted
by the unchanged standard definition; actual batch/completion logs are retained.

| Command, separately executed by each verifier | Actual result |
|---|---|
| clean build | Exit 0; 2192 JUnit cases /381 XML; zero failures/errors/skips. |
| test | Exit 0; a separate 2192 /381 /zero run, not borrowed build results. |
| runData, twice | Both exit 0; each separate git diff --exit-code/status is empty. |
| runGameTestServer, unfiltered | Exit 0; all 588 required tests pass, including sleep_trace (5) and sleep_console (4). |
| Offline local publication | Exit 0. |
| API-only consumer clean build | Exit 0; negative internal-import/classpath/reobfuscation checks execute. |
| Approved-identity repository validator | Actual unchanged 180-second TIMEOUT; buffered output empty, not link/Gate PASS. |

Both native runs preserve 62 ERROR headers /0 FATAL; no log-Gate waiver is made.
The nine new GameTests cover guards, setup-marker release and bounded trace
identity/frame/event/UTF-8/metadata/chat behavior. They do not invoke the twenty
console cases, prove a sleep cause, or qualify saved Spawn serialization.
Root DataGen's original separate Git checks lack separate UTC receipts; actual
raw logs/printed argv/exits and later instrumented final Git checks are preserved,
without reconstructing timestamps. Root cleanup preparation failures remain.

The complete src tree is 001b12375461d7dddec6d74b69f7e0ea7d966188. Both independently
produced normal/API/sources/consumer artifact hashes match. Runtime normal JAR is
6073913 bytes, SHA-256 0a8048308b30b00b5838b46d779b89f1097bdb0a33078c0dffedfb5e95a9a011;
API is 51052 bytes, aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b;
separate reobfuscated fixture is 191702 bytes,
93194a0bb197b5051174d66f2f4ddcd58649fc0764d83b5da3ac0ab7aca600aa.
Host archives contain no adapter classes; the fixture contains corrected sleep
classes with the existing independent API-only boundary. It is not launched in
a packaged observation server. Main's integrated behavioral source stays 3b18a6bc;
these candidate-specific tests are not represented as a Main-SHA rerun.

## Owner record, planning and remaining work

[Decision 03](OWNER-DECISION-03.md) accepts the exact native ancillary scope,
still requiring dimension/spawn/time freezing and review. The
[independent record review](OWNER-DECISION-INDEPENDENT-14.zip) finds no required
change in its fixed fourteen-Markdown-file diff. It separately records fifteen
pre-existing historical log links to ZIPs absent from both fixed Git trees;
those locally untracked files are not adopted or claimed portable. This is an
open documentation limitation, not a new sleep-link regression or gameplay test.
Historical sealed reports' pending owner input is superseded only by decision 03.

[Initial driver design](OBSERVATION-DRIVER-DESIGN-09.zip) is historical input at
the old source. The [corrected-schema proposal](OBSERVATION-DRIVER-CONTRACT-15.zip)
is fully read and sealed, **PROPOSED / no executable freeze**. It separates
intentional intervention failure, actual actions/frames/outcomes, cleanup,
native logout-save and typed disk projections. It contains no driver/parser/tests
or native result. Later artifact qualification is separately supplied above;
stdout routing, native component union/depth and SpawnDimension wire/default
qualification still require disposition before executable assignment.

Root assigns fresh read-only independent driver-contract review (task 16) and
primary wire-contract qualification (task 17), recorded in its external task
evidence. Neither permits a server/driver/parser/test launch, code change or Gate.
B1/R1/M1/M2 and D1's dimension/spawn/time contract remain open. All twenty native
rows, real clients/TCP, prior-world/restart/death, six hosts/Space, receiver/mod/
monster/item/JIT/coexistence, performance/visual, asset/ledger/item and G0-G9
acceptance remain unfinished. No natural/spawn/time/air, public API/schema,
migration, hatch O1/O2/O3, asset or delivery decision is changed.

## Retention and immutable archive identities

Author and Root remove only their owned disposable outputs after native containment,
reparse/process checks. Root preserves two failed cleanup preparations before its
actual successful native attempt. Independent source-review cleanup is policy-denied
and not retried: six build/.gradle trees /5608016 bytes and three empty temp
directories remain. Independent regression cleanup is likewise denied before
launch, not retried: six owned output/temp targets /308834406 bytes remain. No
deletion ownership transfers to Root; no process or peer tree is touched.

| Archive | Compressed /expanded bytes | SHA-256 |
|---|---:|---|
| OBSERVATION-SOURCE-05.zip | 103832 /349500 | 861ebe71c17a49fbbc5a26f681497c8e10e28bb83026e241a6ed48b593f00a2a |
| OBSERVATION-SOURCE-INDEPENDENT-06.zip | 269696 /1505579 | cf6107d8027ef4b89e0fd3cd44ce23a343dc6ddee37e9f3079d57db2f4174e38 |
| OBSERVATION-DRIVER-DESIGN-09.zip | 17849 /40428 | f3e4fabf6216741176885a6bc95050e128ed7b334c48beb4b8aaf0aaf384656f |
| OBSERVATION-ROOT-QUALIFICATION-11.zip | 1201337 /8916526 | 0031b0b637a08210d737d818a80f921f57ec29a41aa183967e388411ef924d50 |
| OBSERVATION-INDEPENDENT-QUALIFICATION-12.zip | 1423508 /10903217 | e1683128720d0fc7fc0a5078eb2813663c30631ba164b4290feac5499b8baacb |
| OWNER-DECISION-INDEPENDENT-14.zip | 86513 /288484 | 52e9a40508e07865fea77a578ee22eccf49bdf279df58484cf5cc0bad67241dd |
| OBSERVATION-DRIVER-CONTRACT-15.zip | 372937 /1507844 | 7cadae8210aaee39ae2d4279ae1fedd5df800d857215532692c4d0b978900944 |

Source review, independent qualification and driver-contract archives explicitly
include their separate post-seal SEAL-RESULT.json outside manifest coverage;
the archive SHA covers those receipts. Policy-retained independent _tmp binaries
are excluded from compact archives. Manifest paths refer only to retained package
members, never live build/Temp outputs. Root confirms all covered payloads/CRC.
No current or inherited Required Gate, ledger unit, release, tag or version is
approved by this development checkpoint.
