# PERFORMANCE — v0.4.0

## Budgets

The sealed-volume scheduler has both a per-task inspection cap and a global
per-Level tick budget. A scan stops at the configured total cell limit, marks
unloaded boundaries `PENDING`, and never loads a chunk to finish a room.

## Forge GameTest pressure scenario

The 16-Vent test ran 6,000 simulated ticks. All 16 providers remained active,
the peak tick inspected 102 cells, and the final measured tick inspected zero
cells after the volumes stabilized. All 25 GameTests passed.

```text
ARCE_ATMOSPHERE_PERF vents=16 simulated_ticks=6000 active=16 peak_inspections=102 measured_inspections=0 elapsed_seconds=38.266
```

## Packaged dedicated-server soak

| Field | Result |
|---|---:|
| Environment | Windows 11, Java 17.0.8, Forge 47.4.10, `-Xms512M -Xmx1024M` |
| Scenario | 16 isolated powered Vents |
| Duration | 300 seconds |
| Samples | 60 at five-second intervals |
| TPS average / minimum | 20.0 / 20.0 |
| Mean tick average / p95 / maximum | 1.281 / 1.440 / 11.182 ms |
| Maximum process RSS | 1,483,173,888 bytes |
| Full GC during sample | 0 |

The machine-readable record is
[`evidence/atmosphere-server/summary.json`](evidence/atmosphere-server/summary.json).
This result is a bounded milestone test, not a claim about arbitrary modpacks
or unlimited base sizes.
