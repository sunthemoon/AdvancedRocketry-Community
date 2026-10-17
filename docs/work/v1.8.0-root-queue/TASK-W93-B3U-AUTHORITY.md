# TASK W93: W11b3 leaf B3-U (the admission authority move) and `EscrowCoordinator.prepareFill`

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w93-b3u` (from the shared branch after the W11b2 merge) |
| Branch | `codex/v1.8.0-w93-b3u` (local commits only; Root merges) |
| Design (frozen) | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) (W75, frozen at `52a0a293`): section 2 (admission, reservations and one authority) and section 4 (exact refill and resources) |
| Leaf rows | Section 5: B3-U, and the `EscrowCoordinator.prepareFill` part of B3-M (W81 delivered B3-M's pure codecs, records [W81](records/W81.md)) |
| Record | `docs/work/v1.8.0-root-queue/records/W93.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W93/` |

W11b2 is now on the shared branch (merge `eff6eff3`); production admission stays disabled, and this task does not
enable it (that is B3-E, later).

## What to do

1. **Recheck section 2 against the merged code first** (W75 read W11b2 at `d178e727`): the current
   `EscrowAdmissionAuthority`, `EscrowRuntime`, the coordinator and the test-only fill entry. Note in your record any
   site that moved; stop and report if a difference changes the design.
2. **B3-U:** the runtime-owned `EscrowAdmissionAuthority`, the PROPULSION delegate and its three states, the
   coordinator attach, the startup lookup and early bind, the test-hook retarget method, and the corrected Javadoc
   terms, as section 2 specifies. Keep one admission authority for fills and the bay.
3. **`EscrowCoordinator.prepareFill`:** the production entry point beside the test-only one, as sections 2 and 4
   specify, including Root-R6's floor-retirement headroom and Root-R8's preview charging. It is not called by any
   device yet.
4. **Tests** as the B3-U and B3-M rows say: extend `EscrowAdmissionAuthorityTest` (63 propulsion plus 1 bay and the
   reverse; mixed 8 per rocket and the ninth refusal; UUID collision; durable, queued, captured and UNKNOWN copies
   counted once; REPAIR and released-unresolved receipts; NOT_LOADED and NO_FILE; over-capacity restart without
   eviction; REJECTED re-arm with the same ID; exact release), each delegate state, a fill and a bay admission in the
   first tick, ledger replacement (retarget, refusal while work is held, no throw), and `prepareFill`'s R6 headroom
   and R8 charging tests (each must fail if the rule is removed). Run each new class alone, then the whole unit suite,
   then one full GameTest with the unchanged log checker.

## Write scope and boundaries

`resourceescrow/` admission authority, runtime and coordinator classes, the test hooks' retarget method, new and
extended tests, and the record. No device wiring, no production ledger creation, no saved-format change, and no relaxed
assertion, limit or timeout. Stop and report if one seems needed.

## Commands

As WORKER-RULES says. **While `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` exists and its `owner.txt` names
`W11B2-C54`, start no Gradle and no Java at all** (continuation 54's benchmark needs a quiet machine); read and write
code meanwhile. After it is gone, compile and run unit tests freely, and take the heavy-JVM lock for
`runGameTestServer`. Check that C: and D: have more than 10 GiB free before each heavy start (C: has about 13 GiB). If
the tool policy refuses to let you remove your own lock or world, write the exact path in your record and final
answer; Root removes it.

## Not in this task

B3-E (the loader queue and admission), W91, W92, W90a and W90b, production admission, ADRs, the risk register, status
files and the ledger (Root).
