# Bounded managed checkpoint comparison

Task: C16a-03b-CHECKPOINT-COMPARE-01. Status: READY_FOR_REVIEW.
Worker branch: codex/v1.8.0-checkpoint-compare-20261007.
Worktree base: `60f1564528de782fa15269884fd1355d3c0b9ff1`.
Root is sole integrator/committer. Effective user-owned AGENTS is read from main.

## Source scope and admitted contract

Only ClassicRootBundle, new private ClassicNbtExactComparison, new focused
ClassicCheckpointComparisonTest and this task record are owned here. RootBundle
starts at exact successor02 bytes: 7,096 / SHA256
`e9db2ec459a1b7e1451ffd64edd5b2436ae69889e52e59ec5a8d8b1735154e4d`.
The other owner worktree is not edited. Main's LOAD-JOIN-IMPLEMENTATION-01 adopts
the outgoing seam `49124a456f55f5da95cb22a35a6959af9e5a3b7bb5e1d732529058aa0309a475`
and LOAD amendment `a02998ec2618106a2709c8e531b6d2cb8e767bad5a444b1a3f4dac0e1886bc87`
for limited private implementation only.

Retained comparison preserves bounded legacy/inconsistent roots verbatim but
returns false for existing missing/unbounded save-refusal conditions. Supported
encoding uses existing controller/hatch preflight. Both entry points require a
current EMIT lease on entry and in finally; the caller retains selected pending/
encoding identity, owner/service/Level/lifetime fences and foreign-owner checks.
The bundle is data, not owner or world authority. Neither method acquires/closes
the caller's lease or publishes any storage. Twelve fixed-key projections borrow
references only inside the synchronous call; unmanaged outgoing keys are untouched.

Native equality is iterative after exact-class shape and lossless byte preflight:
UTF-16 key/string equality, list subtype/index order, array contents, typed
integers and raw floating-point bits. Individual limits and aggregate
1,873,117 bytes / depth25 / nodes86,529 are unchanged. No Tag.equals, recursion,
resource resolution, serialization, hash, world/provider query or copy occurs in
the new comparison path. Exclusive stable transitive input ownership is a caller
precondition; concurrent same-size changes are not promised detectable.

## Verification / non-goals

Focused declarations cover native classes, all twelve managed presences, legacy
roots, limits, malformed/cyclic/foreign inputs, raw bits and EMIT lease discipline.
Native reflective fixtures are test-only and do not prove writer/reload parity.
Real raw-state tests use the existing bootstrap/Chest fixture; no installed service
or guarded authority is fabricated. Java/Jupiter are not run without a separate
Root grant. Whole production dependencies remain real owner/Root joins, not stubs.
Full builds/native runs remain prohibited with C free below10GB. No save policy,
schema/public ID, sticky denial, registration, runtime activation, risk acceptance
or Required Gate is changed.

Actual author checks: original check01 exited1 because its inverse fixture inserted
one extra newline; that helper/raw failure remain unchanged. Separate check02
exited0, proving the 7,096-byte starting body exact, unchanged fixed dependencies,
four-path ownership, line budgets, lexical comparison fences and aggregate budget
arithmetic. Twenty input pins were unchanged. Twenty-two tests are DECLARED ONLY;
no Java/Jupiter/native execution occurred, and the full real owner/Root dependency
integration is still missing from this clean base. Do not replace those lease
fixtures or count a subset as a successful suite. External frozen handoff:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/checkpoint-comparison-implementation-20261007`.
