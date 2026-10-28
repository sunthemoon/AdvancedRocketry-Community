# TASK W182: close W179's menu and flight-intent authority gaps (F1 to F3)

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w182` (new; from the shared head named in your prompt, which includes W179) |
| Branch | `codex/v1.8.0-w182` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W182.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W182/` |
| Findings | the "Findings for Root" section of `docs/work/v1.8.0-root-queue/records/W179.md` |
| Matrix | `MenuAuthorityMatrixGameTests` (batch `menu_authority`) and `src/test/resources/menu-authority-exemptions.txt` |

AGENTS section 3.3 asks every C2S path to check the player, distance, permission, state and range. W179's matrix found
three gaps. Each `gap:W179-F<n>` cell still runs and asserts the gap, so it fails once the gap is fixed; remove its
exemption line then, so the cell asserts the refusal.

## What to do

1. **Design note first.** F3 changes which flight intents the server serves. Before any code, write a one-page note at
   the top of the record: each `RocketFlightAction`, where the client sends it (the flight menu, a key, an
   interaction), and the checks it gets after the fix. No packet format changes.
2. **W179-F1: spectators.** The electrolyzer, microwave receiver, satellite builder, satellite terminal, rolling
   machine, the six endgame device menus and the rocket flight menu refuse a spectator, both at opening and in
   `stillValid`, the way the other 16 menus already do. Reuse their existing check; do not add a second one.
3. **W179-F2: removed holders.** The electrolyzer and rolling machine menus stop being valid once their block entity
   is removed, even while a block of the same kind stands at the position, as the other block menus do.
4. **W179-F3: flight intents.** Every intent through `RocketFlightService.request` gets the checks that the
   landing-pad path has today:
   - the server thread;
   - a connected, living, non-spectator player that is not a fake player.

   An intent that only the flight menu sends also needs that menu open for that rocket and still valid. An intent
   that the client sends outside the menu keeps its own checks; name it in the note. The refusal code stays
   UNAUTHORIZED.
5. **Tests.**
   - The matrix: remove the 14 `gap:` lines; every cell must then refuse.
   - Add a GameTest per changed flight intent: a spectator is refused and, where the menu is required, so is a player
     without the open menu. Each test starts with a positive control.
   - Nothing else in an existing test changes, except where the record names the test and says why.

## Verification

- the new tests alone, failing first on the unfixed code;
- the whole unit suite;
- the `menu_authority` batch and every rocket flight batch, three times each, under the heavy-JVM lock.

Root's hosted run on the pushed branch stands in for a local complete GameTest. An independent read-only review
follows before merge.

## Not in this task

W179-F4 (vanilla's per-file data-pack errors) and F5 (a vanished tag's contents), any packet format, the client
screens, ADRs, status files and the ledger.
