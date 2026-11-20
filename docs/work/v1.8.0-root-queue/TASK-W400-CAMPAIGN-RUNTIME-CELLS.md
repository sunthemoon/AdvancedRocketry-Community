# TASK W400: prepare the four Forge and JEI runtime cells for the final campaign

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w400`, branch `codex/v1.8.0-w400` from the shared branch |
| Decision | Root decision R534 (QUEUE row W400) |
| Sources | [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) sections 2 and 4.1; [W188](records/W188.md); [v100_compatibility_runtime.py](../../../scripts/v100_compatibility_runtime.py); [run_v180_compatibility_matrix.py](../../../scripts/run_v180_compatibility_matrix.py); the version document [V1.8.0](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) section 10 |
| Record | `docs/work/v1.8.0-root-queue/records/W400.md` |
| Write scope | A new script `scripts/prepare_campaign_cells.py`, a new test `tests/test_prepare_campaign_cells.py`, the record. Outside Git: `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/` only. No change to any existing file. |
| Not allowed | Starting a Minecraft server or client, Gradle or GameTest: the heavy-JVM lock belongs to candidate C4's native runs until about 2026-11-21. The official Forge installers that the preparer runs (with `-Xmx1G`) are allowed. Reading any launcher profile or account. |

## Why

The version document's section 10 requires the final candidate on four cells: Forge 47.4.10 and 47.4.23, each with
and without JEI 15.56.0.205 on the client. W188 prepared such a runtime once and deleted it after use. Phase 1 step
P3 needs it again, kept for the campaign, with one fixed folder per cell that the harness (W399) and the owner can
use.

## What to do

1. Check free space on C: and D: (at least 10 GB each). Run the existing preparer, unchanged:
   `python -B scripts/v100_compatibility_runtime.py D:/GitHub/ARCE-Task-Evidence/root-queue/P3/runtime --java "C:/Program Files/Java/jdk-17.0.7/bin/java.exe" --asset-cache D:/GitHub/ARCE-Task-Evidence/root-queue/W181/attempt-4/asset-cache`.
   It downloads only from its allowlisted hosts and checks every hash. Record the time it took, the size of the
   runtime and the SHA-256 of its `prepared.json`. If it fails, keep the log, say why, and stop.
2. Write `scripts/prepare_campaign_cells.py`. Given the runtime root, a candidate JAR, the candidate's expected
   SHA-256 and an output root, it creates four cells, `47.4.10-nojei`, `47.4.10-jei`, `47.4.23-nojei` and
   `47.4.23-jei`. Each cell has:
   - `server/`: a copy of the runtime's `servers/<forge>`, with the candidate in `mods/`. No JEI on the server: the
     cells differ by the client's JEI state, as in the version document's table and the matrix runner. Write
     `eula.txt` only when `--accept-eula` is given;
   - `client/`: an empty game directory with the candidate in `mods/`, plus the runtime's `jei.jar` for the two JEI
     cells, and an `options.txt` with English, GUI scale 3, sound on and the window size 1280 by 720;
   - `cell.json`: `schema_version` 1, `forge`, `jei` (true or false), `runtime_root`, `server_dir`,
     `client_game_dir`, `version_id` (the Forge client version ID the runtime's profile gives), `candidate`
     (`path`, `sha256`) and `jei_jar` (`path`, `sha256`, or null), plus the SHA-256 of every file in both `mods`
     folders and of the server's Forge argument file. W399's harness reads exactly these fields.
   The script refuses a candidate whose hash differs, refuses to overwrite an existing cell, and has a
   `--replace-candidate` mode that swaps only the candidate JAR in existing cells (hash-checked) and rewrites
   `cell.json`, for a later candidate. Paths must stay inside the output root; refuse links and junctions, as the
   preparer does.
3. Run it for C4 into `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells` with `--accept-eula`. C4's JAR is
   `D:/GitHub/ARCE-Task-Evidence/root-queue/C4/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`,
   SHA-256 `c048743e61da4f939ce0492ca3ec73b7d69ef1634577bebc3279c91cf506cea1`. Copy, never move, the JAR.
4. Do not start any cell. Starting each cell once is a later step under the lock.
5. Unit tests on a small fake runtime tree: the four cells and their fields, a wrong candidate hash, an existing
   cell, `--replace-candidate`, a link inside the runtime, and JEI only in the JEI cells' client folders. Run
   `python -B -m unittest discover -s tests -p test_prepare_campaign_cells.py -v` and
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.
6. Record the commands, times, sizes, every `cell.json` hash, free space before and after, and what the later
   start check must look at.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
