# CL18B-EQUIPMENT-WITNESS-05: input and notification boundary corrections

Date: 2026-10-08. Milestone: v1.8.0 / C18b. Status: **proposed, not frozen**; the workstation stays
`DEPENDENCY_BLOCKED`. Author: Claude, fresh delegated worker (not Root), under TASK-05; base
`0c6bc521c41080e386d124df3e9a6ac1cad90026`.

Answers C4-M1 and C4-L1 of [REPORT-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-commit04-independent-20261008-01/reviewer-01/REPORT-01.md)
against [COMMIT-PROPOSAL-04](COMMIT-PROPOSAL-04.md) ("P4§n"), replacing only the clauses named here.
BOUNDS-PROPOSAL-03 ("B§n") and BT cases otherwise stand. No quarantine, truncation, save veto or R-021
decision; no size, schema, numeric or configuration change; no native path admitted; no source task.

## 1. Dispositions

| Finding | Disposition | Where |
| --- | --- | --- |
| C4-M1 | **Corrected** by capturing and finally comparing each affected stack's content; the promise is not narrowed; a route without SQ-3/SQ-4 stays unadmitted | §2 |
| C4-L1 | **Corrected** by one callback-free interval from final check to last assignment and a separate post-commit phase | §3 |

## 2. Live-input witness (adds to P4§2 step 1; replaces P4§2 step 6)

An **affected stack** is a player-side stack (cursor or inventory slot) that step 7 consumes, replaces
or recounts: the incoming stack of click, split click, drag, hotbar-number swap, offhand swap or
quick-move in, and any player stack a hand-out merges into. INSTALL and REMOVE have none. Step 1
captures for each:

- **I-1** stack reference, item reference and count;
- **I-2** tag absent or present, and the tag reference;
- **I-3** a detached copy made by the B§2 native-shape screen and bounded measurer within the target
  ceiling (slot 1: C; slot 0: W per A4; merge: the hand-out's domain). Excess, abort or a foreign tag
  class refuses under R-a before step 2.

Step 2 requires the `tag` entry of the incoming saved form to be value-equal to I-3, or absent with
it (else R-b), so the prepared form derives from captured content. Step 6 runs after every step 2/3
callback and steps 4-5. It rechecks P4's witnesses; I-1 and I-2 by identity and count; and I-3 by
re-screening the live tag with the same walker and bound, comparing type IDs, primitive values,
strings, arrays and key sets with the capture. Any difference, abort or excess is stale refusal R-b;
the newer stack, tag and content stay. A replaced cursor fails I-1; a replaced, added or removed tag
fails I-2; an in-place change fails I-3 only. Each affected stack gets at most three bounded walks
(capture, step 2, step 6), each stopping at ceiling plus one under RP-4; no new number.

- **SQ-3, extended:** I-1..I-3 capture and comparison read only references, counts, tag presence and
  exact native tag classes, and run no foreign code.
- **SQ-4, new:** per route, the complete affected-stack set, so I-1..I-3 cover every player-side
  write of step 7. A route lacking SQ-3 or SQ-4 stays unadmitted.
- **Limits:** capability state is no witness; no serializer runs after step 3. A2 and C rule 3 refuse
  a `ForgeCaps` saved form at step 2, but a capability that saved nothing and changes later is not
  detected (WIT-OPEN-1; BND-OPEN-2 if A2 is rejected). Per OXYGEN-WITNESS-CLARIFICATION-01 a change
  restored before step 6 is undetected; published content then equals the final live input.
  Off-thread mutation is not covered.

## 3. Commit and notification phases

Replaces P4§2 step 3's "every foreign callback" sentence, step 7's notification sentence and step 8.

| Phase | Steps | Foreign callbacks |
| --- | --- | --- |
| P prepare | 1-5 | in 2-3 only |
| F final check | 6 | none |
| C commit | 7: assign prepared compounds, views, hand-outs and affected stacks by reference; last, the persistence mark that makes the next save write the block entity | none |
| N notify | neighbour and comparator notification (with any notifying part of the platform dirty route), menu broadcast, client resync | allowed |

The callback-free interval runs from the start of F through the last C assignment. N decides nothing
from held or player-side state and assigns no operation-owned state.

- P or F refusal: R-a or R-b; zero operation-owned publication; fixed reason scalar.
- C has no failure path; where SQ-2 cannot show C callback-free and non-failing, the route stays
  unadmitted.
- A throw or foreign change in N leaves the operation **COMMITTED**. It is never reported as an
  unchanged refusal, reversed, or retried as an uncommitted move; a repeated intent is a new operation
  on the new state. No exception text reaches players. Whether ARCE catches an N throw to finish its
  other N items is WIT-OPEN-2. Without resync, the next menu broadcast shows actual server state.
- A refusal's resync is likewise post-decision. View-drift repair is a separate operation, never
  inside an F-C interval.

**SQ-2, restated (not weakened):** per route (menu slot, cursor, player inventory slot, persistence
mark), no foreign callback from the start of F through the last C assignment; a callback there must be
proved deferrable to N, else the route stays unadmitted. The whole-operation rule is not adopted.

## 4. Affected planned observations

All planned; none executed; every case **UNVERIFIED**.

| Case | Change or new expectation |
| --- | --- |
| BT preamble | R-a/R-b judge P and F refusals only; COMMITTED with an N fault is not a refusal |
| BT10 | callback counter zero from step 6 start through the last C assignment; N counted separately |
| BT14 | each path also records its affected-stack set and I-1..I-3; **DEPENDENCY_BLOCKED on SQ-4** per path |
| BT27 | replaced by BT27a-d (step 3 callback hook; A1) |
| BT27a | cursor replaced: R-b via I-1; newer cursor kept; old not restored |
| BT27b | incoming tag replaced; separately, tag added to an untagged stack: R-b via I-2; newer tag kept |
| BT27c | key added in place to the same tag: R-b via I-3; newer tag kept; no partial move or drop |
| BT27d | key added, then removed before step 6: committed; published form value-equal to final input |
| BT28 | zero foreign callbacks from step 6 start through the last C assignment; persistence mark precedes N. **DEPENDENCY_BLOCKED on SQ-2** |
| BT35 new (A1, S1) | hook throws in N after passing INSTALL, REMOVE, withdrawal, quick-move: COMMITTED, no refusal, no retry; save and restart keep it, no duplicate or loss. **DEPENDENCY_BLOCKED on SQ-2** |
| BT36 new (A0) | incoming tag at the C and W ceilings, one over, and a foreign tag class: R-a before step 2; at most ceiling plus one visits per walk |

Other cases, B§5.5 destruction and P4§3-§5 are unchanged.

## 5. Dependencies and impact

New: SQ-4, WIT-OPEN-1, WIT-OPEN-2; SQ-2 and SQ-3 restated. Still open: SQ-1, RP-1..RP-6,
BND-OPEN-1..3, CONTRACT-02 D-1..D-9 and E2-OPEN-1..3, REVIEW-02 M2 and other L1 parts, owner choices,
BOUNDS-HANDOFF-03 read deviation; retention, destruction, alias ownership and workstation work. No
source, test, registry, configuration, network, save, asset, ADR, risk, ledger, status or AGENTS
change; no S1, S2, V1 or V2 result; passes no v1.8 G0-G9 Gate.
