# V180 C17c: solar power and the planetary beacon, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C17c ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages and the records in
`docs/work/v1.8.0-root-queue/records/`. Every SHA below resolves with `git cat-file -e`. All of
them are ancestors of `codex/v1.8.0-classic-content` at `e4bd8a12`, except the two W27a design
commits, which are marked as not merged.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slices | Tested HEAD | Hosted run |
|---|---|---|---|
| `b9ace0cd` | W17, W17F: the controller-owned 3 x 22 solar array on the v1.2 kernel, a 20,000 FE buffer, two output-only plugs sharing 1,000 FE per tick, a schema-1 root with retained-root and whole-chunk save protection (R-021) | `81b288b6` | 38283733951 |
| `804be2ab` | W18, W18F: the `planetary_beacon` multiblock, a bounded server-wide beacon index, and the `beacon_finder`, which streams the viewer's own nearest 8 markers over the frozen `classic_controls` channel (R-028) | `9f9e3e85` | 38283811217 |
| `7fb7e50c` | W26: beacon follow-ups F4 (load-time flicker), F6 (nine GameTests) and F7 (the finder strip below boss bars) | `a9a2b691` | 38369606917 |

The registering commits are `2ca12e3e` (solar array and panel, in `b9ace0cd`) and `061eb492`
(beacon and finder, in `804be2ab`). The finder installs into the built-in helmet
(records/W18.md).

The single solar generator was delivered before the Root handover, directly on the shared
branch: `d8568c44` (2026-10-06, "integrate bounded solar generator and lifecycle"), which also
defines `energy.solarGeneratorMultiplier`, its resources `2e00d76a`, and the solar surface
fixture merge `e0c601e4`. Their records are in `docs/work/v1.8.0-c17c-solar-generator/` and
`docs/work/v1.8.0-c17c-solar-surface-fixture/`.

## Hosted CI

Each run is the workflow `v1.8 development regression`
(`.github/workflows/v180-development.yml`): the ledger validator with `--require-accepted`,
`clean build` with fresh unit tests, the artifact audit, the client-boundary check, `runData`
twice with a clean-tree check, all Forge GameTests and `check_gametest_log.py`. It runs no
dedicated server, no restart and no real client.

On 2026-10-13 W31 read each run back through the GitHub REST API
(`/repos/sunthemoon/AdvancedRocketry-Community/actions/runs/<id>`).

| Run | Head SHA returned | Branch | Attempt | Conclusion |
|---|---|---|---|---|
| 38283733951 | `81b288b6` | `codex/v1.8.0-w17-solar-array` | 1 | success |
| 38283811217 | `9f9e3e85` | `codex/v1.8.0-w18-beacon` | 1 | success |
| 38369606917 | `a9a2b691` | `codex/v1.8.0-w26-beacon-followups` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

The solar generator commits are ancestors of every tested HEAD above. Run 38371580132 tested
the shared branch after every Root-queue merge. Since `888a81b2` the shared branch has changed
only documents.

## Independent reviews

The reviews are kept outside the repository. W31 recomputed the SHA-256 values on 2026-10-13;
they match the merge messages. W26 had a Root review only (the one changed fixture assertion is
stricter).

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W17-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W17-review/REVIEW.md` | BLOCK, closed by W17F; Root reviewed the F1 claim diff | `4bd848ae286597d29e05bbb38ca45cddcd1ca9845ae0d07805bf40cc147873b2` |
| W18-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W18-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `7a65b5f1d81f738ea9b1ff101f6d3eea64d0e7f6ea31f3e3034dee904c6e63ff` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `block:beacon` | IMPLEMENTED | `advancedrocketrycommunity:planetary_beacon` (W18, W26): a bounded navigation marker per body |
| `block:solararray` | IMPLEMENTED | `advancedrocketrycommunity:solar_array` (W17): a controller-owned multiblock generator |
| `block:solararraypanel` | IMPLEMENTED | `advancedrocketrycommunity:solar_array_panel` (W17): the solar array structure |
| `block:solarGenerator` | IMPLEMENTED | `advancedrocketrycommunity:solar_generator` (`d8568c44`, resources `2e00d76a`): a single-block generator |
| `block:solarPanel` | IMPLEMENTED | `advancedrocketrycommunity:solar_panel` (`d8568c44`, resources `2e00d76a`): the generator's structure part |
| `config:ENERGY.solarGeneratorMultiplier` | REDESIGNED | COMMON `energy.solarGeneratorMultiplier` (`d8568c44`) |
| `item:beaconFinder` | IMPLEMENTED | `advancedrocketrycommunity:beacon_finder` (W18, W26): points to owned beacons and installs in the helmet |

Seven rows: 6 IMPLEMENTED and 1 REDESIGNED. One C17c row stays PLANNED:
`block_variant:loader/1`, the satellite bay (ADR-065 section 8). Its W27a design is delivered
(`b55a41f2`, `9fffc35b`, not merged), and its review has not run.

## Open items

- **S1 (dedicated server and native restart):** not done for the array, the beacon or the
  generator.
- **S2 (crash cut across stores):** no crash-cut evidence. The beacon index is a server-wide
  store kept apart from the beacon's own retained root.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence. W26's V1 of the finder strip
  position is open.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch:** W18 F8, provenance in the same commit as the assets.

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.
