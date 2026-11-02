# W260: standalone fuel-tank baked model

This implements R286 on `codex/v1.8.0-w260`, from clean shared head
`144794fe34b08697a2a7c125b013a420e3ee1bb5`. The write scope is W260's
plan, coverage, pending provenance and records; the tank model provider,
its bootstrap/propulsion bindings and v1.8 outputs; the three tank
registrations; related model/occlusion tests and the W258-R1 ledger fixture.
Helpers, logs and central-file drafts stay under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W260/`. No push, merge or agents.

## Model and source

One static middle mesh serves `rocket_fuel_tank`, `bipropellant_fuel_tank`
and `oxidizer_fuel_tank`, their block items and assembled block snapshots.
Use P1's ordinary `forge:obj` route, `flip_v: false`,
`automatic_culling: false`, no root transform and no runtime fallback.
The pinned original AdvancedRocketry commit is
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`; its
`src/main/resources/assets/advancedrocketry/blockstates/fueltank.json`
uses `forge:default-block` and the middle mesh for inventory. Its connected
variants are deliberately not transferred: modern tanks have no such state.
The model uses the audited `models/block/models/middletank.obj` rather than
the legacy blockstate's abbreviated mesh location.

Inventory the three files before import. Rewrite OBJ `mtllib middleTank.mtl`
to `middletank.mtl`, preserving case-sensitive material `None`; rewrite MTL
`map_Kd advancedrocketry:models/tank` to
`advancedrocketrycommunity:block/tank`. PNG bytes remain unchanged and go
under the stitched `textures/block/` directory. The `_classic` rule applies
when retained NEW/planned art would collide; no existing or planned
`block/tank` sprite was found, so this batch needs no suffix, as in P3.
Record both rewrites and leave provenance PENDING_HUMAN_REVIEW.

Items reuse P1's eight explicit camera transforms: GUI rotation [30,225,0]
and scale .625; ground translation [0,3,0] and scale .25; fixed scale .5;
both third-person hands rotation [75,45,0], translation [0,2.5,0], scale
.375; first-person right/left rotation [0,45,0]/[0,225,0], scale .4; head
identity. Other components are zero. Tests pin all components.

## Occlusion and review findings

Audit assembly, propulsion, placement/landing, sealing/atmosphere and light
readers before adding `noOcclusion()` to only these three tank registrations.
Preserve full collision/support, IDs, recipes, capacities and fuel roles.
The intended light/adjacent-face effects follow R278; stop if a mod gameplay
predicate would change. Test all three through `assertNonOccludingColumns`.

For W258-R3, arrange up to six states plus the opaque control on a bounded
3-by-3 grid, preserving all light/property assertions and the tick deadline.
Every column, wall and probe stays within relative x/z [-3,3], the `empty`
template's cleared horizontal space. Pin layout and bounds in a unit test.
For W258-R1, explicitly restore the nuclear model rows to IMPORT/C17a in
the batch-order fixture, without changing its rejection assertion.
For W258-R2, the asset-plan draft trims the nuclear owner/reason from the
remaining wildcard. Top/bottom/endtank files remain planned and unimported.

## Validation and integration boundary

Run new tests red where possible, new classes alone, atlas/model/client/data
tests and the whole unit suite. RunData twice and compare outputs. Run
importer, ledger, provenance and resource Python tests; verify imports on a
clean LF export; run provenance validation with the CI JAR arguments and
the repository validator. Focus tank gameplay and all affected occlusion
GameTest batches. DataGen/GameTests use the atomic heavy-JVM lock in a
detached hidden script, disk checks and same-script finally cleanup.

Asset plan, ledger, historical model build exclusions, attributes and the
governance verify list are drafts for Root only. Retain replaced NEW PNGs
and propose their disposition, never delete them. No status/Gate changes.

Real-GPU V1 must judge the standalone open-ended appearance, adjacent faces,
seams, lighting, particles, UV orientation against 1.12, all item contexts
and hands, assembled rockets and resource reload. P1's Forge 1.12 V-default
premise remains unverified, not visual acceptance. Human provenance review,
hosted regression and version Required Gates remain outside this slice.
Connected tanks, top/bottom/endtank imports, X3 exclusions, nuclear tanks,
fuel/bay behaviour, persistence, networking and new renderers are not done.
