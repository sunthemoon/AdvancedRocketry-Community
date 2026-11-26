# TASK W487: trace the phase 2 visual findings to C6's code and propose fixes, without changing code

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w487`, branch `codex/v1.8.0-w487` from the shared branch |
| Decision | Root decision R708 (QUEUE row W487), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | QUEUE rows W482 and W485 (Root decisions R704 and R707), [W482](records/W482.md), [W485](records/W485.md), [W241](records/W241.md), the defect lists `phase2/judgment-r04-r05/visual-defects.json` and `phase2/judgment-r04-r05-coverage/visual-defects.json` under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/`, and the C6 source at commit `c8fa16e16be87554f26c402546733df6555d15e6` |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W487.md` only |

## Why

Phase 2 found four step failures (F-W482-01 to F-W482-04) and 37 noted visual defects. `src/` stays frozen at C6
(R600). The fixes go into one later candidate decision after phase 2's first full pass, so that one candidate and
one targeted re-verification cover them all. This task prepares that decision. It changes no code.

## What to do

1. **Sort every entry of both defect lists** into one of three kinds:
   - **product:** this mod's code, data, language files or assets;
   - **vanilla:** Minecraft or Forge behaviour, such as the chat-security toast or advancement toasts, with nothing
     for this mod to change;
   - **capture:** how the harness took the frame.

   Merge entries that describe the same defect.
2. **For each product defect and each of F-W482-01 to F-W482-04, trace it to C6's code or data.** Name:
   - the file and line, with `git show c8fa16e16:<path>`;
   - the cause;
   - the smallest fix, as a sketch in prose or a short diff in the record, not applied;
   - the screens or models the fix touches;
   - whether it touches saved data, network packets, registries or language keys;
   - the tests that would cover it, existing or new;
   - the steps and rows that would need re-verification on a real GPU.
3. **For F-W482-01 (the precision assembler's hull beside its structure):**
   - read W241's placement table and its design note that redesigned patterns can be smaller than the legacy
     hulls;
   - work out where the hull is drawn relative to the 3 by 4 by 3 pattern for each facing;
   - propose a placement that matches the pattern, or say why none can;
   - say whether the other formed hulls named in W241 can have the same mismatch, from their patterns and
     placements.
4. **For F-W482-03 (the data-pack test body shown as a raw identifier):**
   - find how body names are resolved;
   - propose how a data-defined body without a translation can be shown readably and marked as test content, as
     v1.1's check "数据包新增测试天体后 UI 可发现但明确标记测试内容" asks.
5. **Rank the product defects** by player impact, from a visibly wrong model or unreadable instruction down to
   cosmetic, with a one-line reason each.

## Rules

- Read only. Do not change `src/`, tests, scripts, data or any evidence. Do not start any Minecraft client, server,
  Gradle task or other JVM.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push. Run
  `python -B scripts/validate_repository.py` before committing; its `Summary:` line must show `0 failed`.
- The last line of your answer is the path of the record.
