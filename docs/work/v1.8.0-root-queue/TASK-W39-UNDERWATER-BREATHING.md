# TASK W39: suit underwater breathing (C18a, ADR-066 section 3.3)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w39-underwater` |
| Branch | `codex/v1.8.0-w39-underwater` (local only; Root merges) |
| Ledger row | `event:PlanetEventHandler.playerTick(LivingUpdateEvent)`. That row is shared with the space safety return (W40), so it closes only when both merge |
| Record | `docs/work/v1.8.0-root-queue/records/W39.md` |

## Goal (ADR-066 section 3.3, the underwater bullet; read its full text)

- **Who qualifies.** A player wearing a full, recognized, equipped suit, whose oxygen debit
  succeeds. That player may restore vanilla underwater air.
- **The debit.**
  - At most once per 20 submerged ticks, sharing the existing oxygen cadence of
    `PlayerLifeSupportEngine` and `PlayerLifeSupportService`.
  - Vacuum plus underwater must not debit twice in the same cadence.
- **No suit, no breathing.** An empty or unsupported suit grants nothing. A failed debit grants
  nothing, and vanilla drowning continues.
- **Vanilla stays vanilla** for water breathing effects, the respiration enchantment, conduits,
  turtle helmets and aquatic mobs.
- **Non-players** get nothing here (section 3.3, first bullet).
- **The switch.** Use the existing classic life-support or equipment switch that already governs
  suit oxygen. If none fits, say so in the record and add no new key without Root.

Read the existing life-support code first (`atmosphere/life`, `atmosphere/server`,
`PlayerLifeSupportEngine`, the suit equipment in C18b). Put the decision in the pure engine where
it fits, and keep the Forge adapter thin.

## Tests

- **Unit tests** for the pure decision:
  - the cadence;
  - no double debit with vacuum;
  - an empty suit;
  - an unsupported suit;
  - a failed debit.
- **Native GameTests** with a real submerged player:
  - air is restored and exactly one debit happens per 20 ticks;
  - with no suit, vanilla drowning continues;
  - with an empty tank, vanilla drowning continues;
  - water breathing alone (vanilla) is untouched;
  - in vacuum plus water, there is one debit per cadence.
- **Regressions:** the existing life-support and suit suites pass unchanged.

## Commands

- The whole unit suite.
- `runData` under the lock, only if you add language strings.
- `runGameTestServer` under the lock, then the log checker.

## Not in this task

- The space safety return (W40).
- Other C18a rules.
- Status files, the ledger, ADRs and the risk register (Root).
