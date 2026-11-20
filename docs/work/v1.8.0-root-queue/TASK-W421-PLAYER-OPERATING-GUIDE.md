# TASK W421: player operating documentation for every device (census F1)

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w421`, branch `codex/v1.8.0-w421` from the shared branch |
| Decision | Root decision R572 (QUEUE row W421), following R564 |
| Sources | [MACHINE-CENSUS](../../releases/v1.8.0/MACHINE-CENSUS.md) section 3 (the devices whose documentation cell says "none standalone" or "developer/progression/overview only") and finding F1; `docs/releases/v1.8.0/JEI-APPLICABILITY.md` on branch `codex/v1.8.0-w419` (not yet merged; read it with `git show codex/v1.8.0-w419:docs/releases/v1.8.0/JEI-APPLICABILITY.md`); the existing player guides under `docs/` (for example [rocket-propulsion](../../rocket-propulsion.md), [CELESTIAL-DATA-GUIDE](../../CELESTIAL-DATA-GUIDE.md), the classic progression guide and the endgame operator guide) and the [README](../../../README.md); the code at the shared head (blocks, block entities, menus, multiblock patterns, recipes, language files); [KNOWN-ISSUES](../../releases/v1.8.0/KNOWN-ISSUES.md) |
| Output | A new player guide `docs/MACHINE-GUIDE.md` (English), linked from the README's documentation list; the record `docs/work/v1.8.0-root-queue/records/W421.md` |
| Write scope | The new guide, one link line in the README, and the record. No code, test, language file or other document. |
| Not allowed | Describing behaviour the code does not have. Any JVM, server or client. |

## What to write

For every device the census marks as lacking player documentation, one section indexed by its display name and
registry ID, written for a player, not a developer:

- what it does and where it sits in progression (what unlocks it, what it is for);
- how to build it: the multiblock pattern with its parts and ports, or the single block, and how it is powered;
- how to use it: inputs, outputs, the screen's controls, and the redstone or automation behaviour;
- what goes wrong and how it tells the player (the refusal messages and status lines the code gives, quoted from the
  English language file);
- what survives a restart, a chunk unload or breaking the block, and what does not (from the saved fields the census
  lists), and any known issue that applies.

Keep each value (energy rates, capacities, times, counts) as the code states it and cite nothing outside the
repository. Where a behaviour is unclear from the code, say so in the record instead of guessing. Devices that
already have a player guide get a one-line pointer to it.

## Checks

- Every registry ID the census lists for F1 has a section or a pointer; the record lists them.
- Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. Link only to tracked files.

## Rules

- Commit on the branch in one commit in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
