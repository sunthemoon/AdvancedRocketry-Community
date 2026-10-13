# TASK W34: non-player atmosphere effects, `atmosphere_bypass`, spawn admission, `vacuumDamage`

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w34-nonplayer-atmosphere` |
| Branch | `codex/v1.8.0-w34-nonplayer-atmosphere` (local only; Root merges) |
| Ledger rows | `config:OXYGEN.entityAtmBypass`, `event:...CheckSpawn`, `config:OXYGEN.vacuumDamage` (W30 section 2.3) |
| Record | `docs/work/v1.8.0-root-queue/records/W34.md` |

## Goal

Implement these bullets of ADR-066 section 3.3 exactly, reading the section's full text:

1. **Non-player `LivingEntity` atmosphere effects.**
   - Reuse the existing atmosphere and exposure pure decisions.
   - No suit oxygen, unless supported real equipment is defined explicitly.
   - At most one exposure attempt per 20 entity ticks, with ADR-034 priority and opt-in, and
     no double suffocation or heat attempt.
   - Use entity lifecycle fields, or bounded service state. Never scan all entities or
     chunks as a separate pass.
2. **The `atmosphere_bypass` entity-type tag**, empty by default. It exempts the atmospheric
   effects only: not gravity, fire, drowning or unrelated damage.
3. **Spawn admission.**
   - Natural and spawner checks: tagged bypass entities pass.
   - Otherwise deny at a spawn eye cell that is unavailable, PENDING or not breathable, or for
     an opted-in hostile profile that no supplied sealed room protects.
   - Ordinary biome spawn tables still choose candidates; there is no extra global spawner.
   - Test and document the command and spawn-egg exceptions. Do not present them as natural
     spawn acceptance.
4. **`vacuumDamage`.**
   - COMMON, finite, 0 to 20 health points per scheduled second.
   - The default keeps the present engine's damage, per the proposal; it is not the legacy
     value of 1.
   - Zero disables vacuum damage only, not consumption, breathability or exposure.
   - Follow the WORKER-RULES configuration-key rule: an exact log expectation and the
     `CommonConfigTest` count.

Not in this task, because other tasks cover them: gravity for non-players and fall damage,
sleep and respawn, underwater breathing, and the space safety return.

## Tests

- **Unit tests:** the pure decision reuse, and the cadence of one attempt per 20 ticks.
- **GameTests:**
  - a zombie in vacuum takes the exposure effect at the stated cadence, and does not double
    with the player path;
  - a tagged bypass entity is unaffected by atmosphere but still affected by fire and
    drowning;
  - natural spawn is denied in vacuum, and allowed for a tagged type or in a sealed room;
  - the spawn-egg and command exceptions;
  - `vacuumDamage` = 0 stops damage but not exposure.

  Pace loading waits with `GameTestTickPacer`. Never schedule GameTest callbacks from inside
  a callback (W28 guard).

## Commands

- The whole unit suite once.
- `runData` under the lock, if anything generated changes.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- The other section 3.3 rules.
- Status files and the ledger.
