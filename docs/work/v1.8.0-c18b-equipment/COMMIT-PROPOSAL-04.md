# CL18B-EQUIPMENT-COMMIT-04: publication and retained-ownership proposal

Date: 2026-10-08. Milestone: v1.8.0 / C18b. Status: **proposed, not frozen**; the workstation stays
`DEPENDENCY_BLOCKED`. Author: Claude, fresh delegated worker (not Root), under
[TASK-04](D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18b-equipment/TASK-04.md); base
`9903b3f6ccbe973110732775b116a8165802362f`.

This answers [REVIEW-03](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-bounds-independent-20261008-03/reviewer-01/REVIEW-03.md)
B3-M1/M2/M3/L1 against [BOUNDS-PROPOSAL-03](BOUNDS-PROPOSAL-03.md) ("B§n") and
[BOUNDS-TEST-DESIGN-03](BOUNDS-TEST-DESIGN-03.md) ("BTnn"), replacing only the clauses named below.
It adopts no quarantine, save veto, truncation or R-021 extension, changes no bound, schema, number
or configuration, enables nothing and grants no source task.

## 1. Dispositions

| Finding | Disposition | Where |
| --- | --- | --- |
| B3-M1 | **Corrected.** Views and hand-out stacks are materialized before the final recheck; publication assigns prepared references only. Route proofs are source dependencies SQ-1..SQ-3. | §2 |
| B3-M2 | **Corrected.** The refusal oracle splits into R-a (no intervening external change) and R-b (stale or callback refusal). | §3 |
| B3-M3 | **Narrowed.** ARCE never writes, grows or replaces a retained reference; byte identity with received input is bound to new blocking dependency RP-6. | §4 |
| B3-L1 | **Fixed** as two separate actions; no direct route. | §5 |

## 2. Publication order (replaces B§3 steps 1-7)

1. **Capture** witnesses: menu and container identity, block entity identity and generation, both
   stored slot compounds by identity, enabled state, the affected player slot or cursor by reference
   and count.
2. **Prepare** detached postimage compounds of both slots from the stored compounds and the incoming
   stack's screened saved form. No live held object is mutated.
3. **Materialize** everything step 7 publishes: the view `ItemStack` of each changed slot, built from
   its prepared compound, and any hand-out stack. Every foreign callback of the operation (stack
   save, capability lookup and attachment, preflight rebuild, view and hand-out rebuild) runs in step
   2 or 3. Materialization is treated as callback-capable; a saved form without `ForgeCaps` is not
   evidence otherwise.
4. **Measure** and 5. **Check** as B§3 (unchanged).
6. **Recheck** captured witnesses within OXYGEN-WITNESS-CLARIFICATION-01 limits, using only reference
   identity, counts and the operation's own screened compounds; no item, capability or
   stack-equality method that may run foreign code.
7. **Publish** by assigning prepared compounds, views and hand-out stacks by reference in one
   logical-server operation. The dirty mark and any neighbour or comparator notification follow the
   last assignment. From step 6 to the end of step 7 there is no materialization, save, capability
   lookup or equality call.
8. **Resynchronize** the client from the actual resulting server state.

A refusal before step 7 publishes nothing the operation owns; prepared objects are discarded. A
callback that throws in step 2 or 3 is an ordinary refusal with a fixed reason scalar and no
exception text to players.

View drift (replaces the last sentence of B§4 bullet 2): a drifted view is replaced before the next
menu broadcast by a view prepared from the unchanged stored compound, swapped in by reference. If
that materialization throws, the stored compound is unchanged, the slot shows no item content, the
menu reports `REPAIR_REQUIRED`, and every operation refuses until a rebuild succeeds. A drifted view
is never persisted.

Source dependencies (blocking; this task inspected no Minecraft/Forge source):

| ID | Proof from fixed Minecraft 1.20.1 / Forge 47.4.10 source | Blocks |
| --- | --- | --- |
| SQ-1 | complete callback surface of step 3 materialization (stack from compound, capability attachment, tag verification on load) | step 3 |
| SQ-2 | an assignment route for menu slot, cursor, player inventory slot and dirty mark with no foreign callback between first and last assignment, or proven deferral of each such callback | step 7; BT28 |
| SQ-3 | step 6 comparison primitives run no foreign code | step 6 |

If SQ-2 fails for a route, operations on that route stay unadmitted; the no-callback rule is not
weakened to fit it.

## 3. Refusal scope (replaces B§3 "Refusal is atomic and unchanged" and the BT preamble)

Every refusal: zero operation-owned publication; nothing dropped, deleted, split or partially moved
by the operation; client resynchronized from the actual current state. An over-W REMOVE stays a
plain refusal without `REPAIR_REQUIRED` (unchanged).

- **R-a, no intervening external change** (bound excess, wrong kind, disabled, pending, RETAINED,
  full destination, closed menu, any refusal where no callback or hook changed captured state): both
  stored compounds, both views, player inventory and cursor, and all owned and unrelated bytes are
  byte-identical before and after.
- **R-b, stale or callback refusal** (step 6 finds a changed witness, or a step 2/3 callback throws or
  changes external state): the state after refusal equals the state a test hook records right after
  the external change, not the initial capture. Newer external data, such as a cursor replaced by a
  callback, is kept and never overwritten with an older value. No reversal of unrelated foreign
  effects and no detection of changes restored before step 6 is promised.

## 4. Retained-input ownership (replaces parts of B§5.1-B§5.3)

- **B§5.1** RETAINED treatment begins: "ARCE keeps the received compound reference and never writes
  to, grows, normalizes or replaces it"; the rest of the row is unchanged.
- **B§5.2** first two sentences become: "At save the block entity re-emits the retained reference
  with no omission, truncation, normalization or save veto; ARCE never grows or produces an
  over-bound root. Equality with the bytes received at load is claimed only under RP-6; without it
  the claim is limited to ARCE writing nothing into the reference." RP-3 is unchanged.
- **B§5.3** row "unrelated references shared with outside code" is limited to SUPPORTED slots. New
  row: "alias of a RETAINED reference held by a load caller or a consumer of the emitted save tag |
  live | RP-6".

Emitting the reference gives save consumers an alias, so ownership has inbound and outbound sides.
Whether any concrete caller or consumer keeps or mutates an alias is not established here; it is a
missing proof premise, not an observed mutation.

| ID | Dependency | Root options (none chosen) | Blocks |
| --- | --- | --- | --- |
| RP-6 | exclusive ownership of a RETAINED reference, inbound and outbound | (a) fixed-source proof that every load caller and save consumer relinquishes and never mutates it; (b) an immutable retained representation made within a stated bound, with a defined outcome above it (no arbitrary deep copy of non-faithful, cyclic or over-bound input); (c) alias mutation accepted as a named residual beside RP-1 | B§5.2 byte identity; BT05, BT07, BT22, BT31, BT34 |

RP-2 and RP-3 do not supply RP-6, nor RP-6 them.

## 5. Withdrawal wording (replaces B§4 bullet 3)

> Withdrawal and quick-move out of slot 0 or 1 hand out the stack prepared in §2 step 3; no stored
> compound reference leaves the block entity. REMOVE moves a record into the empty transfer slot
> (slot 1) only, as a prepared compound and view; the player then takes it by an ordinary withdrawal
> or quick-move from slot 1, a separate operation with its own §2 order and postimage check. There is
> no direct REMOVE into the player inventory (CONTRACT-02 §6). Destruction drops stay as B§5.5.

## 6. Affected planned observations

All planned; none executed; every case **UNVERIFIED**.

| Case | Change or new expectation |
| --- | --- |
| BT preamble | single oracle becomes R-a and R-b; each refusal case names its oracle |
| BT10 | R-a; callback counter covers steps 2-3, zero from step 6 to end of step 7 |
| BT11-BT14, BT16(b), BT19-BT21, BT23 | R-a |
| BT15 | full destination, over-bound: R-a; stale slot: R-b against the hook-recorded state |
| BT17 | drift rebuild prepared then swapped; new variant whose rebuild throws: compound unchanged, no slot content, `REPAIR_REQUIRED`, operations refused until a rebuild succeeds |
| BT05, BT07, BT22, BT31 | byte identity asserted only with a test loader keeping no alias; alias cases are BT34 |
| BT26 new (A1) | step 3 callback throws for INSTALL, REMOVE, withdrawal, quick-move out: refused under R-b; prepared objects never assigned |
| BT27 new (A1) | step 3 callback replaces the cursor; separately, adds a key to the incoming stack's tag: stale refusal under R-b; newer cursor and tag remain; old cursor not restored; no partial move or drop; client shows actual state |
| BT28 new (A1) | hook on every foreign callback incl. dirty mark and neighbour notification during passing INSTALL, REMOVE, withdrawal, quick-move: zero from step 6 to last assignment. **DEPENDENCY_BLOCKED on SQ-2** |
| BT34 new (A0, S1) | RETAINED fixture loaded by a caller that keeps then mutates the alias (adds a key; grows it); variant mutating the emitted save tag; save, two restarts: ARCE writes nothing into the reference (hook); persisted bytes recorded; identity required only under RP-6 (a)/(b). **DEPENDENCY_BLOCKED on RP-6** |

BT21 already uses the transfer slot. BT24, BT25 and BT30-BT33 are unchanged.

## 7. Dependencies

New and open: SQ-1..SQ-3, RP-6. Still open: RP-1..RP-5, BND-OPEN-1..3, CONTRACT-02 D-1..D-9 and
E2-OPEN-1..3, REVIEW-02 M2 and other L1 parts, owner choices, BOUNDS-HANDOFF-03 read deviation.

## 8. Impact

No source, test, registry, configuration, network, save, asset, ADR, risk, ledger, status or AGENTS
change. No S1, S2, V1 or V2 result. Passes no v1.8 G0-G9 Gate.
