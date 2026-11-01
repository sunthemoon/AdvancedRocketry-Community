# Owner delegation and Root's decisions of 2026-11-01

Channel: the owner's Claude Code Root session, 2026-11-01, about 08:55. Three questions were open:

- Q1, W230's safe-exit fallback residuals, A or B;
- D1c-N, W231's per-owner pending cap, 8, 16 or 32, and its loader-and-bay scope;
- W235-D, the first-event progression flush on the server thread: accept, or a designed asynchronous flush.

Root's last message had asked for the three answers. The owner replied, verbatim:

> 你来决定吧，就设计而言你的想法肯定更有参考价值

**Scope of the delegation.** These three questions only. Root decides them, records the reasons and may not widen
them. Each decision below stays within what the question offered. None approves a Required Gate, a version or a
release. None turns bay admission on, and none closes a risk register row beyond the text quoted. The owner can
revisit any of them; a revisit supersedes the decision here and is recorded the same way.

| ID | Question | Root's decision under the delegation | Reasons |
|---|---|---|---|
| Q1 (R243) | Accept W230's finite fallback (one ordinary-fall safeguard when no safe landing is proved) with its listed residuals, or require stronger rescue guarantees? | **A: accept the residuals as written in W230-DESIGN Revision 1, section 11.** Implementation, an independent review of the actual diff, and the two-client V2 evidence in its section 7 follow. Bay admission stays off until those exist. | Dismount is never cancelled (D1b), and W230 shows that no fallback is universally safe in an arbitrary edited world without loading chunks or editing terrain. The safeguard removes the common 48-block fall. Stronger rescue (B) would need new terrain, teleport or authority infrastructure, beyond v1.8's scope. The residuals are published, not hidden. |
| D1c-N (R244) | The per-owner pending cap: 8, 16 or 32; one union across loader, cargo and bay operations? | **16 per owner, over the same unique pending union of loader, cargo and bay operations (PROPULSION and BAY), with no operator exemption**, the OWNER_PENDING_FULL refusal, and W231's rules: retained receipts unchanged, re-arm reacquires, startup rebuild, over-cap saves kept without data loss, and no new persisted field. ADR-065 section 10 gets W231's exact text. | 16 lets one owner keep two full eight-bay rockets, or one rocket plus eight loader operations. No owner can hold more than a quarter of the 64 shared slots. A bay-only count would let loader work fill the rest. 8 starves a single full rocket's loader work. 32 lets two owners exhaust the server. |
| W235-D (R245) | Accept the 139.8 ms checked progression flush on the server thread at first-event delivery, or commission a designed asynchronous flush? | **Accept for v1.8 as a disclosed limitation, measured again on the docs/17 reference node in the final campaign.** If the reference node's first-event tick exceeds the budget there, an asynchronous progression flush becomes a designed and reviewed persistence task before release. Otherwise it moves to v1.9. | The flush is the durability barrier that confirms first-event receipts. Moving it off the tick changes the durability order, which is a persistence redesign with crash-window risk. It runs once per first event, not every tick. The 139.8 ms was one Windows development-host sample of an fsync; the budgets are defined on the Linux reference node. |

Each decision's follow-up is a QUEUE task, with an independent review where AGENTS.md section 13 requires one.

## Standing delegation for design points

A few minutes later, while Root was recording the decisions above, the owner wrote in the same session, verbatim:

> 遇到设计点的按你的推荐来即可

**Scope.** From 2026-11-01, when a task reaches a design point that would otherwise go to the owner, Root takes its
own recommendation. A design point is a choice between behaviours, mechanisms, limits or interaction designs that a
design note, review or brief presents, such as W230's Q1 or W231's cap. For each one, Root records the question, the
options, the choice and the reasons in QUEUE.md or a record, with this paragraph as its authority, and the owner can
revisit it at any time.

The delegation does not cover the decisions that AGENTS.md section 13 and the version documents keep with the owner:

- approving a Required Gate, a version or a release;
- tags, merges to main and force pushes;
- the owner's own human reviews (D5's listen to the sounds, D7's provenance and licence review);
- the real-client and reference-node campaign;
- accepting a risk that a design choice does not itself state.
