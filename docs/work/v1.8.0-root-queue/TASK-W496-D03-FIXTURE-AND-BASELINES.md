# TASK W496: author D03's unsupported-container fixture and prepare the baseline profiles for W491's captures

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w496`, branch `codex/v1.8.0-w496` from the shared branch |
| Decision | Root decision R718 (QUEUE row W496), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Rules | Everything in [TASK W486](TASK-W486-PHASE2-ROUND6-AUTHORING-CONTINUED.md) and the tasks it names applies, above all R690's per-step scoring and the ban on starting any client, server or other JVM |
| Sources | QUEUE rows W490 and W491 (R714, R715), [W491](records/W491.md) and its runbook `authoring-w491/OPERATOR-RUNBOOK.md`, checklist D's D03 section in `records/CAMPAIGN-CHECKLISTS-D.md`, round 06's D03 inputs (sealed: copy, do not edit), the real-client harness installation on D: and C6's rocket scan code under `src/` (read only) |
| Outputs | Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w496/`, in round 06's formats, with an inventory `delivery-inputs.json`. Tracked: the record `docs/work/v1.8.0-root-queue/records/W496.md` |
| Time | Deliver by **21:00** local time |

## Why

- **D03.** Rounds 05 and 06 put a furnace beside D03's rocket. In C6 a block joins a rocket only if the
  `rocket_movable` tag lists it, and the furnace is not listed, so the scan never saw it and validation succeeded
  (R714). Checklist D asks for a known unsupported container inside the rocket.
- **Baselines.** W491's frame-time captures compare against an empty Vanilla 1.20.1 client and an empty Forge client
  without this mod or JEI (R715). Those profiles do not exist yet.

## What to do

1. **D03's fixture.**
   - From C6's source, name a block with a block entity that a data pack can add to
     `advancedrocketrycommunity:rocket_movable` and that has no rocket block-entity adapter. Cite the tag, the
     adapter list and `RocketStructureScanTask`'s `UNSUPPORTED_BLOCK_ENTITY` branch.
   - Author a world data pack that adds it to the tag, and a D03 scene that puts that block inside the rocket's
     structure. Keep steps 1 and 2's original actions and checks: a readable bounded refusal naming the unsupported
     condition, and the source unchanged.
   - Keep step 5's healthy variant separate, without the data pack.
   - Steps 3 and 4, the rejected provider, stay NOT_COVERED (C16).
   - Test that the scene puts the block inside the scanned structure.
2. **The baseline profiles.**
   - From files already on this machine only, prepare two disposable client profiles in `authoring-w496/profiles/`:
     - Vanilla 1.20.1;
     - Forge 47.4.22 with an empty mods folder.
   - Download nothing and copy no account or launcher login.
   - Record each profile's version files and their SHA-256, and what W491's adapter checks to tell them from a C6
     profile.
   - If a needed file is missing locally, stop that part and say which file.
3. **Bind W491's captures.** Write the bindings W491's runbook names for these profiles and the D03 scene, so that
   Root can add both to round 08. Do not edit `authoring-w491/`; put the bindings in `authoring-w496/`.
4. **Check.** Run the dry validator on the new rows and the operator tests, and write the inventory.
5. **Record:**
   - the fixture with its source citations;
   - the profiles and their hashes;
   - the bindings;
   - the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task, PresentMon or other JVM. Make no change under `src/`
  (frozen at C6, R600) or `scripts/campaign_client/`.
- Never write inside rounds 01 to 07, `authoring-w491/` or the judgment packages. Round 07 runs tonight.
- Do not change the owner's launcher, accounts or installed game folders: copy into `authoring-w496/` instead.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
