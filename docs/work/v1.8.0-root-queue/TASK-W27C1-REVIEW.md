# TASK W27c1-review: independent read-only review of W27c1 (the bay receipt store) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. A Claude session wrote W27c1 and both Codex slots are
busy, so this is a fresh Claude session with no context from the author (AGENTS.md section 13 allows that).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w27c1` at `f3e86bcf` (base `ffc134e1`) in `D:/GitHub/arce-v180-w27c1`. Read it with `git -C D:/GitHub/arce-v180-w27c1 diff ffc134e1..f3e86bcf`, `git show` and the file reader; never check out, commit or edit there |
| Task it answers | [TASK-W27C1](TASK-W27C1-BAY-RECEIPT-STORE.md) |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md) section 2 (the receipt store and the frozen admission contract), section 5's N5 command, section 6's insertion refusal and the W27c1 row of section 9 with its F3 matrix; [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) section 2 ("Bay" and "Contract delta") |
| Risk and ADR | R-039 in `docs/11-RISK-REGISTER.md` and ADR-065's "W27c1 implementation note" on the shared branch |
| Plan and record | `docs/work/v1.8.0-root-queue/records/W27C1-PLAN.md` and `records/W27C1.md` on the branch (section 5 lists the author's readings where the contract was open) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W27C1/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W27C1-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The codec** against section 2's table and bounds: every field and type, the 1,024-byte receipt (measured on the
   real encoding), the section and root bounds, depth and nodes, 4,096 receipts; the invariants (unique `op`, unique
   key, one receipt per satellite, REGISTERED needs all four hashes, released receipts cannot re-arm or retire online,
   unknown fields refuse); refusal never normalizes.
2. **The writer:** off-thread encoding, digest, staging, force, readback and replacement with only an immutable capture
   on the server thread; exact `(generation, digest, outcome)` completions; unknown outcomes; three unresolved failures
   making the store unavailable; drain at stop and abort on crash; at most one write start per 10 ticks; autosave never
   publishes queued intents. The author fixed a shutdown hang and a cancelled receipt keeping its retained slot: check
   both fixes and look for siblings.
3. **Startup:** the load-only lookup before the first tick, NOT_LOADED, NO_FILE and LOADED, and the whole-world refusal
   of a future or invalid root with its bytes kept. The author notes that BAY can register before server start when a
   ledger binds early: is the NOT_LOADED-then-load order still guaranteed, and can PROPULSION admission ever see a
   loaded authority while BAY is unloaded, or the reverse?
4. **The admission authority:** `tryReserve` unchanged for PROPULSION; `tryRetain` and its results; the pending and
   retained unions; the author's added non-charging `checkReserve` and `checkRetain` (do they keep the frozen results
   and the no-release rule, and can a check and a later reserve race?). Every existing PROPULSION test passed unchanged
   with BAY registered; confirm none was edited to pass.
5. **The insertion refusal** through the menu, hopper, dropper and item capability, simulated and actual, and that a
   REJECTED receipt never blocks.
6. **The N5 command:** permission, every refusal, the write through the writer, the retained slot freed only on the
   exact removal acknowledgement, the one audit line. The author refuses while any disassembly record exists, because
   those records name the physical entity: is that correct and sufficient?
7. **The author's readings in record section 5** (unique key as (rocket, bay), both before hashes in every phase,
   retry every 200 ticks only for unknown outcomes): does each match the frozen contract, or does it need Root's
   decision?
8. **W95's loader cut probe** now swaps in a test index through a gated hook, because the runtime registers BAY: is the
   hook gated and inert in production?
9. **R-039 and ADR-065's W27c1 note** against what was built; propose corrections.

## Rerun

Export the subject with `git -C D:/GitHub/arce-v180-w27c1 archive f3e86bcf` into a new folder under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W27C1-review/` and, there only, run with JDK 17.0.7
(`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: the new bay receipt test classes and the existing
`EscrowAdmissionAuthority*` and `EscrowRuntime*` unit tests. Run no GameTest, `runData` or server. Delete the export's
`build` and `.gradle` folders afterwards and keep only logs.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W27C1-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
