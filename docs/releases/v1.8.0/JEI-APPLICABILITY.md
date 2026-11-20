# JEI applicability for classic devices

Use this table to find a device's processing recipes or operating instructions in JEI.
All IDs below use the `advancedrocketrycommunity` namespace. All obtainable devices also
retain their vanilla crafting acquisition recipes. This classification reconciles the
[machine census, sections 3 and 6](MACHINE-CENSUS.md) with
[ADR-061 section 3.3](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md)
and [version section 6, step 4](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md).
It does not waive startup, behavior, persistence or visual acceptance.

- **Category** means an executable recipe type has a custom JEI category.
- **Information** means an assembly, catalog conversion or service has an ingredient-information
  page. Search the device and open its recipes/uses; relevant inputs and outputs also link
  to these pages. The page key is `jei.advancedrocketrycommunity.operation.<name>`, except
  the existing `jei.advancedrocketrycommunity.solar_array` page.
- **Crafting** means a passive device needs only its acquisition recipe, not a fabricated
  processing recipe. Its role is explained here.

Information describes server-authoritative operations, not new recipes. Values are supplied
from production constants; configurable defaults are explicitly labelled. Server catalogs,
permissions and current configuration remain authoritative. No JEI dependency is introduced
into common initialization. The optional adapter is
[ArceJeiPlugin](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/compat/jei/ArceJeiPlugin.java),
and its testable page model is
[OperationInformation](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/compat/jei/OperationInformation.java).

## Individually registered devices

| Device | Applicability | Category or information page | Reason |
|---|---|---|---|
| `electrolyzer` | Category | `electrolyzing` | Executes item/fluid electrolysis recipes. |
| `rolling_machine` | Category | `rolling` | Executes rolling recipes with item, fluid and FE inputs. |
| `precision_assembler` | Category | `precision_assembling` | Executes ordered component assembly process recipes. |
| `lathe` | Category | `lathe` | Executes lathe recipes. |
| `arc_furnace` | Category | `arc_furnace` | Executes arc-furnace recipes. |
| `cutting_machine` | Category | `cutting` | Executes cutting recipes. |
| `crystallizer` | Category | `crystallizing` | Executes crystallization recipes. |
| `chemical_reactor` | Category | `chemical_reacting` | Executes chemical recipes, including equipment acquisition. |
| `precision_laser_etcher` | Category | `laser_etching` | Executes laser-etching recipes. |
| `centrifuge` | Category | `centrifuging` | Executes centrifuge recipes. |
| `observatory` | Information | `ground_research` | Starts paid ground-survey jobs rather than RecipeManager recipes. |
| `astrobody_data_processor` | Information | `ground_research` | Claims and analyzes surveys for one-time research credit. |
| `combustion_generator` | Information | `combustion` | Converts Forge furnace burn ticks into FE credit. |
| `pressurized_tank` | Crafting | None | Passive fluid storage/capability forwarding; no conversion or process. |
| `pump` | Information | `pump` | Collects world fluid through bounded, protected operations. |
| `small_plate_press` | Category | `small_plate_press` | Executes its registered redstone-driven pressing recipes without a process BE. |
| `solar_generator` | Information | `solar_generator` | Converts eligible environmental exposure into FE without recipe inputs. |
| `solar_array` | Information | `solar_array` (existing key) | Multiblock formation and environmental generation require operating help. |
| `planetary_beacon` | Information | `beacon` | Publishes eligible navigation markers for helmet beacon finders. |
| `co2_scrubber` | Information | `scrubber` | Spends finite cartridge durability to reduce vent oxygen debits. |
| `gas_charge_pad` | Information | `gas_charge_pad` | Converts plain oxygen/hydrogen canisters to and from finite stored gas. |
| `atmosphere_detector` | Information | `detector` | Selectable atmosphere-to-redstone service, not processing. |
| `pipe_seal` | Crafting | None | Passive atmosphere boundary/capability forwarding part. |
| `airlock_door` | Crafting | None | Door/boundary behavior; no conversion, assembly or catalog. |
| `oxygen_vent` | Information | `oxygen_vent` | Converts finite canisters and power into sealed-room supply. |
| `suit_workstation` | Information | `suit_workstation` | Installs/removes compatible modules while retaining module state. |
| `rocket_assembler` | Information | `rocket_assembler` | Scans, assembles and disassembles world structures transactionally. |
| `fuel_loader` | Information | `fuel_loader` | Converts item-fuel catalog entries into rocket fuel credit. |
| `fluid_fuel_loader` | Information | `fluid_fuel_loader` | Converts catalog-approved propulsion fluids, not ordinary cargo. |
| `rocket_fluid_loader` | Information | `rocket_cargo` | Transfers fluid cargo into a linked rocket without processing. |
| `rocket_fluid_unloader` | Information | `rocket_cargo` | Retrieves fluid cargo from a linked rocket without processing. |
| `rocket_item_loader` | Information | `rocket_cargo` | Transfers item cargo into a linked rocket without processing. |
| `rocket_item_unloader` | Information | `rocket_cargo` | Retrieves item cargo from a linked rocket without processing. |
| `rocket_monitoring_station` | Information | `rocket_monitor` | Provides authorized remote rocket status/control. |
| `rocket_satellite_bay` | Information | `satellite_bay` | Deploys an owned package during eligible flight; does not build it. |
| `satellite_terminal` | Information | `satellite_terminal` | Launches/operates catalog-defined missions and claims rewards. |
| `satellite_builder` | Information | `satellite_builder` | Consumes a catalog-validated layout and programs chip/package identity. |
| `microwave_receiver` | Information | `microwave_receiver` | Receives credited orbital-satellite energy under server checks. |
| `warp_core` | Information | `warp` | Bridges station-owned FE and logical relocation state. |
| `warp_controller` | Information | `warp` | Authorized frontend to station travel/catalog operations. |
| `station_gravity_controller` | Information | `station_controls` | Edits station gravity, not area-field recipes. |
| `station_altitude_controller` | Information | `station_controls` | Edits a station property rather than processing ingredients. |
| `station_orientation_controller` | Information | `station_controls` | Edits station orientation rather than processing ingredients. |
| `landing_pad` | Crafting | None | Passive docking marker, with no conversion or assembly action. |
| `force_field_projector` | Information | `force_field` | Maintains protected, owned field claims using power. |
| `orbital_laser_drill` | Information | `laser` | Uses body tables or protected physical shaft operations, not process recipes. |
| `laser_target` | Information | `laser` | Linked physical mining endpoint; shares the drill's operating page. |
| `gravity_field_controller` | Information | `gravity` | Maintains a powered permissioned field with radius-dependent upkeep. |
| `black_hole_generator` | Information | `black_hole` | Converts singularity-profile fuel-table burn ticks into FE. |
| `railgun` | Information | `railgun` | Quotes routes and transports ledger-owned cargo between endpoints. |
| `elevator_anchor` | Information | `elevator` | Paired cargo/ride endpoint with FE costs and access checks. |
| `elevator_terminal` | Information | `elevator` | Paired station endpoint, not a second processing machine. |

There are 52 device rows: 11 Category, 37 Information and 4 Crafting. The separate
`finite_canisters` page links the empty, oxygen, hydrogen and nitrogen canisters and
distinguishes fluid-capability exchange from the gas pad's narrower plain-item conversions.

## Census parts and technical blocks

These are the separately enumerated parts in census section 6, not additional standalone
machines. Ordinary casings, coils, motors and structural blocks have only their acquisition
recipes: their owning controller's category/page explains the machine.

| Part | Applicability | Information page | Reason |
|---|---|---|---|
| `rolling_machine_item_input_port` | Crafting | None | Passive input port; recipes belong to rolling. |
| `rolling_machine_fluid_input_port` | Crafting | None | Passive fluid port; recipes belong to rolling. |
| `rolling_machine_energy_input_port` | Crafting | None | Passive FE port; recipes belong to rolling. |
| `rolling_machine_item_output_port` | Crafting | None | Passive output port; recipes belong to rolling. |
| `precision_assembler_item_input_port` | Crafting | None | Passive input port; recipes belong to precision assembling. |
| `precision_assembler_item_output_port` | Crafting | None | Passive output port; recipes belong to precision assembling. |
| `precision_assembler_energy_input_port` | Crafting | None | Passive FE port; recipes belong to precision assembling. |
| `item_input_hatch` | Crafting | None | Passive shared-family input; controller owns recipes. |
| `item_output_hatch` | Crafting | None | Passive shared-family output; controller owns recipes. |
| `fluid_input_hatch` | Crafting | None | Passive shared-family fluid input; controller owns recipes. |
| `fluid_output_hatch` | Crafting | None | Passive shared-family fluid output; controller owns recipes. |
| `power_input_plug` | Crafting | None | Passive shared-family power input; controller owns recipes. |
| `power_output_plug` | Information | `solar_array` | Already linked to the solar-array page; forwards array-owned FE. |
| `force_field` | Technical | None | No obtainable item or acquisition recipe; projector-owned temporary field block. |

## Display acceptance

Unit registration and translation checks do not prove JEI rendering. The campaign's C31
JEI cells must inspect the candidate with JEI, recipes/uses navigation, both languages,
GUI scales and resource reload, and repeat the declared with/without-JEI startup matrix.
This document records applicability, not a visual or Required Gate pass.
