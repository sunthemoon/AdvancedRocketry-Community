# TASK W179: the menu-authority matrix and the data-pack over-limit reload (VDOC 12.1 and 12.4)

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w179` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w179` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W179.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W179/` |
| Scope source | section 3, B4a and B4b, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`) |

These are two release-evidence tests that the v1.8 version document asks for and nobody has dispatched (W62 named them).
Read the v1.8 version document's sections 12.1 and 12.4 first.

## What to do

1. **The menu-authority matrix (B4a).**
   - Enumerate every `MenuType` the mod registers; there are about 25, in `registry/ModMenuTypes.java`.
   - For each, a GameTest checks that opening or acting on the menu is refused for each of these: out of distance, a
     spectator, a holder that is not loaded, a foreign owner, and the rate check. Follow how `OperatorItemNbtGameTests`
     covers item NBT.
   - Menus without a holder go in a reasoned exemption file under `src/test/resources/`, one reason per entry.
   - The test must fail when a new menu type is registered without either coverage or an exemption.
   - **Tests only.** A real gap you find goes in the record as a finding for Root, not as a fix.
2. **The data-pack over-limit reload (B4b).**
   - Reload a generated test data pack that holds many oversized or invalid classic recipes, tags and patterns.
   - Assert that the reload completes, that every bad entry is refused, and that the logging stays bounded
     (docs/17 section 5).
   - Give each deliberate ERROR or WARN its own exact expectation in `scripts/gametest_expected_log.json`, and update
     the pinned total in `tests/test_check_gametest_log.py`.

## Verification

- Each new GameTest class in its own batch, three times.
- The log checker's Python tests.
- One complete GameTest with the unchanged checker (R131 applies).
- The whole unit suite.

Heavy runs go under the heavy-JVM lock with the hand-off rule.

## Not in this task

Production changes, the JEI matrix (B4c), ADRs, status files and the ledger.
