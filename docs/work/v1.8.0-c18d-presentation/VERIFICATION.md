# V180 C18d: HUD, client settings and render layers, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C18d ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages,
[records/W16.md](../v1.8.0-root-queue/records/W16.md) and the earlier C18d folders named
below. Every SHA below resolves with `git cat-file -e` and is an ancestor of
`codex/v1.8.0-classic-content` at `e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run |
|---|---|---|---|
| `6a20a674` | W16 (C18d, ADR-066 section 7): up to five non-overlapping HUD panels, the ten classic sound events with NEW synthesized audio, worn jetpack, tank and visor mounts, `effects.advancedVisuals`, and client settings for the classic HUD, visuals and sky | `a6001eb9` | 38240401114 |

W16's commits for the rows below (records/W16.md):

- `6afe5c7d`: the CLIENT settings `hud.hydrogen.*`, `hud.suit.*`, `effects.advancedVisuals` and
  `sky.overworldOverride`;
- `af7ddc31`: the panel layout solver for one to five panels;
- `6acbb578`: the hydrogen and suit panels, and the rocket readout from the ridden rocket's
  synchronized state, fuel and altitude;
- `0be6f639`: `effects.advancedVisuals=false` removes accent particles;
- `fc5b57f6`: the player render layer for worn jetpack, tank and visor mounts;
- `3f2f1827`: the provenance record `docs/provenance/v1.8.0-c18d-presentation.json`, review
  pending.

Delivered before the Root handover, directly on the shared branch:

| Content | Commits | Records |
|---|---|---|
| Planet and station sky switches (`sky.planetOverride`, `sky.stationOverride`) | `cbbb78e1`, `d2b4324f` | `docs/work/v1.8.0-c18d-sky-switches/` |
| Environment and oxygen HUD layout (`hud.environment.*`, `hud.oxygen.*`) | `f9f2d9d2` | `docs/work/v1.8.0-c18d-hud-layout/` |

## Hosted CI

Each run is the workflow `v1.8 development regression`
(`.github/workflows/v180-development.yml`): the ledger validator with `--require-accepted`,
`clean build` with fresh unit tests, the artifact audit, the client-boundary check, `runData`
twice with a clean-tree check, all Forge GameTests and `check_gametest_log.py`. It runs no
dedicated server, no restart and no real client, so it does not draw the HUD or the render
layer.

On 2026-10-13 W31 read each run back through the GitHub REST API
(`/repos/sunthemoon/AdvancedRocketry-Community/actions/runs/<id>`).

| Run | Head SHA returned | Branch | Attempt | Conclusion |
|---|---|---|---|---|
| 38240401114 | `a6001eb9` | `codex/v1.8.0-w16-presentation` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

The pre-handover commits above are ancestors of `a6001eb9`. Run 38371580132 tested the shared
branch after every Root-queue merge. Since `888a81b2` the shared branch has changed only
documents.

## Independent reviews

W16 had a Root review only; there is no independent `REVIEW.md` for this batch. The merge
message records that the endgame block-entity changes are client-only particle and sound
gating, and that a trial merge onto `0d5d20b7` passed the full unit suite. The pre-handover
slices record their reviews in their own folders.

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `config:CLIENT.advancedVFX` | REDESIGNED | CLIENT `effects.advancedVisuals` (W16) |
| `config:CLIENT.atmBarModeX` | REDESIGNED | CLIENT `hud.environment.anchorX` (`f9f2d9d2`) |
| `config:CLIENT.atmBarModeY` | REDESIGNED | CLIENT `hud.environment.anchorY` (`f9f2d9d2`) |
| `config:CLIENT.atmBarX` | REDESIGNED | CLIENT `hud.environment.x` (`f9f2d9d2`) |
| `config:CLIENT.atmBarY` | REDESIGNED | CLIENT `hud.environment.y` (`f9f2d9d2`) |
| `config:CLIENT.hydrogenBarModeX` | REDESIGNED | CLIENT `hud.hydrogen.anchorX` (W16) |
| `config:CLIENT.hydrogenBarModeY` | REDESIGNED | CLIENT `hud.hydrogen.anchorY` (W16) |
| `config:CLIENT.hydrogenBarX` | REDESIGNED | CLIENT `hud.hydrogen.x` (W16) |
| `config:CLIENT.hydrogenBarY` | REDESIGNED | CLIENT `hud.hydrogen.y` (W16) |
| `config:CLIENT.overworldSkyOverride` | REDESIGNED | CLIENT `sky.overworldOverride` (W16); defined, but the Overworld has no ARCE sky yet, so both values show the vanilla sky |
| `config:CLIENT.oxygenBarModeX` | REDESIGNED | CLIENT `hud.oxygen.anchorX` (`f9f2d9d2`) |
| `config:CLIENT.oxygenBarModeY` | REDESIGNED | CLIENT `hud.oxygen.anchorY` (`f9f2d9d2`) |
| `config:CLIENT.oxygenBarX` | REDESIGNED | CLIENT `hud.oxygen.x` (`f9f2d9d2`) |
| `config:CLIENT.oxygenBarY` | REDESIGNED | CLIENT `hud.oxygen.y` (`f9f2d9d2`) |
| `config:CLIENT.PlanetSkyOverride` | REDESIGNED | CLIENT `sky.planetOverride` (`cbbb78e1`) |
| `config:CLIENT.StationSkyOverride` | REDESIGNED | CLIENT `sky.stationOverride` (`cbbb78e1`) |
| `config:CLIENT.suitPanelModeX` | REDESIGNED | CLIENT `hud.suit.anchorX` (W16) |
| `config:CLIENT.suitPanelModeY` | REDESIGNED | CLIENT `hud.suit.anchorY` (W16) |
| `config:CLIENT.suitPanelX` | REDESIGNED | CLIENT `hud.suit.x` (W16) |
| `config:CLIENT.suitPanelY` | REDESIGNED | CLIENT `hud.suit.y` (W16) |
| `event:RenderComponents.renderPostSpecial(Post)` | REDESIGNED | a bounded client render layer for worn jetpack, tank and visor mounts (W16, `fc5b57f6`) |
| `event:RocketEventHandler.onScreenRender(Post)` | REDESIGNED | the rocket HUD readout from synchronized flight state (W16, `6acbb578`) |

Twenty-two rows, all REDESIGNED; none names a modern registry ID. Twelve C18d rows stay PLANNED
(W30 section 2.3):

- `entity:ARSpaceElevatorCapsule`: there is no S2C countdown carrier for the elevator capsule
  effect;
- `event:ClientProxy.modelBakeEvent(ModelBakeEvent)`: no OBJ file has been imported;
- ten `sound_event` rows: `sound_event:airHissLoop`, `sound_event:basicLaserGun`,
  `sound_event:combustionRocket`, `sound_event:ElectricShockSmall`, `sound_event:gravityOhhh`,
  `sound_event:laserDrill`, `sound_event:lathe`, `sound_event:MachineLarge`,
  `sound_event:railgunBang` and `sound_event:rollingMachine`. W16's sound definitions are in
  `assets/advancedrocketrycommunity_v180/sounds.json`, which the ledger validator does not read,
  so these rows cannot be IMPLEMENTED until the namespace is folded or the validator changes.

## Open items

- **S1 (dedicated server and native restart):** not done for this batch.
- **S2 (crash cut across stores):** not applicable as far as W31 can tell: this batch adds
  client settings and client rendering and no store. No crash-cut evidence exists.
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence. records/W16.md lists what V1 must
  cover (every HUD panel in en_us and zh_cn, GUI scales 1 to 6, all anchors and offsets, the
  rocket readout while riding, the worn mounts from all sides) and what V2 must show (another
  player's mounts, independent HUD layouts).
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and audio and the provenance review
  (`docs/provenance/v1.8.0-c18d-presentation.json`, review pending) are open.
- **Follow-ups of this batch (ADR-066 section 7.1):**
  - fold the `advancedrocketrycommunity_v180` sound namespace into the main namespace;
  - producers for the `lathe`, `rolling_machine` and `machine_large` sounds;
  - the manual listen of all ten sounds;
  - the elevator capsule effect, which needs a Root-owned S2C carrier;
  - OBJ model loading, once OBJ assets are imported;
  - client rendering of the black-hole sky.

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, V1, V2, the manual
listen and the art and provenance reviews remain open, as listed above.
