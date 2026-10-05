# Gravity switch GameTest diagnostics

Status: READY_FOR_REVIEW; uncommitted diagnostic source, not verified delivery.

Base: `37ef84c26d33d5f369478bd5060dc97b9dc7fbdd` in the isolated
`test/v1.8.0-gravity-disable-diagnostics` worktree. Effective governance is the
user-maintained main AGENTS; Root is the sole integrator and publisher.

## Scope

Add read-only observations to the existing planet gravity switch test. Preserve
every existing assertion, config mutation, batch, two-tick delay and 1200-tick
timeout. No production change, readiness manipulation or oracle substitution.

Owned paths:

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/GravityFieldGameTests.java`
- this task record and `HANDOFF-01.md` in this directory.

## Diagnostic boundary

Observe the native test/world tick, COMMON spec loaded state, raw/effective
gravity switch, runtime availability, cached active/status/index membership and
the exact fixture position's loaded/entity-loaded/ticking predicates. Log at the
switch changes, immediately before the original disable checks, and before/after
AfterBatch restoration. No per-tick logging or chunk acquisition is permitted.

One test-only context may retain the helper/device until AfterBatch; it is
bounded to this single planet fixture, has a six-line hard limit, and must be
cleared in cleanup finally. It grants no runtime, persistence or player authority.

## Verification / non-goals

Compare original assertions, mutations, annotations and sequence delays against
the fixed Git source; inspect the actual diff and read-only call sites. No local
Java/Gradle/native execution is authorized; C-drive free space is below the heavy
execution threshold. Compilation and hosted GameTest observations are required
later, after different-agent source review and Root publication. No Gate is
closed by this diagnostic source, and neither prior failure is erased.

Fresh lexical checks pass 20 controls, including exact restoration of the whole
original source after removing only the diagnostic additions. Compilation and
native observation have not run. Actual commands and retained tooling failures
are recorded in `HANDOFF-01.md` and the small external evidence directory.
