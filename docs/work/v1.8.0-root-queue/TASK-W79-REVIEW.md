# TASK W79-review: independent read-only review of the cargo loader design note

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W11CD-DESIGN.md` on branch `codex/v1.8.0-w79-cargo-design`, commits `51338beb` (note) and `5ab7b4a3` (record `records/W79.md`) |
| Assignment the note answers | [TASK-W79](TASK-W79-CARGO-LOADERS-DESIGN.md), items 1 to 10 |
| Contract it builds on | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), revision 2 (frozen), and Root's reservation decisions R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` |
| Code it cites | The shared branch, and W11b2 at `646bf9ee` (read W11b2 only through Git) |
| Checkout | `D:/GitHub/arce-v180-w79-review`, detached at `5ab7b4a3`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W79-review/REVIEW.md` (create the folder), and its SHA-256 on the last line of your answer |

## What to check

1. **Completeness** against TASK-W79's ten items.
2. **Accuracy** of each code citation at the cited commit, in particular the note's three findings: no Fluid
   cargo exists yet (a rocket containing a `pressurized_tank` is rejected at assembly); the escrow core
   handles only fuel and assumes the rocket is always the destination; and a cargo operation would strand a
   landed rocket, because the transfer service matches it to its arrival record by snapshot content hash.
3. **The identity rule (leaf C-I).** It changes recovery identity. Check it against W11b2's design 2 and the
   transfer recovery code: can it lose, duplicate or wrongly recover a rocket, a passenger or a resource? Does
   it change an acknowledgement order, a proof, a hold or the replay exclusion?
4. **Safety** (AGENTS.md section 3.3): C2S checks, bounded persisted roots with schema versions, no movement
   before an acknowledged L1, no mutating refusal, and risk proposals for any protection that refuses, drops or
   isolates player data. Item stacks with NBT need bounds.
5. **Disassembly and launch:** cargo is restored exactly once, and unloading then materializing the original
   snapshot cannot recreate cargo, including at each crash point.
6. **The twelve open questions:** for each, say whether the recommended answer is sound.
7. **Feasibility:** can each leaf be built and verified on its own, and are the S2 cuts the applicable ones?
8. **Readability** (AGENTS.md section 10): undefined short forms, fragments, overloaded IDs.

Take no heavy-JVM lock. Run no Gradle build, GameTest or native server.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- Findings, each with an ID, a severity (High, Medium, Low, Info), the section and line of the note (and the
  code file and line where relevant), the problem, and a suggested change.
- A table of TASK-W79's items 1 to 10: answered, partly, or not.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
