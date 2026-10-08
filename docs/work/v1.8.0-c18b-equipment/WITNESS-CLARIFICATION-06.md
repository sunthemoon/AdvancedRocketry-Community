# CL18B-EQUIPMENT-WITNESS-06: clarification of the witness-05 review Lows and Infos

Date: 2026-10-08. Milestone: v1.8.0 / C18b. Status: **proposed, not frozen**; the workstation stays
`DEPENDENCY_BLOCKED`. Author: Claude, fresh delegated worker (not Root, reviewer or approver), under
TASK-06; base `10eb068f438b42622000b1316381077431f77879`.

This answers L1-L3 and I1-I4 of [REPORT-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-witness05-claude-review-20261008-01/REPORT-01.md)
("R:n" = its line n) against [WITNESS-CORRECTION-05](WITNESS-CORRECTION-05.md) ("WC§n"),
[COMMIT-PROPOSAL-04](COMMIT-PROPOSAL-04.md) ("P4§n"), [BOUNDS-PROPOSAL-03](BOUNDS-PROPOSAL-03.md)
("B§n") and its test design (BTnn). It replaces only the clauses in §5 and makes no runtime,
save-veto, truncation, quarantine, R-021, bound, schema, numeric or configuration decision; no native
path is admitted and no source task granted. Platform behaviour named here (click types, world
spawns, equality and count writes) is author or reviewer knowledge, **not sourced here**.

## 1. Dispositions

| Item | Disposition | Where |
| --- | --- | --- |
| L1 | **Corrected:** P4§2 step 7's kept sentence replaced; the F walk is the only comparison | §2 |
| L2 | **Corrected:** BT36 split by outcome and refusal step; depth bound to BND-OPEN-1 | §6 |
| L3 | **Narrowed:** closed route table; THROW, CLONE, PICKUP_ALL and mixed drags on slots 0/1 refuse | §3 |
| I1 | **Fixed:** undetected window stated per walk | §4 |
| I2 | **Recorded:** BT10's F-C clause is vacuous; BT11 (pass) and BT28 carry the interval | §6 |
| I3 | **Added:** BT27b tag removal; BT35 checks and WIT-OPEN-2 split | §6 |
| I4 | **Fixed:** recount in place on the captured reference | §4 |

## 2. Reconciled order and the L1 sentence

B§3 steps 1-7 were replaced by P4§2, B§3's atomic-refusal sentence by P4§3. Governing text now:

| Step | Phase | Text | Foreign callbacks |
| --- | --- | --- | --- |
| 1 capture | P | P4§2 step 1; WC§2 I-1..I-3 | none (SQ-3) |
| 2 prepare | P | P4§2 step 2; WC§2 binding | allowed |
| 3 materialize | P | P4§2 step 3; WC§3 callback sentence | allowed |
| 4-5 measure, check | P | B§3 | none |
| 6 final check | F | WC§2 | none (SQ-3) |
| 7 commit | C | WC§3 C row; §4 I4 | none (SQ-2) |
| notify, resync | N | WC§3 N row | allowed |

Replacement for P4§2 step 7's last sentence (P4:41-42):

> From the start of step 6 through the last C assignment there is no stack materialization, stack
> or capability save, capability lookup, or item, stack, tag-object or capability equality method.
> The only comparison is WC§2's bounded walk of the live tag against I-3 (native type IDs,
> primitives, strings, arrays, key sets) in ARCE's own code; any copy it makes is discarded, never
> published. Whether those reads run no foreign code is SQ-3.

## 3. Route coverage on slots 0 and 1 (L3)

Closes B§3's path list (B3:76-78). An unlisted route, including one SQ-4 finds later or one added by
another mod or platform version, is unadmitted and refuses under R-a until a successor lists it.

| Route | Affected player-side stacks | Status |
| --- | --- | --- |
| click or split click (PICKUP) | cursor | candidate under SQ-2..SQ-4 |
| drag (QUICK_CRAFT) ending on that slot only | cursor | candidate |
| drag ending on slot 0 or 1 plus any other slot | none | R-a (author proposal) |
| hotbar or offhand swap (SWAP) | that slot | candidate |
| quick-move in / out | source slot / each destination filled or merged | candidate |
| INSTALL, REMOVE intent | none | candidate |
| THROW, one or whole stack | none | R-a: a world spawn is no C assignment, may run foreign code, and could be lost after an N fault (R:28-33) |
| CLONE (creative) | none | R-a (author proposal): no callback-capable view copy outside step 3 |
| PICKUP_ALL | none | slots 0 and 1 never contribute |

- Every admitted hand-out goes to the cursor or a player slot by C assignment, never to the world.
- Player-only actions with the menu open (THROW or PICKUP_ALL among player slots, clicks outside the
  window, cursor return or drop on close) change no held state, keep vanilla behaviour and are judged
  by neither R-a nor R-b. Refused routes take no capture and publish nothing; resync follows in N.
- **SQ-4, restated (not weakened):** per route, the complete affected-stack set, plus, from fixed
  Minecraft 1.20.1 / Forge 47.4.10 source, every click type and menu write route reaching slots 0
  and 1. A route lacking SQ-3 or SQ-4 stays unadmitted.
- **WIT-OPEN-3 (new):** B§5.5 SUPPORTED break and explosion drops share THROW's unsourced spawn
  concern. B§5.5 is unchanged; this stays distinct from RP-2 and from route admission.

## 4. Recheck and commit wording (I1, I4)

- **I4 (refines the WC§3 C row):** C writes each affected stack one way only: EMPTY when the incoming
  stack is wholly consumed; the prepared hand-out into an empty slot or cursor; or an in-place count
  change on the I-1 reference F rechecked (partial consumption, or a merged hand-out, whose prepared
  object is then discarded). No step 2-3 copy replaces an existing player stack, so its unserialized
  capability state is kept. Whether the count write runs foreign code is part of SQ-2.
- **I1 (replaces WC:47-48, "a change restored before step 6 is undetected"):** a tag change still
  present at the step 2 saved form refuses R-b by the binding; one present at step 6 by the recheck.
  Undetected are only a change made and restored between capture and step 2, or after the step 2
  saved form and before step 6; published content is then value-equal to I-3 and the final live tag.
  Capability state stays outside (WIT-OPEN-1); off-thread mutation is not covered.

## 5. Superseded clauses

| Clause | Superseded or refined by |
| --- | --- |
| P4§2 step 7 last sentence (P4:41-42) | §2 |
| WC§2 limits sentence (WC:47-48) | §4 I1 |
| WC§3 C row "affected stacks by reference" (WC:59) | refined by §4 I4 |
| B§3 path list (B3:76-78); WC§2 SQ-4 (WC:43-44) | §3 |
| WC§4 rows BT10, BT27b, BT28, BT35, BT36; BT14 | §6 |

Unchanged: R-a, R-b, WC§3 phase rules, P4§3-§5, B§5.5, every bound and number.

## 6. Planned observations

All planned; none executed; every case **UNVERIFIED**.

| Case | Change or new expectation |
| --- | --- |
| BT10 | refuses at step 5 (BOUNDS-TEST-DESIGN-03:29), so its F-C counter is vacuous; step 2-3 counter and R-a stay |
| BT11 | pass branch adds zero foreign callbacks from step 6 through the last C assignment; mark before N. **DEPENDENCY_BLOCKED on SQ-2** |
| BT14 | adds THROW (one, whole stack), CLONE, PICKUP_ALL with a matching stackable slot-1 component, drag over slot 1 and a player slot: R-a, no world entity. **DEPENDENCY_BLOCKED on SQ-4** |
| BT27b | adds tag removed: R-b via I-2; untagged stack kept |
| BT28 | hook also counts item, stack and capability equality calls: zero in F-C; the WC§2 walk is allowed. Blocked on SQ-2 |
| BT35 | adds: client view after the next broadcast equals server state; no exception text to players; server keeps running; connection state recorded. Whether other N items finish is **DEPENDENCY_BLOCKED on WIT-OPEN-2**; COMMITTED, persistence and no duplicate or loss are not. Blocked on SQ-2 |
| BT36a (A0 walk, A1 op) | slot-1 incoming tag exactly at C (16,384 B, 256 nodes, depth 16): capture accepted, at most ceiling plus one visits per walk; proceeds to steps 2-5 |
| BT36b (A0 walk, A1 op) | one over C in bytes, nodes or depth; foreign tag class: R-a at capture, before step 2 |
| BT36c (A1) | armor incoming tag exactly at W bytes or nodes: capture accepted (A4), then R-a after step 2 by the A4 preflight or step 5; one over W: R-a at capture. Depth at and over `D_W`: **DEPENDENCY_BLOCKED on BND-OPEN-1** |
| BT37 new (A1) | partial consumption of a stackable component; hand-out merged into a player stack: after C the affected stack is the captured reference with the new count; a test capability's unserialized step 3 marker survives. Count write depends on SQ-2 |

## 7. Open and distinct

New: WIT-OPEN-3; restated: SQ-4. Open, unchanged in scope, none supplying another: native
qualification SQ-1..SQ-4 and WIT-OPEN-1..3; retention RP-3 and RP-1; destruction RP-2 and B§5.5;
alias ownership RP-6; bounds BND-OPEN-1..3, RP-4 frame constants and C versus O headroom (B§7);
whole contract RP-5, CONTRACT-02 D-1..D-9, E2-OPEN-1..3 and E-OPEN-1..10, REVIEW-02 M2 and other L1
parts, owner choices, the BOUNDS-HANDOFF-03 read deviation.

## 8. Evidence and impact

Documentary only: REPORT-01 and the proposals at base `10eb068f`; no repository or platform source,
runtime, server or client output. No source, test, registry, configuration, network, save, asset,
ADR, risk, ledger, status or AGENTS change; no S1, S2, V1 or V2 result; passes no v1.8 G0-G9 Gate.
