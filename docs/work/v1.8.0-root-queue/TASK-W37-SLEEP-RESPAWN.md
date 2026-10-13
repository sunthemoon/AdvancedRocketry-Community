# TASK W37: sleep admission and the two planet-respawn options (ADR-066 section 3.3)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w37-sleep` |
| Branch | `codex/v1.8.0-w37-sleep` (local only; Root merges) |
| Ledger rows | `config:PLANET.allowPlanetRespawn`, `config:PLANET.forcePlanetRespawn`, `event:...sleepEvent` (W30 section 2.3) |
| Record | `docs/work/v1.8.0-root-queue/records/W37.md` |

## Why

W30 found that every pre-handover sleep commit is merged, but no sleep behaviour exists: no
handler for `PlayerSleepInBedEvent` or `CanPlayerSleep`. Root's disposition (QUEUE.md and W30
section 3) is to re-dispatch the behaviour as one W task, using the existing owner decisions as
inputs:
- `docs/work/v1.8.0-c18a-sleep/OWNER-DECISION-01.md`, `-03.md`, `-05.md` and `-07.md`;
- `D1-OUTCOME-FREEZE-33.md`.

Read all of them first. Where a decision record and ADR-066 section 3.3 disagree, stop. Record the
question for Root instead of choosing.

## Goal: ADR-066 section 3.3, the sleep bullet (read its full text)

- **Sleep.** Deny sleep at the sleeping player's actual cell when that cell is unbreathable or
  PENDING, on a non-Overworld mapped surface or in Space. Existing Overworld bed rules stay.
- **`allowPlanetRespawn`.** Default false.
  - Validate a real saved bed or anchor, the destination profile, and a safe loaded arrival.
  - If it is true, accept only a breathable destination, unless `forcePlanetRespawn` is true.
- **`forcePlanetRespawn`.** Default false.
  - It is a respawn option only; it is **not** permission to sleep without air.
  - It bypasses the air check only. It never bypasses solid or liquid hazards, station
    permission, a missing body or an unloaded unsafe arrival.
- **Fallback.** The existing safe Overworld respawn, never an arbitrary coordinate supplied by
  the player.
- **Configuration keys.** Follow the WORKER-RULES configuration-key rule: an exact log
  expectation and the `CommonConfigTest` count.

## Tests

- **GameTests:**
  - sleep is denied in vacuum on a planet and in Space;
  - sleep is allowed in a sealed breathable room;
  - Overworld sleep is unchanged;
  - with both options false, respawn falls back to the Overworld;
  - with allow true, a breathable planet bed is accepted and an unbreathable one refused;
  - with force true, air is bypassed but a hazardous or unloaded arrival still falls back;
  - a missing body falls back.

  Pace loading waits with `GameTestTickPacer`. Never schedule GameTest callbacks from inside a
  callback (W28 guard).

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- The other section 3.3 rules: underwater breathing, the space safety return, and gravity.
- Status files and the ledger.
