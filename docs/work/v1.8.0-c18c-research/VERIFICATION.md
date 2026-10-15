# V180 C18c: classic advancements, observatory and data processor, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C18c ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages, the records in
`docs/work/v1.8.0-root-queue/records/` and `docs/work/v1.8.0-c18c-tutorial-inventory/`. Every
SHA below resolves with `git cat-file -e` and is an ancestor of
`codex/v1.8.0-classic-content` at `e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slices | Tested HEAD | Hosted run |
|---|---|---|---|
| `667fa40c` | W12 (ADR-066 sections 6.2 and 6.3): the classic milestone advancements with the `advancedrocketrycommunity:classic_event` trigger fired at real server completion points, the player guide and a packaged-data reachability test | `4093fa04` | 38229336877 |
| `f5038f2f` | W15: the crystallizer and dilithium advancements use their real items and are proven reachable | `963d7db5` | 38239083487 |
| `fa890675` | W21a: the observatory and processor design (design only) | design only | none |
| `a7b0af22` | W21b, W21bF (ADR-066 section 6.1 D2, the ADR-051 revision): instance schema 2, satellite registry root 4 with the `ground_surveys` section, the pre-start migration | `bf723b8a` | 38344780650 |
| `5e75f835` | W21c, W21cF: the observatory and the astrobody data processor as classic controllers with research profiles | `cabba779` | 38368323736 |

Commits inside these merges: `e6ae40ae` (the milestone advancements, in `667fa40c`), `8bf4fb5f`
(in `f5038f2f`: the crystallizer advancement, which used `minecraft:impossible` until then, and
the reachability proof for both; the generated `classic/dilithium.json` is unchanged since
`e6ae40ae`) and
`4d9496ab` (observatory and processor blocks, in `5e75f835`).

The six inventory tutorial advancements (`classic/root`, `classic/block_press`,
`classic/rolling`, `classic/electrolysis`, `classic/suited_up`, `classic/warp_core`) were
delivered before the Root handover by `a5abc348` (2026-10-04, C18c-01), directly on the shared
branch; their generated files have not changed since. The task is
`docs/work/v1.8.0-c18c-tutorial-inventory/TASK.md`.

The `5e75f835` merge message reports that W21cF re-verified on the W21bF tree with 2,803 unit
tests, 875 GameTests and 8 native cycles pinned to the jar.

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
| 38229336877 | `4093fa04` | `codex/v1.8.0-w12-advancements` | 1 | success |
| 38239083487 | `963d7db5` | `codex/v1.8.0-w15-recipe-graph` | 1 | success |
| 38344780650 | `bf723b8a` | `codex/v1.8.0-w21b-registry-schema4` | 1 | success |
| 38368323736 | `cabba779` | `codex/v1.8.0-w21c-observatory` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

Hosted run 38353212060 of W21c crashed on nested GameTest scheduling; W21cF fixed it, and
38368323736 is the passing run. Run 38371580132 tested the shared branch after every
Root-queue merge. Since `888a81b2` the shared branch has changed only documents.

## Independent reviews

The reviews are kept outside the repository. W31 recomputed the SHA-256 values on 2026-10-13;
they match the merge messages. W12 and W15 had Root reviews only.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W21a-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W21a-review/REVIEW.md` | APPROVE-WITH-CHANGES | `0c76c2d4f9d125ca1af75ee96e35ddbd46ba914aaabbfddf5f9d3f32d01dfb50` |
| W21b-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W21b-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `d2a7401cb474a01c63fe011533ecbfa9d9abdafb441cd41e6b9af64ce0eb9d4a` |
| W21c-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W21c-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `8917c9fa7a34d38b7cb52765477dc7971125cb5dc5ad59a1059574fc1e3f5126` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `advancement:beer` | IMPLEMENTED | advancement `classic/seat_on_tnt` (W12) |
| `advancement:blockpresser` | IMPLEMENTED | advancement `classic/block_press` (`a5abc348`) |
| `advancement:crystalline` | IMPLEMENTED | advancement `classic/crystallizer` (W12, W15) |
| `advancement:dilithium` | IMPLEMENTED | advancement `classic/dilithium` (W12, W15) |
| `advancement:electrifying` | IMPLEMENTED | advancement `classic/electrolysis` (`a5abc348`) |
| `advancement:feeltheheat` | IMPLEMENTED | advancement `classic/arc_furnace` (W12) |
| `advancement:givingitallshesgot` | IMPLEMENTED | advancement `classic/station_warp` (W12) |
| `advancement:holographic` | REDESIGNED | advancement `classic/formed_multiblock` (W12): a new trigger, the first formed classic multiblock |
| `advancement:moonlanding` | IMPLEMENTED | advancement `classic/moon_landing` (W12) |
| `advancement:rollin` | IMPLEMENTED | advancement `classic/rolling` (`a5abc348`) |
| `advancement:root` | IMPLEMENTED | advancement `classic/root` (`a5abc348`) |
| `advancement:spindoctor` | IMPLEMENTED | advancement `classic/lathe` (W12) |
| `advancement:suitedup` | IMPLEMENTED | advancement `classic/suited_up` (`a5abc348`) |
| `advancement:warp` | IMPLEMENTED | advancement `classic/warp_core` (`a5abc348`) |
| `advancement:wenttothemoon` | REDESIGNED | advancement `classic/moon_surface_visit` (W12): a new trigger, the first arrival on the Moon's surface |
| `block:observatory` | IMPLEMENTED | `advancedrocketrycommunity:observatory` (W21a, W21b, W21c; ADR-051): a ground survey that creates ADR-051 asteroid instances |
| `block:planetAnalyser` | IMPLEMENTED | `advancedrocketrycommunity:astrobody_data_processor` (W21c): turns survey data into research |

Seventeen rows: 15 IMPLEMENTED and 2 REDESIGNED. The advancement targets are written as
`advancement classic/<id>`, because the ledger's ID pattern would otherwise read `classic` as an
unregistered modern ID (W30 section 2.1). Two C18c rows stay PLANNED:
`advancement:onesmallstep` and `advancement:flightofpheonix`. Their generated files,
`classic/one_small_step.json` and `classic/first_station_warp.json`, still use
`minecraft:impossible` (ADR-066 section 6.2; D3).

## Open items

- **S1 (dedicated server and native restart):** not done. W21cF ran 8 native cycles pinned to
  the jar; W31 does not count that as the S1 campaign.
- **S2 (crash cut across stores):** no crash-cut evidence for the whole batch. W21b's registry
  schema 4, the instance schema 2 and W21c's physical identity directory with collision
  quarantine persist across stores.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence. W21c's V1 of the menu texts is open.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch:**
  - found by W31 on 2026-10-13 with `python -B scripts/validate_repository.py` (its v0.9.0
    resource audit): the generated block models of the observatory and the astrobody data
    processor (written by `4d9496ab`) use the side and top texture
    `advancedrocketrycommunity:block/machine_casing`, which does not exist. Only
    `machine_casing_front`, `machine_casing_side` and `machine_casing_top` exist. It is the same
    defect that `0596d124` fixed for the cutting machine. A client would draw the missing-texture
    pattern on those faces;
  - W21b F9: a station-scale timing miss under machine load (R-030);
  - W21c M1 (optional): a pause on an over-reserved restored root;
  - the two world-first advancements, after W11b2's rocket journal schema 3.

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.

## Addendum (Root, 2026-10-15): W44 world-first advancements

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run | Reviews |
|---|---|---|---|---|
| `90d0714d` | W44I, W44J, W44K (ADR-066 section 6.2, owner decision D3) | `21d8dfcd` | 38610691744 (success) | design `bf8a200e…`, `35030212…`; implementation `1fa55c36…` (BLOCK, closed by W44J), `ebe02455…` (closed by W44K) |

The design is `records/W44-DESIGN.md`; the records are `records/W44I.md` (with its W44J and W44K sections) and `records/W44R.md`, `W44S.md`. Native evidence is in `D:/GitHub/ARCE-Task-Evidence/root-queue/W44I/`.

| Unit | Disposition | Delivery |
|---|---|---|
| `advancement:onesmallstep` | REDESIGNED | `classic/one_small_step`: the participants of the first committed world Moon rocket landing, elected in the transfer journal's outbox and granted from a durable receipt (R-037) |
| `advancement:flightofpheonix` | REDESIGNED | `classic/first_station_warp`: the participants of the first committed world station warp, elected inside the checked relocation and granted from a durable receipt (R-037) |

This addendum is development evidence only. It approves no Required Gate, candidate, version or release. The remaining H rows (W44L) and the real-client rows remain open.

## Addendum (Root, 2026-10-15): W44L closes the world-first H rows

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run | Review |
|---|---|---|---|---|
| `8e123530` | W44L: the remaining 31 H rows (104 starts, each row with at least two restarts) and the pre-W44 jar's refusal of the four migrated roots (12 starts) | `8c769fcd` | 38686287927 (success) | Root review |

The record is `records/W44L.md`; the packets, pinned to committed sources and jars, are in `D:/GitHub/ARCE-Task-Evidence/root-queue/W44L/`. The real-client rows remain for the final campaign. This addendum is development evidence only. It approves no Required Gate, candidate, version or release.
