# TASK W191: C18 contract leftovers (W172's B8, documents only)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w191` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w191` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W191.md` (new; the review itself) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W191/` |
| Scope source | section 3, B8, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`) |

**Root decision R158.** ADR-066 section 9, items 1 and 5, still require a contract review of numeric tiers that W03
shipped, and section 5.1 names an eligibility extension that ADR-025 does not yet describe. This task changes no code.

## What to do

1. **A scoped contract review**, written into the record. For each tier, compare the shipped value with ADR-066 and
   with the legacy behaviour, cite the code and the source line, and say whether they agree:
   - the suit tanks of 1,000, 2,000, 4,000 and 8,000;
   - `OXYGEN.suitTankCapacity`;
   - the scrubber's fourth-debit schedule, its cartridge balance and the vent migration.
2. **A proposed ADR-025 additive revision** for the `space_breathing` eligibility extension (ADR-066 section 5.1;
   `records/W56.md` lines 129 to 133). Write it as a separate PROPOSED revision file under `docs/decisions/`, in the
   style of the existing revision files. Do not change ADR-025's status or accept anything; Root commits the proposal
   and the owner decides.
3. Any number that should change becomes a finding for Root, not a code change.

## Verification

`python -B scripts/validate_repository.py --require-approved-identity` and `git diff --check`. No Gradle, GameTest or
server run is needed.

## Not in this task

Code, data, tests, accepting any ADR, status files and the ledger.
