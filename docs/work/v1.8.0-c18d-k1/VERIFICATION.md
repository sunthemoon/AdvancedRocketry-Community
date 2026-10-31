# C18d K1: physical-client OBJ loading and centrifuge assets

Tested implementation source: `f699da5a4ed60e1fe7f2caef33901d470e869695`. This is development evidence, not
human provenance approval or a visual Gate. Root applies this summary only
after reviewing the source and [W229](../v1.8.0-root-queue/records/W229.md).

## Content unit and implementation boundary

`event:ClientProxy.modelBakeEvent(ModelBakeEvent)` is REDESIGNED as the physical-client-only bounded OBJ reload path
and centrifuge block-entity renderer. `ClientObjModels` registers both handlers
under `Dist.CLIENT`; it does not run on a dedicated server. The former
`ModelBakeEvent` hook is not copied. K1 now provides the compiled catalog's
centrifuge OBJ, NEW material library and texture for
`block:centrifuge` (`advancedrocketrycommunity:centrifuge`).

W51's source and unit evidence covers bounds, reference restrictions,
orientation/scale, collision and pattern invariance, last-good cache and
fallback. W229 reruns the loader/fixture/projection tests and the whole unit
suite; exact commands, counts, failures and non-unit results are in W229.
None of those tests executes Forge's physical-client parser/baker.

## Provenance and validation

The unchanged `tools/import/v180-k1-plan.json` generates three files in
`src/imported/v1.8/resources`. The OBJ and PNG are byte copies at the upstream
pin in UPSTREAM.md; the MTL is read from authoring commit
`b654e14514d90e3c6e48cd3647cbf3406dc4723f`. The schema-2 provenance record is
`docs/provenance/v1.8.0-c18d-k1.json`; its review remains
`PENDING_HUMAN_REVIEW`. `generate` and `verify` pass without a CLEARED finding.
All three derivation re-runs are CLEAR with no related files. The model has
384 positions, 432 faces and 1,728 baked vertices; the set is 63,894 bytes.
The original history, licence, hash, format, reference and size checks remain.

## Remaining acceptance

No client was started. Real Forge OBJ parse/bake, actual displayed texture,
all-facing fit, light, culling, overlap/z-fighting, reload and resource-pack
fallback still require the final real-GPU campaign. There is no V0/V1/V2
claim, no maintainer provenance sign-off and no Required Gate approval.
The centrifuge cylinder remains static, as W51 documents. K2 to K5 are not
imported; the etcher texture's ambiguous rename remains open.
