# C17 proposed contract and independent review

Date: 2026-10-03. Contract status: **ACCEPTED**, conditional leaf admission;
runtime status: planned.
Owner: root integrator; separate delegated author and independent reviewer.
Branch: `codex/v1.8.0-classic-content`; HEAD `cd63c5ff` plus retained dirty work.
Workers may read this checkout and write only their own Temp proposals/reviews.
Root alone persists work records; no runtime or ledger closure is authorized here.

Coverage: all 41 primary C17 units, C17a 24 / C17b 9 / C17c 8.
The [revision-2 proposal](proposals/revision-02/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md)
and its companion mappings/source hashes are a draft snapshot, not an accepted ADR.

- Owner-confirmed: continuous three-axis rotation and logical orbit altitude,
  without moving station blocks; refuse changes that break linked/busy elevator
  endpoint conditions.
- [Independent revision-2 review](reviews/revision-02/REVIEW-02.md) found
  0 Critical /0 High /1 Medium /0 Low. Its M6 persistent-clock gap is addressed
  in [revision 3](proposals/revision-03/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md).
  [Independent revision-3 review](reviews/revision-03/REVIEW-03.md) found no
  Critical/High/Medium/Low scoped contract issue; native clock/save-order
  fixtures and exact leaf inputs still restrict implementation.
- [Owner decisions](OWNER-DECISIONS.md) now select the retained basic baseline,
  independent typed fluids, complete-vector disassembly consent and typed/external
  engine-capacity isolation. The separately confirmed D3-A selects one persistent
  transaction/receipt coordinator for rocket resource movements and satellite
  deployment; forced-stop recovery verification precedes opening these features.
  [Revision-4 independent review](reviews/revision-04/REVIEW-04.md) has no
  unresolved Critical/High/Medium/Low in its decision-only diff. The
  [acceptance receipt](ACCEPTANCE.md) records conditional contract acceptance. Checked
  writers, exact shared leaf contracts and technical/native proof remain required.
- Joint C18 finder/sky and source-outbox schema changes must freeze once at the
  shared boundary, not as two incompatible independent transitions.

Remaining admission: exact persistence/network
leaf contracts frozen before their runtime implementation. Static counts/hashes
prove draft coverage only; native/GPU tests and all Required Gates remain open.
Next work is eligible v1.8 leaf implementation with the retained technical/native
admission reviews, not v1.9.
