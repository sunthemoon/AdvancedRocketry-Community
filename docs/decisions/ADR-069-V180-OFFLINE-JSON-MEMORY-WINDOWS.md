# ADR-069 - v1.8 offline JSON resource measurement windows

```yaml
status: ACCEPTED
revision: 1
date: 2026-10-09
owner: sunthemoon
deciders: [sunthemoon]
target_version: v1.8.0
slice: C18a-SLEEP-JSON-RESOURCE61
amends: [ADR-066, OFFLINE-CORE-CONTRACT-FREEZE-52]
accepted_at: 2026-10-09
acceptance_basis: actual owner decision07 and complete independent69 static review; normative window amendment only, resource execution and Gates remain open
```

## Context and decision authority

[Decision07](../work/v1.8.0-c18a-sleep/OWNER-DECISION-07.md) records the actual
owner selection of two different memory-measurement windows, with an ADR
amendment and independent review before resource acceptance. It does not approve
an implementation or a result. This proposal changes only the interpretation
of the traced window in the offline JSON qualification requirement under
ADR-066 section 8 and frozen37/43/disposition52. Decoder API, accepted inputs,
diagnostics, numeric ceilings and production Minecraft behavior are unchanged.

Independent64's Medium R64-01 records that startup tracing followed by a final
pre-exit sample cannot establish birth-to-exit traced allocations. Existing
50/55 normal-exit native observations do not extend that traced window. The
original requirements and reports remain immutable historical inputs. This
amendment was not normative at proposal. The separate
[adoption disposition69](../work/v1.8.0-c18a-sleep/MEMORY-WINDOW-ADOPTION-69.md)
records completed independent review and explicit limited adoption.

## Normative amendment

Each of at most twelve fresh resource-case processes must supply BOTH of the
following independent observations. Either missing, indeterminate or over-limit
observation fails qualification; a successful decode or low value in one does
not compensate for the other. Each inclusive ceiling remains 268435456 bytes.

1. **Startup-enabled traced peak through a final pre-exit sample.** Launch the
   pinned CPython executable with `-X tracemalloc=1`, before importing the case
   runner or decoder and before generating input. Keep tracing continuously
   enabled without `stop`, `clear_traces`, `reset_peak`, filtering or subtraction.
   Check that tracing is active with one traceback frame. The final
   `get_traced_memory()` peak is sampled after case input construction, all
   prescribed decoder calls and their full result assertions, with input and
   returned result still retained. The implementation must identify and record
   the exact sample site and subsequent serialization/exit sequence. Allocations
   tracked during that window include runner/import/interpreter allocations
   occurring after tracing is enabled, input, intermediate work and result.
   Allocations before enablement, untraced allocations and all work after the
   final sample, including final receipt serialization and shutdown, are NOT
   established by this metric. Call it a windowed traced peak, never a complete
   interpreter-inclusive process-lifetime traced proof.
2. **Windows post-exit native lifetime peak working set.** The parent retains
   the original process handle belonging to its newly launched child, verifies
   normal termination, and reads `PeakWorkingSetSize` through
   `GetProcessMemoryInfo` after termination and before closing that handle.
   Record executable/source identity, handle ownership, exit status, successful
   API status and counter value. Do not reopen by PID, query a replacement
   process, use a final self-query or take a maximum of sampled current working
   sets. Failure to retain/query that original terminated process fails the
   case. This measures peak resident working set across that process's lifetime;
   it is not private commit, all native allocation, traced bytes or a hard cap.

The two observations apply to the same fresh child, not different successful
cohorts. Traced and native values remain separate and are not added, subtracted,
averaged or substituted. The native observation covers the lifetime containing
the pre-tracing and post-sample periods but does not classify their Python
allocations or make their traced values known. The runner itself is not excluded
from the native measurement or traced-window accounting.

All other limits remain: input 524288 bytes, depth/local names 64,
nodes/global names 65536, strings/names 4096 UTF-16 units and number lexemes
64 ASCII bytes; cooperative decoder deadline 60 seconds; absolute external
deadline 180 seconds; each split raw stream at most 262144 bytes; ONE aggregate
resource-test evidence leaf at most 4194304 bytes. The external deadline includes
launch, wait, both EOF drains, post-exit query, closure and result recording,
including child-exited and failure paths. Limits are not enlarged by this ADR.

## Evidence and implementation requirements

This ADR neither adopts the historical static collector nor selects its code
as the resource launcher. Implement and independently review the launcher and
case child in a separately assigned scope, with finite prospective retention
and deadline checks. Fixed source/executable/helper hashes, exact startup
arguments, actual sample ordering and post-exit API/handle receipts must bind
each case. Preserve launch/query/drain/exit failures; do not retry a failed case
as though only the passing attempt occurred. Prior finite normal-exit probes
are context, not execution evidence for the decoder resource cases.

Independent review must assess the actual amendment and implementation/results
separately. Normative R64-01 disposition does not close R49/R55/U platform/runtime
dependencies or R64-02 merely by changing wording. No native sleep observation,
production policy, dedicated restart, real-client or Required Gate is satisfied
by an accepted amendment or resource sub-suite alone.

## Consequences, duration and recovery

The owner-selected interpretation explicitly permits two different windows
instead of demanding an unproved birth-to-exit traced inventory. This is a
documented semantic change, not equivalence to the historical traced wording or
a blanket waiver. The remaining unobserved traced prefix/tail is disclosed;
neither this ADR nor the native resident counter establishes all allocation or
OS hard-allocation safety. Runtime quota/admission obligations remain open.

Scope is local Windows CPython 3.13.15 offline JSON qualification for v1.8.0.
Re-review on executable/platform/counter/launch or final-sample ordering changes;
do not automatically extend to another platform or version. The amendment adds
no player-data save veto, quarantine, logging or recovery mechanism. Missing or
invalid evidence yields qualification failure and preserves the original case
records. Restoration means correcting a separately reviewed helper and running
new distinctly identified cohorts, never rewriting the old evidence.

## Technical references

Python documents startup `-X tracemalloc` enablement and traced-block peaks;
it does not make a final sample a shutdown-wide inventory.
[CPython 3.13 tracemalloc documentation](https://docs.python.org/3.13/library/tracemalloc.html).
Microsoft defines `PeakWorkingSetSize` as the peak working-set size and explains
that a terminated process object remains until its handles close.
[PROCESS_MEMORY_COUNTERS](https://learn.microsoft.com/en-us/windows/win32/api/psapi/ns-psapi-process_memory_counters),
[process termination](https://learn.microsoft.com/en-us/windows/win32/procthread/terminating-a-process).
These primary references were accessed 2026-10-09; local installed executable
identity and actual supported post-exit behavior still require case evidence.
