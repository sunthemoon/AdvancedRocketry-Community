# TASK W159: W80-rejoin core, protected login, placement and exact readback (third leaf, part 1)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), the frozen design
[records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3; sections 1, 4, 5, 6 and Appendix A), the W80-storage and
W80-release records ([records/W157.md](records/W157.md); `records/W158.md`, merged `b371c9bc1`), and the W80 note in
ADR-065 and the W80 part of R-035 first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w159` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w159` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W159.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W159/` |

## Root decision R105 (2026-10-25): W80-rejoin is split in two

The design's W80-rejoin leaf is too large for one reviewable slice. **W159 (this task)** delivers the rejoin core and
drives it through W158's gated internal entry point. **W160 (later)** adds the `release-absent` command adapter with
the mandatory execution-time check, the read-only `rejoins` view, the command matrix, and the combined native matrix.
No command and no player-facing admission are in W159.

## What to do

1. **Step 0:** check the design's section 4 assumptions against the pinned Forge 47.4.10 and the merged code:
   - the entity-join and login order before the `RootVehicle` remount;
   - `MobEffectEvent.Applicable` from `canBeAffected`;
   - `PlayerList.remove` firing the logout event before `save`;
   - `saveAll` calling `save(ServerPlayer)`;
   - `SaveToFile` firing after `safeReplaceFile`;
   - the autosave order after `tickChildren`.

   Run the Windows overlap test early: a scan held open across two consecutive vanilla saves of the same player, with
   both saves replacing the file. If an assumption fails, or the overlap test fails on this Windows, stop and report.
   The design says that blocks the leaf.
2. **The guard:**
   - the bounded player index (256);
   - a session guard installed at the first join or login callback, before ordinary reconnect;
   - stop riding, pin the position, clear motion, fall, fire and freezing, and cancel damage;
   - deny harmful effects through `MobEffectEvent.Applicable`;
   - record the effects held at guard install and clear the effects gained while guarded before unguarding;
   - suppress boarding and interaction;
   - the 200-tick deadline per login, with a protected disconnect and a clear message.
3. **Placement:**
   - 64 deterministic candidates (32 at the pad, 32 at the Overworld spawn), with the full safety check, rechecked
     before the teleport and before unguarding;
   - at most two tickets per player and 16 in total;
   - checked generation P1, then placement unmounted and the marker `ForgeData.arce_passenger_rejoin` (schema 1, at
     most 160 bytes) set through `getPersistentData()`;
   - `REJOIN_CONFLICT` refusal without choosing a winner;
   - boarding and flight refused for a player of AP until their placement is VERIFIED.
4. **Prompt saves and readback:**
   - `PlayerList.saveAll()` once after a placement, then at most once per 100 ticks, never while a scan is open;
   - re-pin at the four points the design lists;
   - the streaming scanner on one daemon thread, with the 64 MiB, 512 MiB, depth 512 and 10-second ceilings and the
     size and mtime check;
   - the exact proof;
   - checked generation P2, then marker removal and unguarding;
   - stale-marker removal at login;
   - death and clone handling.

Queue: 128 slots and four owner-thread actions per tick. The log budget and player messages follow design section 6;
UUID lists are capped at 16.

## Limits

- No Mixin, access transformer, reflection on the player writer, or offline player-data writer.
- No block writes, beds, respawn anchors or granted equipment.
- No new saved format beyond W157's schema 1: P1 and P2 are the generations that schema already defines. Stop and
  report if they are missing.
- No change to the journal root, bounds, budgets, timeouts or protocols.
- Legacy records only.
- Each step in its own commit, with a test that fails before it.
- New log lines get exact manifest entries.
- Player-facing text goes in the language files (`en_us` and `zh_cn`).

## Verification

- The design's A0 tests for this leaf: the scanner bounds, fake-world safety and budgets, the write-failure cases
  separately, and the proof rules.
- GameTests from Appendix A's list for placement and readback. Cover at least:
  - logins before S1's acknowledgement, during B0 and after a dropped admission;
  - low-health logins with lava, fire, void, obstruction and a wither rose at the old seat;
  - no safe candidate, ending in a timeout;
  - player-first and entity-first load;
  - repeated saves and a logout before P2;
  - `saveAll` at most once per 100 ticks;
  - a client quit between pins saving the exact position;
  - real temporary-write and replacement failures;
  - an unaffected ordinary reconnect;
  - the Windows overlap test.
- Native cuts C7 to C11 and C13, each with two restarts and a kill during the first recovery where recovery work
  exists. Inspect each player's dimension, `Pos`, marker, `RootVehicle` and health, and unchanged resources.
- Each changed class alone, the whole unit suite, and one complete GameTest with the unchanged log checker, under the
  heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. W152 shares the lock.
- Check C: and D: above 10 GiB and 6 GiB free memory first. Keep the evidence under 100,000,000 bytes.
- This is a persistence and protection slice: a fresh Codex session reviews it before merge.

## Not in this task

The command, the `rejoins` view and the command matrix (W160), governed records, two-real-client evidence (owner's
final campaign), ADRs, the risk register, status files and the ledger (Root).

## Root decision R106 (2026-10-25), on the step-0 stop (`b4b7f379`)

1. **The correction stands.** `forceAddEffect` fires `MobEffectEvent.Applicable` indirectly through `canBeAffected`,
   so the design's sentence that it fires no event is wrong. Root has added a correction to W80-DESIGN section 4 and
   to W80.md. This only strengthens the guard. Keep both the `Applicable` denial and the install-time effect record
   with the cleanup before unguarding. Test that a guarded player refuses a harmful `forceAddEffect`, and that the
   cleanup still removes a harmful effect gained while guarded by another route the test can drive.
2. **The other step-0 results hold.** Entity-join is the only hook before the `RootVehicle` remount, so install the
   guard there; the login event repeats it idempotently. The Windows overlap test passes, so the leaf is not blocked.
3. **Continue with steps 2 to 4 of this task** in the same session, on the same branch.
