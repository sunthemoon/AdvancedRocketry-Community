# TASK W96-review: independent read-only review of the P1-N3 design note

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. Codex wrote the note, so this is a fresh Claude
session with no context from the author.

| Item | Value |
|---|---|
| Subject | [records/W96-DESIGN.md](records/W96-DESIGN.md) at the shared branch's `20eb4b9c` (SHA-256 `cb684465f2811f512607af9391a0c61b7fd0bbbc3945e95f320fce1429134fd1`) and its report [records/W96.md](records/W96.md) |
| Task it answers | [TASK-W96](TASK-W96-SOURCE-RETURN-DURABILITY-DESIGN.md) (Root decision R33) |
| Code baseline | `93eb3ad2`, as the note cites it; read it in the main checkout `D:/GitHub/AdvancedRocketry-Community` without changing anything, or with `git show 93eb3ad2:<path>` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91K/` (the N2 after-move and N2 UNKNOWN cuts) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W96-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The inventory (section 2):** is every path that retires or advances a transfer record ahead of the entity image
   recovery would need listed and correctly described? Spot-check the cited lines. Is I8's and I9's protection
   described correctly?
2. **The rejection of option C** (a load-time repair) and of B alone. In particular, judge a narrow variant the note
   does not analyse (first check whether the rocket entity's saved data names its transfer or request ID, and if it
   does not, what identity it does carry): on load an entity whose
   named transfer is absent from an acknowledged, operational journal, in a source-side state (COUNTDOWN, ASCENT or
   TRANSIT before destination authority), with no escrow authority, returns to its own snapshot's origin with its own
   resources. Does such a rule close I1 (the measured W91K cases) safely, which of the note's objections still apply to
   it, and what does it leave open in I2, I3, I6 and I7?
3. **The cost and the player-visible effect of the recommendation (A+B).** A countdown cancellation with a player
   aboard is common. Under the note, crewed retirement stays blocked until a new online-player checkpoint exists. Say
   what a player would see in each crewed case under the recommendation, whether that is acceptable for v1.8, and
   whether a smaller first leaf could fix the uncrewed and crewed I1 cases without that adapter.
4. **The storage integration:** reusing the escrow retained-store, force and readback machinery for ordinary worlds
   (section 4, "Storage integration"); whether the proposed sharing is sound and bounded, and what it risks for the
   governed paths.
5. **Design M (I6) and I7:** whether the proposed pre-retirement checkpoint is correct and needed in v1.8, or can
   follow as a named open item.
6. **The native test matrix and the proposed R-035 and ADR-065 text:** completeness, and any overclaim.
7. Give Root a concrete recommendation for the first implementation leaf: its scope, what it fixes, what stays open.

## Output format

- A verdict: FREEZE, FREEZE-WITH-AMENDMENTS, or REVISE.
- Findings, each with an ID (W96-R1, ...), a severity, the section and line, the problem, and a suggested change.
- The recommended first leaf.
- The commands you ran and their results; run no GameTest or server.
- The SHA-256 of REVIEW.md on the last line of your answer.
