# C18d K2: four classic machine model pairs

Implementation source: `d81e3d38e268e3499750a1f6a3723731daf4528a`. Root decision R235 extends D8's reviewed
URL rule to chemical reactor, crystallizer, cutting machine and lathe.
See [W233](../v1.8.0-root-queue/records/W233.md) for actual commands,
counts, failures, follow-up work and the pre-implementation wire design.
This summary is not human provenance approval or a visual Gate.

## Assets and implementation

`tools/import/v180-k2-plan.json` reproduces 12 importer-written targets:
eight upstream byte copies (four OBJ/PNG pairs) and four NEW material
libraries read from authoring commit
`faa0238169803db40596c0fddac5f3f8e5a38657`. The missing upstream MTLs are
supplied without rewriting the upstream OBJs. The schema-2 record is
`docs/provenance/v1.8.0-c18d-k2.json`, still `PENDING_HUMAN_REVIEW`.
Generate and verify rerun derivation and history checks unchanged. Every
source/recorded derivation verdict is CLEAR; eight upstream histories have
no review trigger. Related textures remain explicitly recorded, not erased.
K2 source geometry has 5,960 budget vertices and 217,747 resource bytes;
combined K1/K2 has 7,688 vertices and 281,641 bytes, within the original
262,144-vertex/16-MiB set limits and 65,536 per object.

The physical-client-only catalog registers formed-only renderers with
explicit existing NEW controller-face fallbacks. Frozen fixtures cover
orientation, scale, rendering, all four facings, actual visible geometry,
collision and unchanged generated-pattern hashes. Three models have a
model-space quarter-turn. The cutting machine's render box, not its
collision or pattern, includes the saw above its one-block structure.
Idle geometry omits crystallizer Liquid and lathe Rod and places the lathe
Shaft at its socket. Full source geometry counts against budgets before
this client-only adaptation; imported bytes do not change.
The recipe-only Rod's 28 untextured faces never reach Forge; its remaining
idle bytes pass the original strict parser check. The crystallizer samples
light above its occupied crucible; fixture tests check all sample cells.

The existing optional transient `formed` update-tag byte now covers these
four kinds; sound-enabled kinds also retain `active`. There is no custom
channel, persistent schema change or client authority. Projection tests
cover malformed/missing flags, save isolation, break/reform transitions
and exclusion of the etcher. Root obtains a separate read-only wire review
before merge. `event:ClientProxy.modelBakeEvent(ModelBakeEvent)` remains
REDESIGNED as the modern physical-client reload/render path, not a copied
legacy model-bake hook.

## Executed checks

At implementation commit `d81e3d38e268e3499750a1f6a3723731daf4528a`,
import verify reproduces all 12 targets and the record byte for byte.
The isolated K2 class passes 23 tests, and the combined client-model/classic
selection passes 397. `gradlew.bat --offline clean build --console=plain`
passes; the fresh unfiltered test task reports 5,930 tests in 1,081 classes,
with no failures, errors or skips. `runData` passes and both generated-only
and whole-worktree `git diff --exit-code` checks return zero.
With the integration edits applied only to a disposable copy, the original
126 importer/ledger/OBJ Python tests pass, the content-ledger validator
passes, and the repository validator reports 45 passes and no pending,
warnings or failures. The W233 record identifies the distinct audit runs.

The full GameTest run completes 1,571 tests but exits nonzero: the laser
drill's unloaded-target test and the station-sky context change-delivery
test fail. All four new K2 projection tests complete without failure.
The unchanged log checker also rejects two `unloadedOwner` player-save
warnings. These are not a local-regression pass or a host-lag-only waiver.
The affected tests and player-save implementation are outside this diff;
their causes have not been established by this worker. Root must resolve
or separately track them and run the hosted regression before acceptance.
The retained server log is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W233/heavy-20261101T023020-25121218/gametest-runtime.log`,
SHA-256 `fe4c9b5b7977641b68d1ab0f9ac0549559d672d4a2b2a1d19aaba0e5703f749e`.

## Remaining acceptance

No real client or Forge physical-client parser/baker was run. Actual
textures, lighting, culling, coplanar overlap, idle presentation, reload
and resource-pack fallback still need the final real-GPU campaign.
Activity animation and recipe-item/liquid projections are not added.
The precision laser etcher pair remains excluded from K2 because its
texture's ambiguous rename has no origin finding. K3-K5, maintainer
provenance sign-off, V1/V2 and all-version Required Gate approval remain
outside this implementation. No version/Gate is marked passed.
