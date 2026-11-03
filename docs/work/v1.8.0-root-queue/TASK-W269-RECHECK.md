# TASK W269-recheck: recheck the campaign gate after its review corrections (W269-R1 to R5)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w269` at `a50576dca`: W269-fix's source commit `afb454240` and record commit `a50576dca` on top of `3f639257a`. Review worktree `D:/GitHub/arce-v180-w269-recheck`, a detached checkout of `a50576dca` that Root created |
| Tasks | [TASK-W269-FIX](TASK-W269-FIX.md) and [TASK-W269](TASK-W269-V2-CAMPAIGN-GATE.md) (R298) |
| Earlier review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W269-review/REVIEW.md` (SHA-256 `3ebf5a25f1854487a78eff2785b361b5c05bee7551b9cc004a97a11008e86bf9`) |
| Records | The "W269-fix" section of `records/W269.md`, `records/W269-DESIGN.md` and `records/W254-V2-CHECKLIST.md` section 0.1 on the subject branch; evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W269-fix/`; the revised ADR-065 note draft `D:/GitHub/ARCE-Task-Evidence/root-queue/W269/ADR065-NOTE-DRAFT-FIX.md` (SHA-256 `d6deae60a85e72d354db7c4966865790e82507d546f106a851b51fd420a2e493`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W269-recheck/REVIEW.md`, and its SHA-256 on the last line of your answer |

The change widens a release-test gate in production source; keep reviewing it as a security boundary.

## What to check

1. Each of W269-R1 to R5 is fixed as the earlier review asked, or the record says why not.
2. **R1's wording.** The design, record, checklist and the revised note draft state exactly what the open gate
   reaches on a marked campaign world (the single full release-test gate behind the gated op-only command families,
   and D0's bay deployment admission), and claim nothing wider or narrower. Check the five changed Javadoc comments
   against the code they describe, in particular the claim that the loader admission switch still needs its own
   property.
3. **R1's test.** `BayV2CampaignAdmissionTest` drives the real ascent-completion path for a named, non-facility owner,
   admits only with both a valid selection and the marker, and would fail if the campaign exception were removed.
4. **R3 and R4.** The new marker tests fail against a length-only check, the symbolic link case really runs on this
   machine, and `ownsAllConnectedPlayersOr` with an empty selection is the old strict expression.
5. **R2 and R5.** The online-mode, whitelist and network prerequisites, the exit-status note, and the marker command
   and its size and SHA-256 are correct; compute the hash yourself.
6. Nothing outside the files the task names changed relative to `3f639257a`.

## Rerun

In fresh clones or your review worktree only: the campaign gate and admission tests, the bay, escrow, release-test
and first-use dependency tests, the whole unit suite and the repository validator. Set `JAVA_HOME` to
`C:/Program Files/Java/jdk-17.0.7` first (the shell default points at a missing JRE). Do not run GameTests, servers
or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK) on whether the branch can be merged. Findings with an ID
(W269-R6, ...), severity, file and line, problem and suggested change. The commands you ran and their results. The
SHA-256 of REVIEW.md on the last line.
