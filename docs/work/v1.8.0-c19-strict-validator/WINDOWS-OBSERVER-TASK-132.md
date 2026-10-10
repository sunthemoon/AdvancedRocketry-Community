# C19 Windows command observer task 132

Date: 2026-10-10. Status: IN_PROGRESS. Phase: IMPLEMENTING. Root owns implementation,
integration records, commits and normal pushes. Independent Codex workers inventory
existing boundaries and review actual changes; no Claude or nested delegation.

## Baseline and previous progress

Main branch codex/v1.8.0-classic-content starts at
d09ae17dac87bbac71db2e29ea3f976273968828. The prior goal turn is progress: source
candidate a8dcd5f7 and five records are committed/pushed, and finite evidence is
sealed/freshly verified. Full Python and revised standard130 still fail. No Gate
is passed and no candidate is integrated by those records.

Fresh source worktree D:/GitHub/arce-v180-windows-observer-20261010-132, branch
fix/v1.8.0-windows-command-observer, starts from exact a8dcd5f7b32a588a2cab77c96a1459a4e577743b.
Previous source124 remains unchanged for independent read-only inventories.

## Observable result and scope

Provide a finite Windows-only observation primitive used by the current v1.8
standard-command evidence operator. It publishes original exit/timeout separately
from owned stop/drain, streams available bytes without an 8192-byte fill wait,
caps each raw stream, and distinguishes EOF/failure/incomplete capture from success.
Only newly started processes are controlled, through their original creation
handles and a fresh private unnamed Job Object assigned before primary-thread
execution. No existing PID, process tree, port owner or failed runtime is queried
or controlled. Failure to establish the private job must fail before child resume.
The job does not enable breakaway; closing its last handle ends associated newly
started children. Inherited standard handles are explicitly allowlisted.

Creation/OS scheduling is not preemptible. Do not promise a universal public wall
clock hard bound. All actual elapsed values must remain observable; overrun,
partial/raw-prefix capture or launch/assignment errors cannot become a qualified pass.
Original command and separate stop/drain timing windows remain distinct.

Root source write scope is exactly:
- scripts/bounded_windows_process.py;
- tests/test_bounded_windows_process.py.

Root Main record scope is exactly this frozen task, WINDOWS-OBSERVER-CHECKPOINT-132.md,
docs/status/CURRENT_VERSION.md, docs/status/COMPLETION-PLAN.md and
docs/work/v1.8.0-implementation-log.md. Root external operators/evidence belong to
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-native-observer-root-20261010-132;
fresh source/temp output belongs to task-owned paths only. Subagent write scopes
are exclusive evidence leaves in TASK133/TASK134 and later actual-diff review.

## Non-goals and protected inputs

No production Minecraft/gameplay, sleep/writer activation, schema/API/ID, asset/
upstream import, G4 selection, Main source integration, fixture/old assertion,
source124 or sealed packet changes. User AGENTS.md stays unstaged/unmodified with
SHA-256 c2448e9357ec77d062ab52ecefbb24724fb5c767fb4955a4cd23ef0efbd8ff09.
Inherited unknown acquisitions remain untouched. Failed 130 runtime/generated
outputs stay uninspected and uncleared; its possibly surviving descendants are
not targeted. Original128/130 results are not replaced by a new declared revision.
No historical timeout cause or controlled speedup is inferred from static calls
or aggregate elapsed time. No v1.9 work, ledger delivery, Gate/ADR acceptance.

## Verification and evidence

Pinned Python 3.13.15 is D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe,
SHA-256 85b71d8c6ec1905935f74be0c9869aae198d00e98f39df699ec66f9c5a84cecd.
Own analyses use -I -X utf8 -B. Target unittest uses -X utf8 -B with the unchanged
180-second outer command ceiling and separate stop/drain <=10 seconds.

Add self-contained finite tests for ordinary/nonzero exit, small flushed partial
streams, output bound, original timeout, inherited-pipe children in the private
job, launch/assignment failure and input-validation/handle-lifecycle boundaries.
Tests start only their own controlled producers; no project target, Gradle,
network or existing process is used as a fixture. Preserve original assertions,
fixtures and all existing tests. Capture exact tested/reviewed/committed images.

After actual-diff independent review, source commit and normal push, freeze a
fresh standard cohort before executing it once per declared target. Use the same
JDK17.0.7/flags and original ceilings: clean build2400, test1200, each DataGen1200,
diff180 and GameTest1800 seconds; separate stop/drain <=10 seconds. Build/test XML,
two DataGen hashes/diffs and unfiltered native logs must be collected/verified
before any ended owned-output cleanup. If launch/capture fails, preserve original
receipts and explicitly identify unexecuted dependents, not retries or passes.
Full Python and strict/link/provenance remain separate open obligations.

Source/JSON/XML <=1 MiB; ordinary evidence <=1 MiB except original command/native
streams <=4 MiB. Metadata streams/displays <=256 KiB. Root132 leaf <=4 MiB;
fresh standard leaf <=100 MiB; no single file >50 MiB. Check C:/D: >=10 GiB before
build/server work. Seal exact contained inventories, then recheck actual names,
hashes/sizes/envelopes without executing sealed helpers. Reports retain deviations.

G0-G9 remain required/open. Only scoped implementation/testing progress may be
recorded; passing self-contained fixtures is not standard-command or Gate success.

## Primary platform references

Windows launch and ownership behavior is checked against Microsoft documentation:
[CreateProcessW](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw),
[creation flags](https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags),
[Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects).
These describe API contracts, not proof that any actual command completed.
