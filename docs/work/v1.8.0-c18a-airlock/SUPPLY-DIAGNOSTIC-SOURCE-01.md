# Installed-airlock supply snapshot: source development record

Date: 2026-10-07. Task: C18a-AIRLOCK-SUPPLY-DIAG-01. Status:
implemented-unverified, independent source review and hosted execution pending.
Base: `f9117b599e10b1f25789741248a6b73db60a0785`.

Only AirlockDoorGameTests.java changes runtime instructions, on its existing
initial-supply failure branch. It distinguishes activeTaskOutcome from retained
outcome/needsScan/nullable bounds and captures the exact installed service's
exposure/catalog policy, seven fixed cell classifications and two door states.
This current snapshot is not proof of the earlier OPEN cell. No production,
resource, permission, schema, packet, registry, asset or save behavior changes.

Development postimage is 39,337 B /539 lines, SHA-256
`b7b6902155f0d699cabdef93a1a2508638daf04d40f0561a698111ab92e8096a`.
The class crosses the recommended 500-line review point: all added work is
private failure diagnostics for this existing native test family, not domain
or production responsibilities. No generic tracing service is introduced.

Root runs `python -B check01.py` in its own
[external evidence leaf](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-supply-diagnostic-root-20261007-01/CHECKS-01.json)
(`c52287`, exit 0). Fourteen static controls pass. Removing only the declared
diagnostic/helper/import edits restores the exact normalized base source;
all seven subjects, six sequencing cases, original remaining predicates,
timeouts, controlled tick loops, scan budgets and cleanup remain unchanged.
The original supply predicate is evaluated once at its original point.
Planning and accepted-content-ledger validators plus git diff --check pass.
These static checks neither compile Java nor execute native classifications.

The helper has constant iteration counts, nonloading preflights, no arbitrary
state/NBT dump and no mutation/retry/scheduling/tick path. Nonfatal reflection,
assertion and linkage failures append UNAVAILABLE and retain the original
assertion; VM exhaustion/ThreadDeath are not hidden. The underlying existing
adapter may read a door counterpart or sky/heightmap internals, not qualified
as only seven primitive world reads. No successful cleanup or cause is inferred
from source structure. Local C remains below 10 GB, so JVM/Gradle/native work
is unrun. Committed-source full hosted regression, diagnostic branch coverage,
cause/repair, packaged restart, real clients and all v1.8 G0-G9 remain open.
