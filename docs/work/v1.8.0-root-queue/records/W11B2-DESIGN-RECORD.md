# W11b2-design record

Date: 2026-10-12. Task: [TASK-W11B2-DESIGN-NOTE.md](../TASK-W11B2-DESIGN-NOTE.md). Implementer: a
headless Claude session in `D:/GitHub/arce-v180-w11b2-design`, branch `codex/v1.8.0-w11b2-design`, base
`0a804174`. Design only: no production or test code, ADR, risk-register, status or ledger change.

## What was done

- Wrote [W11B2-DESIGN.md](W11B2-DESIGN.md), the W11b2 design note. It closes the round-2 review's entry
  conditions P1 to P5 (with H2, H3, M3, M4 and L3 to L6) and specifies the rest of the W11b2 row of
  W11A-DESIGN section 8, each with its tests and S2 cuts. Section 7 holds the proposed text of two risk
  entries and of the ADR-065 implementation note (and an optional ADR-044 note) for Root to add.
- Commits on the local branch: `5c8aeaf2` adds the design note; the next commit adds this record.
  Nothing was pushed or merged, and no other agent was started.

## Files

- `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN.md` (new).
- `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN-RECORD.md` (new, this file).

## Commands and results

| Command | Result |
|---|---|
| `git status`, `git log`, `git worktree list` | Clean at `0a804174`; W11b1's branch has no commits beyond its TASK. |
| `sha256sum` on the round-2 review | `e54fd579dd5dee437c16c5d385a5fe5254280e080c78f1c661d2d3231cb68dc2`, as the TASK states. |
| `sha256sum` on the mapped Forge jar | `95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`, as W11a section 0 states. |
| JDK 17.0.7 `javap -p -c` | `SavedData.save(File)`, `DimensionDataStorage.save` and its lambda, `ServerLevel.save`/`saveLevelData`/`getEntity(UUID)`/`isPositionEntityTicking`, `MinecraftServer.tickServer`, `KillCommand`, `Entity.kill`/`startRiding`/`isAddedToWorld`, `EntityDataAccessor`/`BlockDataAccessor.setData`, `SpreadPlayersCommand.setPlayerPositions`, `TeleportCommand`, `RideCommand.mount`, `ForgeEventFactory.canMountEntity`, `LevelEntityGetterAdapter.get`, `PersistentEntitySectionManager.canPositionTick`/`updateChunkStatus`, `Visibility`. All succeeded; output went to the terminal only. |
| Read and Grep over the rocket, persistence and ADR files cited in the note | Static, read-only. |

No Gradle, GameTest, Minecraft or native server was run; the task is design only.

## Choices where the base design or review left room

- **P1 threading:** the journal keeps synchronous server-thread writes at the same points, made checked;
  an off-thread writer would change the transfer step order and invalidate existing transfer cut evidence.
  The checked writer is attached by composition because `AtomicSavedData.flush(MinecraftServer)` is final
  and throws.
- **P1 acknowledgement:** every session starts unacknowledged and the first escrow need writes one
  checked baseline, which also covers the absent-file case.
- **P2:** the fenced classification fits ADR-044 section 5's existing rules; an online `settle-floor`
  command exits the H3 case with O1 to O4, including an on-disk absence checkpoint.
- **P3:** fenced assembly records have no online exit (local effect only); disassembly records are not
  fenced by a nonzero epoch alone, so idle rockets that once received escrow fuel can still be
  disassembled.
- **Floors:** online collection once the checked journal no longer holds the transfer, and no floor for a
  no-transfer guard. This departs from W11a's start-only rule; the note asks the reviewer to confirm it.
- **L5 correction:** `/spreadplayers` honours cancellation in this Forge build, so it is refused rather
  than only detected.

## Not done

- No W11b2 code or tests (W11b2 itself).
- No ADR or risk-register edits; the proposed text is in the note's section 7 for Root.
- The independent read-only review of this note (AGENTS.md section 13) is still required.

## Known risks

- W11b1's interfaces (policy mode, canonical digest, registration hooks, barrier readback, test endpoint)
  are assumed from its TASK; if W11b1 ships them differently, sections 1, 2 and 8 need adjusting.
- The checked-journal cost at the 146,866,176-byte bound is unmeasured; the note's stop rule applies.
- Online floor collection and the NONE-guard rule are new relative to W11a and need the reviewer's
  confirmation.
