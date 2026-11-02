# W258: nuclear motor baked model

This implements R284 and the R271/R276/R278 baked-model contract for
`nuclear_rocket_motor` and its block item only. The worktree starts clean at
`a3192d6956c03be35da5201c32460cb688ce8da6` on `codex/v1.8.0-w258`.
The write scope is the W258 plan, coverage, pending provenance and records;
the nuclear model helper, its existing propulsion provider and v1.8 outputs;
the nuclear registration's occlusion flag; and corresponding unit/GameTests.
Helpers, logs and central-file drafts belong to
`D:/GitHub/ARCE-Task-Evidence/root-queue/W258/`. No other agent uses this
worktree. No push, merge, delegation or central-file edit is authorized.

## Model and existing facings

The block JSON uses `loader: forge:obj`,
`model: advancedrocketrycommunity:models/block/models/nuclearrocketmotor.obj`,
`flip_v: false`, `automatic_culling: false`, and
`textures.particle: advancedrocketrycommunity:block/nuclearengine`.
No root transform or mesh fitting is added. The original MIT source is
Advanced-Rocketry/AdvancedRocketry commit
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`.
Its `src/main/resources/assets/advancedrocketry/blockstates/nuclearrocketmotor.json`
gives these placement rotations, transcribed onto the six existing FACING
states of `RocketNuclearNozzleBlock`:

| Facing | X | Y |
|---|---|---|
| down | 0 | 0 |
| up | 180 | 0 |
| north | 90 | 0 |
| south | 90 | 180 |
| east | 90 | 90 |
| west | 90 | 270 |

The legacy default/inventory references to the bipropellant mesh and
`nuclearcombustion` are stale. The facing variants name the nuclear mesh,
and its MTL names `advancedrocketry:models/nuclearengine`; that real image
is the imported texture. There is no retained NEW or planned block sprite
at `block/nuclearengine`, so the stitched-folder naming rule needs no
`_classic` suffix. NEW `nuclear_rocket_motor_{side,top,bottom}` PNGs remain
unchanged; their proposed archival disposition goes only in Root's draft.

Mesh bounds are `[-0.0005, 0.005, -0.0005]` to `[1.0005, 1, 1.0005]`.
The 0.0005-block rim is preserved, as for P1; no gameplay shape is changed.
The open throat/corners require `noOcclusion()` after auditing server uses.
Collision, support, sealing, core adjacency and thrust direction remain
unchanged. The record will list each audited use and the intended vanilla
light effects. A GameTest will check all six states' solid/support/sealing
properties and light transmission against an opaque control.

## Items, import and atlas

The item inherits the same block model and reuses P1's explicit camera
transforms: GUI rotation `[30,225,0]`, scale 0.625; ground translation
`[0,3,0]`, scale 0.25; fixed scale 0.5; both third-person hands rotation
`[75,45,0]`, translation `[0,2.5,0]`, scale 0.375; first-person right/left
rotation `[0,45,0]`/`[0,225,0]`, scale 0.4; head identity. All omitted
translations/rotations are zero. Tests pin every component in every context.
Placed blocks and assembled rockets keep the existing baked-model renderer.

Inventory the three source files before importing. Write the plan from
Python with LF bytes; copy OBJ/PNG bytes and preserve the relative mtllib.
Rewrite only MTL map_Kd to `advancedrocketrycommunity:block/nuclearengine`.
The importer records that rewrite and leaves review PENDING_HUMAN_REVIEW.
The existing bounded OBJ checker and atlas walk validate geometry and every
material's stitched dependency; there is no runtime fallback. Verify the
committed plan and record in a clean export. Central asset-plan, ledger,
build exclusions, attributes and governance changes are drafts for Root.

## Verification and deferred acceptance

Run new tests red where possible, each new class in isolation, atlas/motor/
client/data tests and the full unit suite; runData twice and compare output;
run importer, ledger, provenance and resource Python tests, import verify,
CI-argument provenance validation and repository validation. Focused nuclear
propulsion and occlusion GameTests and DataGen take the heavy-JVM lock in
a detached script, check both disks and clean owned temporary worlds in
that script's finally. No full GameTest or full build is requested.

V1 on real GPU must inspect all six placed facings, UV orientation against
a 1.12 reference, adjacent tank/pad faces through the open mesh, rim seams,
flat/smooth lighting, particles, every item context/both hands, assembled
rockets and resource reload without missing-model/sprite logs. P1's premise
about the Forge 1.12 loader's default V orientation remains unverified;
`flip_v: false` follows its blockstate-loader route, not the formed hulls'
LibVulpes route. Automation does not discharge V1 or human provenance review.

P4/P0/H1/U1, the nuclear core/tanks, fuel/bay behaviour, state or persistence
changes, packets, renderers, status/ledger edits, human approval, hosted
regression and version Gate decisions are outside this task.
