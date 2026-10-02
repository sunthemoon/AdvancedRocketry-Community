# v1.7.0 requirement map (development)

Maps the [v1.7 plan](../../versions/V1.7.0-ENDGAME-SYSTEMS.md) checklists (sections 9–14) to
the retained development evidence. "Open" means no evidence yet. Nothing here is
candidate-bound, and nothing is a Gate approval. The contract-level map is
[v1.7.0-contract-coverage](../../work/v1.7.0-contract-coverage.md).

Packets are under `docs/work/`. "C13" is the
[C13 closure](../../work/v1.7.0-c13-closure/VERIFICATION.md); the slice packets are
[C11a](../../work/v1.7.0-c11a-framework/VERIFICATION.md),
[C11b](../../work/v1.7.0-c11b-laser-logical/VERIFICATION.md),
[C11c](../../work/v1.7.0-c11c-laser-physical/VERIFICATION.md),
[C11d](../../work/v1.7.0-c11d-gravity-field/VERIFICATION.md),
[C12a](../../work/v1.7.0-c12a-black-hole/VERIFICATION.md),
[C12b](../../work/v1.7.0-c12b-transit-ledger/VERIFICATION.md),
[C12c](../../work/v1.7.0-c12c-railgun/VERIFICATION.md) and
[C12d](../../work/v1.7.0-c12d-space-elevator/VERIFICATION.md), with the review packets
[C11 review](../../work/v1.7.0-c11-review/VERIFICATION.md) and
[C12 review](../../work/v1.7.0-c12-review/VERIFICATION.md).

## Section 9: automated tests

| Plan item | Evidence | State |
|---|---|---|
| Permission, protection and range matrix | The ADR-054 §3 authority rules and §5 protection chain (C11a); laser, field, railgun and elevator access GameTests; zones, spawn protection, claims through break events and the API event (C11a–C12d); a zone judges a ride by its departing owner (C12 review) | Automated |
| Atomic cost and effect | The same-store rule in every device; laser debt and credit on the engine (C11c, C11 review); railgun and elevator escrow through the ledger (C12b–C12d); generator burn and buffer (C12a) | Automated |
| Crash recovery at every stage | Ledger reference vectors and every named cut (C12b); laser layer cuts (C11c); native: twelve ledger cuts over three railgun pairs, a record pruned while its source was unloaded, an elevator cargo cut and three ride forced stops (C13); [recovery matrix](RECOVERY-MATRIX.md) | Automated and native |
| Target unloaded, removed, other Level | An unloaded laser target stops the drill without loading it (C11c); railgun routes wait for an unloaded destination and end at the star system (C12c); endpoint removal settlement and retirement (C12b); the elevator arrival ticket (C12d) | Automated |
| Config disable and restart | Every system switch: new work refused, settlement and arrivals kept (GameTest switch batches); CommonConfigTest; native restarts (C13) | Automated and native |
| Queue caps and rate limits | Per-tick caps (laser operations and layers, launches, structure validations, registrations, reconciliations, arrivals); intent rate limits; barrier spacing (C12 review); ledger round robin (C12 review) | Automated |
| Malformed or malicious packets | No new client-to-server message: menus send button IDs only, and the server re-derives every intent (distance, Level, owner, rate) (C11a; intent GameTests) | Automated |

## Section 10: dedicated server, restart and multiplayer

| Plan item | Evidence | State |
|---|---|---|
| Players contending for one target | One drill per link and generation (C11 review); the ledger pays one destination per transfer (C12b); spaced binds (C12 review) | Automated; S2 with real players open |
| Non-owner, non-operator overreach | Strangers and members refused per action (C11 review, C12c, C12d); operator exemptions not inherited by automation (C12 review) | Automated |
| Server stops mid-batch | Laser layer cuts; native ledger cuts, the elevator cargo cut and ride forced stops (C13) | Automated and native |
| Large requests batched or refused | Physical shaft one layer per operation, seven layers per tick; validation and launch budgets | Automated |
| Protection mods (adapter or event simulation) | The cancelable `EndgameEffectEvent` and break events in the chain (C11a, C11c) | Automated (event simulation); real mods open |
| Long device runs leak no ticket or cache | Ticket counts unchanged around laser, field and generator chunks (C11 review, C12 review); the ride ticket released at commit, cancel and stop (C12d, C12 review); the C13 reference load ran 25 minutes with 44 test players and stopped cleanly | Automated and native (development host) |

## Section 11: manual and visual

The maintainer deferred these items, and S2 with real players, to a later round on
2026-10-02; they stay open.

| Plan item | State |
|---|---|
| Target selection, danger confirmation, progress, failure | Open (`[H]` V1); covered server-side by menu GameTests |
| Laser, black hole, gravity and elevator visuals do not hide key feedback | Open (`[H]` V1) |
| Many devices visible at once | Open (`[H]` V1) |
| GUI scale and readability | Open (`[H]` V1) |
| Real GPU (LLVMpipe does not pass performance) | Open (`[H]` V1/V2) |

## Section 12: security and abuse

| Plan item | Evidence | State |
|---|---|---|
| Threat model covers grief, duplication, chunk loading, packet spam | ADR-054 §15 and the per-system threat sections of ADR-055 to ADR-059; three implementation reviews | Recorded |
| Conservative defaults | Physical mining off; limits default to their hard maxima and can only be lowered; protected zones | Recorded |
| Audit lines do not leak coordinates to unauthorized players | The audit is operator-only; lines name devices and players by UUID | Automated |
| Target confirmation never takes a client result | The server resolves every selection and confirmation (C11a, C11c) | Automated |
| High-risk features fully disabled by the server | One switch per system (ADR-054 §1) | Automated |

## Section 13: performance and resources

| Plan item | Evidence | State |
|---|---|---|
| Per-system MSPT, memory and ticket budgets | [PERFORMANCE](PERFORMANCE.md): every per-system share within budget; the flush budget met in the closure run but not reliably on this host; the railgun rate a recorded deviation | Development host; flush gate open |
| Large operations split across ticks | Per-tick caps and budgets (ADR-054 §7) | Automated |
| Visual effects capped | At most 8 concurrent railgun effects per client; particle caps per device and tick | Code; frame cost open (`[H]` V1) |
| Idle cost low | Idle 0.054 ms per tick at the reference load after warm-up, 0.104 ms cold (budget 0.1 ms) (C13) | Development host |
| Long reference-server run without watchdog | 25 minutes of reference load without a watchdog | Development host; reference hardware open |

## Section 14: acceptance

| Item | State |
|---|---|
| Each representative endgame system forms a complete loop | Drill, field, generator, railgun and elevator, each with GameTests and the reference load |
| Every high-risk system has an ADR, threat model, configuration and audit | ADR-054 to ADR-059 |
| Permission, protection, recovery and performance pass | Development evidence above; the flush budget and the `[H]` items stay open |
| Disabled features do not break world load | Switch GameTests; native restarts |
| Zero Critical/High | The C11, C12 and C13 reviews leave none open |
