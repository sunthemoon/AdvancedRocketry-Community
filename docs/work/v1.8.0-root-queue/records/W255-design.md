# W255: ordinary bipropellant motor models

This implements R281 and ADR-061 section 4.4 for
`bipropellant_rocket_motor` and `advanced_bipropellant_rocket_motor` only,
starting at `13c4b1eae5c205c441b257286a2298825cbeb4af` on
`codex/v1.8.0-w255`. The write scope is the six-file import plan, coverage,
pending provenance and imported assets; the existing motor-model and
propulsion DataGen providers and their four v1.8 model outputs; the two
registrations, model tests and occlusion GameTest; and W255 records.
Logs, helpers and central-file drafts stay under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W255/`.

## Rendering and import

Reuse the [P1 design](W249-design.md) exactly: ordinary `forge:obj` block
models, `flip_v: false`, `automatic_culling: false`, explicit P1 item camera
transforms and block-item inheritance. Assembled rockets retain their existing
`renderSingleBlock` route. No runtime fallback, custom renderer, block entity,
state property or geometry transformation is added.

The pinned original upstream is `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`.
Its `blockstates/bipropellantrocketmotor.json` and
`blockstates/advbipropellantrocketmotor.json` both specify an identity
`facing=down` placement. The modern blocks have no facing property; their
single identity variant stays unchanged. Preserve the source mesh bounds and
tiny horizontal rim rather than fitting the geometry. Texture orientation
inherits P1's unverified Forge 1.12.2 default premise and requires V1 comparison
against a 1.12 reference.

Inventory all six sources before importing. Copy the two block OBJ files
without changing their local MTL names. Rewrite only each MTL's `map_Kd` to
`advancedrocketrycommunity:block/bipropellantcombustion_classic` or
`advancedrocketrycommunity:block/advbipropellantcombustion_classic`. Copy the
two PNGs byte-identically into `textures/block/`. R281 explicitly chooses
P1's `_classic` family naming; P2 has no direct name collision, unlike P1's
`combustion` sprite. Write the plan through Python with LF bytes and keep
provenance `PENDING_HUMAN_REVIEW`.

## Occlusion and server boundaries

Before changing registration, repeat P1's server-use audit: rocket scan and
transactions; atmosphere sealing/detection; landing, arrival and placement;
sky exposure; and all direct occlusion, solidity and light consumers. Reuse
the non-occluding full-collision motor registration only if those mod rules
remain unchanged. Light transmission and the associated vanilla spawn-light,
grass and beacon effects are the intended R278/R281 presentation consequence,
not a change to fuel roles or rocket legality. Add a bipropellant occlusion
GameTest with P1's enclosed columns and opaque control, checking actual light
beneath both motors plus collision, sturdy support and sealing.

## Verification and exclusions

Demonstrate new checks failing first where feasible; run the motor/atlas,
client/data and whole unit suites, Python importer/ledger/provenance/resource
tests, two identical runData outputs, focused bipropellant and occlusion
GameTests, clean-export import verification, CI-argument provenance validation
and repository validation. Heavy tasks take the machine lock in a detached
script that releases only its own token and cleans only its own world/export.

Draft, but do not apply, asset-plan/ledger/build/attributes/governance changes
for Root. Trim bipropellant ownership from broad rows with no remaining
matching file. Retain replaced NEW art and list its proposed disposition.
No nuclear/tank/other model import, sounds, fuel/bay logic, network,
persistence, status files, ledger edits, push, merge, delegation or Gate
approval is included. Real-GPU V1 must cover both placed/item/assembled
models, both hands and camera contexts, UV orientation, seams, particles,
adjacent tank/pad faces through open geometry, flat/smooth lighting and reload.
