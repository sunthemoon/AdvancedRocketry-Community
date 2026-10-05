# Destination readiness source handoff

Status: READY_FOR_REVIEW; uncommitted candidate, not verified delivery.
Base/branch: `5f1cbefc30d59a35eed80023697a1f4a0c4687fc` /
`fix/v1.8.0-transfer-destination-readiness`.

## Implemented scope

The actual destination spawn call now returns before pad evaluation, entity creation/addition
or phase advancement unless the reserved snapshot origin has loaded entity data AND is
entity-ticking. PREPARED retains source authority; the existing tickLive renews both tickets.
Missing-Level failure and the ready but blocked-pad path are unchanged. No new retry queue,
timer, resource operation, writer, schema, protocol, recovery budget or public gameplay API
is added. Only spawnDestination's package visibility was expanded under Root's explicit
same-package test authorization; the class remains package-private.

## Exact source identities

| File (under src/main/java/io/github/sunthemoon/advancedrocketrycommunity) | Bytes | Lines | SHA-256 |
| --- | ---: | ---: | --- |
| rocket/server/RocketTransferService.java | 33440 | 710 | `7b3f8af92b83ceabe7e6fcde507a4a34886cdd441f05bcf906fc6e0770e11696` |
| rocket/server/RocketTransferEntities.java | 16483 | 380 | `45baa26bdd3675770befe8041c4322c99afb9f760c2742322ab3d6754aa30d1b` |
| rocket/server/RocketDestinationReadinessGameTests.java | 20099 | 341 | `364bf20b73acc5e61ec63e5d6f426866e40b72395e171fdac9f2f5076ea62828` |

Service/entities preimages are respectively `f1439b0f0f378623fa7499588ba5efb3dbf0c73200ebdd1221b6222bb0f993e2`
and `643f8bac9b4f2bf96c49a5810aafed0fd6e28b4d87dde92ffb1c546ac2e6a98c`.
The existing service already exceeds 500 lines; this single admission check does not add
a new responsibility or reach 800 lines. The new test is below 500 lines.

The original TauCetiPathGameTests.java is byte-exact: 12759 B,
`a18b481ac8cd256ee27d8b0a4ed5661c62a489252ee575dd6dad3728fd9a18d3`.
Its cold destination, 270-tick flight checks, 1400-tick deadline, ownership and landing
assertions have not been changed or replaced with prewarming.

## Authored regression (not executed)

One actual call-site test, destinationSpawnWaitsForEntityReadinessAndRechecksTheReservedPad,
uses registered components/native scan, registered RocketEntity instances, actual ServerLevel
predicates, a private unregistered service and uncached local journal. It asserts:

1. A fixed cold Moon chunk at (8192,8192) remains cold/PREPARED with unchanged source,
   reservation and fuel, and no visible destination after the actual method call.
2. A radius-zero FULL ticket plus a block request eventually gives loaded entity data
   without position ticking; that actual state must still wait without mutation.
3. The owned ticket is replaced with radius two. Ordinary server ticks must satisfy both
   predicates before continuing. A newly obstructed reserved cell must return the source
   safely, preserve undebited fuel and remove the local transfer.
4. Restoring that cell and installing a fresh valid record must produce a native-UUID-visible
   destination, exact relocated snapshot and once-only destination debit, without prematurely
   removing the source. Success also asserts both fixture entities/cells were cleaned.

Each of three readiness stages permits 80 one-tick observations; the new test deadline is
300 ticks. It does not expand an existing test or production budget. Four initially empty
source cells and one initially empty Moon obstruction cell are the only block writes. One
owned ticket is removed on success/error/exhaustion. Cleanup attempts all owned actions,
retains original failures and attaches cleanup errors. It does not unload chunks, erase
generated Moon terrain, clear a global journal or suppress the production manager.

The local journal's unchanged flush implementation can save other cached overworld SavedData;
it cannot persist this uncached journal. This fixture is NOT durable transaction/restart proof.
It exercises the pre-spawn boundary directly, not preceding countdown motion, whole transfer
recovery, passengers, natural cold Tau Ceti timing, or native/client gameplay.

## Actual checks and retained failures

External evidence: `D:/GitHub/ARCE-Task-Evidence/v1.8.0/tauceti-readiness-author-20261005-6ed439`.

- Fresh Python 3.13.15 `-B` static_checks05.py: exit 0, 29 source/declaration controls.
  These are NOT Jupiter or GameTest outcomes.
- Read-only Git HEAD/status/diff and diff --check: scoped paths only, exit 0.
- Selected mapped Forge 47.4.10 class declarations and native ticket overload/radius
  calculation were checked from pinned JAR `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
  PRIMARY-05.json retains compact signatures/facts, not official method bodies.
- Static attempts 01/02 exit 1 used guessed constant spelling/number spelling in a control;
  attempt 03 exit 1 assumed a direct literal radius subtraction rather than Forge's delegating
  overload. Original helpers/stdout/stderr/exits remain. Corrected 04 passed 27 controls;
  final 05 binds the added source-visibility/cleanup assertions. No production repair was
  made to satisfy these failed tooling assumptions.
- Earlier wrong-path reads and a final rg no-match exit 1 are qualified in READ-HISTORY-01.json.

No Java, Gradle, DataGen, build, GameTest, server/native/client, network or cleanup command
was run by this worker. No stage/commit/push occurred in the author worktree.
Root may publish a reviewed, explicitly unverified source checkpoint to execute
hosted CI. Qualified execution is required before verified delivery, ledger
acceptance or a Gate conclusion.

## Outstanding verification and risks

Compile/API compatibility, actual FULL-to-ENTITY_TICKING transitions within the new deadline,
native cleanup behavior and the full unchanged cold Tau Ceti path remain unrun. The proposal
is not proof of the unique hosted failure cause. Readiness can remain pending under existing
resource/work limits; this change introduces no new completion timeout. Existing post-authority
lookup/recovery semantics are unchanged. A foreign callback could invalidate native readiness
or visibility during creation; the fixture is not a proof against arbitrary event interference.

All v1.8 Required Gates remain open. Root should run the exact reviewed source on the hosted
cohort (fresh clean build/JUnit/DataGen and unfiltered GameTests), retain any failure and
perform source/artifact postchecks. Native S1/S2 and real V1/V2 are separate requirements.

Rollback consists only of the scoped two-production-file diff and new GT/task/handoff.
No existing world, resource, generated asset or source-authority format is changed.
