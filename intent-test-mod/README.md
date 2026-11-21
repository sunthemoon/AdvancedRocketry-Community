# Bounded intent campaign fixture (K18, never ship)

This independent Java 17 / Forge 47.4.10 / Minecraft 1.20.1 **client-only** mod
is for disposable ARCE 1.8.0 campaign worlds. Do not distribute it with the
product, install it on a server, or enable it in a player world. It adds no
product code, registrations, access transformers, server commands or save data.
Qualification with real clients is still required; building this JAR is not a
V1/V2 refusal result.

## Protocol and consumer boundary

The [product pin](../src/test/resources/network-protocols.txt) has **eight
channels, three C2S messages**, not eight C2S channels:

| Channel suffix | Protocol | Legal fixture send | Other direction |
|---|---|---|---|
| `rocket_flight` | 10 | index 0, flight intent | indices 1/2 S2C |
| `classic_controls` | 3 | index 0, control intent | indices 1/2 S2C |
| `suit_motion` | 2 | index 0, jetpack intent | indices 1/2 S2C |
| `endgame` | 1 | none | index 0 S2C only |
| `life_support_status` | 1 | none | index 0 S2C only |
| `celestial_snapshot` | 4 | none | indices 0/1 S2C only |
| `rocket_visual` | 1 | none | index 0 S2C only |
| `satellite` | 1 | none | indices 0/1 S2C only |

ARCE, required on the client, already registers and negotiates all eight exact
channel versions with the server. Forge rejects duplicate channel registration;
this fixture must **not** register replacements or intercept replies. It writes
the published pinned wire fields independently into a standard vanilla
`ServerboundCustomPayloadPacket` on the existing channel. Forge's
`IndexedMessageCodec` prefixes a one-byte discriminator, here always 0. The
remainder is exactly the product's well-formed C2S frame. No malformed lengths,
unknown indices, wrong directions, injected positions, NBT, resource amounts,
ownership flags, velocity, oversized strings or server-side helpers are sent.
S2C-only channels are NOT_COVERED for sends, not repurposed as C2S.

The fixture does not import **any** product class, including internals or API
classes. Its frozen [wire pin](src/main/resources/network-protocols.txt) must
equal the product pin at build time. Only platform and test dependencies are
resolved. `verifyConsumerBoundary` checks the actual compile classpath, isolated
source root, absence of product class references/shading, metadata, no jar-in-jar
or access transformer, and SHA-256 of the reobfuscated JAR. The campaign must
also pin the actual product JAR/hash: a matching development version string alone
does not establish identical bytes or wire semantics.

## Build and unit verification

Use the repository wrapper to launch this project's own settings, not the host
build. Dependencies must already be cached; do not drop `--offline` to fetch them.
The fixture's Gradle work requires the heavy-JVM lock. From the repository root,
launch the included bounded lock-wait script as a detached process:

```powershell
Start-Process -FilePath powershell.exe -WindowStyle Hidden -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "$PWD\intent-test-mod\tools\build-locked.ps1"
```

It checks C:/D: free space, uses WORKER-RULES' atomic `cmd /c mkdir` loop,
refuses a lock older than three hours, waits at most three hours, and releases
only its own token in the same script's `finally`. It holds the lock only for:

```powershell
.\gradlew.bat -p intent-test-mod --offline clean build --console=plain
```

`check` runs each of the four pure-Java test classes in its own test process,
the complete unfiltered fixture `test` suite, and the archive boundary audit.
There is no Minecraft bootstrap or client/server launch. Reports are under
`intent-test-mod/build/test-results/` and `build/reports/consumer/boundary.json`.
The JAR is `intent-test-mod/build/libs/arce-intent-test-1.0.0.jar`. Copy the final
reobfuscated JAR and retained reports/logs outside Git, record its SHA-256, and
record the tested source commit before campaign qualification. Do not package
another source tree or Gradle cache as evidence.

The default external build log/result directory is
`D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K18/`.
The script does not launch a game or modify that directory's existing JARs or
checksum manifests. Use a new `-EvidenceDirectory` for a later attempt rather
than editing sealed evidence.

## Triggering one case

1. On the campaign's disposable copy, verify both clients and the DS use the
   pinned product JAR. Install this JAR on the probing client only. Disable the
   normal jetpack hotkey/heartbeat and other automatic inputs during measurement.
2. Run the **local client** command `/arce_intent enable test-world`. This is
   explicit operator acknowledgement of the test-world restriction, not a way
   for a client mod to identify a disposable server automatically.
3. Run `/arce_intent select <case>` (tab completion lists the case IDs below).
   For flight cases also run `/arce_intent rocket <entity-id> <target>` before
   opening the menu. `<target>` is a canonical body resource ID, except
   `flight_private_station`, which requires a canonical station UUID. Obtain
   real identities from the campaign's recorded scene; never invent a stale ID.
4. Open the intended menu normally. Press **F8** to capture. Classic capture
   needs the looked-at block matching the menu; flight capture needs the
   looked-at entity matching the configured rocket ID. Capture starts within
   8 blocks and saves only one local menu/target snapshot. For motion cases,
   F8 simply captures the current local context. No packet is sent by F8.
5. Perform the case's negative precondition below and press **F9** once.
   These keys are rebindable in Controls and work through Forge key input while
   menus are open. Holding a key does not repeat a send. Do not bind both to
   the same key (capture takes precedence).
6. Keep the `ARCE_INTENT_FIXTURE stage=queued` client log line and the server
   readbacks. `stage=captured` and `stage=local_refusal` are **not** sends.
   Every successful trigger consumes the capture even if transport fails.
   Use F8 again for another independently recorded attempt.

There is exactly **one packet per trigger**, at most 156 bytes including the
discriminator. There is a global two-second monotonic cooldown across all cases,
a 32-send cap per connection, and a 120-second capture lifetime. `disable` stops
sends and clears the capture. `enable` does not reset the budget. Logout discards
selection, capture, identities and budget and starts disabled on the next
connection. Dimension changes invalidate the retained capture at send time;
cross-world/dimension replay injection is intentionally NOT_COVERED. There are
no loops, queued retries, scheduled bursts, file-controlled payloads or arbitrary
packet commands. The 32-packet cap is not an invitation to repeatedly reconnect.

Local guards check observable prerequisites, not server authority. In
particular the campaign must establish ownership, OP level, membership,
equipment authorization and target properties from the DS. A mistaken
non-owner/forbidden-surface setup can perform a legitimate operation. Use an
idle disposable fixture, retain a positive control, and stop if the recorded
server prerequisites differ. Do not count a different early refusal as the
intended inner branch.

## Case table and authoritative readback

Matrix IDs below mean the exact `id` cells of W402/K13's 35-row
`access-matrix.csv`, SHA-256
`3888f04c844db2deeb2cec16770f2d2f417a7414de031126672ae481d111d882`.
The campaign copy is
`D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K13/access-matrix.csv`.
See [W402 section 6](../docs/work/v1.8.0-root-queue/records/W402.md).
All channel/menu suffixes below have namespace `advancedrocketrycommunity`.

Readback recipes (retain before/after commands, responses and fixture IDs):

- **R:** From the DS operator console, `data get entity <recorded-rocket-UUID>`;
  compare the rocket's persisted flight state/active target/fuel/owner,
  passenger UUIDs, snapshot and inventory. Keep the requesting player and its
  vehicle relation as well. For a truly removed entity, keep the removal and
  absence readback and compare the surviving fixture/replacement rocket, not
  just an empty selector. Do not rely on client NBT or a menu screenshot.
- **C:** `data get block <recorded-x> <y> <z>` for the controller/monitor and its
  attached resource inventories; for station controllers also use
  `arce station admin inspect <station-UUID>` from the DS operator console and
  compare the station's gravity, logical altitude, orientation, warp state,
  membership and credit as relevant. Reopen normally to capture the authoritative
  view/status; a closed menu has no new view. Keep related rocket readback R for
  the monitor. Normal time-dependent fields are compared against a no-input
  interval, not assumed to be bit-identical.
- **M:** From the DS, `data get entity <player-UUID> Inventory` and `data get
  entity <player-UUID> Motion`; compare worn chest/helmet module NBT and hydrogen
  with an idle/no-input control interval. Keep the actual status on the probing
  client. A stationary pose alone cannot prove absence of a paid impulse, and
  vanilla gravity is not a packet-induced movement delta.
- **L:** Pair each case with the campaign's server-side loaded-chunk projection
  for the same fixed dimension/fixture region before/after. No client position
  is used as a server load command. This mod does not add a chunk observer. If
  that projection is unavailable, the no-extra-chunk-load subclaim stays
  NOT_COVERED; neither client render distance nor absence of a log proves it.

Failed requests may update rate/request/audit bookkeeping or a refusal status.
"Unchanged" means the protected resource/authority projections above, not a
byte-for-byte equality of every SavedData file or continuously ticking field.

| Case ID | Matrix row / channel | Checklist | Negative setup after F8; values sent | Expected refusal / readback |
|---|---|---|---|---|
| `flight_stale` | `rocket_flight` menu; C2S `rocket_flight` p10/0 | C08, D09, E07, I11 | Remove the captured rocket through the campaign's supported action; confirm its entity ID is absent locally and on DS. CANCEL=1, old entity, BodySurface target, fresh request UUID, no pad. | Normally early invalid-flight-menu UNAUTHORIZED, **silent**. R+L; this does not establish inner ENTITY_UNAVAILABLE. |
| `flight_range` | same | C08, D09, I11 | Leave the live recorded rocket by >8 blocks, not riding it. CANCEL=1 with its ID. | Invalid menu refuses silently before inner access/distance report. R+L; do not expect OUT_OF_RANGE in the server log. |
| `flight_non_owner` | same | D09, I11 | B is a non-owner, non-creative, non-OP; keep B's valid in-range flight menu open. CANCEL=1. | UNAUTHORIZED action-bar feedback and `ARCE_FLIGHT_INTENT ... request=<receipt UUID> ... code=UNAUTHORIZED`; R+L. |
| `flight_late` | same | C08, D09, E07, I11 | Close the captured menu; current menu must be player inventory. CANCEL=1 with retained entity/target. | Early invalid-menu UNAUTHORIZED, silent; R+L. This is late C2S, not late S2C rendering. |
| `flight_forbidden_surface` | C2S `rocket_flight` p10/0 | E03 | Owner's valid menu and launch-ready idle rocket; choose the manifest's discovered gas giant with no surface Level. LAUNCH=0, BodySurface of that body. | INVALID_DESTINATION for an unsupported surface route, feedback and `ARCE_FLIGHT_INTENT`; R+L. An earlier fuel/state/discovery refusal does not prove surface validation. |
| `flight_private_station` | C2S `rocket_flight` p10/0 | E03 | B owns the ready rocket, is non-OP and nonmember of the recorded station after the independent discovery step. Valid menu; LAUNCH=0, Station UUID, fresh request, no pad. | UNAUTHORIZED feedback and `ARCE_FLIGHT_INTENT`; R+C+L. Ordinary invitation/removal and a member positive control are separate campaign actions. |
| `classic_stale` | C2S `classic_controls` p3/0; five classic menus below | C08, D09, E07, I11 | Close captured menu, open a replacement with a different container ID, retain old fields. | Wrong container/kind is silently dropped. C+L. Old ID must not have wrapped and become valid again. |
| `classic_range` | same | C08, D09, I11 | Move >8 blocks from captured block centre; send its old container/kind. | Invalid/closed menu silently drops it. C+L; no expected log. |
| `classic_non_owner` | same, station controllers only | C08, I11 | B is a station member with VISIT but not owner/OP, with the matching valid menu, in range. | StationManagementCode UNAUTHORIZED in the normal view, no log; C+L. A nonmember loses the menu earlier and does not prove mutation permission. Monitor non-owner open is NOT_COVERED by this case. |
| `classic_late` | same, all five menus | C08, E07, I11 | Close menu, keep inventory menu open; send retained container/kind. | Silent wrong/absent menu drop; C+L. |
| `motion_no_jetpack` | C2S `suit_motion` p2/0 | I11 | Fresh disabled session; no worn jetpack. TOGGLE=0, value=0. | `message.advancedrocketrycommunity.jetpack.no_jetpack` on client, no server log; M. |
| `motion_no_hover` | same | I11 | NORMAL mode, no worn hover upgrade (a held upgrade does not count). CYCLE_MODE=1, value=0. | `message.advancedrocketrycommunity.jetpack.no_hover_upgrade`, no server log; M. |
| `motion_disabled` | same | E07, I11 | Disable jetpack normally first; one late THRUST=2, held=1. | IGNORED, silent; no paid thrust or hydrogen debit; M. No automatic release is sent because this branch must remain disabled. |
| `motion_spectator` | same | I11 | Become spectator on the disposable fixture. TOGGLE=0, value=0. | IGNORED, silent; M. Never claim a distance check for a self-only packet. |
| `motion_config_disabled` | same | I11 | Configured equipment-disabled campaign variant, fresh disabled session. TOGGLE=0, value=0. | `message.advancedrocketrycommunity.jetpack.equipment_disabled`, no server log; M. Do not change product settings just to run an ordinary-fixture phase. |

Classic wire mutations are fixed by the captured menu: warp kind 0 CANCEL=4,
gravity kind 1 step=0, altitude kind 2 step=0, orientation kind 3 step=0,
monitor kind 4 DISARM=5. Schema=1, value=0, token=0 in all cases; container
1..255, exact 13-byte frame (14 with discriminator). The motion frame is exactly
2 bytes (3 with discriminator). Flight uses canonical entity VarInt, target
schema 1, BodySurface kind 0 or Station kind 2, UUID request and absent pad byte.

Every queued receipt includes case, channel/protocol/index, UTC and monotonic
times, ordinal, player, dimension, captured/current menu identities, positions,
all transmitted field values and the exact hex payload. It proves **client
queueing only**, not DS receipt or refusal. Under R546/C14, pair it with
authoritative readback and use server logs only for branches that emit one.
Never infer success from a disabled button, a closed UI, no reply or no log.

## Matrix coverage and NOT_COVERED register

The table below accounts for **all 28 menu rows** of the access matrix. Every
row's stale/range/late variant uses the corresponding case above where listed;
other branches stay NOT_COVERED. An A1 reference is an existing test to consult,
**not a claim that W432 ran the product tests or that A1 replaces V2**.

**A1-M:** [MenuAuthorityMatrixGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/MenuAuthorityMatrixGameTests.java)
`everyRegisteredMenuTypeIsProbedOrExemptForEveryCheck`; its explicit
[exemptions](../src/test/resources/menu-authority-exemptions.txt) still apply.
**A1-F:** [RocketFlightIntentAuthorityGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RocketFlightIntentAuthorityGameTests.java)
`cancelIsServedOnlyFromTheOpenFlightMenuOfAnAdmittedPlayer` and
[RocketFlightSecurityGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RocketFlightSecurityGameTests.java)
`hostileFlightIntentsCannotChangeAuthority`.

| Exact menu row suffix | Tool coverage / remaining limitation | A1 fallback |
|---|---|---|
| `black_hole_generator` | NOT_COVERED: vanilla inventory clicks, S2C-only endgame channel | A1-M |
| `combustion_generator` | NOT_COVERED: vanilla slots, no pinned C2S | A1-M |
| `electrolyzer` | NOT_COVERED: vanilla menu controls | A1-M |
| `elevator` | NOT_COVERED: vanilla buttons/slots, S2C-only endgame | A1-M |
| `fluid_fuel_loader` | NOT_COVERED: vanilla slots/buttons, owner-only menu | A1-M |
| `gravity_field_controller` | NOT_COVERED: vanilla buttons/slots, S2C-only endgame | A1-M |
| `laser_target` | NOT_COVERED: vanilla slots/buttons, S2C-only endgame | A1-M |
| `lathe` | NOT_COVERED: classic machine processing/slots, not the classic_controls intent channel | A1-M |
| `microwave_receiver` | NOT_COVERED: vanilla slots | A1-M |
| `ground_research` | NOT_COVERED: observatory/data-processor menu's vanilla controls | A1-M |
| `orbital_laser_drill` | NOT_COVERED: vanilla buttons/slots, S2C-only endgame | A1-M |
| `precision_assembler` | NOT_COVERED: vanilla menu controls | A1-M |
| `railgun` | NOT_COVERED: vanilla buttons/slots, S2C-only endgame | A1-M |
| `rocket_flight` | Four flight cases; private/surface validation separately above | A1-F, A1-M |
| `rocket_fluid_cargo` | NOT_COVERED: vanilla slots/buttons; loader and unloader | A1-M |
| `rocket_item_cargo` | NOT_COVERED: vanilla slots/buttons; loader and unloader | A1-M |
| `rocket_monitoring_station` | Classic stale/range/late, kind 4; non-owner valid-menu mutation NOT_COVERED because open is owner-gated | A1-M |
| `rocket_satellite_bay` | NOT_COVERED: vanilla slots/buttons | A1-M |
| `rolling_machine` | NOT_COVERED: vanilla slots/menu controls | A1-M |
| `satellite_builder` | NOT_COVERED: vanilla menu buttons, not satellite C2S | A1-M |
| `satellite_terminal` | NOT_COVERED: vanilla menu buttons, no C2S satellite message | A1-M |
| `solar_array` | NOT_COVERED: scalar display with no action | A1-M (action/owner exemptions remain) |
| `solar_generator` | NOT_COVERED: scalar display with no action | A1-M (action/owner exemptions remain) |
| `station_altitude_controller` | Classic four cases, kind 2 | A1-M |
| `station_gravity_controller` | Classic four cases, kind 1 | A1-M |
| `station_orientation_controller` | Classic four cases, kind 3 | A1-M |
| `suit_workstation` | NOT_COVERED: vanilla slots/buttons | A1-M |
| `warp_controller` | Classic four cases, kind 0 | A1-M |

| Other access/checklist branch | Status and reason | Named A1 reference |
|---|---|---|
| Beacon, landing pad, force-field projector, pump (four block-use rows), I11 | NOT_COVERED by this mod: normal block use, no invented menu/C2S channel. Perform ordinary real-client campaign actions and DS block/index/resource readback. Projector refusal is silent. | [BeaconAuthorityGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/BeaconAuthorityGameTests.java), [ForceFieldGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/forcefield/ForceFieldGameTests.java), [PumpGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/PumpGameTests.java). Landing pad's menu exemption is A1-M's explicit elsewhere row; [LandingPadIndexTest](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/station/pad/LandingPadIndexTest.java) is supplemental A0, not A1. |
| Rate limits on flight/classic/motion | NOT_COVERED: fixture deliberately spaces single packets; does not bypass its cooldown to hit server windows. A rate refusal caused by unrelated normal controls is not an isolated rate test. | A1-M where RATE is applicable. Supplemental A0: [RocketIntentRateLimiterTest](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketIntentRateLimiterTest.java) `boundsEachPlayerAndAuditsOnlyTheFirstRejectedIntent`; [StationControllerRateLimitTest](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/station/service/StationControllerRateLimitTest.java); [MotionRateLimiterTest](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/equipment/motion/MotionRateLimiterTest.java) `tenPacketsInOneTickThenNothingUntilTheWindowPasses`. A1 motion rate coverage is not established by those A0 tests. |
| Inner ENTITY_UNAVAILABLE / OUT_OF_RANGE after a valid flight menu | NOT_COVERED independently: invalidated menu normally returns earlier. Stale/range cases prove only the actual early authority boundary. | A1-F; A1-M. |
| Unloaded holder, fake/disconnected/dead player, menu holder spoofing | NOT_COVERED: no chunk-load controls, fake connection or player identity in these packets; connected living client required. | A1-M, A1-F, including `aConnectedFakePlayerWithAValidFlightMenuIsRefusedEveryIntent`. |
| Classic stale positive quote token / expired confirmation | NOT_COVERED: this narrow builder sends only non-quote mutations with token 0, no token extraction or substitution. | [StationControllerMenuGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/StationControllerMenuGameTests.java) `aConfirmationRunsOnlyTheQuoteItsMenuShows`. |
| D08 terminal stale launch/replayed reward, D09 API callback faults, G03 same-tick claims | NOT_COVERED: terminal claims are vanilla menu controls, not a pinned C2S message; client time cannot prove a common server tick, and no fault callback is injected. K09 supplies tick observation separately. | [ResourceMissionGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/ResourceMissionGameTests.java) `surveyAsteroidClaimWithdrawAndAcknowledge`; [SatelliteReviewGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/SatelliteReviewGameTests.java) `builderReportsABlockedOutputAMissingDefinitionAndStaleMenus`; A1-M. These do not alone assert two real claims in one tick. |
| E05 map/catalog cache internals, E07 late S2C/generation/disconnect/dimension response | NOT_COVERED: no S2C injection/interception or product-internal cache access. K15 must observe caches; late C2S is not late S2C. | No identified A1 test for the client-only late-response/cache subbranch: retain this A1 gap explicitly. Supplemental [RocketNavigationTest](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/menu/RocketNavigationTest.java) tests coherence at A0, not a real response lifecycle. |
| Motion stale foreign target, distance, menu late | NOT_APPLICABLE: packet has no target/container/coordinates. `motion_disabled` covers only late thrust after disable. | [SuitMotionGameTests](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/equipment/motion/SuitMotionGameTests.java) `thrustIntentExpiresFiveTicksAfterTheLastHeartbeat`, `hoverIsRefusedWithoutTheHoverUpgrade`. |
| Malformed frames, out-of-range action/value/token, unknown message/direction | NOT_COVERED: forbidden by tool scope; no corrupt-frame facility. | No identified A1 byte-decoding test; retain that gap. Supplemental A0: [RocketFlightIntentPacketTest](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/network/RocketFlightIntentPacketTest.java), [ClassicControlPacketsTest](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/classiccontrol/network/ClassicControlPacketsTest.java). |

Do not clear any NOT_COVERED entry merely because a local builder/unit test
passes. Record actual receipt/readback, the precise reachable branch and the
client/server qualifications in the campaign's evidence, without changing
product Gate status from this fixture's build result.
