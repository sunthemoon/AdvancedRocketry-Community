# W121: design note for W107C's F1, a named fill held at L1, and F2

Date: 2026-10-21. Status: **design for an independent read-only review**; no code, test or saved-format change.
Assignment: [TASK-W121](../TASK-W121-F1-NAMED-FILL-DEADLOCK-DESIGN.md). Base: shared commit `a92f7bfd`, which contains
W107C (merged at `f2df941c`); all line numbers are at that commit. Sources: [W107C](W107C.md), [W11A](W11A-DESIGN.md)
sections 4, 5 and 7, and [W11B3](W11B3-DESIGN.md) sections 2, 4 and 5.

**Terms.**
- T is the rocket's checked transfer-journal record, L its logical rocket, and D the landed rocket entity
  (T's destination). X is a fill whose frozen guard names (L, T).
- "Settled" means membership of the RAM set `settledTransfers` (`RocketTransferService.java:46`).
- S(L, T) is the self-guard predicate of section 3.

## 1. The cycle and where it occurs

1. **Lock, then fence.** `RocketTransferFences.classify` runs at the start of every `recoverNext`
   (`RocketTransferRecoveryService.java:80`) and visits every record, settled ones included.
   - It fences T with ESCROW_PENDING_OR_TOMBSTONE when `RocketEscrowGate.logicalLocked(server, L)` holds (`:62`).
   - `fence` removes T from the live and settled sets (`:246-251`).
   - The lock counts every durable, queued or captured operation whose guard names L
     (`EscrowCoordinator.java:625-639`).
   - The same check refuses recovery's final action (`RocketTransferFences.java:233-235`) and fenced re-evaluation
     (`:171-181`).
2. **Fence, then a closed boundary.** `escrowReconciliationBoundary` admits an ordinary record only when it is
   settled, not live and a stationary arrival (`RocketTransferService.java:467-485`, `:507-509`).
3. **Closed boundary, then no progress.** `EscrowReconciliationView.capture` throws `Refusal(NONE)` (`:93-95`), and
   `End.current` repeats the check for C2 to C7 (`:32`). The scheduler retries the next tick and persists nothing
   (`EscrowReconciliationScheduler.java:97-116`).
4. **No progress, then the lock stays,** so step 1 repeats.
   - Accept-current needs a REPAIR or STALLED member (`EscrowSettlementIntent.java:108-109`), so it cannot release X.
   - The loader shows PREPARING or TRANSFERRING, never a hold (`EscrowLoaderAdmission.java:278-300`).

**Which states reach it.** Only an ordinary T that is COMMITTED with a stationary arrival.
- Admission needs `resourceMutationAllowed` (`LoaderEligibility.java:94-96`), which accepts no record or exactly
  that one (`RocketTransferService.java:502-509`).
- PREPARED, ASCENT, the other flight phases, fail-backs and recovered sources are never COMMITTED, so they refuse
  admission.
- No record can appear while X is pending, because launch makes the same check (`:85`) and `resourcesIdle` refuses
  it (`:490`).
- The NONE guard has no record to fence (`:474`).

**When the hold starts.**
- At admission (K0), because the lock counts queued work. `smoke-native-02` logged the fence in the same second as
  admission (`01:14:19`).
- It becomes visible at L1, when the driver first needs the durable record (`EscrowReconciliationDriver.java:46`).
- It forms again at every start, through recovery's first action check (`RocketTransferRecoveryService.java:316`).
  Both restarts of `w107c-native-02` logged `reason=ESCROW_PENDING_OR_TOMBSTONE`, then `FLOOR_UNSETTLED ticks=2300
  reasons=[NONE] boundary=false mutation_allowed=false`.

**Other paths.**
- **Governed flights: no.** The ARMED entry refuses admission (`EscrowCoordinator.java:236-239`). Without one, the
  dispatcher holds `GOVERNED_ARM_MISSING` (`EscrowGovernedDispatch.java:140`), so `terminalSuccessor`, their boundary
  (`RocketTransferService.java:484`), is false. F8 removes T before it prunes the ARMED entry
  (`RocketGovernedCleanupDispatcher.java:44,65`).
- **Cargo: yes, once C-E2 admits it.** Replay uses the same boundary (`RocketCargoPreparations.java:44`).
- **Bay: not today.** A claim unsettles T (`RocketTransferBayDispatch.java:70-74`), and the boundary needs an
  UNCLAIMED bay (`RocketTransferService.java:475-476`). W27d must recheck this.

**Two further consequences, not in W107C.**
- **Starvation (found by reading; not run).** A held attempt takes the driver's one capture per tick
  (`EscrowReconciliationDriver.java:62,88-92`) and is retried every tick. With at most eight records, the scheduler
  keeps the same visiting order (`EscrowReconciliationScheduler.java:75-84`), so an older held fill defers the first
  capture of every younger fill in another component, on any rocket.
- **F1b.** After X retires, L2 leaves a named floor (L, T) (`scripts/loader_cut_oracle.py:318-321`).
  - `classify` then fences T with FLOOR_PROOF_REQUIRED (`RocketTransferFences.java:63-64`;
    `EscrowRecoveryFence.java:24-25`).
  - Its proof needs both ends already loaded and entity-ticking (`RocketTransferFences.java:192-194`,
    `RocketTransferEntities.java:211-219`), and the fence returns at every start.
  - A rocket refuelled on the Moon then cannot launch (`RocketTransferService.java:85`) until its Overworld launch
    site is loaded.
  - W107C's fixture hides this: its source end is 16 blocks away in the same Level
    (`EscrowLoaderFloorProbe.java:194-195`), so both ends are loaded. O3J avoids it by removing T.

## 2. Options

Options (a) and (b) keep every saved format and every L1, apply, barrier and L2 step and order, so the disk state at
cuts K0 to K10 is unchanged. Only restart behaviour differs.

| Option | Recovery order and liveness | Tests and cuts touched |
|---|---|---|
| (a) The fence lets T be settled | Recovery may run KEEP_DESTINATION, which includes discards, on a marked D while X is pending. A join during the fill fences T again, and re-evaluation then needs both ends loaded. F1b remains. | `RocketTransferFences.java` at 62-65 (`classify`), 157-160 (`joined`), 171-181 (`loadedReason`) and 233-237 (`actionAllowed`), and `EscrowRecoveryFence.java:44,50`; `RocketTransferFencesTest`, `RocketTransferFenceGameTests` and its expected-log count, W11b2's named fixtures. |
| (b) Reconciliation accepts X's own guard | Recovery is unchanged. C2 to C8 run, including after both restarts. After C8, T stays fenced and needs both ends loaded, so a hand-back is required. | A new predicate at `EscrowReconciliationView.java:32,93`; `RocketResourceMutationAdmissionTest:19-22` stays as it is; the named R1 cuts. |
| (c) Settle T before admission | Admission already requires this (`RocketTransferService.java:502-509`). It does not help, because X itself unsettles T at K0 and the settled set is RAM. (c1) Refusing every named fill is an owner scope decision. (c2) Retiring T first needs a new journal transition with an on-disk absence proof of the source copy; today such proofs exist only for source-side records (`RocketOrdinaryRetirement.java:52`). | Large; not recommended. |
| (d) A persisted reason | Every named fill would need an operator (O1 to O4), contrary to W11A section 7's R1 row. A manual reason never clears (`EscrowCoordinator.java:577`). A new constant would change the format (`EscrowCodec.java:60`). | O-cuts. Not recommended; R-4 keeps only its visible, unpersisted form. |

## 3. Recommendation: (b), plus a no-revoke rule, a C8 hand-back and a visible reason

**S(L, T)** holds when all of the following are true:
- the coordinator is operational (a closed one locks every rocket, `EscrowCoordinator.java:627`, which is why every
  record is fenced at shutdown in `w107c-native-02`);
- L has no tombstone, flight checkpoint, flight-write lock or ARMED entry;
- at least one unresolved operation names L;
- each such operation is an ordinary PREPARED loader fill into D with reason NONE
  (`EscrowCoordinator.ordinaryFillsOnly`, `:103-109`), guarded by (L, T, the digest of T's acknowledged record);
- each marker on D and each frozen frontier naming L belongs to those operations.

The last two conditions keep F3's orphan markers out.

- **R-1.** `capture` and `End.current` use a new `escrowReconciliationBoundary(rocket, guard)`. It returns today's
  answer, or true when all of the following hold:
  - T is ordinary, COMMITTED and not live;
  - L's acknowledged guard equals the component's guard (as `EscrowReconciliationView.java:69-70` checks);
  - D is the replaceable landed authority, the bay is UNCLAIMED, and no disassembly or transfer is pending;
  - T is unfenced, or fenced only with ESCROW_PENDING_OR_TOMBSTONE, ENDPOINT_JOIN_PROOF_REQUIRED or
    ENDPOINT_PROOF_REQUIRED, with S recomputed rather than read from the reason string.

  Every other caller keeps today's predicate.
- **R-2.** While S holds, `classify` does not fence a settled, not-live T: it skips `RocketTransferFences.java:62`,
  `:63-64` and the marker and epoch hint (`:96-97`) for that record only. This keeps an existing settlement and permits no action:
  - `actionAllowed`, `joined` and `EscrowRecoveryFence` are unchanged;
  - every resource path is still refused by `resourcesIdle`, including disassembly
    (`RocketDisassemblyService.java:143`).
- **R-3.** In the C8 callback (`EscrowReconciliationDriver.java:207-212`) of T's last guarded operation:
  1. Refresh T's `approved` proof key (`RocketTransferFences.java:124,144-149`). The new floor's identity and epoch
     were just proved at C6 and C7 and written by L2.
  2. If T's only fence is one of R-1's three reasons, remove it. Ordinary recovery then loads both ends with
     tickets, as at a start, and re-settles T; `actionAllowed` repeats every check, including the floor's
     (`EscrowRecoveryFence.java:62-68`).
- **R-4.** Any other boundary refusal at `EscrowReconciliationView.java:93-94` returns TRANSFER_JOURNAL_UNPROVED. It
  is retried every 20 ticks, as WAITING_UNLOADED is (`EscrowReconciliationScheduler.java:115-116`), and never
  persisted. The loader then shows ESCROW_PENDING (`EscrowLoaderAdmission.java:278-279`), and the starvation ends.

**Invariants.**
- **No resource can be spent twice.** No debit, credit, proof or L2 order changes. The exact guard and the joint
  readback still gate L2. Recovery still cannot discard, overwrite, rebind or materialize L while anything names L.
  After L2, the floor protects D, and other copies stay inert (`RocketTransferEntities.java:288-294`).
- **No fence is weakened for any action other than the guarded settlement.** R-1 relaxes only escrow's own
  precondition, R-2 permits no action, and R-3 hands over to unchanged final checks.
- **Every hold resolves automatically or shows a reason.** Self-guarded holds resolve. Other holds show a reason that
  is derived again at every start, and manual reasons persist as today. F1b's hold shows only the transfer fence's
  warning and diagnostics until D2 is decided.

**Crash windows.**
- K0 and K1: no change.
- K2 to K8: at both restarts, R-1 and R-3 retire X and re-settle T, including across Levels.
- K9, K10 and any later start: F1b holds T.

The fix adds no log line, saved-data refusal or isolation, so in my reading AGENTS.md section 3.3 needs no risk entry
(Root decides).

## 4. F2 and F3

**F2.** `FluidFuelLoaderIndex` already maps each loaded device UUID to its block entity, per Level
(`FluidFuelLoaderIndex.java:61-82`).
- **Detection.** When the loader is absent at the recorded position (`EscrowReconciliationView.java:100-102`), look
  up the holder in each Level, bounded at 64 as `:83-86` is. A holder elsewhere reports RELOCATED_ENDPOINT; otherwise
  MISSING_ENDPOINT stands.
- **Cost.** At most 64 hash lookups, once per operation, because manual reasons are then skipped
  (`EscrowReconciliationScheduler.java:86`). There is no scan, no chunk load and no new reason.
- **Why it matters.**
  - MISSING lets an operator write an abandon intent at O1. Its O2 disk census then refuses "Relocated source
    identity" (`EscrowSettlementDisk.java:45-46`), leaving SETTLING_ABANDON stuck.
  - RELOCATED is excluded from settlement (`EscrowSettlementIntent.java:106`).
  - The abandon preview (`EscrowLoaderEndpoint.java:52`) should use the same lookup.
- **Limit.** A move into an unloaded chunk still reads MISSING, and the first manual reason is final
  (`EscrowCoordinator.java:577`).

**F3** stays as recorded. Orphan markers make S false, so T is fenced exactly as today.

## 5. Tests and cuts that prove the fix

**Unit tests**, each class run alone and then in the full suite:
- R-1's predicate across every case S excludes, and the conflict reasons;
- S itself;
- R-2's settlement kept across 1,200 calls, while foreign, orphan and unsettled cases still fence;
- R-3's key refresh and reason removal, with FLOOR_CONFLICT kept;
- the starvation case before and after R-4, and that R-4's reason is never persisted.

**GameTests:**
- an in-session named fill retires with no fence line, and a second fill is admitted;
- the same with a rocket reload during the fill;
- F2 within a chunk, across chunks and into an unloaded chunk.

**Native cuts, two restarts each:**
- the failing `LF_M_R1_FLOORS_255_DISTINCT` and `LF_M_R1_FLOORS_255_COALESCED`, stopped after the L1 callback
  again (now `EscrowLoaderFloorProbe.java:109-115`) with the unchanged oracle and the K2 variants kept;
- a new check that `resourceMutationAllowed` returns after retirement, kept outside the 500-line probe;
- a cross-Level named cut at K2 and K9;
- reruns of `R1_FLOORS_256`, the R3 endpoint cuts (`R3_ENDPOINT_RELOCATED` now expecting RELOCATED_ENDPOINT), K6 and
  K9 representatives, W11b2's named settlement fixtures (their chest sources stop at
  `EscrowReconciliationDriver.java:55-57`), and the log checker's unchanged count of 5.

## 6. Decisions for Root

- **D1.** Adopt R-1 to R-4 and the F2 lookup as one persistence slice, with a read-only review before merge.
- **D2.** Before fuel admission opens, either document F1b (an operator force-loads the source end and runs
  `/arce rocket recover`) or commission a destination-end proof for FLOOR_PROOF_REQUIRED. I recommend the latter.
- **D3.** Confirm that a visible, re-derived reason satisfies the third invariant for transfer-side holds.
- **D4.** C-E2 must extend S to cargo kinds before cargo admission.
