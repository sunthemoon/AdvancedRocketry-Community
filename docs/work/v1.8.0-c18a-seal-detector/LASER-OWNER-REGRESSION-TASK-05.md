# v1.8 native owner-registration fixture correction

Date: 2026-10-09. This is a bounded test-only prerequisite correction for the
failed required native regression recorded in SPATIAL-VERIFICATION-04, not
production ownership/persistence implementation or detector delivery.

## Basis and scope before source edits

Fixed failing source 770b3134cae99ab603ccde610c3731a98d63f524, native-01 exit 1:
597 tests complete, one required waiting-owner fixture failure. Independent
read-only diagnosis01 identifies a delayed unregistered-precondition assumption
and insufficient causal receipts. Mapped-sequence bytecode can replace an
earlier assertion's diagnostic; the original first assertion/save chronology
remains an inference. Original source/commands/raw findings remain unchanged.

Create a new isolated worktree at that fixed source. The sole write_scope is
src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/LaserTargetGameTests.java,
limited to the waiting-owner case and its narrowly necessary test helpers/imports.
Root owns central task/status/log/integration and all Git commits/pushes.
No other source, production service, command, event, persistence/schema, registry,
build, assets, policy, timeout, game-tick budget or existing expectation changes.

## Required behavior and evidence

Establish the actual unregistered native target precondition and execute the
existing operator owner command before yielding the initiating test call.
Capture exact target/Level/ID, original/new owner, command result and live owner;
verify subsequent registration belongs to the intended new owner. Preserve
the original ownership condition rather than waiting for a registered target
and thereby testing the different registered-owner command path.

Use existing public test/native/service entry points, actual connected player
and loaded BlockEntity. Stop at the first failed prerequisite and clean only
the fixture's owned player/block/resources without replacing that first failure.
Provide bounded diagnostic/observation data sufficient to distinguish execution
and refusal. Do not silence ordinary saves, alter scheduling, suppress failures,
increase timeout or use an exception as ordinary world-save control.

The existing direct chunk-save-event fixture remains explicitly injected, not
proof of disk persistence, source-owner serialization or restart. Any actual
native outgoing-tag observation must be separately attributed. No new production
logging, save veto or durable-writer claim. Do not add an unrelated service test
or source mutation experiment under this one-file scope.

## Verification and completion boundary

Root inspects the complete diff/postimage and commits the candidate before
fixed-source execution. Independently review actual corrected source and rerun
key checks; preserve all old and new failures. Run standard clean build, forced
explicit test, runData twice with empty generated diffs, unfiltered GameTest,
ledger/provenance/strict repository checks with current bounds. Capture actual
source/inputs/argv/UTC/exit/XML or available native logs and bounded receipts.
No same-label overwrite or changed-budget retry.

Both drives must have >=10 GiB before sustained execution. Only owned generated
outputs may be retired after process/containment/reparse checks. Keep retained
evidence <=100 MiB/files <50 MiB, no source/build/world archive. No inherited cleanup.
This can qualify a corrected fixture only; full detector/sleep/persistence/client
and v1.8 Required Gates remain open. No release, ledger delivery or tag.
