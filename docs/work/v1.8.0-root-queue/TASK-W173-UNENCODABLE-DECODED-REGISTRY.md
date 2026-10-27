# TASK W173: decoded satellite registry records that cannot be encoded again (design note)

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is a design
note only: no production code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w173` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w173` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W173-DESIGN.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W173/` |
| Source finding | W168-R3 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W168-rereview/REVIEW.md` (SHA-256 `b112ec0a…5886`), with its probe `probes/DecoderReviewTest.java` |

## The problem

`SatelliteNbtCodec.parseLocation` limits a name's input spelling to 128 characters and accepts an omitted namespace.
Normalization then adds `minecraft:`, and nothing checks the canonical length afterwards. Some names are affected:
- mission definition and target IDs;
- reward item IDs;
- possibly others; find them all.

A record that decodes within the 4,096-byte record limit can therefore need more than that once normalized. The
review's raw GAS mission decodes at 4,090 bytes and needs 4,280. Such a root:
- loads through `SatelliteMissionSavedData.load` and is operational;
- but every `save(CompoundTag)` throws, so the registry can never be written again.

The mod never writes unqualified names. Only an edited, foreign or older file can contain them; find out which.

## What to do

1. **Find every decoded field** whose canonical encoding can be longer than its decoded input, for each record kind
   (satellites, missions, instances, ground surveys, accounts), and the largest growth for each.
2. **Find what happens now** at load, at save, on the off-thread registry writer (ADR-074, W129b), at autosave and at
   shutdown, with such a root. Can the world lose data, and does the writer report it (R-041)?
3. **Give options with consequences,** for example:
   - refuse the root at load, as an invalid root under R-030;
   - normalize and check the canonical length at decode, and refuse or quarantine only that record;
   - keep the input spelling for re-encoding;
   - a one-time offline repair.

   For each option, state the compatibility effect on worlds that load today, the risk-register text it needs
   (AGENTS 3.3: scope, duration, log volume, recovery) and whether an ADR is needed.
4. **Recommend one option.** Measure nothing that needs a server; unit-level probes with the real codec are fine.

## Verification

Any probe runs with the real codec as a unit test, kept in the evidence folder and not committed. Run
`python -B scripts/validate_repository.py --require-approved-identity` on the record commit.

## Not in this task

Any production change, W168's fast path, ADRs, the risk register, status files and the ledger (Root). Root decides,
with the owner if a compatibility change is involved, before an implementation task.
