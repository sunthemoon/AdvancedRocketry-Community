# Machine guide

This guide tells a player how to build, power and use each machine and control device of the v1.8 development
build. It is indexed by display name and registry ID; every ID uses the `advancedrocketrycommunity:` namespace.
It describes what the code does today. It is not a release approval, and v1.8 is still a development build:
test only on copies of backed-up worlds.

Crafting recipes are not repeated here. With JEI installed, look up any block to see how to craft it, and look up
a processing machine to see all its recipes.

## Contents

- [Before you start](#before-you-start)
- Processing machines:
  [Electrolyzer](#electrolyzer-electrolyzer),
  [Rolling Machine](#rolling-machine-rolling_machine),
  [Precision Assembler](#precision-assembler-precision_assembler),
  [classic multiblock machines](#classic-multiblock-machines)
  ([Lathe](#lathe-lathe), [Electric Arc Furnace](#electric-arc-furnace-arc_furnace),
  [Cutting Machine](#cutting-machine-cutting_machine), [Crystallizer](#crystallizer-crystallizer),
  [Chemical Reactor](#chemical-reactor-chemical_reactor),
  [Precision Laser Etcher](#precision-laser-etcher-precision_laser_etcher), [Centrifuge](#centrifuge-centrifuge)),
  [Small Plate Press](#small-plate-press-small_plate_press)
- Ground research: [Observatory](#observatory-observatory),
  [Astrobody Data Processor](#astrobody-data-processor-astrobody_data_processor)
- Power: [Combustion Generator](#combustion-generator-combustion_generator),
  [Solar Generator](#solar-generator-solar_generator), [Solar Array](#solar-array-solar_array)
- Fluids: [Pressurized Tank](#pressurized-tank-pressurized_tank), [Fluid Pump](#fluid-pump-pump)
- Life support and equipment: [Oxygen Vent](#oxygen-vent-oxygen_vent), [CO2 Scrubber](#co2-scrubber-co2_scrubber),
  [Gas Charge Pad](#gas-charge-pad-gas_charge_pad), [Atmosphere Detector](#atmosphere-detector-atmosphere_detector),
  [Pipe Seal](#pipe-seal-pipe_seal), [Airlock Door](#airlock-door-airlock_door),
  [Suit Workstation](#suit-workstation-suit_workstation)
- Rockets: [Rocket Assembler](#rocket-assembler-rocket_assembler),
  [Rocket Satellite Bay](#rocket-satellite-bay-rocket_satellite_bay)
- Satellites: [Satellite Builder](#satellite-builder-satellite_builder),
  [Satellite Mission Terminal](#satellite-mission-terminal-satellite_terminal),
  [Microwave Receiver](#microwave-receiver-microwave_receiver)
- Navigation and protection: [Planetary Beacon](#planetary-beacon-planetary_beacon),
  [Force Field Projector](#force-field-projector-force_field_projector)
- Space stations: [Warp Core](#warp-core-warp_core), [Warp Controller](#warp-controller-warp_controller),
  [Station Gravity Controller](#station-gravity-controller-station_gravity_controller),
  [Station Altitude Controller](#station-altitude-controller-station_altitude_controller),
  [Station Orientation Controller](#station-orientation-controller-station_orientation_controller),
  [Landing Pad](#landing-pad-landing_pad)
- [Devices described in other guides](#devices-described-in-other-guides)

## Before you start

**Power.** Machines use Forge Energy (FE). The three generators in this guide push their energy into any energy
receiver that touches them, on all six sides, so the simplest setup is a generator placed directly against a
machine's energy input. Cables from other mods also work.

**Reading the multiblock diagrams.** Each layer is drawn from above, layer 1 at the bottom. The top row of every
layer is the front of the machine, the row that holds the controller, and the controller's face points out of the
top of the diagram. When you stand in front of the machine and look at the controller, the left end of each row is
on your right. A controller faces the player who places it, and a machine can be built facing any of the four
horizontal directions.

Symbols used in the diagrams:

| Symbol | Block |
|---|---|
| `C` | the machine's controller |
| `K` | Machine Casing (`machine_casing`) |
| `A` | Advanced Machine Casing (`endgame_casing`) |
| `M` | any motor: Motor, Advanced Motor, Enhanced Motor or Elite Motor. The tier does not change speed or cost. |
| `P` | energy input: a Power Input Plug (`power_input_plug`), or the machine's own energy port for the Rolling Machine and Precision Assembler |
| `I` / `O` | item input / item output: an Item Input Hatch (`item_input_hatch`) / Item Output Hatch (`item_output_hatch`), or the machine's own item ports |
| `F` / `G` | fluid input / fluid output: a Fluid Input Hatch (`fluid_input_hatch`) / Fluid Output Hatch (`fluid_output_hatch`), or the machine's own fluid port |
| `_` | must be air |
| `.` | should be clear; a small set of blocks is tolerated (air, snow, torches, redstone wire, rails, carpets, vines, ladders, levers, buttons, pressure plates, tripwire) |
| `:` | must be one of those clear blocks; anything else stops the structure from forming |

Other letters are explained next to the diagram that uses them.

**When a structure forms.** A multiblock checks itself when one of its blocks changes. There is no wrench and no
activation item: place the last missing block and the controller's screen changes to formed.

**Configuration switches.** Several devices can be switched off by the server in the COMMON configuration. A
switched-off device keeps its blocks, items and stored resources. Changing the file while the server runs does not
always take effect; stop the server first (known issue KI-V180-CONFIG-01 in the
[known issues](releases/v1.8.0/KNOWN-ISSUES.md)).

**"Needs repair" messages.** A device that reads saved data it does not understand (for example from a newer
version) keeps that data unchanged, stops working and usually refuses to be broken. Messages such as
"Data needs administrator repair" or "Unsupported saved data" mean exactly this. Do not try to break or replace the
block; back up the world and ask the server administrator.

**Restarts.** Unless a section says otherwise, stored items, fluids, energy and progress are saved with the block
and come back after a server restart or after the chunk is reloaded. A machine does nothing while its chunk is
unloaded.

## Electrolyzer (`electrolyzer`)

**What it is for.** The electrolyzer splits water into hydrogen and oxygen canisters. Hydrogen makes rocket fuel
cells and, with oxygen, liquid rocket fuel; oxygen supplies suits and oxygen vents. It is one of the first powered
machines.

**Build and power.** It is a single block. It stores 20,000 FE and accepts up to 1,000 FE per tick through its four
horizontal sides. One piece of redstone dust in its charge slot becomes 2,000 FE when the buffer has room for all of
it, so a new world can run it without a generator.

**Use.** Right-click to open the screen. Right-click with a water bucket to fill its 4,000 mB water tank.

- One batch takes 2 empty canisters and 1,000 mB of water, runs for 100 ticks at 20 FE per tick (2,000 FE) and makes
  one hydrogen canister and one oxygen canister.
- Automation: empty canisters go in through the top, redstone dust and water and energy through the horizontal
  sides, and the filled canisters come out of the bottom.
- A redstone signal pauses it.

**Status lines.** "Insert two empty canisters", "Needs water", "Needs energy", "Output blocked",
"Paused by redstone signal", "Electrolyzing", "Invalid recipe or saved data", and
"Newer saved-data schema; processing disabled".

**Keeping and losing.** Items, water, energy and the progress of the running batch are saved. Breaking the block
drops the four item slots; the water and the stored energy are lost.

## Rolling Machine (`rolling_machine`)

**What it is for.** It rolls ingots into plates and plates into sheets, the basic parts of almost every later
machine and rocket component.

**Build.** 5 wide, 3 high, 2 deep: the controller, four ports and 25 machine casings.

```text
layer 3     layer 2     layer 1
K K K K K   K K K K K   I F C P O
K K K K K   K K K K K   K K K K K
```

The ports are the Rolling Machine Item Input (`rolling_machine_item_input_port`), Fluid Input
(`rolling_machine_fluid_input_port`), Energy Input (`rolling_machine_energy_input_port`) and Item Output
(`rolling_machine_item_output_port`). The energy port stores 20,000 FE and accepts 1,000 FE per tick. The fluid port
holds 4,000 mB of water. Each item port holds one stack.

**Use.** Right-click the controller for the Rolling Machine Console. It shows the input and output slots, so you can
load and unload by hand, the structure line and the process line. Water has to be delivered to the fluid port by a
pipe, a pump or a tank; the port does not take a bucket by hand. Right-clicking a port shows its channel and whether
it is bound to a controller.

Every recipe uses 100 mB of water:

| Input | Output | Time | Energy |
|---|---|---|---|
| 1 ingot (aluminum, copper, gold, iridium, silicon, steel, tin, titanium, titanium aluminide, titanium-iridium) | 1 plate | 300 ticks | 20 FE/t |
| 1 plate (aluminum, copper, iron, steel, titanium, titanium aluminide, titanium-iridium) | 1 sheet | 300 ticks | 200 FE/t |
| 2 iron ingots | 8 iron bars | 100 ticks | 20 FE/t |

Iron plates come from the [Small Plate Press](#small-plate-press-small_plate_press). A redstone signal at the
controller pauses the machine.

**Status lines.** Structure: "FORMED", "INCOMPLETE", "WAITING FOR LOADED CHUNKS", "PORT BINDING CONFLICT",
"INVALID PATTERN", "UNSUPPORTED SAVED DATA". When the structure is incomplete the console names the first wrong
block: "Block x, y, z (local x, y, z)" with "BLOCK MISMATCH", "CHUNK NOT LOADED" or "ORIENTATION NOT ALLOWED".
Process: "IDLE", "ROLLING", "WAITING FOR INPUT", "WAITING FOR ENERGY", "OUTPUT BLOCKED", "PAUSED BY REDSTONE",
"INVALID RECIPE", "RECOVERY REQUIRED". The reason line adds "Missing item input", "Missing water input",
"Insufficient energy", "Output is blocked", "Disabled by redstone", "Recipe changed or is invalid" or
"Resources changed; retrying".

**Keeping and losing.** Items, water and energy live in the ports and are saved there; progress is saved in the
controller and continues after a restart. Breaking an item port drops its stack. Water and energy in a broken port
are lost. Breaking any block of the structure stops the machine until it is rebuilt.

## Precision Assembler (`precision_assembler`)

**What it is for.** It assembles circuit boards and electronic parts from several ingredients at once.

**Build.** 3 wide, 3 high, 4 deep: the controller, five item inputs, two item outputs, one energy input and
27 machine casings. This is the only machine whose mirror image also forms.

```text
layer 3   layer 2   layer 1
K K K     K K K     I C I
K K K     K K K     I K I
K K K     K K K     I K O
K K K     K K K     O K P
```

The ports are the Precision Assembler Item Input (`precision_assembler_item_input_port`), Item Output
(`precision_assembler_item_output_port`) and Energy Input (`precision_assembler_energy_input_port`). The energy
port stores 20,000 FE and accepts 1,000 FE per tick.

**Use.** Right-click the controller for the Precision Assembler Console. It shows the five inputs ("ITEM INPUTS
0–4") and the two outputs ("OUTPUT 0–1"), which you can fill and empty by hand; pipes and hoppers use the ports.
A redstone signal at the controller pauses it.

| Inputs | Outputs | Time | Energy |
|---|---|---|---|
| redstone dust, copper plate, steel plate | Control Board | 200 ticks | 10 FE/t |
| redstone dust, gold plate, steel plate | Item IO Board | 200 ticks | 10 FE/t |
| redstone dust, lapis lazuli, steel plate | Liquid IO Board | 200 ticks | 10 FE/t |
| gold ingot, redstone dust, silicon wafer | Basic Circuit Plate | 900 ticks | 100 FE/t |
| gold ingot, block of redstone, silicon wafer | Advanced Circuit Plate | 900 ticks | 100 FE/t |
| redstone dust, eye of ender, basic circuit plate | Tracking Circuit | 900 ticks | 50 FE/t |
| 2 iron ingots, 2 control boards | 1 Advanced Circuit and 2 redstone torches | 20 ticks | 40 FE/t |
| 2 iron ingots, 2 control boards, tracking circuit, quartz, copper ingot | 1 comparator | 30 ticks | 50 FE/t |

**Status lines.** The same structure, diagnostic and reason lines as the Rolling Machine; the process line reads
"ASSEMBLING" while it runs and the fluid reason does not apply.

**Keeping and losing.** Port contents and progress are saved and continue after a restart. Breaking a port or the
controller drops the items it holds. If the assembler cannot tell which block owns its resources, breaking is
refused with "Removal paused: resource ownership is unresolved. Load and repair the full assembler to finish
migration; unsupported saves need a compatible version." Load the whole structure and rebuild any missing block.

## Classic multiblock machines

The lathe, electric arc furnace, cutting machine, crystallizer, chemical reactor, precision laser etcher and
centrifuge share one design. What is said here applies to all seven.

**Parts.** They use the shared hatches and plug, not machine-specific ports:

- **Power Input Plug**: stores 10,000 FE and accepts energy from any side. A machine with several plugs adds their
  energy together. Energy cannot be taken back out.
- **Item Input Hatch** and **Item Output Hatch**: four slots of up to 64 items each. Right-click an input hatch with
  a stack to insert it; right-click an output hatch with an empty hand to take one slot's contents. Hoppers and
  pipes work.
- **Fluid Input Hatch** and **Fluid Output Hatch**: 16,000 mB each. Right-click with a bucket or canister, or use
  pipes.

Hatches accept nothing until they are part of a formed machine. A hatch belongs to one machine; two controllers that
both include the same hatch show "Hatch binding conflict".

**Screen.** Right-click the controller. The screen has no slots and no buttons. It shows "Formed: true/false", a
progress bar with "Progress: n / n ticks", "Energy: n FE" (the total in the plugs) and one status line.

**Running.** A formed machine starts by itself when a hatch holds the ingredients of a recipe and the outputs have
room. A redstone signal at the controller pauses it without using items or energy. A machine that runs out of power
keeps its progress and waits.

| Status line | Meaning |
|---|---|
| "Structure incomplete" | A block is missing or wrong. Compare with the diagram. |
| "Waiting for loaded parts" | Part of the structure is in an unloaded chunk. |
| "Hatch binding conflict" | A hatch already belongs to another controller. |
| "Checking recipes", "Waiting for inputs" | No recipe matches the hatch contents yet. |
| "Input item not eligible for this recipe" | A special chemical-reactor recipe refuses this particular item; nothing is used. |
| "Output full" | Empty the output hatches. |
| "Waiting for power" | The plugs do not hold enough for the next tick. |
| "Paused by redstone" | Remove the redstone signal from the controller. |
| "Running" | Working. |
| "Disabled by configuration" | The server switched this recipe family off. |
| "Gravity above machine limit" | Crystallizer only; see its section. |
| "Recipe missing", "Recipe changed", "Recipe tags unavailable" | The server's recipes changed while a batch was running. The batch waits; nothing is lost. |
| "Resource or catalog limit" | An internal limit was reached, for example more than 64 hatches in one structure. |
| "Data needs administrator repair" | See "Needs repair" messages above. |

**Keeping and losing.** The controller saves everything the hatches hold, the running batch and its progress; the
plugs save their own energy. All of it returns after a restart or a chunk reload, and a batch continues where it
stopped. Breaking an item hatch drops that hatch's items at the controller. Breaking the controller drops every item
the machine holds; fluids held by the machine are lost. Breaking a plug loses its energy. While part of the
structure is unloaded, or while its data needs repair, the controller and its hatches cannot be broken.

### Lathe (`lathe`)

It turns ingots into rods, used for motors and structural parts. Its motor needs steel plates, so it follows the
arc furnace and the rolling machine.

```text
layer 2   layer 1
C M _ I   P K K O
```

Each recipe takes 1 ingot and makes 2 rods in 300 ticks at 20 FE per tick (6,000 FE). It works for copper, iron,
steel, titanium, iridium, titanium aluminide and titanium-iridium.

### Electric Arc Furnace (`arc_furnace`)

It makes steel, silicon, titanium and the two alloys. Steel from this furnace opens most of the later machines.

```text
layer 5     layer 4     layer 3     layer 2     layer 1
. . . . .   . B B B .   B B B B B   B S C S B   B B B B B
. P B P .   B L _ L B   B _ _ _ B   S B B B S   B B B B B
. B B B .   B _ _ _ B   B _ _ _ B   S B B B S   B B B B B
. B P B .   B _ L _ B   B _ _ _ B   S B B B S   B B B B B
. . . . .   . B B B .   B B B B B   B S S S B   B B B B B
```

`B` is Blast Brick (`blast_brick`). `L` is any coil block (copper, gold, aluminum, titanium or iridium). `S` is a
shell position that takes blast brick, machine casing, any hatch or a power input plug; put the item input and
item output hatches in `S` positions on layer 2. The three `P` positions on the top layer must be power input
plugs.

| Inputs | Output | Time | Energy |
|---|---|---|---|
| 1 iron ingot, 1 charcoal | 1 steel ingot | 6,000 ticks | 1 FE/t |
| 1 sand | 1 silicon ingot | 12,000 ticks | 1 FE/t |
| 1 rutile ore or raw rutile | 1 titanium ingot | 100 ticks | 10 FE/t |
| 7 aluminum ingots, 3 titanium ingots | 3 titanium aluminide ingots | 9,000 ticks | 20 FE/t |
| 1 iridium ingot, 1 titanium ingot | 2 titanium-iridium ingots | 3,000 ticks | 20 FE/t |

### Cutting Machine (`cutting_machine`)

It cuts silicon boules into wafers and circuit plates into circuits, and saws logs into planks.

```text
layer 1
I C O
M X P
```

`X` is the Saw Blade (`saw_blade`).

| Input | Output | Time | Energy |
|---|---|---|---|
| 1 silicon boule | 4 silicon wafers | 300 ticks | 100 FE/t |
| 1 basic circuit plate | 4 basic circuits | 300 ticks | 100 FE/t |
| 1 advanced circuit plate | 4 advanced circuits | 300 ticks | 100 FE/t |
| 1 log or stem (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, crimson, warped, lightwood) | 6 planks | 80 ticks | 10 FE/t |

The log recipes can be switched off with `machines.sawmillVanillaWood`; they then show
"Disabled by configuration".

### Crystallizer (`crystallizer`)

It grows silicon boules for circuits and dilithium crystals.

```text
layer 2   layer 1
Q Q Q     O C I
Q Q Q     G P F
```

`Q` is the Quartz Crucible (`quartz_crucible`). The two fluid hatches are part of the structure although the
packaged recipes use no fluid.

| Inputs | Output | Time | Energy |
|---|---|---|---|
| 1 silicon ingot, 1 silicon nugget | 1 silicon boule | 300 ticks | 20 FE/t |
| 1 dilithium dust | 1 dilithium crystal | 300 ticks | 20 FE/t |

The server can set `machines.crystallizerMaximumGravity`. The default 0 means no limit. With a limit, a crystallizer
on a body or station whose gravity is above it shows "Gravity above machine limit" and waits.

### Chemical Reactor (`chemical_reactor`)

It makes liquid rocket fuel, and it treats armor and scrubber cartridges.

```text
layer 2   layer 1
. C .     P M P
F I F     G O G
```

| Inputs | Output | Time | Energy |
|---|---|---|---|
| 10 mB oxygen, 10 mB hydrogen | 20 mB rocket fuel | 100 ticks | 10 FE/t |
| 1 bone, 10 mB nitrogen | 8 bone meal | 100 ticks | 1 FE/t |
| 1 helmet, leggings or boots, 1 pipe seal, 4 titanium aluminide sheets | the same armor piece with Space Breathing | 100 ticks | 10 FE/t |
| 1 chestplate, 1 pipe seal, 4 titanium aluminide sheets, 1 super high pressure tank | the same chestplate with Space Breathing | 100 ticks | 10 FE/t |
| 1 spent carbon scrubber cartridge | the same cartridge, refreshed, and 1 charcoal | 40 ticks | 20 FE/t |

The armor and cartridge recipes return the very item you put in, with its other data unchanged. They refuse an item
that does not qualify ("Input item not eligible for this recipe"): a cartridge that still has uses left, an armor
piece that cannot take the enchantment, or a pressure tank that still holds gas. The armor recipes stop when the server disables `equipment.classicEnabled`, and the cartridge
recipe when it disables `lifeSupport.classicDevicesEnabled`.

### Precision Laser Etcher (`precision_laser_etcher`)

It etches circuit plates two at a time, faster than the precision assembler but at a much higher power draw.

```text
layer 3   layer 2   layer 1
s s s     K _ K     K C I
_ s _     _ A K     P M O
s s s     K _ K     P K K
```

`s` is any slab. Both `P` positions must be power input plugs.

| Inputs | Output | Time | Energy |
|---|---|---|---|
| 1 gold plate, 1 redstone dust, 4 silicon wafers, 1 laser lens | 2 basic circuit plates | 1,200 ticks | 400 FE/t |
| 1 gold plate, 1 block of redstone, 4 silicon wafers, 1 laser lens | 2 advanced circuit plates | 1,200 ticks | 600 FE/t |

The laser lens is an ingredient and is used up with the rest. Two plugs hold 20,000 FE together, which is 50 ticks
of work at 400 FE per tick, so the etcher needs a steady supply.

### Centrifuge (`centrifuge`)

It spins enriched lava, found in Venus volcanoes, into ordinary lava and metal nuggets.

```text
layer 4   layer 3   layer 2   layer 1
_ K G     _ K G     C K G     P F G
A A K     A A K     A A K     M O K
A A .     A A G     A A G     K K G
```

The controller sits on layer 2. All seven `G` positions must be fluid output hatches.

One batch takes 1,000 mB of enriched lava for 200 ticks at 10 FE per tick and returns 1,000 mB of lava. Each batch
also rolls separately for one copper nugget (100%), one iron nugget (100%), one tin nugget (100%), one gold nugget
(75%) and one iridium nugget (1%); at most four of them are produced in one batch.

## Small Plate Press (`small_plate_press`)

**What it is for.** The first machine of the mod. It needs no power, no steel and no motor, and it makes the plates
and dusts that start everything else.

**Build.** Stack three blocks: the press on top, the block to be pressed under it, and obsidian under that. The
press always works downwards.

**Use.** Give the press a redstone pulse (a button on it is enough). On each change from unpowered to powered it
presses the block below once and leaves the product as an item where the block was.

| Block below the press | Product |
|---|---|
| a storage block of iron, copper, gold, aluminum, tin, steel, titanium, iridium, titanium aluminide or titanium-iridium | 4 plates of that metal |
| an ore of iron, copper, gold, aluminum, tin, iridium or dilithium | 2 dusts of that material |

**What goes wrong.** The press gives no message. Nothing happens when the block two below is not obsidian, when the
block below has no pressing recipe or is a container, when the signal was already on, when a protection mod refuses
the piston-like action, or when the server has switched off `classic.smallPlatePress`.

**Keeping and losing.** The press stores nothing; only whether it is currently powered is saved with the block.

## Observatory (`observatory`)

**What it is for.** Ground research without a satellite. An observatory surveys the sky of its star system; an
astrobody data processor then analyzes the survey and credits research. Both jobs cost energy for every tick of
work.

**Build.** 5 wide, 5 high, 5 deep, with clear space around the dome.

```text
layer 5     layer 4     layer 3     layer 2     layer 1
: : : : :   : : : : :   : K K K :   : S C S :   : S S S :
: K W K :   : K K K :   K _ _ _ K   S K K K S   S K K K S
: K K K :   : K W K :   K _ _ _ K   S K K K S   S K M K S
: K K K :   : K K K :   K _ W _ K   S K K K S   S K K K S
: : : : :   : : : : :   : K K K :   : S S S :   : S S S :
```

`W` is glass. `S` takes machine casing, a block of iron or a power input plug; use at least one plug there, because
that is the observatory's only power input. The motor sits in the middle of the floor. The glass block on layer 5
is the lens.

**Use.** The player who places the controller owns it, and only the owner can press its buttons. Right-click the
controller. The screen shows progress, "Energy: n FE (needs n FE/t)", the selected job ("Job: n / n"),
"Survey: … / analysis: …", "Owner: …" and a status line. When the structure is incomplete it also shows
"Missing cell: (x, y, z), needs …", which names the first wrong block.

- **Start survey** begins a survey: 1,000 ticks of work at 20 FE per tick.
- **Previous** and **Next** choose among your jobs.
- **Cancel survey** cancels the selected job.
- **Rebind here** moves a job to this observatory when its original observatory was removed.

On a planet or moon the survey advances only while the lens can see the sky, it is night and it is not raining or
snowing on the lens. In a space station it advances at any time, using the body the station orbits; you need
permission to visit that station. A survey that waits keeps its progress.

**Status lines.** "Idle", "Queued", "Running", "Complete", "Multiblock not formed", "Waiting for loaded cells",
"Insufficient power", "Lens cannot see sky", "Waiting for night", "Precipitation pauses observation",
"Body or station permission unavailable", "Star system changed", "Asteroid tables unavailable",
"Only the owner can operate", "Observatory busy", "No matching job", "Survey cancelled", "Owner record limit",
"Global capacity reached", "Storage budget reached", "Waiting for registry save", "Ground research disabled"
(the server switch `research.groundSurveysEnabled`), "Quarantined; an operator can release it",
"Copied identity quarantined; repair offline", "Registry blocked; back up and repair offline" and
"Back up and repair job/instance or device offline".

"Waiting for registry save" is normal: a paid step is only counted after the server has saved the one before it,
so work proceeds in short steps between saves.

**Keeping and losing.** Jobs, their paid progress and research credits are stored by the server, not in the block,
so they are not lost when the observatory is broken; rebuild one and use **Rebind here**. The controller saves its
owner and identity, and the plugs save their energy. Known gap: there is no automated test yet that restarts a
server in the middle of a paid survey, so treat an interrupted survey with care and report anything odd.

## Astrobody Data Processor (`astrobody_data_processor`)

**What it is for.** It turns a finished observatory survey into research.

**Build.** 3 wide, 2 high, 2 deep.

```text
layer 2   layer 1
s C s     P K K
s s s     K K K
```

`s` is any slab.

**Use.** The owner opens the screen, chooses a finished survey with **Previous** / **Next** and presses
**Claim analysis**. The analysis runs for 200 ticks at 20 FE per tick and then credits 120 research to the owner,
once per survey. **Rebind here** moves an analysis whose processor was removed. The analysis column of the state
line reads "Waiting", "Running" and finally "Credited".

**Status lines.** As for the observatory, plus "Job not ready" (the survey is not finished), "Processor busy",
"Analysis already claimed or credited", "Job is bound to another device",
"Job's device is not loaded or was removed; the owner can rebind here" and "Research account full".

**Keeping and losing.** As for the observatory.

## Combustion Generator (`combustion_generator`)

**What it is for.** The first generator. It burns anything a furnace accepts as fuel.

**Build and use.** A single block. Right-click it and put fuel in its one slot; hoppers and pipes can insert fuel
too. It generates 40 FE per tick, stores 20,000 FE and pushes up to 1,000 FE per tick in total into the energy
receivers that touch it. A fuel item is worth its furnace burn time times 40 FE, so one coal gives 64,000 FE. A lava
bucket leaves its empty bucket in the slot. When the buffer is full it stops burning and wastes nothing.

**Status lines.** The screen shows the fuel, "Burn credit: n / n ticks", the energy and one of "Generating",
"No fuel", "Buffer full", "Container blocked" (the slot has no room for the empty container of the next fuel),
"Fuel too long" (a fuel that burns longer than 1,000,000 ticks is refused), "Disabled" (the server switch
`machines.combustionGeneratorEnabled`) and "Repair required".

**Keeping and losing.** Fuel, energy and the remaining burn time are saved. Breaking the generator drops the unburned
fuel or its container; the stored energy and the burn time already started are lost.

## Solar Generator (`solar_generator`)

**What it is for.** Free, slow power from sunlight.

**Build and use.** A single block that collects through its top face. It stores 10,000 FE and pushes up to 1,000 FE
per tick into the receivers that touch it. Each tick it produces 2 FE times the sunlight intensity of the place,
rounded down, times the server's `energy.solarGeneratorMultiplier` (1 to 4). Rain and thunder each halve the
light. On a planet it works only in daytime and only when nothing blocks the sky above it; in a station it uses
the sunlight of the body the station orbits and has no night.

**Status lines.** The screen shows "Actual credit: n FE/t", the context (surface, station orbit, or
"Missing orbit: Space fallback"), day or night, "UP exposure: open" or "not confirmed open", the weather
transmission and one reason: "Collecting sunlight", "Energy buffer full", "UP face obstructed",
"No surface daylight", "Insufficient solar credit" (the place is too dim to make 1 FE),
"Context or owner unavailable", "Disabled; stored FE retained" (the server switch
`machines.solarGeneratorEnabled`) and "Data retained; back up and repair offline".

**Keeping and losing.** Only the stored energy is saved. Breaking the generator loses it.

## Solar Array (`solar_array`)

**What it is for.** The large version of the solar generator, with up to 63 panels on one controller.

**Build.** One layer, 3 wide and 22 deep. The front row is a Power Output Plug (`power_output_plug`), the
controller, and a second power output plug. Each of the 63 positions behind that row holds a Solar Array Panel
(`solar_array_panel`), machine casing or air. At least one panel is needed.

**Use.** The array produces 2 FE per tick for each panel, times the sunlight intensity and the same multiplier as
the solar generator, under the same day, sky and weather rules. Only the sky above the controller is checked. It
stores 20,000 FE, and the two plugs together push up to 1,000 FE per tick into the receivers that touch them.

**Status lines.** The screen adds "Collectors: n / 63" and a structure line: "Structure: formed",
"awaiting validation", "mismatch", "chunk unloaded", "no collectors", "output plug already claimed" (a plug
belongs to another array) or "unavailable". The other lines are those of the solar generator.

**Keeping and losing.** The stored energy is saved; the structure is checked again after loading. Breaking the
controller loses the stored energy.

## Pressurized Tank (`pressurized_tank`)

**What it is for.** Bulk storage for one fluid, including the three gases.

**Use.** A single block holding 64,000 mB (the server can scale this from 0.25 to 4 times with
`machines.tankCapacityMultiplier`). Right-click with a bucket or a canister to move exactly 1,000 mB in or out.
Right-click with anything else to read the contents: "Fluid: n / n mB" or "Empty". Pipes connect on every side. A
tank placed on top of another drains into the one below, up to 1,000 mB per tick.

**What goes wrong.** "Cannot exchange a full 1,000 mB: check capacity, fluid, metadata and inventory space." means
the tank is too full or too empty for a whole container, holds a different fluid, or your inventory has no room
for the returned container. "Unsupported tank data; back up the world and ask an administrator to repair it."
means the tank needs repair and cannot be used or broken.

**Keeping and losing.** The fluid is saved. Breaking a tank keeps the fluid inside the dropped item, and placing it
again restores it. If the server lowers the capacity below what a tank holds, the extra fluid can still be drained
but nothing more can be added.

## Fluid Pump (`pump`)

**What it is for.** It collects water, lava and other source blocks from the world.

**Use.** Place the pump above the fluid. It stores 10,000 FE and 16,000 mB. Each source block it removes costs
100 FE and gives 1,000 mB, followed by a 5-tick pause. It looks for source blocks connected to the fluid below it,
up to 32 blocks sideways and 64 blocks down, and never loads chunks to do so. It pushes up to 1,000 mB per tick
into the tanks and machines that touch it.

The pump works for the player who placed it and asks the server's protection rules in that player's name. A pump
placed by a machine has no owner and does not drain. Right-click to read "status | n FE | n mB"; when the owner or
an operator does this, the pump also retries at once.

**Status lines.** "Searching", "Source ready", "Pumped", "Cooling down", "No fluid below", "Not enough energy",
"Not enough tank space", "Incompatible fluid" (the pump already holds another fluid), "Connected search exhausted",
"Search limit reached", "Source changed", "Target protected", "Target chunk not loaded",
"Target outside world bounds", "Unsupported source adapter" (waterlogged blocks and special fluid blocks are not
pumped), "No player owner", "Disabled" (the server switch `machines.pumpEnabled`) and
"Administrator repair required".

**Keeping and losing.** The owner, energy, fluid and pause are saved. Breaking a pump keeps them in the dropped
item.

## Oxygen Vent (`oxygen_vent`)

**What it is for.** It makes a sealed room breathable in vacuum or in an unbreathable atmosphere.

**Use.** A single block placed inside the room. It stores 4,000 units of oxygen and 40,000 FE.

- Right-click with an oxygen canister to add 1,000 units ("Vent oxygen: n"); the empty canister goes back to your
  inventory. The vent refuses a canister that would not fit completely
  ("Vent cannot accept a whole oxygen canister (n)").
- Right-click with redstone dust to add 2,000 FE ("Vent energy: n FE"), or feed energy through any horizontal side.
- Right-click with anything else to read "Vent: status | O2 n | n FE".
- The vent has two item slots, one for oxygen canisters and one for empty canisters, which hoppers and pipes can
  reach.

The vent first scans the room. A room counts as sealed when it is fully enclosed and no larger than 4,096 blocks of
space; a closed [Airlock Door](#airlock-door-airlock_door) and a [Pipe Seal](#pipe-seal-pipe_seal) count as walls.
A working vent uses 20 FE per tick and one unit of oxygen every 20 ticks. When two vents share a room, one works
and the other stands by.

**Status lines.** "Scanning", "Active", "Standby (shared room)", "Room open to vacuum", "Room exceeds scan limit",
"No oxygen", "No power", "Waiting for loaded chunk", "Scanner busy", "Scan cancelled", "Invalid saved data" and
"Newer saved-data schema".

**Keeping and losing.** Oxygen, energy and the canister slots are saved. Breaking the vent drops the canisters and
any scrubber cartridges it holds; the oxygen units and energy inside it are lost. The room is scanned again after a
restart.

## CO2 Scrubber (`co2_scrubber`)

**What it is for.** It makes a vent's oxygen last longer.

**Use.** Place the scrubber directly against an oxygen vent and right-click it with a Carbon Scrubber Cartridge.
Sneak-right-click with an empty hand to take the cartridge back. One working scrubber halves the vent's oxygen use;
two or more cut it to a quarter. A vent has six sides, so it can hold at most six scrubbers, and more than two give
no further saving. Each unit of oxygen saved costs one use of one cartridge; a new cartridge has 32,766 uses. A spent cartridge is
refreshed in the [Chemical Reactor](#chemical-reactor-chemical_reactor). A scrubber does not make oxygen and does
not replace the vent's power or a sealed room.

**What goes wrong.** The scrubber itself shows no status; watch the vent. If the vent a scrubber was attached to is
gone and the scrubber cannot be removed, sneak-right-click it with an empty hand: "Released this scrubber's stale
vent binding; no item moved, and it can now be removed." If a scrubber was lost while the vent kept its cartridge,
sneak-right-click the vent with an empty hand: "Recovered an orphaned scrubber cartridge." The vent answers
"Nothing to recover…" when there is nothing to do. Scrubbers stop saving when the server disables
`lifeSupport.classicDevicesEnabled`; with `OXYGEN.scrubberRequiresCartrige` set to false they work without a
cartridge.

**Keeping and losing.** The cartridge is stored by the vent and saved there; the scrubber saves which vent it
belongs to. Take the cartridge out before you move a scrubber.

## Gas Charge Pad (`gas_charge_pad`)

**What it is for.** A small store of oxygen or hydrogen that fills and empties canisters. It holds one gas at a
time, up to 16,000 units.

**Use.** Right-click with a plain full oxygen or hydrogen canister to put it in the input slot. Once a second the
pad empties the input canister into its store, 1,000 units at a time, and leaves the empty canister in the input
slot. Right-click with an empty canister to put it in the output slot, where the pad fills it once the store holds
1,000 units. Right-click with an empty hand to take the output slot's item; sneak-right-click with an empty hand to
take the input slot's item. Each slot holds one canister. Pipes can move the gases as fluids
and the canisters as items. A redstone signal pauses the pad.

The pad does not charge a worn suit by itself, and it does not accept nitrogen or canisters that carry extra data.
When nothing can be done it shows "Insert gas to supply or an empty canister to fill. Empty hand: output;
sneak: input. No automatic suit charging."

**Keeping and losing.** The gas, its amount and both slots are saved. Breaking the pad drops the two canisters; the
stored gas is lost. The pad stops working when the server disables `lifeSupport.classicDevicesEnabled`.

## Atmosphere Detector (`atmosphere_detector`)

**What it is for.** A redstone sensor for the air around it, for alarms and automatic doors.

**Use.** A single block. It gives a full-strength redstone signal while any of the six spaces touching it matches
its mode. Right-click to read the mode: "Detector: mode (sneak-use to cycle)". Sneak-right-click to change it.

| Mode | Signal when the neighbouring space is |
|---|---|
| Breathable | breathable air, including a room supplied by a vent |
| Vacuum | not breathable and without pressure |
| Non-breathable atmosphere | not breathable but with pressure |
| High pressure | under high pressure |
| Hot | hotter than 330 K |
| Cold | colder than 240 K |

Inside a room supplied by a vent only the Breathable mode matches. The detector measures; it does not seal or
supply a room.

**Keeping and losing.** The mode is saved.

## Pipe Seal (`pipe_seal`)

**What it is for.** It lets pipes pass through the wall of a sealed room without a leak.

**Use.** Place it as a block of the wall. It counts as a solid wall for the oxygen vent. A pipe or machine on one
face reaches the items, fluids and energy of the block on the opposite face, as if the seal were not there. It
shows at most eight item slots of the block behind it. Two seals in a row do not pass anything on.

**Keeping and losing.** The seal stores nothing. It passes nothing when the server disables
`lifeSupport.classicDevicesEnabled`, but it still seals.

## Airlock Door (`airlock_door`)

**What it is for.** A door for sealed rooms.

**Use.** It behaves like an iron door: it opens with redstone, not by hand. Closed, it is a wall for the oxygen
vent; open, the room behind it is connected to whatever is on the other side. Opening or closing it makes the vent
check the room again, so use two doors with a small chamber between them to enter without losing the room. It
cannot be placed when the server has disabled the classic life-support devices.

**Keeping and losing.** Only the door's open and powered state is saved, as for any door.

## Suit Workstation (`suit_workstation`)

**What it is for.** It installs upgrade modules in space-suit pieces and takes them out again.

**Use.** Right-click the workstation. Put one suit piece in the left slot and a module in the right slot, then press
**Install**. To take a module out, use the "-" button beside it: "-: remove; Shift+-: stow". Remove puts the module
in the right slot, which must be empty; stow puts it in a free slot of your inventory. A removed pressure tank keeps
the gas it holds. Installing is refused while the server has disabled `equipment.classicEnabled`; removing still
works.

| Suit piece | Modules |
|---|---|
| Helmet | anti-fog visor, earthbright visor, hover upgrade, beacon finder |
| Chestplate | jetpack, pressure tanks |
| Leggings | flight speed upgrade, bionic leg upgrade |
| Boots | padded landing boots |

A module that does not fit the piece is not installed and stays in its slot.

**Keeping and losing.** Both slots are saved with their full item data. Breaking the workstation drops them.

## Rocket Assembler (`rocket_assembler`)

**What it is for.** It turns a rocket built from blocks into a rocket you can fly.

**Use.** Build the rocket directly above the assembler: at least one rocket motor, a fuel tank that suits the
motor, one guidance computer and at least one seat. The [propulsion guide](rocket-propulsion.md) explains the
motor and tank combinations.

- **Right-click** scans the structure ("Scanning the loaded structure above the assembler") and reports
  "blocks n, mass n, thrust n, fuel n, seats n" or the reason the structure is not valid.
- **Sneak-right-click** assembles it: the blocks are replaced by the rocket.

Holding a block makes a right-click place that block instead. To take a rocket apart, sneak-right-click the rocket
itself with an empty hand; remaining fuel has to be confirmed as discarded.

**What goes wrong.** The most common messages are "No movable structure found", "Rocket requires at least one
motor", "Rocket requires one guidance computer", "Rocket requires at least one seat", "Rocket thrust is below its
mass", "This mode requires matching fuel and, for bipropellant, oxidizer tanks.", "Host engines select
incompatible propulsion modes.", "Rocket contains a forbidden block", "Rocket exceeds the block limit" (2,048
blocks), "Rocket bounding volume is too large", "Rocket contains too many block entities" (128),
"Rocket touches an unloaded chunk", "Rocket region is busy", "World changed after validation" (a block changed
between the check and the assembly; try again) and "Player is not authorized for this rocket".

**Keeping and losing.** The assembler saves only its last scan report. The rocket is a separate object with its own
saved data; breaking the assembler does not affect an assembled rocket.

## Rocket Satellite Bay (`rocket_satellite_bay`)

**What it is for.** It carries a satellite package on a rocket and releases it in orbit. It does not build
satellites; see the [Satellite Builder](#satellite-builder-satellite_builder).

**Use.** Build the bay into the rocket before assembly. The player who places it owns it. Open it from within
8 blocks and put one satellite package inside. The bay, the package and the rocket must have the same owner, and
the owner must be the one who launches. A rocket deploys from at most 8 bays per flight. During the flight the bay
registers the satellite around the body the rocket launched from, and the screen shows "Deployment phase: None",
"Deploying", "Registered", "Refused" or "Needs offline repair".

**What goes wrong.** A refused deployment keeps the package in the bay, and the screen names the reason, for
example "The bay, the package and the rocket must have the same owner.", "The package's owner did not launch this
rocket.", "The satellite cannot orbit the body this rocket launched from.", "This satellite needs more research
before it can launch.", "Your satellite limit is reached.", "This rocket was filled through a resource loader, so
its bays cannot deploy.", "The server stopped before the deployment finished; the next flight retries it." and
"Satellite deployment is not available in this version." The last one means this server does not deploy from
bays at all; use the [Satellite Mission Terminal](#satellite-mission-terminal-satellite_terminal) instead.

"Only the bay's owner or an operator can open it, from within 8 blocks." and "A rocket assembly or disassembly
holds this bay; wait for it to finish." are shown when the bay cannot be opened.

**Keeping and losing.** The owner and the package are saved. Breaking a bay drops the bay and its package as two
items; a bay whose package was deployed drops only the bay. A bay whose data needs repair, or one that an assembly
is using, cannot be broken.

## Satellite Builder (`satellite_builder`)

**What it is for.** It builds any of the five satellite kinds as a package, ready for a rocket's satellite bay.

**Use.** A single block with 10,000 FE of storage. Right-click it and fill the slots: one chassis, one primary
module (it decides the kind), up to six further modules and one blank satellite control chip. The package slot must
be empty. Redstone dust in the charge slot becomes 2,000 FE each. The screen shows the kind, "PWR n BAT n DATA n
CARGO n RATING n" for the parts you inserted and a status line. Press **ASSEMBLE**: it costs 1,000 FE, uses up the
parts, binds the chip to the new satellite and puts the package in the output slot. Keep the chip; it controls
the satellite later.

**Status lines.** "Insert a chassis and a primary module", "Builder ready", "Satellite ready",
"Insert chassis, solar module, data storage, and blank chip" (the parts do not make a valid satellite; the builder
reuses the terminal's wording), "Terminal needs more power" (the builder's own buffer is meant),
"Clear the satellite package bay", "More lifetime research is required", "Satellite definitions are unavailable",
"Satellite definition was not found", "Please wait before the next request" and
"Satellite data is unsupported or invalid". A long status line is cut off at the top of the screen; hover over it to
read all of it (known issue KI-V180-UI-01).

**Keeping and losing.** The slots, the energy and the last result are saved. Known gap: no automated test yet
restores a builder and then completes a build, so check the slots after a restart before you press **ASSEMBLE**.

## Satellite Mission Terminal (`satellite_terminal`)

**What it is for.** It assembles and launches the basic data satellite, runs satellite missions and collects their
results. The [discovery guide](PLANETARY-DISCOVERY-GUIDE.md) walks through researching a planet with it.

**Use.** A single block with 10,000 FE of storage; redstone dust in the charge slot becomes 2,000 FE each.

- **ASSEMBLE** needs a chassis, a satellite solar module, a data storage unit and a blank control chip, and
  1,000 FE. It binds the chip and makes a package.
- **LAUNCH** sends the package to the selected target; the terminal needs at least 2,000 FE.
- With the bound chip in its slot the terminal shows the satellite ("AWAITING LAUNCH" or "ORBITING body") and its
  mission: "MISSION IDLE", "ACTIVE n s", "DATA READY", "CLAIMED" or "CANCELLED". **CLAIM** collects a finished
  mission, **CANCEL** stops one, and **RETIRE** decommissions an idle satellite and blanks its chip, with nothing
  refunded. **UNLINK** clears a solar satellite's link when its receiver is gone.
- The **DELIVERY** page lists the resource missions bound to this terminal and lets you **WITHDRAW** their
  products.

A terminal has one owner; other players get "This terminal or satellite belongs to another player".

**Status lines.** Common ones: "Terminal ready / operation completed", "Insert chassis, solar module, data storage,
and blank chip", "Terminal needs more power", "Clear the satellite package bay", "Selected target is not allowed",
"More lifetime research is required", "Satellite already has an unfinished mission", "Mission result is not
ready", "Mission result was already claimed", "Terminal reward buffer is full", "This mission is bound to another
terminal", "This terminal or satellite belongs to another player", "Move closer to the loaded terminal",
"Please wait before the next request", "Waiting for the next world save", "Your satellite or mission limit is
reached" and "Offline repair required; back up the world and ask an administrator".

**Keeping and losing.** The slots, energy, selected target, owner and delivery state are saved. Satellites and
missions are stored by the server and continue while the terminal is unloaded; keep the chip to reach them.

## Microwave Receiver (`microwave_receiver`)

**What it is for.** It receives the power beamed down by your solar satellites.

**Use.** A single block with four chip slots and a 100,000 FE buffer. Put the bound control chip of a solar
satellite in a slot. Once a second the receiver checks its links and adds what they deliver, and it pushes the
energy into the receivers that touch it. The screen shows "OUTPUT n FE/t" and, for each slot, "Empty slot",
"Linked: this receiver holds the link", "Linked to another receiver" or "Satellite missing or not a solar
satellite". A satellite powers one receiver at a time. Nothing is collected while the receiver's chunk is
unloaded.

**Keeping and losing.** The chips, the buffer and the receiver's identity are saved. If a receiver is lost while it
holds a link, use **UNLINK** at the satellite terminal to free the satellite. Known gap: no automated test yet
restores a linked receiver and checks that power continues, so look at the link lines after a restart.

## Planetary Beacon (`planetary_beacon`)

**What it is for.** A landmark you can find again. A Beacon Finder, held or installed in a suit helmet, shows the
direction and distance of your active beacons on the same body.

**Build.** No power is needed.

```text
layer 5   layer 4   layer 3   layer 2   layer 1
. . .     . . .     . . .     . . .     K C K
. R .     . K .     . K .     . K .     K K K
. . .     . . .     . . .     . . .     K K K
```

`R` is a block of redstone. The base is 3 by 3 with the controller in the middle of the front; the column stands on
the centre of the base.

**Use.** The player who places the controller owns the beacon; only the owner or an operator can use it.

- Right-click with an empty hand to switch it on or off.
- Sneak-right-click with an empty hand to read its status.
- Right-click with a renamed name tag to name the beacon (at most 32 characters). The name tag is not used up.

**Status lines.** "Beacon active.", "Beacon switched off.", "Beacon checking its structure.", "Beacon structure
incomplete: a 3 x 3 casing base with the controller at the front, a casing column of three and a redstone block on
top, with clear space around the column.", "Part of the beacon structure is not loaded.", "Beacon limit reached
(32 per owner, 256 per body).", "This position has no known celestial body; the beacon waits.", "This beacon has
no owner and cannot activate.", "This beacon belongs to another player." and "Another copy of this beacon is
registered elsewhere."

**Keeping and losing.** The owner, name and on/off state are saved with the controller, and the server keeps the
list of active beacons. Breaking the controller removes the beacon from that list.

## Force Field Projector (`force_field_projector`)

**What it is for.** A wall of force-field blocks that appears on a redstone signal, for doors, hangars and
airlocks. Force-field blocks seal a room like solid blocks.

**Use.** The projector's face points at you as you place it, in any of the six directions, and the field leaves
through that face, so stand where the field should go. You become its owner. While the projector receives a
redstone signal it extends a straight line of force-field blocks from its face, one block at a time, up to
32 blocks. Without a signal the line is taken back. The field does not enter protected areas or unloaded chunks.

**What goes wrong.** The projector has no screen and no messages. An ownerless projector, for example one placed by
a machine, does nothing until an operator right-clicks it to bind it; a player who is not an operator gets no
message when trying (known issue KI-V180-UI-01). The server can refuse to place a projector, for example when its
list of fields is full; the block then does not appear. The server can switch all projectors off with `forceField.enabled` in
`advancedrocketrycommunity-force-field.toml`.

**Keeping and losing.** The owner, direction and the blocks the projector has placed are saved, and the server
keeps a list for cleaning up fields. Breaking the projector removes its field blocks. Force-field blocks are not
items and cannot be collected.

## Warp Core (`warp_core`)

**What it is for.** It stores the energy a space station needs to warp to another body.

**Use.** Place one or more warp cores inside your station and feed them FE. The energy belongs to the station, not
to the block: every core of a station fills the same store, which holds up to 10,000,000 FE and accepts up to
200,000 FE per tick. Energy cannot be taken back out. With default settings a warp inside one star system costs
2,000,000 FE and an interstellar warp 8,000,000 FE. A core has no screen; read the balance at a
[Warp Controller](#warp-controller-warp_controller) or with the commands in the
[classic advancements guide](CLASSIC-ADVANCEMENTS-GUIDE.md).

**Keeping and losing.** The station's warp energy, orbit and any running countdown are saved by the server with the
station, so breaking or moving a core does not lose energy. Known gap: no automated test yet restarts a server in
the middle of charging or of a countdown.

## Warp Controller (`warp_controller`)

**What it is for.** The station's warp console: choose a destination, see the price, confirm and watch the
countdown.

**Use.** Place it in the station with a loaded warp core of the same station within 5 blocks, and stand within
8 blocks of it, inside the station. Only the station's owner or an operator can warp. The screen shows
"Orbit: body", "Target: body", "Warp energy: n FE (pending n FE)" and "Cost: n FE, in-system/interstellar".

1. Choose the target with **<** and **>**. The target must be a discovered body that can be orbited.
2. Press **Quote** to get the price.
3. Press **Confirm** within 10 seconds. A 10-second countdown starts ("Warp in n s").
4. **Cancel** stops the countdown. When it ends, the station orbits the new body. The station's blocks do not move.

**What goes wrong.** "Place a loaded warp core of this station within 5 blocks of the controller.", "The station's
warp energy does not cover the cost.", "The station already orbits that body.", "The target body has not been
discovered.", "The target body is missing or cannot be orbited.", "A rocket that can still move uses this station,
or rocket state is not yet known; wait until it has landed or left.", "A space elevator is bound to this station or
a ride is pending; unbind it first.", "The station changed moments ago; wait 5 seconds.", "The warp cost or its
class changed; request the warp again.", "The confirmation expired…", "Only the owner or an operator standing in
the station can manage it.", "Stand within 8 blocks of the loaded controller, inside its station." and "Station
warp is disabled on this server." (the server switch `stations.warpEnabled`). The warning "The target orbit has no
rocket routes; rockets cannot reach or leave the station in that orbit." is not a refusal: think before you
confirm.

The countdown on the screen does not tick down while the screen stays open; close and reopen it to see the current
value (known issue KI-V180-UI-01).

**Keeping and losing.** The controller stores nothing; everything belongs to the station. Breaking it changes
nothing.

## Station Gravity Controller (`station_gravity_controller`)

**What it is for.** It sets the gravity inside your space station.

**Use.** Place it in the station and open it from within 8 blocks, inside the station. The screen shows
"Gravity: n%" and four buttons: **-10**, **-1**, **+1** and **+10** percent. Only the owner or an operator can
change the value; visitors who may enter the station can read it.

**What goes wrong.** "One control change every half second; try again.", "The station's gravity or orbit was
changed moments ago; wait 5 seconds.", "The station already uses that gravity.", "Only the owner or an operator
standing in the station can manage it.", "Not in a station you can view" and "The station registry is saving
another change; try again in a few seconds."

**Keeping and losing.** The gravity belongs to the station and is saved with it. The controller stores nothing;
breaking it changes nothing.

## Station Altitude Controller (`station_altitude_controller`)

**What it is for.** It sets how far from its body the station orbits.

**Use.** As for the gravity controller. The screen shows "Orbital distance: n (4..190)", "Altitude: n km" and
whether the orbit is geostationary. The buttons change the distance by -10, -1, +1 and +10; **GEO (179)** jumps to
the geostationary distance.

**What goes wrong.** The same messages as the gravity controller, with "The station already uses that orbital
distance." A station with a bound space elevator refuses orbit changes: "A space elevator is bound to this station
or a ride is pending; unbind it first."

**Keeping and losing.** The distance belongs to the station and is saved with it.

## Station Orientation Controller (`station_orientation_controller`)

**What it is for.** It turns the station, or makes it rotate, which changes how the sky moves around it.

**Use.** As for the gravity controller. For each of the three axes, East, Up and North, the screen shows
"axis: rate n, phase n°". Each axis has rate buttons that change its rotation rate by -10, -1, +1 and +10, and
**-15°** and **+15°** buttons that turn a station that is not rotating by 15 degrees about that axis.
**Stop rotation** stops the rotation.

**What goes wrong.** "Stop the rotation before choosing a fixed orientation.", "The station already uses that
orientation.", "The station orbital clock is exhausted; only stopping the rotation is possible." and the messages
of the gravity controller.

**Keeping and losing.** The orientation and rotation belong to the station and are saved with it.

## Landing Pad (`landing_pad`)

**What it is for.** A named arrival point for rockets in a space station, so that a rocket lands where you want
it.

**Use.** Build a flat, solid floor large enough for the whole rocket, with clear space above it, and place the
landing pad block in it. The station's owner or an operator registers the pad by right-clicking it; holding a
renamed item gives the pad that item's name, otherwise it is called "Landing Pad" ("Registered pad name …").
Sneak-right-click unregisters it ("Landing pad unregistered."). The commands `/arcepad register <name>` and
`/arcepad remove` do the same for the pad you are looking at. A traveller runs `/arcepad list <station>` for a
station they may visit, clicks an entry to select that pad, then opens the rocket's flight console and launches
("Selected name. Open the nearby rocket flight console and launch.").

**What goes wrong.** "Refused: station owner/operator, a valid name and an available pad slot are required.",
"Pad unavailable, unloaded, or interaction rate exceeded." and, when a rocket tries to land, "Landing pad
refused: reason" with one of: "Pad occupied by another rocket.", "Pad claimed by another transfer.", "Landing
clearance is obstructed.", "Pad too small: every footprint column needs solid support.", "Landing footprint
includes an unloaded chunk.", "Landing leaves station, build-height or world-border bounds." or "Pad unloaded,
removed, stale or no longer owned by this station."

**Keeping and losing.** The pad block saves its identity and its station; the server keeps the list of registered
pads. Breaking the block unregisters the pad.

## Devices described in other guides

| Device | Registry ID | Guide |
|---|---|---|
| Rocket Fuel Loader | `fuel_loader` | [Rocket propulsion and fueling](rocket-propulsion.md) |
| Fluid Fuel Loader | `fluid_fuel_loader` | [Rocket propulsion and fueling](rocket-propulsion.md) |
| Rocket Fluid Loader | `rocket_fluid_loader` | [Rocket propulsion and fueling](rocket-propulsion.md) |
| Rocket Fluid Unloader | `rocket_fluid_unloader` | [Rocket propulsion and fueling](rocket-propulsion.md) |
| Rocket Item Loader | `rocket_item_loader` | [Rocket propulsion and fueling](rocket-propulsion.md) |
| Rocket Item Unloader | `rocket_item_unloader` | [Rocket propulsion and fueling](rocket-propulsion.md) |
| Rocket Monitoring Station | `rocket_monitoring_station` | [Rocket propulsion and fueling](rocket-propulsion.md) |
| Orbital Laser Drill | `orbital_laser_drill` | [Endgame operator guide](ENDGAME-OPERATOR-GUIDE.md) |
| Laser Target | `laser_target` | [Endgame operator guide](ENDGAME-OPERATOR-GUIDE.md) |
| Gravity Field Controller | `gravity_field_controller` | [Endgame operator guide](ENDGAME-OPERATOR-GUIDE.md) |
| Black Hole Generator | `black_hole_generator` | [Endgame operator guide](ENDGAME-OPERATOR-GUIDE.md) |
| Railgun Cargo Launcher | `railgun` | [Endgame operator guide](ENDGAME-OPERATOR-GUIDE.md) |
| Space Elevator Anchor | `elevator_anchor` | [Endgame operator guide](ENDGAME-OPERATOR-GUIDE.md) |
| Space Elevator Terminal | `elevator_terminal` | [Endgame operator guide](ENDGAME-OPERATOR-GUIDE.md) |
