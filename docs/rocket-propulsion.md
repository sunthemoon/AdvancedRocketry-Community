# Rocket propulsion and fueling

Basic rockets keep their original mass, thrust and item-fuel behavior. Advanced
mono rockets use the existing mono fuel tanks. Bipropellant rockets need both
bipropellant fuel tanks and oxidizer tanks. Nuclear nozzles face down and need a
contiguous column of nuclear cores directly above a nozzle, plus working-fluid
tanks. Host engines from different modes cannot be assembled together.

New host-only mono tanks select abstract item fuel or actual Fluid on the first
fill. A nonempty bank cannot mix or convert these branches. The item fuel loader
still transfers its captured abstract fuel at 25 units per tick. It cannot fill
bipropellant, oxidizer or working-fluid tanks. Existing assembled rockets never
gain Fluid content by reloading an old save.

Use a filled bucket or gas canister on an owned, stationary rocket to transfer one
whole 1,000 mB container. Empty containers unload actual Fluid, preferring the
primary bank and then oxidizer. The container and its metadata are exchanged
without dropping a remainder; insufficient space or capacity leaves both intact.
Tagged Fluid that cannot be losslessly represented in a container cannot be
unloaded into it. Creative mode uses the same exact container exchange as survival.
Sneak-use with an empty hand requests disassembly. Any remaining propulsion
resource, including oxidizer alone, requires explicit one-use confirmation.
Disassembly disposes of the complete confirmed propulsion vector only after
successful block restoration; it does not dispose of ordinary cargo Fluid.

External ADR-026 engine/capacity components remain abstract-only. They may join
basic/advanced mono hardware, but the resulting rocket offers no Fluid branch.
They cannot join bipropellant/nuclear hardware or typed tanks. External components
with zero thrust and capacity may still supply structure, seats or guidance.
Explicit definitions for new host IDs take precedence over host classification.

## Server configuration

The COMMON `ROCKET` section captures `thrustMultiplier`, `fuelCapacityMultiplier`
and `nuclearCoreThrustRatio` at new assembly. Each is finite and bounded to 0.1..4.0.
Basic/external metrics and old captures remain unchanged. Advanced host thrust is
rounded after aggregate tier scaling; capacity is scaled per matching bank, and
the combined capacity cannot exceed 2,048,000. Nuclear connected-core support is
applied before the global thrust multiplier. Each core's scaled integer support
rating is captured before summing connected cores. Partial support reduces its working
fluid coefficient by the same support fraction.

`gravityAffectsFuels` omits only the gravity term from the route-base cost.
`rocketsRequireFuel` waives only the resource debit, not discovery, thrust,
ownership, passenger or landing checks. `canBeFueledByHand` controls direct
container-to-rocket exchange; it does not disable installed item loaders. These
switches and the four Fluid allowlists are also captured at assembly.

`rocketFuels`, `rocketBipropellants`, `rocketOxidizers` and
`rocketNuclearWorkingFluids` are canonical, namespace-qualified source Fluid ID
allowlists, with at most 64 entries across all roles. Each allowed Fluid needs a
definition in `data/<namespace>/rocket_fluid_fuels/<name>.json`:

```json
{"schema":1,"role":"MONO","fluid":"advancedrocketrycommunity:rocket_fuel","numerator":2,"denominator":1}
```

Roles are `MONO`, `BIPROPELLANT_FUEL`, `OXIDIZER` and `WORKING_FLUID`. The integer
efficiency numerator and denominator are each 1..1,000. Definitions are at most
8 KiB, no more than 64 are loaded, IDs are at most 128 characters, and unknown
fields, flowing/unknown Fluids or duplicate role/Fluid pairs reject the generation.
Reload retains the last valid complete catalog on failure. The defaults are rocket
fuel at 2/1 for mono, hydrogen at 1/1 for bipropellant and nuclear, and oxygen at
1/1 for oxidizer. The accepted flight plan freezes both debits and catalog signature;
later reloads cannot reinterpret a flight already in progress.

The navigation fuel table currently shows route-base cost, not oxidizer or the
efficiency-adjusted Fluid debit. The opening message identifies the mode and exact
bank contents. Never interpret primary fuel alone as bipropellant launch authority.
