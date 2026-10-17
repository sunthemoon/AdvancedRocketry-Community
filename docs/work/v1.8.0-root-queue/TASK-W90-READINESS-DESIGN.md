# TASK W90: design note for governed flight readiness (the flight tickets do not cover the discovery margin)

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work only: write no
production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w90-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w90-design` (local only; Root merges) |
| W11b2 code | Read W11b2 (design 2 in `records/W11B2-DESIGN-2.md` and its code) only through Git at commit `379525ea` (`git show 379525ea:<path>`, `git grep <pattern> 379525ea`). Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` or `D:/GitHub/arce-v180-w11b2-c53`, where continuation 53 is working |
| Evidence | Continuation 52's record (`git show 379525ea:docs/work/v1.8.0-root-queue/records/W11B2-C52.md`, "Investigation and harness changes") and its `native-01` result in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C52/native-01/` (read only) |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W90-DESIGN.md` (two to three pages) and a short record `records/W90.md` |

## The problem

`RocketGovernedSourceDispatcher.tick` executes a checked ACTIVE governed record only when
`RocketTransferEntities.recoveryEndsAlreadyLoaded` is true: every chunk column of each end's discovery margin
(`region.minimum - 1` to `region.maximum + 2`, at most `MAX_LANDING_CHUNKS`) must have its entities loaded and be
entity-ticking. Each visit then calls `refreshGovernedFlightTickets`, which adds one `arce_rocket_flight` region ticket
at level distance 2 on each end's origin chunk only. That makes the origin chunk entity-ticking and its neighbours only
ticking or border. So a governed flight whose margin crosses a chunk border, with no player near that end, is visited
and re-ticketed forever without reaching F3, F4 or F6, and nothing is logged. Continuation 52 observed this natively
(all four discovery columns loaded, only the origin entity-ticking) and pinned it in `RocketBenchmarkReadinessTest`.
The legacy, ungoverned path checks only the origin chunk (`entityChunkLoaded`). The same readiness predicate is also
used by `RocketGovernedLaunchBoundary.allows` (F1 and F2), `RocketTransferFences` and `RocketTransferRecoveryService`.
Production admission is disabled, so no player world is affected; this must be fixed before admission.

## What the note must settle, citing code at `379525ea`

1. **Why the margin must be entity-ticking**, not only loaded: what each caller of `recoveryEndsAlreadyLoaded` relies
   on (entity discovery for the election, duplicate and relocated rocket detection, the executor's entity ticks). In
   Minecraft 1.20.1, say exactly what a chunk at ticket level 32 or 33 gives (entity section visibility, whether
   `getEntitiesOfClass` sees its entities, whether entities there can still be loading), with the source you read.
2. **Options**, at least these three, each with its safety argument, chunk-loading cost (tickets per end, chunks
   kept loaded and entity-ticking per transfer, and the worst case at 64 active transfers) and release at F8 and on
   expiry:
   - (a) one ticket per margin chunk column at the existing level and type;
   - (b) one ticket at each end with a level chosen from the margin's size, bounded;
   - (c) keep the tickets and require only entity accessibility (loaded and visible) for the non-origin margin
     columns, if item 1 shows that this is enough for every caller; say plainly whether this weakens any guarantee.
   Recommend one.
3. **What must not change:** the acknowledgement order, the proofs, the leases, the holds, the replay exclusion, the
   saved format, and the rule that unloaded ends wait without tickets in ordinary reconciliation, pruning and held
   repair (design 2: "Transfers load only server-recorded ends under existing bounded flight tickets after positive
   classification"). If the recommended option changes one of these, say so and stop there with options for Root.
4. **Release:** how F8's `releaseFlightTickets` and ticket expiry remove every ticket the option adds, including after
   a restart and when the record's ends are in different Levels; no leaked ticket may keep chunks loaded.
5. **Players:** what a player sees while a flight waits, and whether the wait needs a log line (once per transition,
   not per tick) or an inspect field.
6. **Tests:** a unit test of the ticket set or level for margins inside one chunk, across one border and at the
   `MAX_LANDING_CHUNKS` bound; a GameTest with a governed flight whose source and destination margins cross chunk
   borders and no player nearby, which must land; and a release test after F8 and after a restart. Estimate the JVM
   starts.
7. **Risk and ADR text** for Root (the R-035 sentence that changes), and **open questions**, each with a recommended
   answer.

## Commands

- No Gradle, GameTest or server run: the machine is reserved for continuation 53's benchmark. `git show`, `git grep` and
  reading files are enough. To read Minecraft or Forge classes, use the sources jar or mapped jar already in the
  Gradle cache with `javap` or an unzip listing, not a build.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git
  tracks (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code; continuation 53's pad reservations; W83 (arrival entity readiness).
- ADRs, the risk register, status files and the ledger (Root).
