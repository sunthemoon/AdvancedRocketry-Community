# V130-ROCKET-02 registry unit tests

- Status: `READY_FOR_REVIEW` (source inspection only; execution pending integration).
- Owner: delegated registry-unit implementation worker; integration/review: root.
- Branch: `codex/v1.3.0-registry-unit`.
- Worktree: `D:/GitHub/arce-v130-registry-unit`.
- Base: `b0ea71f8ffa5465dc9f94ad84e5fa66bcbf65bba`.
- Contract: [ADR-022](../../decisions/ADR-022-ROCKET-ADAPTER-REGISTRATION.md).
- Write scope: `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/compat/rocket/**`
  and this task directory only.

## Outcome and boundaries

Add finite, server-free JUnit coverage for owner-scoped registration, handle and
thread lifetime, atomic rejection, fixed capacities and versioned provider NBT.
Use the host-supplied known-type predicate, without starting a Forge registry or
Minecraft world. Provider callbacks must not run during catalog registration or
payload-availability queries.

Also cover the package-private returned-time guard with an injected monotonic
clock, without sleeping or invoking world callbacks.

Production classes, public API, Gradle, registration wiring, runtime GameTests,
artifacts and central status files are integrator-owned.
No upstream code or assets are copied. No schema or network changes are made.

## Validation

The worker may inspect files, use text checks and commit its owned test/doc files.
Gradle execution is deferred until the integrator combines the production classes
and tests. Test existence does not imply a passing build or any Required Gate.
Record actual static checks and the final test-only commit in the handoff.

The integrator confirmed internal rejection types: argument/conflict/capacity
failures use `IllegalArgumentException` (null may use `NullPointerException`),
and lifecycle/thread failures use `IllegalStateException`. Invalid persisted
envelopes return an empty decode result; invalid decoder arguments are programmer
errors. External capacity excludes the reserved legacy entries.
