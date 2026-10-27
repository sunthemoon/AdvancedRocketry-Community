# TASK W174: the gas charge pad's feasibility design (ADR-066 section 4, D4)

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is a design
note only: no production code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w174` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w174` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W174-DESIGN.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W174/` |
| Scope source | section 3, B1, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`), Root's inventory of the remaining v1.8 work |

Do not read `AdvancedRocketry-Community-v1plus-Development-Docs/`.

## Read first

- [ADR-066](../../decisions/) section 4 (the gas charge pad and its feasibility gate) and its D4 entry. The ADR's file
  name starts with `ADR-066`.
- The ledger row `block:oxygenCharger`.
- `records/W80-DESIGN.md` (rule R1: `PlayerEvent.SaveToFile` is only a read trigger), and the records of W157, W158,
  W159 and W160. These cover the bounded streaming readback of the installed `playerdata/<uuid>.dat` and its Windows
  overlap rule.
- The C16 canister mapping.

## What to do

Write the design note that answers ADR-066 section 4's feasibility gate for `gas_charge_pad`. Show whether the pad's
crash-safe protocol can observe a durable player-file save with mechanisms that are already merged. Map each step onto
them:
- the pad's outbox at the source;
- the durable chunk observation;
- the worn chest's receipt in the player file;
- the player-file observation;
- the source acknowledgement.

Keep ADR-066's bounds:
- a 16,000 mB bank;
- 100 units per 20 ticks;
- 32 pad attempts per tick through a fair queue;
- at most eight outboxes and eight receipts.

The note must also state:
- the refusal for unsupported player roots;
- the crash cuts on both sides of each save;
- the Windows overlap rule W80 already handles;
- what stays non-atomic;
- the risk-register text Root would register (AGENTS 3.3: scope, duration, log volume, recovery).

**Stop condition.** If the gate needs a new lifecycle hook, an Access Transformer or a Mixin, stop there. Instead,
write the alternative interaction for the owner: ADR-066 section 4 names a finite canister exchange on the pad.

End the note with the implementation leaves:
- block, block entity and menu;
- the receipt codec;
- the readback adapter;
- S2 cuts;
- DataGen art and data.

Give each leaf a scope, tests and a size.

## Verification

`python -B scripts/validate_repository.py --require-approved-identity` on the record commit. No Gradle, GameTest or
server is needed. Unit-level probes, if any, stay in the evidence folder.

## Not in this task

Any production code, ADR changes (Root proposes them from your note), the risk register, status files and the ledger.
A fresh read-only review of the note follows. Root brings any owner decision to the owner.
