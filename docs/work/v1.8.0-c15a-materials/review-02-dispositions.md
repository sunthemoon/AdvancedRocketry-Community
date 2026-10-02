# C15a implementation review round 2 — dispositions

Confirmation review of `1a9f956` by the same independent reviewer
([report](reviews/REVIEW-02.md)): 0 Critical, 0 High, 0 Medium, 1 Low, 4 Info,
no new defect. Verdicts: **C15a accept**; **ADR-063 revision 4 accept as
written**; **ADR-061 revision 7 accept as written**. Every round-1 finding that
needed a change was confirmed resolved by re-run mutants and probes; L3 (a
client start with JEI) and I4 (the human visual review of six textures) stay
tracked as open C15 items.

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| L1 | The network-side tag refusal was tested only for the rolling machine (mutants N2, N3 survived) | Fixed: wire tests for the precision assembler and the electrolyzer; both mutants now fail | `f00e2a3` |
| I1 | Kernel recipe signatures come from the resolved alternatives; lazy tags in C16a would invalidate running processes | ADR-061 revision 7 now makes a JSON-based signature (or a defined outcome) part of the C16a duty | `4201186` |
| I2 | Titanium, steel, silicon and both alloy dusts have no source in C15a | Recorded for the C16d recipe graph: each needs a source or an exemption entry | — |
| I3 | ADR-061's history listed revision 7 before revision 6 | Fixed | `4201186` |
| I4 | The electrolyzer now also refuses non-standard ingredient shapes | The CHANGELOG's data-pack note says so | `d18a58f` |

Owner decisions that remain open: acceptance of ADR-063 revision 4 and ADR-061
revision 7 (both recommended for acceptance as written), and the iron-plate
route (the press, as proposed, or re-keying `rolling_iron_bars`).
