# W265: laser gun baked item model

This note implements R291 in [TASK W265](../TASK-W265-H1-LASER-GUN.md),
starting at `baaf12456abe922864aa942078f5f7530bc34925` on
`codex/v1.8.0-w265`. The write scope is the laser gun's importer plan,
coverage, pending provenance, imported assets, v1.8 model provider/output,
model/atlas tests and W265 records. Helpers, logs and central-file drafts
belong to `D:/GitHub/ARCE-Task-Evidence/root-queue/W265/`. No other worktree,
central file, gameplay, renderer, sound, registry, recipe, packet or state
changes. No agents, push or merge.

## Upstream views and the flat icon

The inspected original Advanced-Rocketry/AdvancedRocketry pin is
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`. Its `ClientProxy.java` registers
the laser gun's inventory model at line 236. The corresponding
`assets/advancedrocketry/models/item/basiclasergun.json` has parent
`item/generated`, a flat `items/basicLaserGun` layer and four hand display
transforms. The extra `model: model/basicLaserGun` string is not a Forge OBJ
binding. No code references the laser OBJ; the model-bake callback only
handles the rocket, the custom ModelLoader accepts rocketmotor, and the item
has no custom item renderer. Thus source inspection finds the generated
flat sprite for every view, not flat GUI plus mesh hands. This is source
evidence, not a claim to have run a legacy client.

R291's otherwise case therefore selects the mesh everywhere. Do not add
`forge:separate_transforms` or import an unused flat icon. Draft an EXCLUDE
row for `textures/items/basiclasergun.png`, explaining this mesh replacement.
Keep the existing NEW generated icon and its historical provenance unchanged;
draft its retained-but-unbound disposition. The atlas walk already models
`forge:obj`; add item-root fixtures including failures and retain its unknown
loader/overrides rejection. No new loader needs support.

## Geometry, materials and camera presentation

Import the item OBJ/MTL and `textures/models/basiclasergun.png`, recording
them in coverage and an LF Python-written plan before copying. Lowercase
the target names and rewrite `mtllib basicLaserGun.mtl` to
`basiclasergun.mtl`. Preserve the `Diff` and `spec` material identities;
rewrite both `map_Kd advancedrocketry:models/basicLaserGun` values to
`advancedrocketrycommunity:block/basiclasergun`. Move the PNG byte-identically
to `textures/block/` so the standard block atlas stitches it. Review remains
`PENDING_HUMAN_REVIEW`.

The item JSON uses `forge:obj`, `automatic_culling: false`, `flip_v: false`
and this sprite for particles. Forge-native unflipped UVs are an explicit
presentation choice: the legacy mesh was not bound, so there is no verified
legacy mesh UV presentation to reproduce. No runtime fallback is added.

The raw mesh has 206 positions, 398 faces, 1,592 baked corners and bounds
`[-0.6, -0.416498, -0.4]` to `[0.6, 1.576, 2.899999]`. Its box center is
`[0, 0.579751, 1.2499995]`. A root affine matrix maps `p` to
`0.3 * p + [0.5, 0.3260747, 0.12500015]`, centering it at `[0.5,0.5,0.5]`
with maximum extent 0.9899997. This normalization is derived from the box,
not a mesh edit. Use Forge's matrix form to avoid implicit transform origins.
Source inspection before implementation found that Forge 47.4.10's
`ObjModel.makeQuad` calls `blockCenterToCorner()` on the root matrix.
The JSON matrix must therefore specify translation
`[0.15, -0.0239253, -0.22499985]`; that conversion adds 0.35 on each axis
at scale 0.3, producing the corner-space map above. The test applies the
same conversion, rather than testing only the JSON matrix in isolation.

Display values below are rotation degrees, translation model pixels and
uniform scale. Hand values transcribe the upstream item JSON, now applied
to the normalized mesh; they are not evidence of a legacy mesh appearance.
Other contexts are explicit modern presentation choices.

| Context | Rotation | Translation | Scale |
|---|---|---|---|
| First person right | 0, -90, 0 | 1.13, 3.2, 1.13 | 0.68 |
| First person left | 0, 90, 0 | 1.13, 3.2, 1.13 | 0.68 |
| Third person right | -80, 260, -150 | -1, 5, 2.5 | 0.9 |
| Third person left | -80, -280, 150 | -1, 5, 2.5 | 0.9 |
| GUI | 30, 135, 0 | 0, 0, 0 | 0.8 |
| Ground | 0, 0, 0 | 0, 3, 0 | 0.5 |
| Fixed | 0, 90, 0 | 0, 0, 0 | 0.75 |
| Head | 0, 0, 0 | 0, 0, 0 | 1 |

## Verification and remaining acceptance

Demonstrate failing model/transform tests before implementation where
possible. Pin every transform and geometry/material reference, parse the
matrix with Forge's deserializer and check normalized bounds. Exercise the
packaged item OBJ through the same atlas walk as production, including
missing mesh/material/sprite and unsupported loaders. Run client/data and
whole unit suites, two identical runData outputs, importer/ledger/provenance/
resource Python checks, clean-export import verify, CI-argument provenance
validation, focused `laser_gun` GameTests and repository validation. Every
heavy command uses the atomic machine lock and disk checks; disposable
worlds/exports are removed by their owning script.

Root receives drafts only for the asset plan, notes-only ledger disposition,
binary-preserving `.gitattributes` lines and governance import-verify list.
V1 on a real GPU must check hand grip and muzzle direction in both hands,
idle/use animation, unchanged beam alignment and sound, GUI/ground/fixed/head,
UV orientation, both materials, lighting, particles and resource reload.
Human provenance approval, independent other-engine review, central
integration, hosted regression and all version/Gate approvals remain open.
