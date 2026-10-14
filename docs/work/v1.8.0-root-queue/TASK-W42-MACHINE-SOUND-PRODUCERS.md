# TASK W42: producers for the `lathe`, `rolling_machine` and `machine_large` sounds

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w42-machine-sounds` (from the shared branch) |
| Branch | `codex/v1.8.0-w42-machine-sounds` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W42.md` (new) |
| Specification | ADR-066 section 7.1 (the sound bullet); `docs/work/v1.8.0-c18d-audio/CONTRACT-02.md`; `records/W16.md` ("Choices", "Loop producers", "Unbound sound events") |

W16 registered all ten sound events with NEW audio. Three have no producer, because no processing
state reaches nearby clients. This task adds those producers.

## Root's decisions on the open audio items

- **A-OPEN-7, the processing-flag carrier: the block-entity update tag.** Add one boolean
  `active` (processing) to the controller's update tag and send it only when it changes, as the
  gravity controller and laser drill already do. Do not add a channel, a packet or a per-tick
  update. The client reads a missing or malformed flag as inactive.
- **A-OPEN-5, the `machine_large` machines: from the legacy tile bodies.** Read the upstream MIT
  source as `UPSTREAM.md` describes. List each legacy machine and the sound it played, with its
  file and line. `lathe` and `rolling_machine` go to the lathe and the rolling machine. Every other
  legacy machine that played the large-machine sound gets `machine_large`. Machines that played
  nothing stay silent. Record the list in your record before any code.

## What to do

1. Add the `active` flag to the controllers on the list. It is true exactly while a recipe is
   progressing. It is false while the machine is unformed, idle, out of power or blocked by output.
2. Add the client loop producers through W16's existing loop manager. Keep its ranges, the 32-loop
   cap, nearest-first ordering, discovery budgets, and clearing on unload or reload. Common code
   must not reference client classes.
3. **Tests:**
   - unit tests for the flag codec;
   - native GameTests: each listed machine sets and clears `active` with a real recipe, and the
     update tag carries it;
   - client loop selection under the existing client-logic unit tests, if W16's tests show how.
4. Do not add or change audio files, the sound definitions or their namespace.

## Commands

- The whole unit suite once.
- `runData` under the lock, only if generated data changes.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- The sound-namespace fold. It needs a Root `build.gradle` change and is a separate task.
- The manual listen (owner), the railgun arrival bang (owner question A-OPEN-6) and
  `advancedVisuals` (A-OPEN-8).
- Status files and the ledger (Root).
