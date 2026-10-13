# V180 C17b: station controls, landing pads, force field and light, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C17b ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages and the records in
`docs/work/v1.8.0-root-queue/records/`. Every SHA below resolves with `git cat-file -e` and is
an ancestor of `codex/v1.8.0-classic-content` at `e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slices | Tested HEAD | Hosted run |
|---|---|---|---|
| `17fb7e15` | W04 (ADR-065 section 6.2 projector, technical field, exact claims, orphan cleanup root, API 1.9 `FIELD_PROJECTION`, R-022), Root's merge fixes, W04F (review F1 to F4) | `874bba5c` | 38207662335 |
| `f557e5c0` | W04G (force field N1 to N3) | `d3e04104` | 38220027274 |
| `0fc2aed4` | W07 (ADR-065 section 5: warp, gravity, altitude and orientation controllers; record schema 3 and registry schema 5 with the orbital clock; station sky payload schema 2 on celestial protocol 4), W07F (review R1 to R5 and the frozen `classic_controls` channel at protocol 1), W07G (warp quote cost and countdown always visible), Root's merge fixes | `5cdef2ae` | 38226376205 |
| `19da6240` | W13 (named landing pads as flight targets), W13F (review R1: named-pad occupancy against every discovered rocket's structure extent) | `20bc128f` | 38248887802 |
| `58d763d4` | W24 (the legacy unnamed-pad selector's occupancy, the W13 review's R1, legacy half) | `b91efd55` | 38336046793 |
| `286bdfe9` | W22, W22F (ADR-065 section 5.3 station notices and the bounded offline member-notice SavedData) | `4901af7b` | 38337117540 |

The registering commits inside these merges are `44822fe6` (force field and projector, in
`17fb7e15`), `2f66f118` (the four station controllers, in `0fc2aed4`) and `e0bebdbd` (landing
pad, in `19da6240`).

The station light was delivered before the Root handover: `a9393281` (2026-10-06, "add ordinary
light and bounded regression tests") with its resources in `7ae88260`, both directly on the
shared branch. Its records are in `docs/work/v1.8.0-c17b-station-light/`.

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
| 38207662335 | `874bba5c` | `codex/v1.8.0-w04-force-field` | 1 | success |
| 38220027274 | `d3e04104` | `codex/v1.8.0-w04g-force-field` | 1 | success |
| 38226376205 | `5cdef2ae` | `codex/v1.8.0-w07-station-controls` | 1 | success |
| 38229462409 | `a04db360` | `codex/v1.8.0-classic-content` | 1 | success |
| 38248887802 | `20bc128f` | `codex/v1.8.0-w13-landing-pads` | 1 | success |
| 38336046793 | `b91efd55` | `codex/v1.8.0-w24-legacy-pad-occupancy` | 1 | success |
| 38337117540 | `4901af7b` | `codex/v1.8.0-w22-station-notices` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

QUEUE.md also cites run 38229462409 for W07. Its head `a04db360` is on the shared branch and
contains `0fc2aed4`. Run 38371580132 tested the shared branch after every Root-queue merge.
Since `888a81b2` the shared branch has changed only documents.

## Independent reviews

The reviews are kept outside the repository. W31 recomputed the SHA-256 values on 2026-10-13;
they match the merge messages. W24 had a Root review only; W07G's client-only change was
reviewed by Root.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W04-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W04-review/REVIEW.md` | BLOCK, fixed by W04F | `45695dd5b04eb9761f75e5b900515b2935fbb56375948e004b777043a958e6b4` |
| W04F-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W04F-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `49319b9caa76a894835008101aac6bd7ab60ff08e8e89567826a2499605d3735` |
| W04G-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W04G-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `ec894726a1ffe653d6bee9d276a7eddeff3b2f09e04c11bfb948a8e759c2eb3e` |
| W07-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W07-review/REVIEW.md` | BLOCK, fixed by W07F | `266db5a543b00f3b4eb45a112209b62f9d14cfb12b4f1d2383ed6afbdeb38edb` |
| W07F-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W07F-review/REVIEW.md` | BLOCK on N1 only, fixed by W07G | `19859b139a62abaf42ad7f27c9f5b017a683c9ceda59aea5b21614b65636c39b` |
| W13-review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W13-review/REVIEW.md` | BLOCK on R1 | `d5b1db4adc436c5059af516a5637904038d1ab125fd9d80117471e667c167a63` |
| W13F-review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W13F-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `ba6c974da4eece579c262c14d7d717aa5da18d70acb8806dc9d686a5ad85465d` |
| W22-review (Claude) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W22-review/REVIEW.md` | MERGE-WITH-FOLLOWUP | `1cbb11093ac46480458f0230dd741e31cac9df1331371f0d135bc243705b103d` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `block:altitudeController` | IMPLEMENTED | `advancedrocketrycommunity:station_altitude_controller` (W07): station orbital distance through the checked station commit |
| `block:circleLight` | IMPLEMENTED | `advancedrocketrycommunity:station_light` (`a9393281`): a decorative light block |
| `block:concrete` | REDESIGNED | vanilla `minecraft:gray_concrete` in the landing pad recipe (`recipes/landing_pad.json`, W13) replaces the legacy block |
| `block:forceField` | IMPLEMENTED | `advancedrocketrycommunity:force_field` (W04): a technical block placed only by the projector |
| `block:forceFieldProjector` | IMPLEMENTED | `advancedrocketrycommunity:force_field_projector` (W04, W04F, W04G): an owner-protected, bounded field through the protection chain |
| `block:gravityController` | IMPLEMENTED | `advancedrocketrycommunity:station_gravity_controller` (W07): the block front end for the station gravity commit |
| `block:landingPad` | IMPLEMENTED | `advancedrocketrycommunity:landing_pad` (W13, W13F, W24): named landing pads as flight targets with occupancy checks |
| `block:orientationController` | IMPLEMENTED | `advancedrocketrycommunity:station_orientation_controller` (W07): station orientation (sky) through the checked station commit |
| `block:warpMonitor` | IMPLEMENTED | `advancedrocketrycommunity:warp_controller` (W07, W07G, W22): a screen over the ADR-046 commands on the `classic_controls` channel |

Nine rows: 8 IMPLEMENTED and 1 REDESIGNED. No C17b row stays PLANNED.

## Open items

- **S1 (dedicated server and native restart):** not done. The W07 merge lists V1/V2,
  dedicated-server and native restart evidence as open.
- **S2 (crash cut across stores):** no crash-cut evidence for this batch. It persists across
  several stores: the station registry (schema 5) with the orbital clock, the station records
  (schema 3), the force field's orphan cleanup root and W22's offline member-notice SavedData.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence for the controller screens, the warp
  quote, the notices, the landing pads, the force field or the light.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch:**
  - W04G: the optional N4 to N6;
  - W13: N1 (the loaded-only discovery residual), N2 (three weak GameTest assertions) and N3
    (station-wide refusal diagnostics);
  - W22: F8 (a new member is shown the outcome from before they joined), F9 (default names
    contain the creator's name) and F10 (two tests).

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.
