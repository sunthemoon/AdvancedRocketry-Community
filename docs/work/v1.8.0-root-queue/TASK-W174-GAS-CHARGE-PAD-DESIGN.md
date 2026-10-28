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

## Revision 2 under Root decision R134 (2026-10-28)

Revision 1 (`f9dbf78e5`) was reviewed. The review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review/REVIEW.md`
(SHA-256 `f390b483…913c`), returned BLOCK. Read the whole review and its design-model tests first. A fresh session
revises `records/W174-DESIGN.md` on `codex/v1.8.0-w174` as revision 2. Keep revision 1's text where it still holds,
and mark each change with the finding it answers:

- **W174-R1 (Critical).** Breaking a pad after its last outbox is removed in RAM, but before PS2 stores that, lets the
  no-pad reconciliation prune the receipt without a durable proof. A crash then credits the charge twice. Protect the
  source until its acknowledgement is durable, not only while it holds an outbox. Every orphan prune, including for an
  absent or replaced pad, needs positive durable evidence. Define:
  - what keeps an acknowledgement's identity across unload and removal;
  - how late proof callbacks are rejected;
  - the native cuts that pin it.

  Do not repair it by refunding or discarding an outbox.
- **W174-R2 (High).** Pad proofs must take part in the existing sealed and ordinary turn policy (`SealedProofAdmission`,
  `SealedProofTurns`; ADR-065's proof admission reserves all four ordinary captures for an active sealed attempt),
  and must yield the whole Level window while a sealed attempt needs it. Specify the ordering with waiting rocket
  demand, and the fairness tests, without raising any quota or deadline.
- **W174-R3 to R8 (Medium):**
  - R3: a pad-owned attempt lifecycle and deadline, with late-completion handling and an honest IO-failure duration;
  - R4: credit catch-up within 100 per 20 ticks and one eligible player per pass;
  - R5: an eligibility predicate that accepts enchanted chests;
  - R6: state the integrated-host unreachability as an enforced check, not an observation;
  - R7: order the leaves so that the risk registration and review come before the first persistence leaf;
  - R8: correct L4's sum.

If a finding cannot be closed without a new hook, an Access Transformer or a Mixin, say so. Then strengthen the owner
alternative (O1, canister filling) instead. Verification as before: the validator on the record commit, and any probes
kept in the evidence folder. A fresh read-only review follows.

## Revision 3 under Root decision R136 (2026-10-28)

Revision 2 (`cf656dfe8`) was reviewed again: `D:/GitHub/ARCE-Task-Evidence/root-queue/W174-review2/REVIEW.md`
(SHA-256 `c8683ad1…ddb3`), APPROVE-WITH-CHANGES. W174-R1 and R3 to R8 are closed. A fresh session revises the note as
revision 3, closing W174-R9 to R15 as that review suggests. Mark each change with its finding:

- **R9 (High).** Pad admission must not hang on any waiting rocket retirement anywhere. Bind it to the existing "due"
  predicates, or register the hold as server-wide. Refuse D while proofs are closed, and add the test.
- **R10.** List every barrier consumer (W158 release proofs and the governed escrow captures included). Either include
  each one in pad admission, or bound and test its interference. Keep the dependency one-way.
- **R11.** Quantify the sealed-start delay that waiting for pad IO to drain causes, include it in the test, and check
  it against ADR-065's hand-off bounds and the 200-tick pre-U expiry.
- **R12.** Make proof validity per transaction entry and incarnation, with tests for continuous input and a second
  player.
- **R13.** State the vanilla rollback when a world moves between dedicated and single-player hosting, with a
  whole-world repair step, and match the receipt holder's UUID to the outbox owner's.
- **R14 and R15:** the wording fixes.

Verification as before: the validator on the record commit, and any probes kept in the evidence folder. A fresh
read-only review follows.

## Not in this task

Any production code, ADR changes (Root proposes them from your note), the risk register, status files and the ledger.
A fresh read-only review of the note follows. Root brings any owner decision to the owner.
