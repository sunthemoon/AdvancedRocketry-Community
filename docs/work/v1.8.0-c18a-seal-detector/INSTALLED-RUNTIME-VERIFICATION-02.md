# Installed seal detector rule qualification

Task: [C18a-SEAL-INSTALLED-RUNTIME-02](INSTALLED-RUNTIME-TASK-02.md).
Started 2026-10-08; integration record completed 2026-10-09. Root implements
directly under the existing accepted [ADR-067](../../decisions/ADR-067-V180-READ-ONLY-SEAL-DETECTOR.md).
No Claude assistance is used. This is a verified development-test slice, not
delivery of the full detector, content-ledger acceptance or a Required Gate.

## Source and actual scope

Base: `80328a2d25c0c695cc9c8b09ee79910bfc458ec9`.
Original candidate: `8b608d88205be9ff4cd19bdf9516317c9a6494d5`.
Actual corrected tested source: `794dd123f9af0047adbab385eddeb1c9d5089773`.
Integration is committed and normally pushed at
`c554e810780f0f9b5b8b6cd290bddd6e0822b2eb`.

Complete src tree at tested and integrated commits:
`005126337796bcdb1abbfda699bd601c4c2db253`. All seven Gradle inputs match.
This is source/build-input equivalence, not a rerun of the merge SHA.
Production src/main remains `9b1e79254d7467c8afc1c700006cf2c0318d4cd3`.

Only the new task and 242-line development adapter test class change in the
source commits. The actual registered detector is called through native
`ServerPlayerGameMode.useItemOn` with ordinary survival players joined through
owned embedded channels. Both hands measure the existing startup-registered
external boundary's closed/open/closed states while collision remains full.
No local catalog compilation, runtime replacement, extra event, registration,
host implementation import, production behavior, asset or schema change.

The tests assert serialized outer/nested translatable payloads and non-overlay
actor-only callbacks; ambient air is not called supplied. They also check
shared two-tick cooldown, both exact held identities/counts/custom NBT,
selected-cell nonmutation and owned cleanup. A full-collision open state is the
discriminator from a geometry-only/empty-catalog implementation. The existing
local-catalog test is retained separately, not relabeled as installed evidence.
Embedded callbacks do not establish actual client packet/rendering behavior.

## Independent review and corrections

Fresh read-only Codex agent `/root/seal_installed_runtime_review` independently
reviews both actual source diffs, adjacent catalog/item/fixture/source-set
boundaries and reruns both committed candidates in its own fresh clones.
The original **Medium test-evidence limitation** compares rendered
`Component.getString()` rather than translation identity, nested arguments or
style. It is not an observed production defect or proof that fallback occurred
in the original run. Corrected 794dd123 captures/asserts
`Component.Serializer.toJson`; its independently observed six structured
payloads resolve that limitation. No remaining source fix is requested in the
corrected inspected scope; there is no whole-mod absence-of-defects conclusion.

The reviewer separately records an open **Low procedural limitation**:
Root creates the dedicated task before source but updates the version
implementation log only after Root verification, contrary to AGENTS section 4.1
ordering. The source/command review does not reconstruct or approve that later
central record. This deviation remains explicit and unwaived in the
[implementation log](../v1.8.0-implementation-log.md); future source tasks must
update it before source edits. No retroactive compliance is claimed.

Root's first wrapper syntax error never starts Gradle. Independent initial
long-path omissions, locked cache-copy files and wrapper errors are retained;
corrections affect only owned setup/runner settings, not global cache/policy or
source assertions/timeouts/budgets. Original genuine cohorts pass but have the
projection-only limitation. Original latest.log observation scans miss
console-only lines; separate supplements retain the raw console observations.
Neither original results nor sealed packages are modified or rebound.

## Actual fixed-source commands and results

Both corrected runs use Oracle Java 17.0.7, Gradle 8.8 and Forge 47.4.10,
offline/no-daemon commands and task-owned D: TEMP/TMP/java.io.tmpdir. Both drives
exceed 10 GiB before sustained commands. Independent Gradle caches are private;
no global setting/cache edits or installs. Full native suites are unfiltered.

| Command | Root corrected cohort-03 | Independent corrected cohort-03 |
|---|---|---|
| clean build --no-build-cache --rerun-tasks | exit0, all20 actionable tasks execute | same |
| actual JUnit XML testcase nodes | 2,179, zero failures/errors/skips | same |
| runData twice | both exit0 | both exit0 |
| git diff --exit-code after each generation | both exit0/empty | both exit0/empty |
| runGameTestServer --no-build-cache | exit0, all553 required pass | same |
| content ledger --require-accepted | exit0 | exit0 |
| bootstrap provenance | exit0 | exit0 |
| strict repository --require-approved-identity | exit1, 44 PASS/one inherited link failure | same |
| git diff --check | exit0 | exit0 |

Exact argv, UTC times, output hashes, 378 JUnit suites/actual nodes and native
terminal lines are retained. The configured native runner produces no separate
GameTest XML; raw console/latest/debug logs and terminal counts are evidence.
Each corrected latest.log contains **62 unwaived ERROR headers/zero FATAL**.
Counts are distinct from test pass totals. Batch context is not causality or
permission to discard errors; no logging/release Gate is approved.
Root corrected command ends at `2026-10-08T15:42:27.726670Z`; independent
corrected execution ends at 15:52:44 UTC. Original runs remain separately pinned.

Root scope/identity controls pass 23/23, original controls 19/19. Both agents
retain four bounded primary javap outputs against mapped JAR SHA
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
All three normal artifact identities match the preceding placement qualification
and contain no development-adapter entries:

| Artifact | SHA-256 |
|---|---|
| dev JAR | `dd93cb968430eba12fb13539bbdfb29e943af1f16a6b6a8ebb3257a14d9a6b87` |
| sources JAR | `ed57d4d0923c1571b421979a8e6068165b70dc048b755b8d8bdd8faf1c27d2a9` |
| API JAR | `aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b` |

## Sealed evidence and cleanup

| Package | ZIP bytes/entries | ZIP SHA-256 |
|---|---:|---|
| [Root](INSTALLED-RUNTIME-ROOT-02.zip) | 1,252,110 /62 | `db96396eb1f0727b37893b21c36b0fc83ed4b87e5bf06251b0e2d0a8654c2b2d` |
| [Independent source/commands](INSTALLED-RUNTIME-INDEPENDENT-02.zip) | 1,976,192 /94 | `5196bc10f020da4160e9da79a398bbe4db2c8041eb721aef03b91612159208c0` |

Both contain relative SHA256SUMS manifests binding REPORT, raw logs/results,
source patches, unit XML, primary facts and cleanup inventories. Root has 60
pre-manifest payloads/11,827,572 bytes; independent has 93 retained payloads/
18,512,743 bytes. Root REPORT SHA:
`c1599fab59ab7b0bc713fdf62fdbf57789f95066a41f3e1887684cd9e61245a3`.
Independent REPORT SHA:
`de51f3bf97603c973ffae5dc15823f541780611d334dd5d536032d75d9e1a732`.
External sealed leaves remain under `D:/GitHub/ARCE-Task-Evidence/v1.8.0/`,
named `seal-installed-runtime-root-20261008-01` and
`seal-installed-runtime-independent-20261008-01`; ZIP names do not alter inputs.

Each agent makes one successful native PowerShell cleanup attempt after process,
ownership, resolved containment and reparse checks. Only its new ended disposable
source caches, build/generated worlds/project caches and TEMP outputs are
removed. Root's source worktree remains clean. No physical reclaimed-space
claim; source-world inputs, user AGENTS/other-agent work, previous refused
targets and immutable earlier evidence remain untouched. No reproducible full
source/build/world copy or large binary is packaged. All command/read interests
are released before integration.

## Changed records and remaining acceptance

Source: new task and
`src/adapterTest/java/io/github/sunthemoon/arceadaptertest/SealDetectorRuntimeGameTests.java`.
Follow-up records: this verification, the two evidence ZIPs, COMPLETION-PLAN,
CURRENT_VERSION and the version implementation log. Independent record audit,
if added, is separately attributed and cannot substitute for source execution.

The installed existing fixture rule is now qualified for both native hands.
Custom tag/door precedence through installed runtime, complete actor/thread/
lifecycle/post-query refusal/order, unlock, reload/unload, packaged/restart,
protection mods and actual V1/V2 remain open. No full detector/item or content
unit is delivered: ledger remains **186 PLANNED /154 REVIEW assets**.
Physical hatch prerequisites/O1/O2/O3, ADR-068 PROPOSED and R-021 OPEN remain;
no owner policy response is inferred. C16-C19 and all G0-G9 stay unfinished.
v1.8 stays IN_PROGRESS /IMPLEMENTING; the acceptance cursor stays v1.0.0.

## Separate independent record audit (2026-10-09)

Fresh read-only Codex agent `/root/seal_installed_runtime_disposition_review`
audits the six draft records above, actual Git objects and both sealed archives.
No introduced record finding is identified in that inspected scope. Its own
ledger, provenance and whitespace commands exit0; strict repository validation
still exits1 with 44 PASS/one inherited link-check failure. Focused checking of
the four draft Markdown files records 613 links, 49 inherited errors and zero
errors on introduced lines; it is not an all-links-valid result. The original
supplemental CRLF/LF checker failure and separate normalization diagnosis are
retained. No build, Minecraft runtime or new product test is run by this reviewer.

The [separate record-review archive](INSTALLED-RUNTIME-RECORD-REVIEW-02.zip)
contains 92 entries, 351,029 bytes; ZIP SHA-256:
`1500799e811d07fb666814adf5682287d8e74fa727645c7a7ca9a043889f4529`.
Its relative manifest binds 91 payloads/1,089,108 bytes. REPORT SHA-256:
`8f120baa70b7c2fba2405ceaef773cc51e44c2190c6cb18c01c8551628d7567c`.
The external sealed leaf is `seal-installed-runtime-disposition-independent-20261009-01`
under the same external evidence parent. Root verifies CRC, all manifest digests
and exact payload identity before adding this compact archive; sealed inputs
remain unchanged. The reviewer releases all read/HEAD interests before publication.

The audited verification preimage SHA-256 is
`564491dbf052a79c7baff19f523e1d327860fae0bed96d383b98e4c55f5a66be`;
the other draft hashes and snapshots are retained in that archive. This section
is a later Root packaging/association addition, not part of those audited draft
bytes. The third ZIP adds one follow-up record to the six listed above. Source
execution, the Low preparation deviation, unwaived errors and all open Required
Gates remain as recorded; this audit is not another runtime or release approval.
