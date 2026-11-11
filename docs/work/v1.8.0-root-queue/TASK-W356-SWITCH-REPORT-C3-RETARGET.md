# TASK W356: retarget the per-switch report draft from candidate C2 to candidate C3

Root: Claude. Date: 2026-11-11. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w351`, branch `codex/v1.8.0-w351`, which Root brought to the shared head (it holds W351's draft) |
| Decision | Root decision R430 (QUEUE row W356) |
| Draft | `docs/work/v1.8.0-root-queue/records/C2-SWITCH-REPORT.md` (W351, under R422) |
| Record | `docs/work/v1.8.0-root-queue/records/W356.md` |

## Why

W351 drafted the owner's per-switch report (the satellite bay and the four cargo kinds) for candidate C2, with every
result cell left as PENDING. C2's campaign found four families that failed on test fixtures, a harness gap and a
campaign selection, all fixed since (W352 to W355), so the owner's report gives the results of candidate C3, whose
58-family run is in progress. Root fills the result cells from C3's evidence with a script once the run ends. This
task makes the draft describe C3, so that only those results remain.

## Facts to use (do not re-derive them; cite them)

- **Candidate C3** is the hosted build of `9a15e1f42dbacf6c81b6332192f523cd25f59035` (shared run 42947784467,
  passed on its first attempt with 1,859 GameTests; governance run 42947784571 passed). Its production paths equal
  W355's merge `26b1ecda9`. JAR SHA-256 `459669bf802ea7ca0ca88bf5a84902eb69274f4a3313305319843c6d8f81744b`, which the
  build's sealed identity files confirm. Evidence root `D:/GitHub/ARCE-Task-Evidence/root-queue/C3/` (outside Git):
  `campaign/` (the driver's evidence root, suffix `attempt1`), `candidate/` and `hosted/regression/`. The run started
  at 12:27 on 2026-11-11 with C2's driver arguments; its plan has 58 families, and `cargo-B3-S6-REUSE` selects 96
  cases with `LF_S6_F9_RESPAWN` reported as NOT_COVERED ([W355](records/W355.md)).
- **C2's outcome** is in `docs/status/CURRENT_VERSION.md` (the ledger paragraph and the open items) and in the QUEUE
  rows W352 to W355 and W351 (decisions R424 to R429): 49 families passed, 6 failed and 3 did not run; the nine
  later `cargo-B3-S6-REUSE` cases and the three families not run then passed on C2.
- **Changes between C2 (`ade1538c3`) and C3 (`9a15e1f42`)** in the production paths (`src/main`, `src/generated`,
  the Gradle files) are GameTest classes and gated release-test probes only: W350's registry test windows
  (`67c310530`), W352's C-S3 R1 fixture fuel (`c7bca5eed`) and W354's backed bay store for the loader refusal cuts
  (`266df4cc3`). W353 (`b4dd5f320`) and W355 (`26b1ecda9`) changed Python and tests only. Confirm this with
  `git diff --stat ade1538c3 9a15e1f42 -- src/main src/generated build.gradle settings.gradle gradle.properties`
  and say so in your record.

## What to do

1. **Rename.** `git mv` the draft to `records/C3-SWITCH-REPORT.md`. Update the title, the Status row (C3's result
   cells say PENDING until Root fills them in from C3's campaign and hosted run), the Base row and the names in
   section 1.
2. **Candidate name.** Where the text names the candidate whose results the report gives, write C3: column headers
   (`C2` to `C3`, `C2 family` to `C3 family`, `Test in C2's hosted run` to `Test in C3's hosted run`), "craftable at
   C2", "Reachable at C2" and similar. **Never change a case or boundary name** that contains `C2`, for example
   `C2_DESTINATION_AFTER`, `FC_LOAD_C2_SOURCE_AFTER` or `FC_LOAD_D2_C2`. Keep "C2" where the text describes C2 itself.
3. **Section 2.** Rewrite 2.1 as candidate C3 with the facts above. Add a subsection on C2's campaign: each failed
   family with its cause and its fix or classification (R424 to R429), the follow-up passes, and the statement that
   C2's results are history while C3's are this report's evidence. Make 2.2 cover the production changes from C1 to
   C3 (keep the C1-to-C2 content and add C2-to-C3 from the facts above). Update every figure that names C2's hosted
   run (run 42831910740, 1,858 GameTests) to C3's run where the text means the candidate's run.
4. **Result cells.** Keep every result cell exactly `PENDING` or `PENDING (n)`, in its row, and keep every
   backticked family name, case name and `Class.method` test name in those rows exactly as written: Root's filler
   reads them. Do not fill any result.
5. **Known outcomes.** Where a row or paragraph involves `cargo-G2-REUSE` or `w336-downgrade`, say in its text (not
   in the result cell) that the historical-JAR downgrade checks are expected to fail under R-044 until the owner
   decides it (R428, R429). Where it involves `cargo-B3-S6-REUSE`, say that `LF_S6_F9_RESPAWN` is NOT_COVERED (W211
   R203, R427). Update RT.1's text: W350 is in C3.
6. **Limits from W355's audit.** Add any of these that the draft does not already state, in the section of the
   switch it concerns, citing the record: `forced-W331-R` passes KEEP_SOURCE, not destination reconstruction
   ([W331](records/W331.md)); B11.7's UNKNOWN component is not covered; the `L_LEDGER` vanilla-dismount subrow is uncovered
   (W332, carried by [W343](records/W343.md) and [W345](records/W345.md)); `P_OWNER` uses a fixed bay owner. The first BI veto
   attempt stays failed ([W222](records/W222.md)); mention it only where the draft discusses the fuel loader.

## Verification

- Before and after, count the table rows whose last cell is `PENDING` or `PENDING (n)`, and list the backticked
  spans in each; the counts and spans must match apart from rows you can justify in your record.
- No remaining "C2" names the reported candidate; list every remaining "C2" occurrence in your record with its kind
  (case or boundary name, or C2's history).
- Links point only to tracked files; `python -B scripts/validate_repository.py --require-approved-identity` passes.
- UTF-8 without BOM, LF only, no newline inside a table cell.

Commit on the branch (the rename and edit, then your record); do not push. Root reviews, fills the results and merges.

## Not in this task

Filling any result; production code, scripts and tests; the status file, QUEUE and the risk register; the C2 or C3
evidence; any decision about the switches.
