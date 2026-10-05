# Cold Tau Ceti missing-rocket diagnostic

2026-10-06. Author/integrator: Root. Reviewer: separate agent, not yet assigned.
Base: `adcd46d2300b74be60f0621ce1b76598f233dce8`.
Worktree: `D:/GitHub/arce-v180-tau-diagnostic-20261006`.
Branch: `test/v1.8.0-tau-missing-rocket-diagnostic`.
Write scope: existing `gametest/TauCetiPathGameTests.java` and this TASK/HANDOFF.
All other source, central files, owner AGENTS, generated data and Git are excluded.

The actual CFD cohort still fails "No rocket at Tau Ceti f". Add one failure-only
scalar diagnostic of current journal phase, source/destination UUID lookup,
record origin, non-loading block/entity availability and time. Do not change
flight requests, cleanup, existing assertions, 270-tick leg delay, 1,400-tick
test timeout, pad setup, ticket rules or production behavior. No unique cause
or fix follows from this observation.

The existing fixture lookup primes pad chunks before the diagnostic; these are
post-lookup observations, not untouched cold-load proof. Logging may emit one
line per failed invocation (the existing failure throws and runs cleanup), not
a per-tick loop, native Tag or all-world entity dump. Getter reads may not load
chunks or change any authority/capability/flight state.

Required: independent actual diff, exact original assertion/delay/request checks,
scoped whitespace, committed hosted compilation and unfiltered native replay.
Local C is below10GB; no local fullbuild/GT/native. Scratch/process temps D-only.
Root alone stages/commits/integrates/pushes; no ledger/Gate completion.
