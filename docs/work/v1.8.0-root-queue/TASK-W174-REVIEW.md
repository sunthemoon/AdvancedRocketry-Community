# TASK W174-review: independent read-only review of the gas charge pad design

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W174-DESIGN.md` on branch `codex/v1.8.0-w174` at `f9dbf78e5`. Review worktree `D:/GitHub/arce-v180-w174-review`, a detached checkout of `f9dbf78e5` |
| Task it answers | [TASK-W174](TASK-W174-GAS-CHARGE-PAD-DESIGN.md): ADR-066 section 4's feasibility gate for `gas_charge_pad` (D4) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W174/bytecode/` (the Forge 47.4.10 bytecode the note cites) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

Do not read `AdvancedRocketry-Community-v1plus-Development-Docs/`.

## What to check

1. **The five protocol steps,** checked against the merged code each one cites:
   - the debit and outbox in the pad's chunk;
   - the durable chunk check (`beginOrdinaryTerrain`);
   - the credit and receipt in one server-thread step;
   - W80's prompt save and streaming readback of `playerdata/<uuid>.dat`;
   - the acknowledgement by a second terrain proof.

   For each crash cut the note lists, on both sides of each save, check that each charge is credited exactly once.
   Look for any cut it misses.
2. **The receipt in `ForgeData.PlayerPersisted`.** Check in the bytecode the claims that it is written in the same
   file and save call as the worn chest, and that Forge copies it on death and on the End exit. Check what happens
   on dimension change, respawn, an operator `/data` edit, and a player-file restore.
3. **The single-player host.** Check the claim that the integrated host's player data loads from `level.dat`'s Player
   tag and not from `playerdata`, and that no merged mechanism observes that write. Check that W80's readback cannot
   reach that host.
4. **The weaker conservation claim.** The note says equipment is never "pending": a charge the pad owes behaves like
   a canister the player owns, and moving the credited chest afterwards tears like any item. State exactly what is
   lost or created at each cut, so that Root and the owner can judge it.
5. **Bounds and fairness.** Check the proposed limits against ADR-066's bounds: 4 pending outboxes per owner per pad,
   and 1 pad proof per Level per 20 ticks, 2 server-wide. Can pads starve rocket proofs, or one player jam a pad?
6. **The risk text** for AGENTS 3.3 (scope, duration, log volume, recovery), and the six implementation leaves: is
   each one testable and correctly ordered?
7. **The owner options** O1 and O2 in section 9: are they described fairly, including the claim that nothing else can
   fill suit tanks today?

## Rerun

No Gradle is needed. Use `javap` on the Forge jar to check the bytecode claims, and keep your dumps in your output
folder.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W174-R1, ...), severity, file and line or
section, problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last
line.

## Second review, of revision 2 (2026-10-28)

A new session reviews revision 2 of `records/W174-DESIGN.md`, on `codex/v1.8.0-w174` at `cf656dfe8` (changes
`f9dbf78e5..cf656dfe8`). Use the review worktree `D:/GitHub/arce-v180-w174-review2`, a detached checkout of
`cf656dfe8`, and write `D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review2/REVIEW.md`. The first review is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review/REVIEW.md` (SHA-256 `f390b483…913c`). Revision 2's probes are in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W174/revision-2/`.

1. **W174-R1 is closed.**
   - Rerun the first review's design-model trace (break the pad between A and PS2, prune, save the player only, kill):
     no charge may be credited twice.
   - Check that every receipt prune, including for an absent or replaced pad, needs durable evidence.
   - Check that acknowledgement identity survives unload and removal, and that late callbacks are rejected.
2. **W174-R2 is closed.** Pad proofs must go through the sealed and ordinary turn policy and yield the whole Level
   window to an active sealed attempt. Rerun the first review's 21-slot probe with pads.
3. **R3 to R8.** Check each against its finding.
4. **New gaps.** Look for any the revision introduced, especially indefinite holds and cross-store losses, and check
   that the risk text states them honestly.

Use the same rules and output format as above, with findings numbered from W174-R9.
