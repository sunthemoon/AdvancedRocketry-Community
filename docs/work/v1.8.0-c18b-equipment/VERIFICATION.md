# V180 C18b: suit equipment, jetpack, upgrades and tools, delivery evidence

Date: 2026-10-13. Written by W31 under
[TASK-W31](../v1.8.0-root-queue/TASK-W31-LEDGER-EVIDENCE.md) for the C18b ledger rows that
W30's proposal (`records/W30-STATUS-PROPOSAL.md` on branch `codex/v1.8.0-w30-status`,
`2bc87cee`, sections 1.1 and 2 and Appendix A) marks delivered. The facts come from
[QUEUE.md](../v1.8.0-root-queue/QUEUE.md), the merge commit messages, the records in
`docs/work/v1.8.0-root-queue/records/` and the earlier C18b folders named below. Every SHA
below resolves with `git cat-file -e` and is an ancestor of `codex/v1.8.0-classic-content` at
`e4bd8a12`.

This file is development evidence only. **It approves no Required Gate, candidate, version or
release.** Only the owner marks a Gate as passed (AGENTS.md sections 5 and 13).

## Merges and tested HEADs

| Merge on `codex/v1.8.0-classic-content` | Slices | Tested HEAD | Hosted run |
|---|---|---|---|
| `1e36af58` | W03: the suit workstation, pressure tanks and modules | `6132f9ca` | 38155101250 |
| `d8352a27` | W06, W06F (ADR-066 section 5.2): the jetpack channel, hydrogen-paid thrust and hover, bionic legs, padded boots, visors; W03 review M1 and L1 | `fda2cc98` | 38217298302 |
| `284709bf` | W20 (ADR-066 section 5.3): the basic laser gun, server-authoritative through vanilla item use with no new packet | `3611d553` | 38289378640 |

Commits inside these merges: `e0e378c1` (workstation, finite module storage, `OXYGEN.suitTankCapacity`
and `OXYGEN.spaceSuitO2Buffer`) and `736ae94c` (equipment resources and recipes) in
`1e36af58`; `030c3eb4` (`equipment.jetPackForce`, `equipment.lowGravityBoots`,
`effects.atmosphericNausea`), `1c2f4550` (jetpack, bionic legs, padded boots and the
`key.advancedrocketrycommunity.toggle_jetpack` key) and `61b07a0b` (anti-fog and Earthbright
visor effects, including the fog override in `client/sky/ClientSkyEvents.java`) in
`d8352a27`; `5ffd3358` (basic laser gun) in `284709bf`.

Delivered before the Root handover:

| Content | Commits | Records |
|---|---|---|
| Jackhammer | `61cada9e` (resources), `80bbf16d` (integration), `d95aa9b5` (start feedback when disabled) | `docs/work/v1.8.0-c18b-jackhammer/` |
| Suit reserve calculator, used by W03 | `707c389b`, merged by `eb90c643` | `docs/work/v1.8.0-c18b-reserve-transition/` |

The atmosphere fog itself predates v1.8 (`e810fb39`); W06 added the anti-fog visor and the
`effects.atmosphericNausea` switch that remove only the fog override.

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
| 38155101250 | `6132f9ca` | `codex/v1.8.0-equipment` | 1 | success |
| 38217298302 | `fda2cc98` | `codex/v1.8.0-w06-motion` | 1 | success |
| 38289378640 | `3611d553` | `codex/v1.8.0-w20-laser-gun` | 1 | success |
| 38371580132 | `888a81b2` | `codex/v1.8.0-classic-content` | 1 | success |

Every commit named above is an ancestor of `3611d553`. Run 38371580132 tested the shared branch
after every Root-queue merge. Since `888a81b2` the shared branch has changed only documents.

## Independent reviews

The reviews are kept outside the repository. W31 recomputed the SHA-256 values on 2026-10-13.
The W06 values match the merge message prefixes. The W03 merge message and QUEUE.md give no
value for W03-review, so W30 recorded the value first; W31's recomputation matches W30's. W20
had a Root review only.

| Review | Path | Verdict | SHA-256 |
|---|---|---|---|
| W03-review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W03-review/REVIEW.md` | MERGE-WITH-FOLLOWUP (M1 and L1, fixed by W06) | `4b74bf776c585444e8332a89a410baa612030ceb41e6861f6ba06c102b64f73f` |
| W06-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W06-review/REVIEW.md` | BLOCK, fixed by W06F | `9bdd12238f18c32df093a42df5bb9c610aeafe78852b1a53b34928409d0c71e0` |
| W06F-review (Codex) | `D:/GitHub/ARCE-Task-Evidence/root-queue/W06F-review/REVIEW.md` | MERGE | `753c20281362745da4d53077f6bc981b3a69aea282bd1be95294111c21d27f15` |

## Delivered ledger units

| Unit | Disposition | Delivery |
|---|---|---|
| `block:suitWorkStation` | IMPLEMENTED | `advancedrocketrycommunity:suit_workstation` (W03): installs suit upgrades and tanks |
| `config:CATEGORY_GENERAL.jetPackForce` | REDESIGNED | COMMON `equipment.jetPackForce` (W06) |
| `config:CATEGORY_GENERAL.lowGravityBoots` | REDESIGNED | COMMON `equipment.lowGravityBoots` (W06) |
| `config:CLIENT.EnableAtmosphericNausea` | REDESIGNED | CLIENT `effects.atmosphericNausea` (W06) |
| `config:OXYGEN.spaceSuitO2Buffer` | REDESIGNED | COMMON `OXYGEN.spaceSuitO2Buffer` (W03): the finite built-in reserve under D1 |
| `config:OXYGEN.suitTankCapacity` | REDESIGNED | COMMON `OXYGEN.suitTankCapacity` (W03): the tank creation multiplier under D1 |
| `event:PlanetEventHandler.fogColor(RenderFogEvent)` | REDESIGNED | atmosphere-density fog and the anti-fog visor (`e810fb39`, W06 `61b07a0b`) |
| `item:basicLaserGun` | IMPLEMENTED | `advancedrocketrycommunity:basic_laser_gun` (W20): ranged mining through break events and protection |
| `item:jackHammer` | IMPLEMENTED | `advancedrocketrycommunity:jackhammer` (`80bbf16d`, `d95aa9b5`): a fast mining tool |
| `item:jetPack` | IMPLEMENTED | `advancedrocketrycommunity:jetpack` (W03 registration, W06 behaviour): a chest attachment using suit hydrogen |
| `item_variant:itemUpgrade/0` | IMPLEMENTED | `advancedrocketrycommunity:hover_upgrade`: the jetpack hover mode |
| `item_variant:itemUpgrade/1` | IMPLEMENTED | `advancedrocketrycommunity:flight_speed_upgrade`: jetpack speed |
| `item_variant:itemUpgrade/2` | IMPLEMENTED | `advancedrocketrycommunity:bionic_leg_upgrade`: movement speed |
| `item_variant:itemUpgrade/3` | IMPLEMENTED | `advancedrocketrycommunity:padded_landing_boots`: fall protection |
| `item_variant:itemUpgrade/4` | IMPLEMENTED | `advancedrocketrycommunity:anti_fog_visor`: removes atmospheric nausea |
| `item_variant:itemUpgrade/5` | IMPLEMENTED | `advancedrocketrycommunity:earthbright_visor`: night vision in space |
| `item_variant:pressureTank/0` | IMPLEMENTED | `advancedrocketrycommunity:low_pressure_tank` (W03): suit gas capacity |
| `item_variant:pressureTank/1` | IMPLEMENTED | `advancedrocketrycommunity:pressure_tank` (W03) |
| `item_variant:pressureTank/2` | IMPLEMENTED | `advancedrocketrycommunity:high_pressure_tank` (W03) |
| `item_variant:pressureTank/3` | IMPLEMENTED | `advancedrocketrycommunity:super_high_pressure_tank` (W03) |
| `keybinding:toggleJetpack` | REDESIGNED | the jetpack toggle key, with a bounded, server-checked toggle intent (W06) |
| `packet:PacketLaserGun` | REDESIGNED | vanilla item use with a server-side loaded-chunk trace; no custom packet (W20) |

The six upgrades were registered by W03 (`736ae94c`); W06 added their behaviour. Twenty-two
rows: 14 IMPLEMENTED and 8 REDESIGNED. One C18b row stays PLANNED: `enchantment:spacebreathing`
is registered by W03, but the chemical recipe that applies it is missing, so survival players
cannot obtain it (ADR-066 section 5.1; records/W03.md).

## Open items

- **S1 (dedicated server and native restart):** not done for the workstation, the suit storage,
  the jetpack, the laser gun or the jackhammer.
- **S2 (crash cut across stores):** no crash-cut evidence. W03's schema-1 equipment root owns
  the installed components, and the workstation persists only its two held stacks
  (records/W03.md).
- **V1 and V2:** no real-GPU V1 or two-client V2 evidence for the jetpack, the visors, the
  workstation screen or the laser gun.
- **Dedicated server:** W08's review R02 stays open: start the packaged SRG jar on a dedicated
  server.
- **Art and provenance:** the manual review of the NEW art and the provenance review are open.
- **Follow-ups of this batch:**
  - known limitation (W06): with `allow-flight=true`, vanilla trusts client flight;
  - W03 chose the tank tiers 1,000, 2,000, 4,000 and 8,000 (records/W03.md). ADR-066
    section 9 item 1 asks for a scoped review of numeric tiers; no record of that review was
    found;
  - the `space_breathing` application recipe in the chemical reactor.

## Gate statement

No Gate is approved. This file supports the ledger delivery marks only. S1, S2, V1, V2 and the
art and provenance reviews remain open, as listed above.

## Addendum (Root, 2026-10-15): W32 delivers the space breathing enchantment

W32 merged after W31 wrote this file. It adds the chemical reactor recipe that the last follow-up above names, so survival players can now obtain `enchantment:spacebreathing`. This addendum delivers the row and closes that follow-up.

| Merge on `codex/v1.8.0-classic-content` | Slice | Tested HEAD | Hosted run | Review |
|---|---|---|---|---|
| `1e36af58` | W03 (ADR-066 section 5.1): registers the enchantment (`e0e378c1`, `736ae94c`). Correctly worn level-1 native armor seals, and only an enchanted chest holds oxygen (`e0e378c1`, with the fixes `e58e5cd8` and `0b2d1cfe`) | `6132f9ca` | 38155101250 (success) | W03-review, SHA-256 `4b74bf77…` (above), which covers the enchanted native armor rules |
| `58ca2269` | W32 (ADR-064 section 9 item 2, ADR-066 sections 3.1 and 5.1): the chemical reactor's special recipes | `5f880382` | 38390433422 (success) | Root review |

The records are `records/W03.md` and `records/W32.md`. Run 38603920684 (v1.8 development regression) passed at `cc36612c`, which contains both merges.

| Unit | Disposition | Delivery |
|---|---|---|
| `enchantment:spacebreathing` | IMPLEMENTED | `advancedrocketrycommunity:space_breathing`, level 1 only. Four chemical reactor recipes, one per armor slot, apply it to an eligible native armor piece with the legacy inputs (one pipe seal and four titanium aluminide sheets; the chest also takes an empty super high pressure tank), 100 ticks at 10 FE/t. Enchanting tables, books, trades and loot refuse it. Correctly worn enchanted armor seals, and only the chest holds oxygen, at most 2,000 units |

Tests: the unit classes `SpaceBreathingApplicationTest`, `ClassicSpecialRecipeTest` and `ChemicalSpecialDataTest`, and `RegisteredItemReachabilityTest.everyRegisteredEnchantmentHasASurvivalRoute`; the GameTests `ChemicalSpecialGameTests` and `SuitWorkstationGameTests.enchantedNativeArmorSealsOnlyWhenWornAndHasFiniteOxygen`.

ADR-066 section 5.1 asks for the native armor eligibility rule to be reviewed as an additive revision of ADR-025. The W03 review covered the rule, and Root recorded it in ADR-025 as the implementation note "Additive revision note: enchanted native armor (v1.8.0)". S1, V1 and V2 stay open as listed above.

This addendum is development evidence only. It approves no Required Gate, candidate, version or release.
