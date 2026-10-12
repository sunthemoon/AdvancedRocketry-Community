# TASK W07F: fix the W07 independent review findings and add the `classic_controls` channel

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.
Implementer: a headless Claude session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w07-station-controls` (at the W07 delivery `035e78f8`) |
| Branch | `codex/v1.8.0-w07-station-controls` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W07-review/REVIEW.md`, SHA-256 `266db5a543b00f3b4eb45a112209b62f9d14cfb12b4f1d2383ed6afbdeb38edb`. Verdict BLOCK: R1 to R3 Medium, R4 and R5 Low, plus scope items. |
| Record | `docs/work/v1.8.0-root-queue/records/W07F.md` |

Read the whole review first.

## Root's decisions

Fix each item in its own commit, together with its tests.

1. **The `classic_controls` channel (review scope item 1).** ADR-065 §11.1 freezes the
   table, so W07's deviation is not accepted. Register the new channel at protocol 1
   exactly as the table lists it:
   - index 0, `ClassicControlIntentPacket`, PLAY_TO_SERVER;
   - index 1, `ClassicControlViewPacket`, PLAY_TO_CLIENT;
   - index 2, `BeaconMarkerSnapshotPacket`, PLAY_TO_CLIENT.

   Use exact-version negotiation, wrong-direction and wrong-discriminator rejection, and
   bounded codecs. Read §11 for the message fields.
   - Index 2 belongs to C17c. Register its frozen codec now and test its bounds. The
     server does not send it yet, and the client handler only caches it.
   - Move the four controllers' actions from vanilla menu buttons to
     `ClassicControlIntentPacket`.
   - Send their protected status through `ClassicControlViewPacket`, to the validated
     holder only.
   - Every C2S intent is validated on the server: player, distance, permission, state,
     range and loaded chunk (AGENTS.md §3.3).
2. **R1 (Confirm runs a different quote).** The confirm intent carries the identity of the
   exact quote that the menu displayed. The server confirms only that quote. Navigating to
   another target, replacing or reopening the menu, or a failed request invalidates it.
   Keep the command path's one-shot, expiry and authority checks. Cover the review's
   three sequences.
3. **R2 (Confirm skips the core reach).** Before a confirmation is consumed, check that a
   loaded warp core is within the controller's 5 blocks. Do not add a ray-pick requirement
   to the command path. Test a removed core, a core too far away and an unloaded core
   after a successful quote: there is no countdown and no energy is spent.
4. **R3 (a revoked viewer keeps the feed).** Remember the opening player and station
   identity on the server. Close the menu, or stop sending views, when VISIT access, the
   station identity or the controller's validity is lost. Test membership revocation,
   owner transfer and operator removal, and station replacement while the menu is open.
5. **R4 (the countdown row is hidden).** Lay out the panel so that all five warp lines and
   the status area fit. Record that this still needs V1 visual evidence.
6. **R5 (weakened assertions).**
   - Restore the `DecoderException` assertions for deliberately rejected frames.
   - Keep any justified truncated-buffer case explicitly separate.
   - Restore the check that rejects an overlong context or body at construction.
   - Correct the claim in the W07 record that no assertion was relaxed.
7. **Documentation:**
   - correct the stale sentence in the design note about the checked-commit clock floor;
   - leave the §5.3 notices as they are. They are listed as not done and stay open in your
     record.

## Tests and commands

- The tests above, plus the channel tests:
  - the codec bounds of all three messages;
  - wrong direction, wrong discriminator and wrong version are rejected;
  - an intent from an unauthorized, distant or unloaded context is refused.
- The whole unit suite once. Each new class must also pass when run alone.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- The §5.3 offline and visitor notices.
- The beacon itself (C17c).
- Landing pads.
- Status files and the ledger.
