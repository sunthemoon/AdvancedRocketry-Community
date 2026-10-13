# W19 Root decision 01: the five dusts, and finishing the graph gaps

Root: Claude. Date: 2026-10-12. This answers the question in `records/W19.md` (`053fae93`).
It amends [TASK-W19](TASK-W19-ACQUISITION-GRAPH.md).

## Decision 1: the five dusts are optional cross-mod inputs

The five dusts are `silicon_dust`, `steel_dust`, `titanium_dust`,
`titanium_aluminide_dust` and `titanium_iridium_dust`, together with their
`#forge:dusts/<material>` tags. Classify them as **optional cross-mod inputs**.

Do not add a native producer for them.

**Why.**
- ADR-063 §1 registers these material forms for tag compatibility.
- ADR-063 §2 limits native processing to ore → dust pressing. None of the five materials
  has an eligible ore:
  - steel, titanium aluminide and titanium iridium are alloys;
  - silicon has no ore;
  - titanium's rutile pressing was removed in C15a, so that titanium keeps its electric arc
    furnace progression.
- The 1.12.2 baseline worked the same way: LibVulpes registered these dusts for every
  material, and Advanced Rocketry itself had no grinder, so other mods' ore processing
  supplied them.
- In W19's baseline report, nothing other than these dust nodes is unavailable because of
  them. They are leaves.

**ADR-064 §10.2 note.** Root adds an implementation note at merge. It says that §10.2's
all-item survival check treats exactly these five registered dusts as optional cross-mod
inputs. Do not edit the ADR yourself.

**What you implement:**
1. An explicit, documented set of optional cross-mod inputs in the graph test code. It
   holds exactly these five items and their five tags, and cites this decision.
2. Assertions that:
   - the set is exact: adding or removing an entry fails;
   - no required node depends **only** on an optional input. Recompute reachability with
     the optional nodes forced unavailable, and check that nothing else becomes
     unavailable;
   - every other registered item is still strictly required.
3. The strict failure stays. Optional nodes are reported in their own section of the
   report, not hidden.

## Decision 2: the graph gaps are not blocked

The dust question never blocked task part 1. Do it now: model each code-backed acquisition
and cite its code path.

| Gap | Model |
|---|---|
| Hydrogen, oxygen and nitrogen fluids | Gas-canister draining (`GasCanisterHandler` / `GasCanisterCatalog`) and the gas-harvest tables (`gas_harvest/*.json`) |
| Enriched lava | Venus volcano placement (`VolcanoPiece`), then pumping (`PumpSources`) |
| Rocket fuel | Follows once its fluid inputs are available |
| `access:mars` | The flight model, in the same way the Moon is modelled. Name the actual code path that permits Mars travel |
| Lightwood | `LightwoodTreeFeature` in the packaged alien-forest biome, plus whatever the graph needs to reach that biome |

Add no model for something the game cannot actually do. If one of these turns out to be
impossible in game, record it with the evidence. Do not invent a route.

## Testing notes

- **`MixinPackagingTest` failure.** This is a known build-cache bug, and Root is fixing it
  in `build.gradle` now. When `compileJava` comes from the Gradle build cache, the Mixin
  refmap is not regenerated. Record the failure; do not work around it.
- **GameTest.** Your PowerShell lock commands were rejected by the tool policy. Use the
  plain snippet from WORKER-RULES unchanged; other workers ran it successfully today. If
  it is still rejected, record that and stop at the unit level.
