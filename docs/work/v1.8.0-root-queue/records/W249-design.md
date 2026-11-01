# W249: ordinary mono motor model design

This design implements R271 for `rocket_motor` and `advanced_rocket_motor` only.
The worktree starts at `d6e4b0fc8a45adde06711dbef260a1764bf1b51b`.
The write scope is these motors' import plan, coverage, pending provenance,
v1.8 model DataGen/output, model/atlas tests and W249 records. External logs,
helpers and central-file drafts belong to `D:/GitHub/ARCE-Task-Evidence/root-queue/W249/`.
No gameplay, registry, state, persistence, network or renderer change is needed.

## Block models and placement

Each block JSON uses `loader: forge:obj`, `model:
advancedrocketrycommunity:models/block/models/<legacy-lower-case-name>.obj`,
`flip_v: false`, `automatic_culling: false`, and a `textures.particle` pointing
to its imported `block/combustion_classic` or `block/advcombustion_classic` sprite.
Disabling automatic culling keeps an adjacent block from hiding nozzle faces
based only on their normal. No custom root transform is required.

The placement source is the original Advanced-Rocketry/AdvancedRocketry tree at
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`, specifically
`src/main/resources/assets/advancedrocketry/blockstates/rocketmotor.json` and
`src/main/resources/assets/advancedrocketry/blockstates/advrocketmotor.json`.
Both `facing=down` variants specify no rotation, translation or scale. The
modern mono blocks have no facing property and thrust downward, so their
single existing blockstate variant retains this identity transform. Upstream's
other facing variants are not new modern states. Its `forge:default-block`
camera preset is not a placement rotation. Both old and modern Forge OBJ
loaders default to unflipped V; explicitly retaining false preserves that
convention rather than changing mesh UVs.

Both source position bounds are `[-0.0005, 0.005, -0.0005]` to
`[1.0005, 1.0, 1.0005]`. The horizontal rim projects 0.0005 blocks (0.008
model pixels) past each side. Preserve that tiny upstream rim, not an
eye-fitted scale or mesh edit; test the exact bounds and document V1's seam
check. Selection, collision and server legality remain the existing full cube.

## Items and generated resources

Each block item inherits its own block JSON. Explicit modern block-style
camera transforms use rotation/translation/scale triples:

| Context | Rotation | Translation (model pixels) | Uniform scale |
|---|---|---|---|
| GUI | 30, 225, 0 | 0, 0, 0 | 0.625 |
| Ground | 0, 0, 0 | 0, 3, 0 | 0.25 |
| Fixed | 0, 0, 0 | 0, 0, 0 | 0.5 |
| Third person, either hand | 75, 45, 0 | 0, 2.5, 0 | 0.375 |
| First person, right | 0, 45, 0 | 0, 0, 0 | 0.4 |
| First person, left | 0, 225, 0 | 0, 0, 0 | 0.4 |
| Head | 0, 0, 0 | 0, 0, 0 | 1 |

These are explicit modern inventory/held presentation choices, not an import
of the redundant legacy item meshes or a claim that the old camera preset
has been copied. Placed and assembled rockets share the baked block model;
`RocketEntityRenderer.renderSingleBlock` remains unchanged.

The advanced motor stays in its existing v1.8 propulsion provider. A small
v1.8 provider emits the basic motor's two model replacements, leaving the
v0.5 resources immutable and the unchanged blockstate in place. Root must
exclude those two historical model paths; only a build-script draft is
authorized here. Verification can apply that exact exclusion through an
external init script, clearly identified separately from the unmodified build.

## Import and atlas handling

Record all six source files in the import plan/coverage before generating:
the two block OBJ/MTL pairs and `textures/models/combustion.png` and
`textures/models/advcombustion.png`. Preserve OBJ bytes and local `mtllib`
names. Rewrite the two MTL `map_Kd` values into the mod namespace and
`block/*_classic` sprite IDs. PNGs move byte-identically to `textures/block/`.
The importer reproduces every rewrite and leaves review
`PENDING_HUMAN_REVIEW`. Existing NEW advanced-motor PNGs are retained, with
dispositions drafted for Root. No automatic fallback is added.

The packaged atlas check follows `forge:obj` JSON to its packaged OBJ,
`mtllib`, and every MTL texture; it checks existence and stitching, including
unreferenced materials. Unsupported loaders, material overrides and item
`overrides` fail closed until modelled. Fixtures first demonstrate the missing
checks; positive and rejection cases then exercise the same production walk.
The existing bounded OBJ/MTL checker supplies syntax and geometry checks.

## Deferred visual acceptance and exclusions

V1 on a real GPU must judge both placed motors, adjacent seams, underside
orientation, UV alignment, particles, lighting, GUI/ground/fixed and both
hands in first/third person, resource reload, and both motors in assembled
rockets. Missing-model/sprite logs must be absent. Automation is not V1.
Human provenance review, independent engine review, central draft integration,
hosted regression and all version Gate approvals remain with Root/owner.

P2-P4, S1, P0, H1, U1, duplicate item meshes, block-sprite combustion,
sounds, fuel/bay behaviour, status files, ledger edits, ADR edits and direct
`.gitattributes`/build changes are excluded. The record will retain actual
test failures and commands without weakening checks or changing Gate status.
