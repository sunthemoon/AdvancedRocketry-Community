# Solar generator preservation decision

Date received: 2026-10-06, asynchronous conversation reply. Scope: v1.8.0
single solar generator only. This is a product decision, not implementation,
evidence acceptance, whole-risk acceptance or a release approval.

Root's question describes preservation/refusal and its consequences: data is
not overwritten, a refused save affects the whole chunk, the 257th different
refused chunk expands refusal to the entire ServerLevel, and restart does not
repair anomalous data. It asks whether solar energy data should join that
protection with an R-021 update, without accepting/closing all R-021.
Its short wording combines unknown and over-limit cases; actual application
must distinguish them. Bounded faithfully preservable unknown/future roots
pass through unchanged. Whole-chunk veto occurs only when the existing
operational/native preflight fails or the physical BE-list bound is exceeded,
not merely because a schema is unknown. Sticky denial follows an actual veto.

The owner's exact selected answer is:

> 纳入太阳能数据，保留拒存与修复规则（推荐）

## Application and limits

This selects applying the existing preservation/refusal policy to the proposed
solar generator's own energy root under ADR-065 revision 4, sections 2 and 9.
The root and its chunk may acquire the existing sticky Level-lifetime refusal;
unrelated changes in that chunk may remain unsaved, and saturation may refuse
other chunks. Existing diagnostics can emit paired EventBus/ChunkMap ERRORs
with stacks, without a guard-layer total retry/log cap. A newly constructed
Level can rebuild runtime denial state, but the unsupported native data still
needs verified recovery/offline repair. No production online-clear mechanism
or new discard, reset, quarantine or forced-shutdown policy is selected.

The independently reviewed leaf must freeze the exact bounded native root,
recovery tests and registration before code is assigned. Normal supported
energy saves, unsupported/future/oversize raw retention and shutdown/restart
must be verified on the committed implementation; this answer is not that proof.
It does not approve solar recipe/weather/art choices, arrays, shared hatches,
GuardTicket/frame writers, typed rocket transactions, first-event holds or a
general saving-policy extension. R-021 stays open and unaccepted as a whole.

Root only synchronizes the R-021 row and this scoped record. Other risk rows,
accepted ADR text, source behavior and owner-maintained AGENTS are unchanged.

The [original independent review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/solar-owner-record-review-20261006-8a6701/REVIEW-01.md)
identifies that trigger-wording Low. This clarification preserves the original
review and decision, changes no save behavior, and requires separate recheck.
