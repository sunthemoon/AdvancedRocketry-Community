# TASK W419: JEI information for the non-process operations, with a per-device applicability note

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w419`, branch `codex/v1.8.0-w419` from the shared branch |
| Decision | Root decision R570 (QUEUE row W419), following R564 |
| Sources | [MACHINE-CENSUS](../../releases/v1.8.0/MACHINE-CENSUS.md) finding F5 and its section 3 (which devices have custom JEI categories and which have only vanilla acquisition recipes, with reasons); the JEI plugin (`ArceJeiPlugin` and its categories); [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) section 3.3; the version document's section 6 step 4; the language files (`en_us`, `zh_cn`) |
| Record | `docs/work/v1.8.0-root-queue/records/W419.md` |
| Write scope | The JEI plugin package (new information entries or a small information category), the two language files for the new keys, unit tests, the record, and a new applicability table in `docs/releases/v1.8.0/JEI-APPLICABILITY.md`. No change to recipes, machines, saved data or other client screens. JEI stays an optional dependency: the mod must load and behave identically without it. |
| Not allowed | Inventing process recipes for passive devices. A full GameTest run, `runData` or a native server (compiling and unit tests are allowed). |

## What to build

1. **Applicability table** (`JEI-APPLICABILITY.md`): every device from the census, and for each whether it needs a
   custom JEI category (it processes recipes), an information entry (its operation is a catalog conversion, an
   assembly or a service the player must understand), or nothing beyond its vanilla crafting recipe (a passive part),
   with one line of reason.
2. **Information entries** for the operations the census names as missing: combustion fuel credit, finite gas and
   vent conversions, workstation assembly, satellite build and research, and the endgame catalog operations. Use JEI's
   ingredient information mechanism (or one small information category if a table is clearer), with text that
   matches what C5's code does (read the code; quote the values it uses, for example fuel credit per item). English and
   Simplified Chinese, the same keys in both.
3. **Tests**: unit tests that the plugin registers the new entries without JEI present at runtime failing anything,
   that every new key exists in both language files, and that the quoted values match the code's constants.
4. Compile, run the unit suite for the touched packages, and `python -B scripts/validate_repository.py`, which must end
   with `0 failed`. The real display is checked in the campaign's JEI cells (C31); say so in the record.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
