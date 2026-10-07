# C18a-AIRLOCK-SUPPLY-DIAG-01

Date: 2026-10-07. Status: in-progress; diagnostic source only, not repair.
Root is author/integrator. Base: `f9117b599e10b1f25789741248a6b73db60a0785`.
Owned worktree: `D:/GitHub/arce-v180-airlock-supply-diagnostic-20261007`.

## Outcome and basis

The exact code cohort `10eb561a5112ee6226e2c52fef1c1cc358c5ef3f`
fails the installed-airlock subject at its second initial-supply assertion.
The independent [investigation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-initial-supply-regression-review-20261007-r1-3d962a/REPORT-01.md)
has SHA-256 `f73eec41e1bdddab8100718a3121622bcafb740711462038c576e813e80e4422`.
It distinguishes drained active-task lookup from retained terminal OPEN; it
does not prove a production cause. Root admits a failure-only fixture snapshot
to acquire that missing current-state evidence, not to rescan or repair air.

## Exact write scope and non-goals

- Existing `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/AirlockDoorGameTests.java`.
- This task, own `SUPPLY-DIAGNOSTIC-SOURCE-01.md`, and Root implementation-log entry.
- No production service, parser, boundary, resource, save, config, registry,
  recipe, translation, asset, native hook, schema, C2S or permission change.
- Keep all seven registered subjects, six case order, original assertions,
  deadlines, scan/dirty limits, controlled ticks, service ownership and cleanup.

## Read and error bounds

Keep one original supply predicate evaluation at the assertion point. Only
when false, read the installed service's retained state for the exact vent by
one map lookup, its outcome/needsScan/nullable bounds and actual exposure policy.
Use that service's existing boundary catalog with ServerLevelVolumeWorldView:
exactly the seed and six cardinal neighbors, one classification each, guarded
by nonloading getChunkNow. Inspect exactly the two native door-half states with
the same loaded guard. There is no world/entity/vent-map traversal, retry,
new scan, ticking, scheduling, cancellation or mutation. Native classification
can itself read a counterpart or the existing sky/heightmap branch; seven
adapter calls are not a claim of seven total internal block reads.

Output only fixed labels, coordinates, enums, booleans and bounded native
volume bounds; no raw NBT, arbitrary state/property dump or exception message.
Handle nonfatal reflection/classification failures as explicit diagnostic
unavailability without replacing the original supply assertion. The snapshot
is later current state, not the historical OPEN observation.

## Verification and acceptance

Different-author actual-diff review and independent applicable checks precede
Root source publication. Static checks compare the exact base, seven subjects,
assertions/timeouts/budgets, unchanged success path and complete diff scope.
Qualified source-bound hosted clean build/unit, twice DataGen and cleanliness,
and full unfiltered GameTests remain required. A failure-only branch may not
execute; no diagnostic or cause is inferred when it does not. Local C has less
than 10 GB free, so no local Java/Gradle/native execution is admitted.
No existing failure, content delivery or v1.8 G0-G9 is closed by this task.
