# TASK W374: draft the candidate C4 per-switch report, results pending

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w374`, branch `codex/v1.8.0-w374`, at the shared head |
| Decision | Root decision R470 (QUEUE row W374) |
| Source | [C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md), the filled report for candidate C3 |
| Deliverables | `docs/work/v1.8.0-root-queue/records/C4-SWITCH-REPORT.md` and `records/W374.md` |
| Write scope | Those two files only. No code, script, register, ADR, status file or QUEUE change. |

## Why

The owner handed every design and decision point to Root and waits for a finished product (2026-11-17,
[OWNER-DECISIONS-20261117](records/OWNER-DECISIONS-20261117.md)). At the end Root gives one final packet. It contains each
switch's risks and evidence and Root's recommendation, and the owner answers yes or no. Its core is the per-switch
report for candidate C4, which follows the accounting activation and the work since C3. This task drafts that report
now, with every C4 result cell left `PENDING`, so that Root can fill it from C4's campaign and hosted run.

## What to do

1. **Start from C3's report.** Keep its structure and every row. Turn each C3 result into a C4 result cell holding
   exactly `PENDING`, or `PENDING (n)` with the case count. Keep every backticked family, case and `Class.method` name
   exactly as the campaign driver and the tests spell them; Root's filler reads them.
2. **Add the rows covered since C3,** each with its source record (QUEUE rows W358, W360, W363, W364 to W366, W370 to
   W372, and R444 to R469):
   - **Bay:**
     - B6.8 through `forced-W363-B6-GOVERNED` (`forced-W358-B6-BOUND` left the selection under R451);
     - B10.6, B11.8, both B11.9 directions, B11.7's UNKNOWN part and CAP.3, through the `forced-W358-*` families;
     - B17.3's two forms through the `forced-W363-B17-*` families;
     - the restart-slot fix and `BayResumeFairnessGameTests`;
     - the governed-flight refusal and `BayGovernedFlightGameTests`;
     - the reasoned unreachable rows B11.2 and B17.5.
   - **Cargo:**
     - the fourteen item before-write and store-veto cuts in the D-S2 families;
     - R-031's two `r042-R031_*` families and the `r042-X_*` presence families;
     - the two item-device hold GameTests;
     - the vanilla-dismount row, unreachable under production rules (W360's argument).
3. **A new section for the per-fluid signed accounting,** for each fluid kind and the fuel loader where it shares a
   risk:
   - the format and its holds (R-035's accounting paragraph);
   - the evidence rows: Slices A to D unit tests, W372's activation run and W373's native accounting cases;
   - its remaining work.
4. **A section for X01 to X03.** Use the plan as amended by R457 and the designs W367 and W368, with result rows
   `PENDING` and the stated residuals.
5. **Update the switch sections.** The bay now has a production constant, `BayDeploymentSwitches.PRODUCTION_ADMISSION`,
   which is false (W371). R-044's downgrade rows gain the root-3 refusal as a second reason, as the R-044 draft's
   section 3 notes. The owner's choices are the decision record's items. Leave Root's recommendation as a marked
   placeholder per switch.
6. **Facts.** Write candidate C4's identity (commit, run, JAR hash, campaign run) as `PENDING`. Cite every other fact
   from its record, without re-deriving it.

## Rules

- Docs only. English, full sentences, UTF-8 without BOM, LF line endings. Links only to tracked files.
- Commit the two files on your branch with messages in the style of AGENTS.md section 6. Do not push.
- The record lists every row added and every row whose text changed, with its source. Its last line, and the last
  line of your answer, is the path of `records/W374.md`.
