# C16 contract reviews — dispositions

The [first review](reviews/REVIEW-01.md) requested changes (3 High, 7 Medium).
The [second review](reviews/REVIEW-02.md) found no additional unresolved
Critical/High/Medium technical finding in revision 2. That report correctly
retains the two owner decisions as pending at its own review timestamp.

The owner subsequently confirmed both decisions in the 2026-10-03 continuation:

- Controller-owned Item/Fluid banks and transaction journal; cross-chunk
  structures remain allowed, physical hatches expose interfaces only. Breaking
  a hatch drops its assigned Item bank at the loaded controller, not a second
  physical-hatch inventory. FE transfer has a separately stated guarantee.
- Nitrogen joins the existing eligible gas-giant harvest table. Tau Ceti f
  remains ineligible; mission rules and hydrogen output remain unchanged.

These decisions and the owner's conditional authorization accept ADR-064
revision 2. Reviewed technical text is bound by SHA-256
`41645ee12011bf33d74f7fd43962566790ac43a8232bca48283ea4b5d5400cdb`;
acceptance metadata/context/history was added afterwards, without changing its
technical requirements.

| Finding group | Revision 2 requirement |
|---|---|
| Resource ownership and recovery | One authoritative bounded controller snapshot; generation-scoped banks; facade invalidation; retained resources on unload/unform; prepared journal recovery |
| Pump protection | The complete ADR-054 loaded-chunk, bounds, protected-zone, station permission, spawn, public event and FakePlayer break sequence |
| Nitrogen eligibility | Existing gas-giant table only; no expansion of mission eligibility |
| Recipe signatures and migration | Canonical semantic JSON SHA-256; explicit legacy proof/conversion; pending journals reconciled before conversion; uncertain roots retained and blocked |
| Centrifuge chance/replay | Ordered independent rolls, candidate/output caps, stable ordinal-derived outcomes, stale pre-prepared plans re-simulated against current resources without rerolling |
| Energy and gravity | Positive recipe FE; body/station gravity, not a player-only field |
| Tank lifecycle and budgets | Deduplicated nonreentrant bounded work queue, dirty retention, per-tank and per-Level transfer caps |
| Stable roots and lookup budgets | Named independent schemas, byte/count caps, fail-closed preservation; bounded deterministic fair lookup; v1.2 ambiguity policy unchanged |

Independent Java 17 kernel probes ran successfully; raw source/logs and review
snapshots are in `reviews/rounds-01-02-evidence.zip`. They verify existing kernel
assumptions, not new C16 machine behavior. Runtime C16a–d, migration fixtures,
S1, visuals and version Gates are still pending. No candidate or Gate is approved.
