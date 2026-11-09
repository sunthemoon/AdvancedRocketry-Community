# TASK W329: escrow reads that treat an unloaded ledger as no ledger

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w329`, branch `codex/v1.8.0-w329`, created by Root from W328's delivered commit (W328's change is on your base) |
| Decision | Root decision R393 (QUEUE row W329), from section 4 of W328's design note `records/W328-DESIGN.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W329.md` and the design note `records/W329-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W329/` |

## The finding

`ResourceEscrowSavedData.existing(server)` returns null both when no ledger file exists (the PROPULSION delegate is
NO_FILE) and when the ledger did not load or its presence is unknown (NOT_LOADED; W328's `EscrowLedgerStartup`). Several
production reads treat null as no ledger. For example, in `resourceescrow/RocketEscrowGate.java`:

- `escrowIdle` returns `section.ledger() == null`, so the ledger-only facts (armed flights, records naming the rocket,
  logical locks, settlement tombstones and the authority head) are skipped;
- `retirementUnprotected` and `bayNeutral` return true;
- `escrowLogicalLocked` returns false, and `flightFrozen` ignores armed flights.

While the ledger is NOT_LOADED, a rocket whose escrow history lives only in the ledger can therefore pass as idle,
retire as unprotected, or count as a neutral bay source. Admission already refuses (the shared authority answers
UNAVAILABLE), so this concerns the read-only gates and any other read that weakens a protection. After W328 this state
is reachable only when a status check or load fails after the pre-start check, and only on a world that has a ledger,
but it must be closed before any cargo switch is enabled.

Root decision R393: under NOT_LOADED, a protective read answers its protective value (not idle, not unprotected, not
neutral, locked, frozen), as the bay receipts already do under R390 (`BayReceipts.holds` returns true). NO_FILE keeps
today's answers, so a world without a ledger, which is every production world while admission is off, is unaffected.

## What to do

1. **Design note first** (`records/W329-DESIGN.md`, committed before the code):
   - An inventory of every production read of `ResourceEscrowSavedData.existing` outside GameTest classes, probes, cut
     drivers and test fixtures: the file and line, what a null result means there today, whether it weakens a
     protection under NOT_LOADED, and the change. About 30 sites; a table is fine.
   - The shape of the fix, for example one lookup that tells LOADED, NO_FILE and NOT_LOADED apart from the PROPULSION
     delegate's state, used by the protective reads.
   - The delegate is NOT_LOADED before `ServerStartingEvent`. Find any protective read that can run earlier (for
     example while spawn chunks load entities) and say whether the change alters it and whether that is harmless.
   - What the hold means for players, as AGENTS.md section 3.3 asks for protections that refuse work: scope (expected:
     the server, every rocket the changed reads guard), duration (the session), logs (W328's one WARN per start; add
     no per-rocket line unless bounded), and recovery (a restart that loads the ledger or proves it absent). Include
     the proposed risk-register text; Root edits the register.
2. **Implement** the design. Keep the admission paths unchanged unless the inventory shows a gap; if it does, record it.
3. **Tests.** A GameTest or unit test per changed read, with the ledger NOT_LOADED (through a test-only hook or an
   injected status failure) and a ledger holding history for the rocket: the read answers its protective value, and
   the operation it guards (for example retirement, disassembly, a bay source check) is refused. A NO_FILE case per
   read keeps today's answer for an ordinary rocket. Each new test fails without its fix.

## Verification

The new and affected test classes alone, then `./gradlew build` with all unit tests; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock (backslash lock path, as WORKER-RULES says; W325 and W326 hold
it per runner invocation, so wait your turn); `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. A separate read-only session reviews W328 and this change together before merge
(AGENTS.md section 13). Root pushes for a sharded run and merges.

## Not in this task

W328's pre-start refusal and startup lookup; the bay's NOT_LOADED policy (R390); production admission; the ledger or
receipt formats and schema versions; the risk register, the status file and QUEUE; the native runs of W325 and W326.
