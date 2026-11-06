# TASK W296: the "experimental settings" warnings when a single-player world is created

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w296`, branch `codex/v1.8.0-w296`, created by Root from the shared head |
| Decision | Root decision R333 (QUEUE row W296) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 5 finding 5-2 (in Chinese; screenshots `p11` and `h5` under `evidence/`) |
| Record | `docs/work/v1.8.0-root-queue/records/W296.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W296/` |

## The finding

Creating a single-player world with the mod shows vanilla's "Warning! These settings are using experimental features
... Do you wish to proceed?", and the first load shows "Worlds using Experimental Settings are not supported ... Here
be dragons! / This message will not show again for this world". After confirming both, the world works. The reporter
guesses, from reading only, that the cause is the six dimensions the JAR ships as datapack files
(`data/advancedrocketrycommunity/dimension/*.json` and `dimension_type/*.json`), because vanilla 1.20.1 marks world
settings with custom dimensions as experimental. No project document mentions the warnings, and a player may take
them to mean the mod is unstable.

## What to do

1. **Confirm the cause** from the Minecraft 1.20.1 and Forge 47 code paths that decide these screens (the world
   creation confirmation and the first-load backup warning), and say exactly which condition the mod's data meets.
   Read decompiled sources from the Gradle cache or the ForgeGradle mappings; copy no Mojang code into the repository.
2. **Look for a supported way** to mark the mod's dimensions as stable in 1.20.1 without a Mixin, a coremod or an
   access transformer change that AGENTS.md section 3.3 would forbid (for example a Forge option, a lifecycle field in
   the data, or registering the levels another supported way). If one exists, describe it with its trade-offs and the
   saved-world compatibility impact in a short proposal in the record; do not implement it in this task.
3. **Document it for players** either way: in the player-facing documentation that covers installing and first worlds
   (find it; `README.md` if nothing better exists), say what the two warnings mean, that they are expected with this
   mod's dimensions, that the world is not damaged by confirming them, and what "This message will not show again"
   means. Use plain words for players; add the Chinese text where the project keeps Chinese player documentation.

## Verification

`python -B scripts/validate_repository.py --require-approved-identity`. No code change is expected; if you change any,
run `./gradlew build` with all unit tests too.

Commit on the branch; do not push. Root reviews and merges; Root decides on any proposal from step 2.

## Not in this task

Implementing a lifecycle change, the dimension data itself, the ledger, the status file and QUEUE.
