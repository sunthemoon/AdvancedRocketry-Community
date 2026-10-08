# C16a-HATCH-NATIVE-LOAD-21

Date: 2026-10-08. Owner/implementer: Root. Status: verified test-side qualification.
Base: 355766c6841137c8e8b5977be1fc3e25bc074fa7.
Branch: test/v1.8.0-hatch-native-load-order.
Worktree: D:/GitHub/arce-v180-hatch-load-order-20261008.

## Observable outcome

Establish native fresh-owner LOAD scheduling relative to completed ordinary
server game-mode placement and pre-tick serialization. Use real registered
native block entities, not a synthetic owner or direct call to onLoad. Relate
the observed order to the pending physical hatch's two-LOAD binding requirement.
The LOAD-BINDING-20 proposal at 932600d9 is read-only input, not adopted source.

## Write scope and dependencies

Root owns this NEW task, NEW NATIVE-LOAD-ORDER-VERIFICATION-21.md, compact new
evidence ZIPs beside it, one NEW gametest/BlockEntityLoadOrderingGameTests.java
and the canonical v1.8 implementation log. Integration ownership also covers
the completion plan and development status after exact-source verification.
User AGENTS.md, inherited untracked work, other worktrees, old proposals and
sealed evidence are excluded. No Claude call or implementation delegation.
Independent Codex actual-diff review and key reruns are required by AGENTS.

Read the applicable governance, ADR-060/064/068, owner interception decision,
private-operation review, primary placement facts and LOAD-BINDING-20 before
using the observation. Final hook/admission/outcome/save contracts remain
unfrozen; this task does not activate any of them.

## Non-goals and invariants

No production behavior, registration, persistent ID/schema, asset, network,
save policy, source cap, hook/dependency or physical hatch activation change.
No reflected writes to a native queue, synthetic onLoad invocation, world
configuration override, existing-test change or chunk ticket. Probe fixtures
own only their bounded BE-free cells and restore them on terminal outcomes.
Fixture players are native server game-mode callers on embedded test connections, not real multiplayer
or real packet evidence. Reflection, if used, is read-only test observation.

## Verification

Inspect pinned mapped Forge 47.4.10 bytecode and its member hashes. Exercise
survival and creative native game-mode placement, exact source Item/count,
installed owner identity, fresh scheduling queues, pre-tick native serialization
and identity after a subsequent natural world tick with a finite deadline. Preserve every
failed execution and avoid translating queue observations into installed-hatch
authority. New tests must be required in the unfiltered GameTest suite.

Run Java 17 clean build/test, twice runData plus git diff --exit-code, full
runGameTestServer, accepted-ledger, bootstrap-provenance, strict-repository and
whitespace checks. Check both drives exceed 10 GiB before sustained commands.
Store scripts/logs/results and actual committed source identity under
D:/GitHub/ARCE-Task-Evidence/v1.8.0/hatch-native-load-order-root-20261008-01.
Use task-local TEMP/TMP/java.io.tmpdir. No retry of previously refused cleanup.

An observation qualifies only its measured route/version, not all custom block
callbacks, final writer/disposal, crash recovery, real clients or release Gates.
After independent review, record the concrete disposition of the pending
two-LOAD binding while keeping full physical lifecycle and v1.8 scope open.

## Completion record

The [verification](NATIVE-LOAD-ORDER-VERIFICATION-21.md) identifies corrected
tested source 3939dd55, independently reviewed/rerun and integrated/normally
pushed at 79cb3e91. Original helper-login failures remain retained. Qualification
is limited to normal in-Level native scheduling; both Medium admission
prerequisites and full physical LOAD/save/terminal authority remain open.
No Required Gate, ADR-068, R-021 or ledger delivery is accepted here.
