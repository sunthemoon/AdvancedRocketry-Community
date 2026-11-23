# TASK W456: package K20, second pass: the delivered packages into checklists A to J

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w456`, branch `codex/v1.8.0-w456` from the shared branch |
| Decision | Root decision R649 (QUEUE row W456) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K20 and every row and plan choice it names; [W408](records/W408.md) (K20's first pass, which wrote "Needs package Kxx" notes into the checklists); the package records and their QUEUE rows with Root's acceptance decisions: K01 [W424](records/W424.md), K02 [W431](records/W431.md) and [W437](records/W437.md), K03 [W436](records/W436.md), K04 [W434](records/W434.md), K05 [W435](records/W435.md), K06 [W406](records/W406.md), K07 [W438](records/W438.md), K08 [W440](records/W440.md), K08b [W442](records/W442.md), K09 [W441](records/W441.md), K10 [W444](records/W444.md), K11 [W453](records/W453.md), K13 [W402](records/W402.md), K14 [W449](records/W449.md), K15 [W403](records/W403.md), K16 [W427](records/W427.md) and [W443](records/W443.md), K17 [W452](records/W452.md), K18 [W432](records/W432.md), K19 [J21-FINDING-MAP](records/J21-FINDING-MAP.md), K21 [W445](records/W445.md); the [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) as filled by W455 |
| Inputs (read only) | The packages' manifests, recipes and client-step files under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/` (for example K11's `v1/variants.json`, K14's `partial-r3/client-steps.json`, K21's `R638/client-steps.json`, K17's schedule) |
| Outputs | Tracked: the ten checklists `docs/work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md` to `-J.md` and the record `docs/work/v1.8.0-root-queue/records/W456.md`. Nothing else |
| Lock | No server runs and no lock: this is a reading and writing task. Run the validator only. |
| Not allowed | Removing a checklist row or step, or changing an expected observation, a budget or an oracle. Marking any row passed, or turning a NOT_COVERED, partial or FAILED item into anything else. Changing any file other than the ten checklists and the record. Linking to a file that is not tracked on the shared branch (cite fixture files as code spans). |

## What to do

1. **Each "Needs package Kxx" note.** Replace it with what that package delivered for the row: the sealed fixture path
   and its `SHA256SUMS.txt` hash, the recipe or client-step file and the step it maps to, and the package's
   NOT_COVERED or partial items for that row with their reasons, citing Root's acceptance decision (R-number in the
   package's QUEUE row). Keep every existing step and expected observation.
2. **K02 and K12 are still in progress** (W447 and W454). For their rows, keep the note and add "in progress (W447)"
   or "in progress (W454)"; Root fills them when those land.
3. **J04's table.** Copy K11's `variants.json` into checklist J as J04's per-variant table: one row for each of the
   ten online variants and EXIT.0 to EXIT.10, with its disposition and its native case name (C35).
4. **Root's run-time decisions.** Where Root decided how a row runs, write it into the row's preparation steps with the
   decision number: R638 (no NBT writes; D04 and D06 setup by players), R642 (prior-world sleep copy from the
   campaign's own world; V02, V03, V12 as campaign host steps), R647 (K11's fuel controller and other-owner cap rows
   NOT_COVERED while the switches stay off), R648 (A08 without test properties; A09 from a separate labelled REF run
   with only the release-test property for the raw sampler).
5. **Record** a table of every changed note (checklist, row, old note, new text, source) and the notes left open
   with the reason, and run `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
