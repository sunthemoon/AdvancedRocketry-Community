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
camera preset is not a placement rotation. The model sets `flip_v: false`.
[Texture orientation](#texture-orientation-w249-r3) below gives the reason and
says what remains unverified (W249-fix).

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

## Texture orientation (W249-R3)

This section was added by W249-fix on 2026-11-02 under Root decision R276.

The motors use `flip_v: false`, while every formed-hull fixture from K1 to
K4 and W244 uses `true`. The value follows the 1.12 loader that drew each
mesh:

- The formed hulls were drawn by LibVulpes' own OBJ loader, which flips V
  (`1 - v`), as the [W244 record](W244.md) explains. Their modern models
  therefore flip V to keep the same image on the same faces.
- The motors were drawn by Forge 1.12's blockstate OBJ loader. Upstream's
  `rocketmotor.json` and `advrocketmotor.json` (`forge_marker: 1`) name the
  OBJ directly, and no file in the upstream tree at `c5cd5af6` sets a
  `flip-v` custom value (a case-insensitive `git grep` for `flip-v`,
  `flipv` and `flip_v` finds nothing).
- In Forge 47.4.10, `ObjLoader.java` line 57 reads `flip_v` with a default
  of `false`, and `ObjModel.java` line 428 takes the sprite V as
  `flipV ? 1 - v : v`. This was read from
  `forge-1.20.1-47.4.10-sources.jar`, SHA-256
  `918a11bdfceace2752d4c29bddbdf327981e1f6a1e1f0675f23e5fbf01e226c0`.

The remaining premise is unverified. It is that Forge 1.12.2's `OBJModel`
also defaulted `CustomData.flipV` to `false` and applied the same
expression in `putVertexData`. No Forge 1.12.2 source or JAR is on this
machine, and web access was refused in the W249-fix session, so the
1.12.2 source could not be cited. If that premise is wrong, both motor
textures render upside down on every face. Texture orientation against a
1.12 reference therefore stays on the V1 list below, for both motors.

## Sprite names (W249-R4)

The two imported PNGs are named `combustion_classic.png` and
`advcombustion_classic.png`, although no retained NEW art holds
`textures/block/combustion.png` or `textures/block/advcombustion.png`.
The reason is a collision with a planned import. Asset-plan row 236
(`textures/blocks/combustion.png`, IMPORT C17a, owned by
`block:rocketmotor`) is an upstream block texture. Under W246's
stitched-folder rule, it would land on `textures/block/combustion.png`,
which is the name the model texture would otherwise take. The suffix keeps
that name free for row 236.

Upstream has no `textures/blocks/advcombustion.png`. The advanced motor's
texture takes the suffix only so that the pair is named alike. P2's two
bipropellant model textures have no such collision either; whether they
follow the pair is for Root to decide.

## Deferred visual acceptance and exclusions

V1 on a real GPU must judge both placed motors, adjacent seams, underside
orientation, UV alignment, particles, lighting, GUI/ground/fixed and both
hands in first/third person, resource reload, and both motors in assembled
rockets. Missing-model/sprite logs must be absent. Automation is not V1.
W249-fix adds two items. First, texture orientation on both motors against a
1.12 reference, because the 1.12 default above is unverified. Second, now
that the motors no longer occlude (W249-R2): the faces of a fuel tank
beside a motor and of the ground or pad beneath it must render through the
open throat and corners, with no see-through holes, and the slanted nozzle
faces must be lit, in both flat and smooth lighting.
Human provenance review, independent engine review, central draft integration,
hosted regression and all version Gate approvals remain with Root/owner.

P2-P4, S1, P0, H1, U1, duplicate item meshes, block-sprite combustion,
sounds, fuel/bay behaviour, status files, ledger edits, ADR edits and direct
`.gitattributes`/build changes are excluded. The record will retain actual
test failures and commands without weakening checks or changing Gate status.
