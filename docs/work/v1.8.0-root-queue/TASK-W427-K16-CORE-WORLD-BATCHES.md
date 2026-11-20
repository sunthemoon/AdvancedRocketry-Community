# TASK W427: package K16 part 1, the core route world and the regenerated batches on C6 (no clients)

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w427`, branch `codex/v1.8.0-w427` from the shared branch |
| Decision | Root decision R588 (QUEUE row W427) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K16, choices C2 (with Root's R535 correction: windowed 1280 by 720 at GUI scales 2 and 3, fullscreen at scale 4) and C22, rows A-2, E-8, I-1 and J-11; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI16 and section 4; [J21-FINDING-MAP](records/J21-FINDING-MAP.md) section 4 (the rechecks); the step lists [A](records/CAMPAIGN-CHECKLISTS-A.md), [E](records/CAMPAIGN-CHECKLISTS-E.md), [I](records/CAMPAIGN-CHECKLISTS-I.md) and [J](records/CAMPAIGN-CHECKLISTS-J.md); the old harness's builders and batches outside Git (`D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/` and `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/scripts/`, `gen*.py`, `final_world6.py`); the tracked harness [scripts/campaign_client/](../../../scripts/campaign_client/README.md) and its request protocol; C6's identity in QUEUE row "C6 run" (R581) |
| Outputs | A generator `scripts/campaign_prep/k16_core.py` (world-building console commands and request batches, deterministic) with `tests/test_campaign_prep_k16.py`; outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K16/`: the core route world (built on a copy of cell `47.4.10-nojei`'s server under C6), its manifest (coordinates, devices, players, hashes), the control manifest and the batches with `SHA256SUMS.txt`; the record `docs/work/v1.8.0-root-queue/records/W427.md` |
| Lock | Building the world runs a server: take the heavy-JVM lock with the WORKER-RULES loop (C6's campaign yields it for 120 seconds after each family), hold it only while your server runs. |
| Not allowed | Starting any client (part 2, the P9 dry run, starts clients later). Changing product code, existing scripts or the cells in place (work on copies). |

## What to build

1. **The core route world** (FI16's role, rebuilt on C6): from the old builders, the scene the step lists' core rows
   need, from the first machines to one endgame device (C29: the railgun pair), with each device's coordinates, owner
   and state in a manifest. Build it through the server console on a copy of the `47.4.10-nojei` cell's server with
   C6's JAR; save, stop, hash the world, and keep the source world unchanged afterwards (runs use copies).
2. **The control manifest**: for each screen the batches use, the controls' positions computed for the C2 viewports
   (1280 by 720 at GUI scales 2 and 3, fullscreen 1920 by 1080 at scale 4), from the screens' layout code, not from old
   pointer coordinates; state the computation.
3. **The batches**, in the tracked harness's request format: the regenerated core-route batch, one single-player round
   for the P9 dry run (C22's integrated pause branch included), and the J21 recheck batches from the finding map, each
   step naming the checklist row it serves. Historical regex waits become literal substrings, as the harness requires.
4. Unit tests for the generator (determinism, the viewport computation on known layouts, the batches' schema), and
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
