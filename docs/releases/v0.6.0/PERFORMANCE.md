# PERFORMANCE — v0.6.0

## Measured workloads

- The unchanged atmosphere workload ran 16 active vents for exactly 6,000
  active ticks. Peak inspection work was 112, below the fixed 1,024
  level-inspection budget; all 39 GameTests passed under the original timeout.
- The packaged candidate completed 40 flight legs and eight restart cases with
  no restart failure, authority duplication, material loss, or fuel mismatch.

## Fixed v0.6 limits

| Boundary | Limit |
|---|---:|
| Active transfer journal entries | 64 |
| Entity matches inspected per recovery | 64 |
| Passengers per rocket | 16 |
| Accepted intents per player / 20 ticks | 8 |
| Landing pad candidates | 8 |
| Landing chunks | 16 |
| Landing block inspections | 2,048 |
| Flight data NBT | 65,536 bytes |

Destination selection inspects server-owned, already loaded areas only. No
client request can trigger an arbitrary chunk load. Detailed structured values
are in [`evidence/performance/summary.json`](evidence/performance/summary.json).
