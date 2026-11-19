# TASK W388: the fuel loader's admission readiness, for the owner's final packet

Root: Claude. Date: 2026-11-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w388`, branch `codex/v1.8.0-w388` from the shared branch |
| Decision | Root decision R518 (QUEUE row W388) |
| Output | `docs/work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md`, a DRAFT record, committed on the branch |
| Write scope | That one record. No code, script, test, register, ADR, status file, QUEUE or other record. |
| Not in scope | Native runs, Gradle, GameTest and the heavy-JVM lock. Enabling anything. |

## Why

The owner's final packet gives one yes or no for each production switch, with its risk scope, evidence and Root's
recommendation ([OWNER-DECISIONS-20261117](records/OWNER-DECISIONS-20261117.md), R459). The draft
[C4-SWITCH-REPORT](records/C4-SWITCH-REPORT.md) covers the satellite bay and the four cargo kinds, and leaves the fuel
loader's admission out of scope. The fuel switch is `EscrowCoordinator.PRODUCTION_ADMISSION`, still false. Its
prerequisites are spread over the risk register, ADR-065, design records and many QUEUE rows. This task gathers them
in one place, with the current status of each, so that Root can write the fuel item of the packet.

## What to do

1. **Collect every prerequisite for fuel admission** from these sources, and any they point to:
   - R-035 in [the risk register](../../11-RISK-REGISTER.md), especially the sentence that begins with
     `准入前另需` (signed per-fluid accounting, and W90's measured cost and readiness time for a cold destination, a
     chunk-boundary crossing and the maximum margin, R22 item 6), and its availability clauses;
   - [ADR-065](../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md): the W11b3, W90, W91, W176 and
     W357 to W362 notes;
   - the records [W11B3-DESIGN](records/W11B3-DESIGN.md), [W176](records/W176.md),
     [W357-DESIGN](records/W357-DESIGN.md), [W373B](records/W373B.md) (the loader's settlement cuts, merged in R517) and
     [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md);
   - [QUEUE](QUEUE.md): rows W75, W90, W90a, W90b, W91, W91N and its follow-ups, W176, W206, W207, W212, W357 to W373C,
     and any row that says "before fuel admission" or "before any admission";
   - the open items in [CURRENT_VERSION](../../status/CURRENT_VERSION.md).
2. **For each prerequisite, give one row:** what it requires; where it is stated; its status (DONE, PARTIAL, OPEN or
   PENDING_C4); the evidence (commit, hosted run, record or evidence path) that supports the status; what would close
   it; a rough effort; and whether it needs the owner (an account session, a destructive test or a signature). A
   requirement that a later decision replaced is listed with the decision that replaced it. Do not mark anything DONE
   without a citation.
3. **Name the campaign families that exercise the fuel loader** in `scripts/run_v180_integrated_campaign.py` (read it;
   you may run `python -B` one-liners that import it and print the family names). For each, say which part of the
   loader it covers and that its C4 result is PENDING_C4.
4. **Write the switch section in the shape of C4-SWITCH-REPORT sections 3 to 7:** the switch and what is live while
   it is off; the scenarios with their evidence; the applicable risks, each with scope, duration, logging and
   recovery as the register states them; and the remaining work. Leave the recommendation as PLACEHOLDER for Root.
5. **List what stays unaccepted** for fuel under R-035 (for example R-031, R-032, single-file rollback, OS crash and
   power loss, and UNKNOWN settlement loss), quoting the register's scope for each.
6. **Run the repository validator** on your record: `python -B scripts/validate_repository.py` must end with
   `0 failed`. Link only to tracked files that exist on the shared branch; use a code span for anything else.

## Rules

- Change nothing outside your record. Commit it on the branch in one commit in the style of AGENTS.md section 6. Do
  not push.
- Write full sentences in plain English. Quote the Chinese register text where you cite it, then say in English what
  it means.
- The last line of your answer is the path of the record.
