# W256: static saw block model

This implements R282 on `codex/v1.8.0-w256`, starting at `54d78cb82`.
The write scope is the four S1 imports, their plan/coverage/pending provenance,
the saw entries in `V180ArcCuttingData`, `ModBlocks.SAW_BLADE`, focused tests,
v1.8 generated saw models, and W256 records. Helpers, logs and central-file
drafts belong to `D:/GitHub/ARCE-Task-Evidence/root-queue/W256/`.

## Geometry and placement

The ordinary block model uses `loader: forge:obj`,
`model: advancedrocketrycommunity:models/block/models/sawblade.obj`,
`flip_v: false`, and `automatic_culling: false`. The particle sprite is
`advancedrocketrycommunity:block/cuttingmachine2`. No runtime fallback,
block entity, renderer, network or persisted state is added.

The pinned original upstream is `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`.
Its `src/main/resources/assets/advancedrocketry/blockstates/sawblade.json`
has an identity `facing=north` variant and an identity inventory variant.
The modern stateless block uses that identity, not the other five rotations.
The blade is vertical in the YZ plane and reads as a fixed structure part;
the cutting machine's existing rotated pattern does not require this block
to gain a facing. Real-GPU review must judge this in every machine rotation.
The bounds are `[0, -0.005880, -0.005879]` to
`[1, 1.005879, 1.005880]`. Preserve the tiny tooth overhang (less than 0.095
model pixels) rather than scaling or editing source vertices.

As with P1, this came through the legacy Forge blockstate OBJ route, not
LibVulpes' formed-model renderer. Use P1's `flip_v: false`; the legacy
Forge V-coordinate premise remains unverified, so texture orientation
against 1.12 is explicitly a V1 check, not an automated acceptance claim.

## Item and atlas

The block item inherits the same block mesh. Explicit camera transforms
match P1: GUI rotation `[30,225,0]`, scale `0.625`; ground translation
`[0,3,0]`, scale `0.25`; fixed scale `0.5`; both third-person hands rotation
`[75,45,0]`, translation `[0,2.5,0]`, scale `0.375`; first-person right/left
rotation `[0,45,0]` / `[0,225,0]`, scale `0.4`; head identity. Unspecified
translations/rotations are zero. The audited assembly icon is the item's
particle sprite, `item/saw_blade_classic`, not a flat GUI override or fallback.
Its name remains distinct from retained NEW art and the block mesh texture.
The existing packaged atlas walk validates both sprites and OBJ/MTL bindings.

## Import and server properties

Record the four W245 S1 sources before importing. Rewrite OBJ `mtllib
sawBlade.mtl` to `sawblade.mtl`, retaining every other source byte. Rewrite
MTL `map_Kd advancedrocketry:models/cuttingmachine2` to
`advancedrocketrycommunity:block/cuttingmachine2`. Both PNGs are byte copies
into stitched folders. Write the plan using Python with LF and verify a
clean export; provenance stays `PENDING_HUMAN_REVIEW`.

The blade does not fill a cube, so set only `noOcclusion()` after checking
the server consumers. Pattern matching uses the exact block ID, assembly
uses tags/air, sealing uses collision, landing uses sturdy support/air and
heightmaps, and sky checks require sky light 15. These properties must stay
unchanged. A focused GameTest will check non-occlusion, transmitted block
light, full collision/support and sealing with an opaque control. If a mod
gameplay dependency changes, stop rather than adjusting that dependency.

## Verification and deferred work

Exercise new tests red where practical, then isolated model tests, atlas,
client/data tests and the complete unit suite. Run DataGen twice and focused
cutting/saw GameTests under the machine lock, with disk checks and cleanup
in the detached lock owner's `finally`. Verify importer/Python resource,
ledger/provenance tests, the CI-argument provenance validator and repository
validator. Central updates are drafts only; report unapplied-checkout failures
separately from draft-overlay results.

V1 must check the placed blade and seams, both sides/teeth/UVs and lighting,
all item contexts and hands, particles, resource reload, and all four cutting
machine orientations, including adjacent surfaces seen through the blade.
Human provenance review and independent other-engine review remain pending.
No version/Gate approval is claimed. Iron saw art, the duplicate item mesh,
K2 hull, P2-P4/P0/H1/U1, gameplay changes, status/ledger/build/workflow/attribute
edits, pushing, merging and other agents are excluded. Retained NEW saw art
is listed for Root's disposition, never deleted.
