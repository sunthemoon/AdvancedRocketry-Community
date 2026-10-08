# C16a-PLACEMENT-PROVENANCE-22

Date: 2026-10-08. Implementer/integrator: Root. Status: in-progress.
Base: 4ab2ef8ec5a82e79b4244d92e89320848f4c0564.
Branch: test/v1.8.0-placement-provenance.
Worktree: D:/GitHub/arce-v180-placement-provenance-20261008.

## Outcome and ownership

Exercise ordinary native server placement and callback-originated direct item
use, recording actual contexts and bounded ordered caller frames at the item
entry and actual pre-write placeBlock seam. Relate the observations to the
unresolved authentic-entry prerequisite; do not manufacture an authority token.

Root owns this task, NEW FixturePlacementProbeItem.java and
PlacementProvenanceGameTests.java in src/adapterTest/java/io/github/sunthemoon/
arceadaptertest, the adapter fixture constructor registration line, a NEW
PLACEMENT-PROVENANCE-VERIFICATION-22.md and compact evidence ZIPs beside it.
Root also owns the v1.8 implementation log and subsequent integration/status
association. Other agents, user AGENTS.md, inherited untracked files, other
worktrees, existing source/tests and sealed evidence are excluded.
Independent Codex actual-diff review and key reruns are required by AGENTS;
no Claude or delegated implementation.

## Boundaries

The probe is a development-only registered BlockItem for the native chest block,
not a gameplay item, hatch, hook, public API or production registration. Its
observations use invocation-local events and fixture-owned bounded lists, never
a static mutable world/player map. The fixture owns two loaded BE-free cells,
one plain native ServerPlayer, its EmbeddedChannel and temporary event listener.
Restore owned cells and release player/listener/channel on every test terminal.
ItemStack is final on the pinned platform; do not subclass or transform it.

Preserve native first-use/block/item priority, original ItemStack/context,
returns, counts and tags. No queue/field mutation, synthetic onLoad, source cap,
save policy, protection, physical hatch activation or existing-test changes.
Same-context observations qualify only measured paths on baseline Forge;
StackWalker does not expose receivers/arguments and is not adopted authority.
Packaged transformed provenance and the two full Medium prerequisites remain
open. ADR-068 stays PROPOSED and R-021 OPEN.

## Verification

Add required, finite native cases for ordinary survival and creative placement,
and a first-use callback's direct ItemStack.useOn followed by ordinary native
dispatch. Observe actual source/context identity, selected target, call order,
registered owner and source count/tag, with <=16 caller frames and bounded
fixture event counts. Do not prefill BCI values from decompiled line numbers.

Execute Java17 clean build/test, two runData plus empty diffs, unfiltered
runGameTestServer, ledger/provenance/strict-repository and whitespace checks on
committed source. Check drives >=10 GiB and use task-local TEMP/TMP before
sustained commands. Preserve all failures and actual command SHAs under
D:/GitHub/ARCE-Task-Evidence/v1.8.0/placement-provenance-root-20261008-01.
Native embedded players are not packet/V1/V2 or dedicated-restart evidence.
Retire only this task's own ended disposable outputs once; no old cleanup retry.
