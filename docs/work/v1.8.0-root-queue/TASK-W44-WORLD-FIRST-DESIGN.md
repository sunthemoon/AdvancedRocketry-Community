# TASK W44: design note for the two world-first advancements (design only)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w44-world-first-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w44-world-first-design` (local only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W44-DESIGN.md` (new). No production code |
| Specification | ADR-066 section 6.2, from "World-first flags need versioned world state" to the end of the section, and owner decision D3 quoted there |

`one_small_step` (the first committed world Moon rocket landing) and `first_station_warp` (the first
committed world station warp) are the last two C18c advancements. ADR-066 section 6.2 selects their
design:
- two source outboxes, committed with the actual event;
- a progression SavedData that holds receipts and login grants.

It also says that the source journal's checked writer, and its coordination with ordinary saves,
"must be frozen and independently reviewed before runtime". This task writes that design. An
independent review follows, then the implementation.

## Read first

- ADR-066 section 6.2 in full, and the rows for advancements and progression in its test table.
- ADR-065:
  - section 4.1 (entity, flight and journal schema plans);
  - the W11b1 and W11b2 part 1 implementation notes (the checked off-thread journal writer, its
    acknowledgement and its continuations);
  - R-031, R-032 and R-035 in `docs/11-RISK-REGISTER.md`.
- The current code:
  - `RocketTransferSavedData`, `RocketJournalWriter`, `RocketJournalCheckpoint` and
    `RocketTransferJournalCodec` (journal root schema 2);
  - `StationRegistrySavedData` and its checked relocation for warps, with its current schema;
  - the advancement triggers already delivered for C18c (W12, W15).

## The design must answer

1. **Station warp outbox.**
   - Where `classic_first_warp` lives in the station registry root.
   - How it joins the checked relocation candidate. ADR-066 says one write: the same
     `CheckedSavedDataFile` candidate as the orbit and the FE debit.
   - The empty-slot election.
   - The migration from the registry's current schema, done once.
2. **Moon landing outbox.**
   - Where `classic_first_moon_landing` lives in the journal root, and the journal 2 → 3 migration.
   - The checked landing-completion authority: what is validated, what is snapshotted (terminal
     rocket and transfer identity, participants), and which acknowledgement must precede LANDED
     and the notifications.
   - How it uses the W11b2 checked writer and continuations, not a second writer.
   - Recovery when the entity's chunk state lags.
   - What a failed or unknown write leaves behind.
3. **Progression SavedData** (`advancedrocketrycommunity_classic_progression`, schema 1).
   - Its bounds.
   - Its checked write and reload.
   - How a login grant reads the immutable participant set.
   - The order between the source acknowledgement, the receipt, and the advancement award, so
     that no award is lost and none is doubled at any cut.
4. **Ordinary saves.** How an ordinary save serializes only the latest acknowledged source authority
   and never overwrites a checked first-event commit with stale memory.
5. **Participants.**
   - Who is captured, with which checks: real connected passengers, or players in the station
     region with live VISIT permission.
   - The preflight bounds, and the visible refusals when over budget.
6. **Native cut plan.** The forced-stop cuts the implementation must run, with two restarts each,
   per ADR-066's test table.
7. **Risk and ADR text.** Propose the risk-register entry (scope, duration, logs, recovery) and the
   ADR-066 note. Root edits those files.
8. **Owner questions.** List anything that needs the owner. D3 is already decided; do not reopen it.

Keep the note focused: tables over prose, one section per question. If ADR-066 is wrong or
impossible somewhere, say exactly where and propose the smallest change.

## Not in this task

- Any production or test code.
- ADRs, the risk register, status files and the ledger (Root).
