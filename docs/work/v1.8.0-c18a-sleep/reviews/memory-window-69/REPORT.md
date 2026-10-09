# Task69 independent ADR-069 amendment review

Date: 2026-10-09. Delegated read-only reviewer, not Root or the amendment author.
Fixed proposal: `cbbc8de6a6574afa2872c2aaeea3916cf8aeebd3`.

## Findings first

**No new material amendment defect or required text correction was identified in
this static review.** New findings: 0 Critical, 0 High, 0 Medium, 0 Low. This is
not executable qualification, normative adoption, risk acceptance or a Gate PASS.

The inherited Medium R64-01 is now the subject of a precise, owner-selected
normative treatment, rather than an unanswered policy choice. ADR-069 explicitly
changes the traced requirement to a startup-enabled, final-pre-exit window and
does not claim equivalence to a birth-to-exit traced inventory. The proposed text
is suitable for a separate explicit integration/adoption decision. It remains
PROPOSED until that decision is recorded; this report does not make it normative
or close R49/R55/U platform/runtime qualification. References:
[ADR-069:17-29,38-52,90-107](D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L17),
[Decision07:9-20,29-35](D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18a-sleep/OWNER-DECISION-07.md#L9),
[independent64 R64-01:9-33](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/REPORT.md#L9).

**Inherited Low R64-02 remains open at the implementation boundary.** The new
text correctly includes both EOF drains, query, closure and recording in the
same 180-second external ceiling even after child exit or on failure. This
does not repair or adopt the historical collector. A separate launcher review
and actual qualification are still required. References:
[ADR-069:73-88](D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L73),
[independent64 R64-02:35-55](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/REPORT.md#L35),
[protocol02:257-289](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input61-proposal02.md#L257).

## Independent semantic and authority assessment

### Policy selection is recorded, not inferred approval

Decision07 identifies the actual Root-conversation asynchronous item, reproduces
the presented question and submitted answer, states the missing submission
timestamp, and limits the selection to the two windows and amendment/review
procedure. The recommendation suffix is explicitly part of the submitted answer,
not a presumed UI default. ADR-069 has `status: PROPOSED` and an empty acceptance
basis. The record does not expand the reply into source/helper authorization,
resource acceptance, production sleep behavior or risk/Gate approval.

I inspected the committed owner record, not an independently exported original
conversation transport. Its exact attribution is reviewable in the record; no
submission time, independent signature or broader authorization is manufactured.

### The measurements are distinct and conjunctive

The traced observation has a stated start and end: startup `-X tracemalloc=1`
before runner/decoder import and construction, continuously active with one
traceback frame, then a final accumulated-peak sample after prescribed calls and
all-field assertions while the applicable live input/result is retained. Stop,
clear, peak reset, filtering and subtraction are disallowed. The sample site and
subsequent output/exit ordering must be recorded. Pre-enable, untraced and
post-sample work, including final serialization and shutdown, are expressly
outside what this metric establishes. This directly addresses the semantic
ambiguity identified by R64-01 without pretending to measure that omitted work.

The native observation must be on the same fresh child, through its original
launch-owned handle, after verified normal termination and before handle closure.
It is the API's peak working-set counter, not current samples, private commit,
traced bytes or a hard allocation cap. Missing/query-failed/indeterminate/over-cap
observations fail qualification. No averaging, subtraction, counter substitution
or use of different passing cohorts is allowed. Native lifetime coverage does
not classify the traced prefix/tail or make their traced values known.
References:
[ADR-069:33-69](D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L33).

For a two-call case, unchanged37/protocol02 still require the first payload/tree
and aliases to be released before constructing the second, without resetting
the peak. The final live-retention wording must be implemented for the applicable
last input/result; it does not authorize retaining two ASTs. Earlier live-result
assertions/samples and all sixteen prescribed calls remain obligations. No
resource case, constructor, digest oracle, decoder or Python object allocation
probe was executed in this review. References:
[complete37:218-224](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input37-proposal.md#L218),
[protocol02:181-191,217-247](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input61-proposal02.md#L181).

### Limits and public/production boundaries are preserved

Both inclusive memory ceilings remain **268435456 bytes**, independently.
The original 524288-byte input, 65536-node/global-name, 64-depth/local-name,
4096-UTF-16-unit string/name and 64-ASCII-byte number bounds remain. The
60-second cooperative decoder and 180-second external ceilings, twelve fresh
process maximum, split 262144-byte stream caps and ONE 4194304-byte aggregate
resource-test leaf remain distinct. No per-case evidence multiplication is
introduced. Fixed contract52/complete37/43 retain the decoder API, precedence,
error locations, metrics, accepted lexical profile and finite case obligations.
References:
[ADR-069:20-23,71-77](D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L20),
[disposition52:12-19,31-58](D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18a-sleep/OFFLINE-CORE-CONTRACT-FREEZE-52.md#L12),
[protocol02:291-315](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input61-proposal02.md#L291).

The duration is local Windows/CPython 3.13.15/v1.8.0 only, with re-review for
executable/platform/counter/launch/final-sample-order changes. Invalid evidence
fails qualification and preserves the original records. Separately corrected
helpers and distinctly identified new cohorts are recovery, not historical
evidence rewriting. No player-data save veto, quarantine, added logging policy,
dimension/sleep/spawn/time behavior, API/schema, asset, quota or runtime policy
is introduced. The unchanged other requirements prevent reading the failure-
preservation wording as permission to silently reuse/retry a fixed case.
References:
[ADR-069:79-111](D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L79).

## Primary technical check and access limitations

Python's documentation supports startup tracing, one-frame operation and the
accumulated traced-block current/peak API; it does not make a sampled peak cover
later operations. This supports the proposed window, not an installed-binary
birth/shutdown timeline. The public 3.13 pages presently identify **3.13.16**,
not the pinned 3.13.15 executable. No online page is substituted for executable
identity or case receipts.
[Python tracemalloc](https://docs.python.org/3.13/library/tracemalloc.html),
[CPython command-line option](https://docs.python.org/3.13/using/cmdline.html#cmdoption-X).

Microsoft's primary API documentation distinguishes peak/current working set
from commit, requires query-capable handles and a correctly sized structure,
and makes nonzero BOOL the success predicate. The retained-object termination
documentation supports handle attribution after exit; it does **not** alone
promise every memory API succeeds then. The working set is pageable resident
memory, not all allocation. These facts support the counter interpretation
conditional on each actual successful attributed post-exit query, as the ADR
requires. They do not establish portability or hard quota enforcement.
[GetProcessMemoryInfo](https://learn.microsoft.com/en-us/windows/win32/api/psapi/nf-psapi-getprocessmemoryinfo),
[process termination](https://learn.microsoft.com/en-us/windows/win32/procthread/terminating-a-process),
[working set](https://learn.microsoft.com/en-us/windows/win32/memory/working-set),
[official PROCESS_MEMORY_COUNTERS source](https://raw.githubusercontent.com/MicrosoftDocs/sdk-api/docs/sdk-api-src/content/psapi/ns-psapi-process_memory_counters.md).

The Learn PROCESS_MEMORY_COUNTERS page failed on two browser attempts (the
second reported a fetch timeout). Its official MicrosoftDocs source was read
successfully instead. The other Learn pages displayed authorization notices but
also exposed their relevant actual technical text. Browser results are tool
records, not locally retained or byte-hashed page archives. This review does not
claim source-tag/binary equivalence or perform disassembly/instrumentation.

The actual50 child samples `get_traced_memory()` before `emit` constructs and
serializes its record, confirming the disclosed tail boundary statically.
The relevant50/55 reports establish only their four named local normal-exit
native observations and explicitly preserve portable/alternate-exit/decoder
qualification. I did not rerun or recustody those packets.
References:
[actual50 child:17-21,49-54](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input50-child-static.md#L17),
[actual50:8-40](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input50-report.md#L8),
[actual55:7-57,264-267](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input55-report.md#L7).

## Affected record consistency

The complete actual five-file commit diff is 184 insertions/3 deletions, all
record-only. ADR-069 and Decision07 are new; the other changes explicitly record
the answered policy choice and still-pending adoption/execution. CURRENT_VERSION
retains the inherited acceptance cursor v1.0 and separate active development
v1.8 status. COMPLETION-PLAN keeps RESOURCE61 unchecked/in-progress and both
inherited findings visible. The log explicitly supersedes the earlier detector-
preparation pending-choice sentence only for that policy decision. Historical
functional/checkpoint statements about unadopted measurement remain correctly
historical, not contradictory current acceptance. No source/test/build/status
Gate or sealed original is changed by the proposal.
References:
[CURRENT_VERSION:106-117](D:/GitHub/AdvancedRocketry-Community/docs/status/CURRENT_VERSION.md#L106),
[COMPLETION-PLAN:354-362](D:/GitHub/AdvancedRocketry-Community/docs/status/COMPLETION-PLAN.md#L354),
[implementation log:3-14,36-41](D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-implementation-log.md#L3).

## Exact inputs, hashes and commands

[INPUTS.tsv](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-memory-window-adr-independent-20261009-69/INPUTS.tsv)
lists every textual file read, exact read coverage, physical byte length/SHA256
and, for repository records, the fixed cbbc8de6 Git blob identity. Physical-file
hashes are labelled as such, not silently equated to Git hashes. Working
governance and reviewed records compare unchanged against cbbc8de6; AGENTS alone
is user-modified and its actual working SHA256 is separately identified.

Materialized complete37/43 hashes independently match disposition52's frozen
pins: `f3658d5c631a04dfc6e053883f6ab56843fc441e163d78561f86041a862ee6f0`
and `69f6d6b85379b23d67566ead27db5d75e6ef039066e28614f7767ebf29298048`.
Protocol02 is `7c813d16f64d53f37fe1b1258bdb83cb7e72167e63999045b9763e563453e2ad`;
independent64 REPORT is `6a907063669645cef99e9e0619afbedec064adaa79832b7f6bcfb1f85de4e5a7`.
This is targeted input identity, not a renewed whole-archive/custody audit.

[COMMANDS.md](D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-memory-window-adr-independent-20261009-69/COMMANDS.md)
records actual command/tool calls and results, including the wrong ADR path and
display truncations. `git diff --check cbbc8de6^ cbbc8de6` passes; indexed changes
are absent; fixed governance/affected-record working diffs are empty. Read-only
commands completed promptly (all local command calls before report construction
were below three seconds). No full external raw capture/watchdog receipts were
retained, and no stronger historical capture/deadline qualification is claimed.
No command output approached the 262144-byte split cap. Finite selected reads
avoid emitting the 575254-byte entire implementation log.

## Completion, remaining risk, changes, tests and Gates

Completed: independent fixed actual-diff/normative/authority/window/limit/status
assessment, targeted50/55 static comparison, primary technical checks, exact
input hashing, report and small checksum manifest. No further amendment text
correction is required by this review. No normative adoption was performed.

Remaining: explicit integration/adoption disposition; then a separately scoped
actual launcher/case-child implementation and independent actual-diff/fault-path
review before authorized resource execution. Each case still needs executable/
source/helper identity, tracing state/nonreset peak/live-result ordering,
original-handle post-exit successful native query/closure, all-field outcomes,
real 60/180-second checks, complete capped EOF/raw capture, aggregate admission
and preserved failure records. R49/R55/U, R64-02, hard allocation/quota and wider
sleep/native/runtime/security/persistence/client obligations remain open.

Modified files: only new REPORT.md, INPUTS.tsv, COMMANDS.md and SHA256SUMS.txt in
this task's exclusive evidence leaf. No Temp directory was needed. Source,
tests, repository/index/HEAD, existing evidence and user AGENTS were untouched;
no commit/push, process management, probe/decoder launch or further delegation.
Root handles OpenPet. Pre-existing untracked ZIPs/directories were inspected
only via status and were not taken over.

Tests added/modified/executed: NONE. Python functional/resource/allocation/
counter probes and all Gradle clean build/test/runData/generated cleanliness/
GameTest, dedicated/restart/real-GPU/multiplayer/soak/release checks are
NOT_EXECUTED/OUT_OF_SCOPE here, not PASS or NOT_APPLICABLE.

**All current and inherited Required Gates G0-G9 satisfied: NO.** v1.8 remains
IN_PROGRESS, with the inherited acceptance cursor at v1.0. No ledger delivery,
Gate/risk approval, release/tag or whole-driver adoption follows from this report.
Next action is limited to the current v1.8 explicit amendment disposition and
separately assigned qualification prerequisites, not a new version or runtime
policy implementation.

The small SHA256SUMS covers only this leaf's retained report/input inventory/
command record. It does not impose a Root ACK, recursive custody operation or
another report-only review layer.
