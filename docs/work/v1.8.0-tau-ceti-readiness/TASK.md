# Transfer destination entity readiness

Status: READY_FOR_REVIEW (source only; Java and GameTest not run).

Base: `5f1cbefc30d59a35eed80023697a1f4a0c4687fc`.
Worktree: `D:/GitHub/arce-v180-tau-ceti-readiness-20261005`.

## Scope

Wait before creating a destination entity or advancing PREPARED when the exact reserved
snapshot origin does not have both loaded entity data and native entity-ticking eligibility.
The existing executor continues to renew source/destination flight tickets before this check.
A missing destination Level and a ready but blocked pad retain their existing failure behavior.

Owned paths are RocketTransferService.java, RocketTransferEntities.java, the new
RocketDestinationReadinessGameTests.java, and this task/HANDOFF-01.md. Root additionally
authorized only spawnDestination's private-to-package-private visibility change for the
same-package actual call-site fixture. No public API or injected readiness predicate is added.

## Evidence and boundaries

The hosted 516317 GameTest failed at the cold Tau Ceti landing assertion; a previous passing
cohort recovered a temporarily unresolved second-leg entity. This change addresses a source-
supported readiness gap, not a demonstrated unique cause of that hosted failure.

The new fixture uses a real scanned structure, registered entities and actual ServerLevel
predicates with an uncached local journal/unregistered service. Its ordinary flush can save
other cached SavedData but cannot persist the local journal; it is not a durability proof.
One fixed remote Moon chunk, four source cells and one temporary obstruction are owned;
finite readiness polling and success/error cleanup are required. The fixture first owns
a radius-zero FULL ticket, observes loaded entity data without position ticking, then
replaces it with a radius-two ticket and observes both native readiness predicates.
Those preconditions are assertions, not a reported native test outcome.

## Verification

- [ ] Different-agent actual source review.
- [ ] Java compile and new actual call-site GameTest execution on a qualified host.
- [ ] Full unfiltered GameTest cohort, including unchanged cold Tau Ceti test.
- [ ] Hosted build/JUnit/DataGen/artifact checks and fixed source postchecks.

No fuel, phases, schema, pad selection/reservation, ticket level/timeout, recovery limit,
existing test duration/assertion, shared registration, or client behavior is changed.
Native/S2/V1/V2 and all v1.8 Required Gates remain separate.
