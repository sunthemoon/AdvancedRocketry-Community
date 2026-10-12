# TASK W11a round 2: revise the D3-A escrow design after the independent review

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first. This is
design work only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11a-d3-design` (round 1 plus the shared branch at `19da6240`) |
| Branch | `codex/v1.8.0-w11a-d3-design` (local only; Root merges) |
| Round 1 | `records/W11A-DESIGN.md` at `8e767cd6`, and its record `records/W11A.md` |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11a-review/REVIEW.md`, SHA-256 `1cfcbb8f6d4406f4302ed73cd346f99f3429ce9cf4bffcae17d1fc107316b0fe`. Verdict REDESIGN: F1 and F2 Critical, F3 and F4 High, F5 to F7 Medium, F8 Low |
| Deliverables | A revised `records/W11A-DESIGN.md` (up to six pages); a section at its end that answers each finding; a short record `records/W11A2.md` |

Read the round-1 design, the whole review and the original task
[TASK-W11A-D3-ESCROW-DESIGN](TASK-W11A-D3-ESCROW-DESIGN.md) first. Everything from round 1
that the review did not challenge stays, unless a decision below changes it. Check each
vanilla claim against the mapped jar
(`forge-1.20.1-47.4.10_mapped_official_1.20.1.jar` in the Gradle cache) with `javap`, and
cite the class and method.

## Root's decisions

Root accepts all eight findings. The revised design must do the following.

1. **F1: the endpoint proof.**
   - **The barrier's own store.** The barrier serializes the endpoint chunk itself and
     submits it through `IOWorker.store` (Access Transformer access). It keeps that
     store's future, so a failure of its own write, or of a write that replaces it while
     it is pending, is observed.
   - **The order.** Store, then `synchronize(true)`, then read back. The readback comes
     **after** the force. It checks that the header and record are stable, for example
     by reading the header before and after the record. It also confirms the marker and
     the applied prefix, not one operation's standalone after-hash (the review's note in
     "Vanilla saving").
   - **Superseding saves.** Any newer save of an endpoint chunk observed between the
     barrier's store and its readback restarts the barrier. External `.mcc` records are
     refused.
   - **The residual.** Name it exactly: a vanilla writer failure the mod cannot observe,
     such as a failed eviction close or a later write that corrupts the chunk after
     confirmation. It is outside S2, in the same class as the per-file rollback that
     ADR-065 §8 already excludes. It gets a risk-register entry in W11b2.
   - **No new Mixin.** If you conclude a Mixin is unavoidable, stop. Give the evidence
     and the exact injection, so that Root can take an ADR to the owner.
2. **F2: operator resolution is a durable settlement.**
   - `accept-current` first checkpoints the exact live states of all surviving endpoints
     through the same barrier. It then retires the record durably, with any floor or
     tombstone, and only then unlocks.
   - If a save is vetoed, nothing is released. Document the offline whole-world repair
     procedure instead.
   - Define `abandon` the same way, including an endpoint that returns later.
   - Add forced-stop cuts before and after each settlement acknowledgement, including
     chained operations.
3. **F3: recovery must ask the coordinator.**
   - Every transfer-recovery action that discards, overwrites, rebinds or materializes
     a rocket authority must consult pending records and floors first. This includes
     `KEEP_SOURCE`, `REMOVE_DESTINATION_KEEP_SOURCE`, duplicate election and passenger
     reconnect.
   - Admission requires a checked-durable transfer-journal state for the rocket.
     Otherwise it refuses conservatively.
   - Add the cut with a stale journal phase and two present entities.
4. **F4: floors are reserved at admission.**
   - Reserve floor slots before L1: durable floors plus every outstanding reservation,
     including queued admissions and unknown L1 outcomes.
   - Persist enough to rebuild the reservations after a restart.
   - Refuse with `ESCROW_FULL` before any mutation.
   - Status shows used and reserved slots.
   - Collection at server start only is accepted for v1.8 (the review's D-4 answer). The
     uptime limit is documented.
5. **F5:** freeze one reason-specific transition table. It distinguishes retryable barrier
   stalls from manual conflicts, and says which reasons survive a restart and which clear
   automatically. A healthy record must not starve behind unloaded or blocked records.
6. **F6: the ledger writer.**
   - One bounded ledger writer works off the server thread on immutable snapshots, and
     posts its completion back to the server thread.
   - Order L1 and L2 by writer generation, and acknowledge only the exact snapshot
     written.
   - Autosave and explicit commits go through the same writer. Specify draining it at
     shutdown.
   - The benchmark plan separates main-thread cost from IO latency. It uses saturated
     region caches, autosave and competing operations, against the unchanged
     `docs/17` budgets.
7. **F7: pre-start policy.**
   - The escrow ledger follows the existing managed-type policy. A future or invalid
     ledger root refuses world start, as other managed SavedData types do, with the
     documented offline recovery. Drop the online `BLOCKED` mode for root-level
     invalidity. An invalid section on an individual endpoint still blocks only that
     endpoint.
   - The risk entry states the startup scope, the chunks and Level a save veto affects,
     the log volume of both the coordinator and the underlying writers (say "no lifetime
     cap" where none exists), and the backup, offline repair and verification
     procedure.
8. **F8: passengers.**
   - Correct section 0: `RocketEntity` overrides `hasExactlyOnePlayerPassenger`, so the
     rocket stays in the entity chunk.
   - Keep the no-occupant rule as a conservative interlock. Add `!rocket.isPassenger()`.
   - Guard forced boarding and remounting at the Forge mounting boundary and in the
     production and reconnect callers.
9. **The review's other requirements**, under "Other requested checks", are part of the
   contract unless you show one is wrong:
   - guard both mutation layers (`RocketRuntime` and `RocketFlightService`);
   - refuse or detect `/tp`, dimension travel and `/ride`;
   - a no-op post-store consumer for `storeChunkSections`, and check `areEntitiesLoaded`;
   - restore the dirty flag deliberately on a failed store;
   - a per-type writer policy instead of the `AtomicSavedData` allowlist;
   - narrow the backup-rollback claims;
   - `ServerStoppedEvent` does not prove that saves were flushed.
10. **The split.** Replace section 8 with the review's three slices:
    - **W11b1**: coordinator, storage and barrier, with no production admission;
    - **W11b2**: rocket integration, recovery, floors and reservations, operator settlement,
      startup policy and the risk entry, with admission still disabled;
    - **W11b3**: `fluid_fuel_loader`, the rocket-binding half of the linker, and the S2
      evidence, after which admission is enabled.

    For each slice, give its scope, its tests and the cuts it must pass.

Root's round-1 decisions stand, with the review's conditions:
- D-1, D-3 (as a conservative policy) and D-5 are accepted;
- D-2 holds on this base, because the shared branch wires the Access Transformer;
- D-4 holds only with F3 and F4 in place.

## Not in this task

- Code and tests.
- ADR text changes. The ADR-065 implementation note is part of W11b2.
- Status files and the ledger.
- Cargo, item and satellite-bay contracts beyond one sketch paragraph.

Commit the revised design and your record on the branch. The last line of your answer is
the path of the revised design.
