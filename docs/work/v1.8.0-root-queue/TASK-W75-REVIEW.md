# TASK W75-review: independent read-only review of the W11b3 design note

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W11B3-DESIGN.md` on branch `codex/v1.8.0-w75-w11b3-design`, commits `918d5e9e` (note) and `df3babeb` (record `records/W75.md`) |
| Assignment the note answers | [TASK-W75](TASK-W75-W11B3-DESIGN-NOTE.md), items 1 to 11 |
| Code it cites | The shared branch at `0c502197`, and the W11b2 branch at `d178e727` (read W11b2 only through Git: `git show d178e727:<path>`, `git grep <pattern> d178e727`) |
| Root decisions it uses | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md`, SHA-256 `1cff184db88c738e8f697ed793298f14bf50a9e444c52ef4d851b05e066ac64d` |
| Checkout | `D:/GitHub/arce-v180-w75-review`, detached at `df3babeb`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W75-review/REVIEW.md` (create the folder), and its SHA-256 on the last line of your answer |

## What to check

1. **Completeness.** Does the note answer each of TASK-W75's eleven items? Name any it skips or only
   gestures at.
2. **Accuracy.** Check each code citation at the cited commit. Say which claims are wrong, in particular:
   - that there is no production admission entry point at `d178e727`;
   - the state of F3, F8, F9, N1 and N2 (section 1);
   - that `fluid_fuel_loader` differs from `block_variant:loader/5` and from the existing `fuel_loader`.
3. **Safety of the design** (AGENTS.md section 3.3):
   - every C2S path is checked for player, distance, permission, state, range and loaded chunks, with
     no client-supplied position forcing a chunk load;
   - every persisted root has a schema version and size, depth and node limits;
   - no path moves fuel before an acknowledged L1, and no refusal mutates a store;
   - any protection that refuses, drops or isolates player data has a risk-register proposal with scope,
     duration, log volume and recovery.
4. **Consistency with the W11b2 contract** (`W11B2-DESIGN-2.md` at `d178e727`) and with Root's R1 to R4.
   Flag anything that would change an acknowledgement order, a proof, a hold or the replay exclusion.
5. **The shared authority** with the satellite bay (TASK-W27A2 F3, W27a design section 2): is the move
   of `EscrowAdmissionAuthority` to `EscrowRuntime` sound, and does it leave W27c1 what it needs?
6. **Provenance:** no upstream or LibVulpes copy without a row first; the proposed art and recipes are
   allowed by `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`.
7. **Feasibility and size:** can the leaves in section 5 be built and verified one at a time? Is any leaf
   too large for one implementer session? Is the S2 cut list right and complete?
8. **Readability:** the note is dense. Say where a missing word or an undefined short form would make an
   implementer guess (AGENTS.md section 10).

Take no heavy-JVM lock. Run no Gradle build, GameTest or native server. Reading files and Git are enough.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- Findings, each with an ID, a severity (High, Medium, Low, Info), the section and line of the note
  (and the code file and line where relevant), the problem, and a suggested change.
- A table of TASK-W75's items 1 to 11: answered, partly, or not.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
