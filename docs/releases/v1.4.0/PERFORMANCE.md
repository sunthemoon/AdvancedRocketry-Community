# v1.4 bounded work and unmeasured performance

No reference-hardware performance or long-load acceptance is claimed. The short
checks use a local Windows/Java 17 development environment. Build duration,
startup delays and a passing finite test are not MSPT/FPS/latency budgets.

| Area | Implemented bound / finite evidence | Measurement still needed |
|---|---|---|
| Definitions/reload | 128 bodies, 512 routes; body 32768 and route 4096 raw bytes/file; JSON depth 16; bounded diagnostics | Candidate reload allocation/latency and malformed input cost |
| Route search/cache | 256 anchors, 64 outgoing edges, 1024 expansions, 256 cached plans | Representative hit/miss/tick/network cost and invalidation under load |
| Synthetic catalogs | DATA-02 final 100-body/101-route case; NAV deterministic 100/128-node layout | End-to-end 100-body workload, UI/GPU and reference-node measurements |
| Landing | Eight candidates; per-candidate chunk/column work is bounded, no terrain scan for quotes | Real worldgen/memory cost, unsuccessful and concurrent arrivals |
| Display synchronization | Generation-aware summaries and rate-limited full refresh | Actual two-client bandwidth, stale/reopen and reconnect behavior |
| Sky | Cached geometry; bounded profiles (128 files, 16 KiB each); no per-frame resource rebuild | At least two real GPU categories, shader compatibility, allocation/frame pacing |
| Ambience | At most one non-looping sound, at least 400 unpaused ticks between starts | Real audio behavior and rapid transition checks |
| Discovery replay | At most eight receipts per tick; queue capped by 8192 mission records, lifecycle cleanup | Worst-case disk-force cost and tick latency, especially unavailable storage |
| Persisted identities | 128 retained body bindings/discoveries, including removed IDs | Operator capacity planning; no automatic eviction |

The eight-receipt limit counts work; synchronous persistence can still consume
significant tick time. It is not a measured millisecond ceiling. Separate fixed
worlds also incur native chunk/worldgen costs. Earlier finite logs retain timing
warnings and failures; [TEST-REPORT](TEST-REPORT.md) attributes them by run.

Candidate measurements must use [quality budgets](../../17-V1PLUS-QUALITY-BUDGETS.md)
and retain hardware, configuration, workload, percentiles, raw samples and
artifact identities. The deferred full campaign follows ADR-018 after original
machinery/dimensions are implemented; completion of v1.4 alone does not trigger
or satisfy it. No budgets or timeout assertions are relaxed by this handoff.
