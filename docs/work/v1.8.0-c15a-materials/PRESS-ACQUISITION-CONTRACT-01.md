# C15a Small Plate Press acquisition amendment01

Status:ACCEPTED (contract scope),2026-10-04. This additive acquisition contract does not replace
accepted press processing/protection behavior or imply a version Gate pass.
Root owns the proposed central DataGen correction; independent contract review
precedes production editing. The current missing route is observed in the
[bounded survival inspection](../v1.8.0-c16a-hatches/SURVIVAL-FEASIBILITY-01.md).
The [independent contract review](reviews/PRESS-ACQUISITION-CONTRACT-REVIEW-01.md)
has no unresolved Critical/High/Medium finding; Root adopts it under the owner's
conditional authorization. The subsequent unchanged acquisition implementation
passes independent source review and full Root/independent regression;
see [development verification](PRESS-ACQUISITION-VERIFICATION.md).

## Source and exact proposed behavior

Approved source:Advanced-Rocketry/AdvancedRocketry (MIT), commit
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`,
`src/main/resources/assets/advancedrocketry/recipes/platepress.json`,380 bytes,
SHA256 `df50c4227b7b2c28f5a6849f5cc647f887f5860bfc41edb2e0ef3902293272ad`.
Its entry is already inventoried in `legacy-manifest/assets.csv`; Root verifies
the actual read-only source hash before using its recipe facts. No LibVulpes,
fork, vanilla bitmap/model or upstream JSON is copied into runtime resources.

The old crafting pattern has a blank upper row, one centered vanilla piston,
then three `ingotIron`. Modern shaped normalization drops only the blank upper
row: `" P "`, `"III"`. `P` is exactly `minecraft:piston`, not a sticky piston;
`I` is `forge:ingots/iron`. Result:one
`advancedrocketrycommunity:small_plate_press`, recipe ID of the same name.
The existing registry ID, block properties, piston-protection exception,
obsidian backing, processing recipes, pulse behavior and COMMON switch stay
unchanged. There is no steel, motor, powered-machine or other-mod prerequisite.

DataGen also creates the ordinary recipe-unlock advancement after acquiring a
piston. It is not the C18 classic advancement trigger. Existing recipes remain
byte-identical; only this missing crafting recipe/unlock are added. Datapacks
may override the stable new recipe ID using ordinary recipe replacement.

## Verification and admission

Before code, record source/target/transform in development provenance. Unit
checks inspect actual generated JSON and distinguish piston from sticky piston,
iron ingot tag from plate selectors, quantities and normalized grid. Registered
Forge crafting checks use the actual recipe manager, exercise accepted offset
and mirrored placement plus absent/wrong/extra ingredients, and preserve input
stacks while matching. Confirm no resource-ID collision and actual survival
vanilla seeds. Repeat DataGen with zero second-pass changes; inspect packaged
main/sources singleton resources and unchanged existing recipes. Independent
actual-diff/scoped replay precedes Root full build/GT and final delivery.

Contract acceptance under the owner's conditional authorization is separate
from source/runtime delivery. M1 steel and M2 controllers/hatches/components
remain pending; one new route cannot establish the full C16d graph or new-world
progression. No current recipe signature/process/journal or network schema is
changed, and the earlier missing-route report remains immutable history.
