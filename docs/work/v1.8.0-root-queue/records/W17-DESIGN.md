# W17 solar array persistence and formation design

This implementer owns the solar array runtime under `machine/solar`, its screen, its
registry entries, its generated resources and strings, its tests, the matching log
expectation, and the W17 records. It does not change the ledger, status files, build
scripts or other slices. ADR-065 sections 2, 9 and 11 govern.

## Layout and formation

The array is one layer, three wide and 22 deep. The front row holds a power output
plug, the controller and a second plug. The 63 cells behind it accept a
`solar_array_panel`, AIR or any block in the data-driven tag
`advancedrocketrycommunity:solar_array_cells` (the panel and `machine_casing` by
default). Each panel counts as one collector, and at least one is required. The
controller's horizontal facing selects one of four rotations; there is no mirroring.

The v1.2 pattern schema limits every axis to 16 cells, so a datapack definition cannot
express the 22-deep layout. Widening that shared limit would change an accepted kernel
bound. The layout is therefore a fixed list of 66 cells in Java. It is evaluated with
the kernel's own `PatternMatcher` set, `PatternTransform`, and the loaded-only
`ServerLevelPatternWorldView`. Each revalidation inspects at most 66 cells and never
loads a chunk; an unloaded cell takes precedence over a mismatch, as in the kernel
validator.

A Level capability owns each Level's controller map, a `MultiblockFootprintIndex` and a
`MultiblockDirtyQueue`, with the classic machines' budget of 32 controllers and 8,192
cells per tick. Block placement, breaking, neighbor notifications, explosions, chunk
load and unload, and tag reloads re-queue the affected controllers. Nothing scans
every tick. Formation is runtime state; a loaded controller generates nothing until
its first validation.

Plugs store no energy and no saved binding. A formed controller gives each of its two
loaded plugs a runtime token. A plug's ENERGY capability resolves its controller
through an already loaded chunk and checks the token on every call. A plug that is
already bound to another live, formed array makes the second array report a conflict.
Array resources live only in the controller.

## Generation and buffer

Generation reuses `SolarExposure.read` with the controller as owner and the single
generator's accounting. `SolarGeneration.tick` gains a collector count and a capacity;
the single generator keeps calling it with one collector and 10,000 FE. The request is
`floor(2 * collectors * multiplier * intensity)`, credited only up to the room left in
the 20,000 FE buffer. A full buffer stops credit; nothing wraps or goes negative.

On a surface, the controller top must see the sky in server daytime, and rain and
thunder use the shared attenuation function. In a station, the orbit body's intensity
applies, and the exposure face is the controller's UP face, because all four
rotations keep the layout horizontal. A missing orbit body shows the Space-fallback
context. The modern warp commit is synchronous within one server tick. Generation
continues during the countdown, and the next tick reads the committed orbit. This is
the single generator's policy, and no penalty is added.

The two plugs share one 1,000 FE/tick allowance for push and pull across all their
faces. The controller exposes no ENERGY capability itself. The disable switch stops
generation and output and keeps the stored energy.

## Native root and refusal

The controller's `arce_solar_array` root is schema 1 with exactly two native ints,
`schema` and `energy` (0 to 20,000). It reuses the single generator's 4,096-byte,
depth-16 and 1,024-node preflight. A bounded but unsupported root is kept verbatim;
generation, output and ordinary removal stop until offline repair. A non-admissible
root keeps its original Tag reference and triggers the existing whole-chunk save guard
in `SolarProtection`. That guard is the same R-021 mechanism the single generator
uses. Its scope is documented in a W17 note in ADR-065 and in a risk-register
supplement, which are committed before the protection code. Ordinary breaking destroys
the stored energy, and drops carry no energy.

## Not in this slice

The beacon (W18), the satellite bay, the orbital solar transmitter, status files and
the ledger are out of scope.
