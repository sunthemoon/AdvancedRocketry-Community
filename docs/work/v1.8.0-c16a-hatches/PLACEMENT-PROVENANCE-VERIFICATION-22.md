# Native placement invocation qualification 22

2026-10-08. Root directly implements and verifies the development-only
[task22](PLACEMENT-PROVENANCE-TASK-22.md); no Claude or delegated implementation.
Actual corrected source: 7f5c5b1802e5d63a26a306c7ec8e1a1d49b6865e.
Complete src tree: d12da7cfbb3aab3fc6bd21eab60dc08d56ce2025.
After independent actual-source review, integration is normally pushed at
715945736ed1201ec5aa2ee1821c5fca985f50b9. Complete src and seven Gradle
inputs are identical to corrected tested source; the merge SHA is not rerun.
The task branch is also normally pushed. Metadata association is separate.

## Delivered scope and explicit limits

Only three adapter-test source files change: one new registered probe BlockItem,
one new three-case native GameTest class and one constructor registration line.
There is no production registration, hook/dependency, API/schema, asset, save
protection or physical hatch change. All three main development JARs exclude the
fixture and retain the preceding baseline hashes. Existing tests and deadlines
remain unchanged. The test mod is not a gameplay content delivery.

Ordinary survival/creative native game-mode use and first-use direct item use
record actual source/context identity and <=16 ordered caller frames. At the
real placeBlock seam the selected cell is checked empty before the inherited
native write. Exact source counts/tags and the registered native chest owner
are checked afterward; fixture cells/listener/player/channel are retired on
terminal results. These are embedded native players, not real packet/clients.

In the survival counterexample the same actual UseOnContext reaches direct
and later ordinary USE. Both have a game-mode ancestor; direct USE observes
index9/BCI128 and later ordinary USE index4/BCI366. Ordinary creative USE has
BCI349. Direct use installs a chest/consumes one source while the later ordinary
return is non-consuming FAIL. Thus neither shared context nor outer return
alone is a witnessed outcome. Ten observations per cohort are retained.
Each reaches the 16-frame cap; this does not establish the complete outer stack.
Cleanup failure branches are source-reviewed, not separately fault-injected.

These observations qualify measured paths only, not an authentication mechanism.
Java's [StackFrame API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackWalker.StackFrame.html)
provides caller metadata; receiver/local authentication is not proved by it.
The actual packet wrapper, transformed production whitelist, nested/foreign
routes, full source/tool/drop witnesses, provisional LOAD/availability, both
save consumers, origin/Proto, final disposal and restart remain unqualified.
Both Medium assignment prerequisites remain open; U1 is not closed wholesale.
ADR-068 stays PROPOSED, R-021 OPEN and O1/O2/O3 unadopted. The asynchronous
owner question is not a decision or authorization. No ledger/Gate is approved.

## Actual commands and source association

Java17.0.7 / Gradle8.8 / Forge47.4.10, offline and task-local TEMP/TMP.
All sustained commands check both disks >=10GiB. 47.4.23 is not qualified.

| Check at corrected 7f5c5b18 | Root | Independent Codex |
| --- | --- | --- |
| clean build | exit0; test restored FROM-CACHE | exit0; --no-build-cache, all20 tasks execute |
| actual JUnit | separate test --rerun-tasks --no-build-cache exit0, all17 tasks execute | actual build test execution |
| emitted XML | 378 suites /2,179 testcase nodes /0 F/E/S | 378 suites /2,179 testcase nodes /0 F/E/S |
| twice runData /diff --exit-code | all exit0, empty diffs | all exit0, empty diffs |
| full runGameTestServer | exit0, all551 required pass | exit0, all551 required pass |
| ledger --require-accepted /bootstrap provenance /diff --check | all exit0 | all exit0 |
| strict repository --require-approved-identity | exit1,44 pass/one inherited link failure | exit1,44 pass/one inherited link failure |
| native ERROR/FATAL | 62 unwaived /0 | 62 unwaived /0 |

Each complete runner exits1 for strict validation; this is not a wholly passing
Gate cohort. Root corrected native ends 13:02:36.722726 UTC; forced JUnit ends
13:07:29.316245 UTC. Actual command SHAs are not rebound to later merges.

## Findings and original records

Original committed 5b48514e passes Root build/actual JUnit/twice DataGen/551
native checks but has an independent Medium: inherited BlockItem registration
overwrites the native chest-to-item map. Corrected 7f5c5b18 opts only this
development probe out of register/remove map callbacks and adds direct
Item.byBlock(CHEST) assertions before/after use. Block.asItem may cache a prior
lookup; the tests do not use that weaker oracle. Static map-write proof is not
a demonstrated chest drop/pick failure. The original cohort/finding remain.

The initial independent Windows launcher fails before Gradle; zero JUnit/native
execution is retained separately, not reported as successful DataGen/build.
Root's original result summaries select latest.log and therefore report no
frame lines; actual System.out records are in native.log. Separate hash-bound
console-frames.json extraction preserves those summaries unchanged. Initial
PATH javap cannot launch; the explicit pinned JDK inspection establishes
ItemStack final before implementing a BlockItem fixture. No stack subclass,
native body or official art is imported into production.

## Evidence and retention

Root sealed external packet:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/placement-provenance-root-20261008-01/`.
51 payloads /12,039,345 bytes; portable ZIP53 entries /1,454,546 bytes.
ZIP SHA256 c4fdf5dec4b27fba08a49ba1d6e2301851d1bd8f586f180c9f84b67dba828dc9.
The [portable Root packet](PLACEMENT-PROVENANCE-ROOT-22.zip) retains both original
and corrected cohorts, forced JUnit, original empty summaries/separate console
extractions, primary/archive/artifact hashes, patches, scripts and cleanup.

Root performs one successful native PowerShell cleanup: 272,317,760 logical
bytes inventoried, not a measured physical-space gain. New ended build/local
.gradle/run-data/three TEMP outputs are removed; source checkout retained.
No old denied cleanup, input world, inherited work or sealed evidence is touched.
The [portable independent packet](PLACEMENT-PROVENANCE-INDEPENDENT-22.zip)
exactly copies the sealed independent evidence.zip: 117 payloads /12,242,587
bytes; ZIP119 entries /1,115,179 bytes. ZIP SHA256
02a35f37d119d337e0eaa4c183eed037048212e2f631764dac94e7f38f9badc6.
Independent REPORT.md SHA256
b9b2ec9e97612b3877bed8c67b038172291ce6be4483901ce1e699b76ed18c1b.
It retains original launcher/checkout failures, the explicit progress correction,
original Medium, corrected actual reruns, native frames, hashes and cleanup.
Independent cleanup succeeds once on two ended clones and two TEMP directories:
2,546,145,449 logical bytes inventoried. No task disposable output remains.
Root independently checks all original/copy payload hashes, manifest/checksum
rows, exact archive entry sets and CRC before associating either packet.
Packet copies together are 2,569,725 bytes, below the evidence/file budgets.
No dedicated/restart, V1/V2, progression, performance or full v1.8 Gate claim.
