# TASK W189: rocket flight sync, rocket wording and the Moon slow-fall hover (W172's B5, B6 and B7)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w189` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w189` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W189.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W189/` |
| Scope source | section 3, B5, B6 and B7, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`) |

Three small items, each in its own commits, none a persistence, protocol or save-refusal slice. Root decision R152
chooses the option for B7.

## What to do

1. **B5, rocket flight position sync** (real-client finding F3). The rocket entity is registered with
   `updateInterval(20)` (`registry/ModEntities.java`), so in flight passengers and bystanders see the server's trail run
   ahead of the rocket. Pick the smallest fix that keeps network cost bounded: a shorter tracking interval only in
   flight states, or velocity updates so the client interpolates. Write the choice and the estimated traffic per
   tracked rocket in the record. No packet format change. A GameTest pins the server side, meaning the tracker's
   interval or velocity flag in flight and at rest. The visual check stays V1 in the final campaign.
2. **B6, W87's deferred rocket wording.**
   - Give the rocket entity a proper entity name, with English and Chinese text, instead of the menu key
     `menu.advancedrocketrycommunity.rocket_flight` (`RocketEntity.java`).
   - Stop player-facing assembly and disassembly failures from showing developer details such as
     `coordinate_space=absolute` (`RocketAssemblyTransaction`, `RocketDisassemblyTransaction`,
     `RocketStructureScanTask`). Keep the details in the log or the operator view. First check that no saved or wire
     field carries the detail map; if one does, stop and report.
3. **B7, the Moon slow-fall hover (Root's choice: fix it).** Under slow falling, Moon gravity gives 0.00165 blocks per
   tick, below vanilla's 0.003 motion dead zone, so a mob at rest hovers (`records/W38.md`). Floor the scaled
   slow-fall gravity just above the dead zone in the existing gravity code, with a GameTest that starts the mob at rest
   and fails first. No Mixin; if the fix needs one, stop and report.

Language entries go through the existing providers; run `runData` once under the heavy-JVM lock and commit its
output.

## Verification

- the new tests alone, failing first where possible; the whole unit suite;
- the affected GameTest batches three times, and `runData` with a clean tree, under the lock;
- `python -B scripts/check_client_imports.py` and `validate_repository.py --require-approved-identity`.

Root's hosted run on the pushed branch stands in for a complete local GameTest.

## Not in this task

Real-client checks, other rocket behaviour, ADRs, status files and the ledger.
