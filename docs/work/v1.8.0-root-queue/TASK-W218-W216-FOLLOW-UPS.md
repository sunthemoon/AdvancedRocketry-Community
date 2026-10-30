# TASK W218: W216's follow-ups, ordinary-advance coverage and R-030 wording

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Part A is a
committed test change; part B is drafts only, outside the repository.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w218` (new; from the shared head named in your prompt, which contains W216 at `15f9e052e`) |
| Branch | `codex/v1.8.0-w218` (local commits only, part A; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W216-review/REVIEW.md` (SHA-256 `24529d20…c0f0d64cf`): findings W216-R1 and W216-R2, "Cost moved, not hidden" |
| Inputs | `records/W216.md`, `records/W213.md`, `records/W168.md`; `satellite/mission/RecordEncodingIndex`, `satellite/persistence/ExactGroundPreflight`, `GroundSurveyScaleTest` (its `CountingSizer`, lines 223 to 250); R-030 in `docs/11-RISK-REGISTER.md` (line 95) |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W217/W217-drafts.md` and its audit script |
| Output | part A: commits on the branch and a record `docs/work/v1.8.0-root-queue/records/W218.md`; part B: `D:/GitHub/ARCE-Task-Evidence/root-queue/W218/W218-drafts.md`, an audit script and its JSON result |

**Root decision R195.** W216 merged. Its review found that the ordinary-advance scale test uses a `CountingSizer`
whose `encodingSize` returns UNKNOWN, so that test never runs W216's eager per-record measurement (W216-R2), and that
R-030 still describes the whole-root preflight as the current path (W216-R1).

## Part A: ordinary-advance coverage with real measurement

Add separate regression coverage of ordinary survey and analysis advances whose sizer delegates `encodingSize` to the
real measurement (`CodecRecordSizer.INSTANCE` or a counting wrapper around it), asserting that the resident
measurements are known before and after the advances and that the advance stays within the existing 1 ms median
budget at the same registry scale. Keep `GroundSurveyScaleTest`'s existing limits, warm-ups, samples and assertions
unchanged; add the new test beside them. Show it would fail if eager sizing were disabled (the measurements stay
UNKNOWN) and pass on the current source; state the measured medians.

## Part B: R-030 drafts (change no tracked file for this part)

R-030 is one Markdown table row, in Chinese, with 8 pipes. Write exact `Target` / `Replace exactly` / `With`
replacements that qualify the sentences saying an unproven lifecycle reservation, or a reservation sum over the root
bound, makes ground mutations use the whole-root preflight, as the behaviour before W216; and describe the three cases
now: the unchanged reservation proof; exact resident byte and heap arithmetic with only the candidate records encoded;
the original whole-root preflight when any resident measurement is unknown. Keep the historical W168 and W213 figures
as history, add W216's measured START (286.2 to 1.4 ms idle median) and the eager costs its review measured (restore of
5,242 missions 171 to 296 ms, measurement per record, the unchanged startup decode), and keep every limitation
(reference host, soak, concurrency, recovery and refusal rules). Do not close the risk or any Gate. The audit script
checks unique, non-overlapping anchors and the 8-pipe row; run it and keep its JSON result.

## Verification

Part A: the new test (with the disabled-sizing control), `GroundSurveyScaleTest` and the satellite persistence and
mission packages; `python -B scripts/validate_repository.py --require-approved-identity`. Part B: the audit script.
No GameTest or native run.

## Not in this task

Production changes, the budgets and limits, other risks, ADRs, status files and the ledger.
