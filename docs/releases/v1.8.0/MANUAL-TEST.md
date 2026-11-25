# v1.8.0 manual-test report — C6 draft

**DRAFT; all row actuals PENDING; phase 2 operator rounds are recorded below.** Candidate commit
`c8fa16e16be87554f26c402546733df6555d15e6`, JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
These are report slots for the 117 IDs of the
[final campaign plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md),
not a new or reduced checklist. Follow each linked checklist's preconditions,
numbered actions, expected results and evidence requirements.

## Environment and execution rules

[W471](../../work/v1.8.0-root-queue/records/W471.md) and
[W472](../../work/v1.8.0-root-queue/records/W472.md), under Root decisions
R686–R688/R690 in [QUEUE](../../work/v1.8.0-root-queue/QUEUE.md), ran **43 sessions**
(15 + 28) on 2026-11-24/25. These were launch-mode, isolated offline loopback
sessions, not authenticated-account or V2 evidence. **No step was scored PASS.**
Round 01 records 0 PASS / 2 FAIL / 9 NOT_COVERED / 34 NOT_RUN rows; round 02
records 20 NOT_COVERED / 25 NOT_RUN rows. The original 45-row / 394-step inventory
is retained. Operator/staging failures remain sealed, not product defects or
retroactive passes; J13's interruption produced no runtime result. Two pre-join
timeouts remain A18/KI-V180-CONNECT-01 findings, with no accepted failure ratio.
Under R690, each later step is scored against **its own oracle and evidence**,
and rows run depth first. W474's scenario preparation is not execution. Every
actual-result cell below stays PENDING; round observations are not copied into
those cells.

The recorded primary environment is Windows 11 Pro 10.0.26200, AMD Ryzen 5 5500
(6 cores / 12 threads), NVIDIA GeForce RTX 3070 Laptop GPU, WMI driver
32.0.15.6636 and native F3 OpenGL 4.6 NVIDIA 566.36. Java is JDK 17.0.7;
Forge 47.4.10, JEI absent, en_us, effective GUI scale 3, windowed 1280×720
on a 2560×1440 desktop. RAM, complete JVM/configuration inventory, other display
cells and the second GPU remain PENDING. The client was ArceTester1, an offline
profile, on 127.0.0.1:25574 with a 3G server heap, RCON disabled and no extra
JVM test properties. This is real-GPU observation, not an owner-account result.


- Minecraft 1.20.1, Java 17; Forge 47.4.10 baseline and 47.4.23 compatibility,
  each without JEI and with client-only JEI 15.56.0.205. I04 fills all four cells.
- DS is a packaged dedicated server; S2 is a forced-stop recovery run. Neither
  is a real-client visual result. V1 needs a real GPU; V2 needs two signed-in
  real clients with independent accounts. V0/LLVMpipe and fake players cannot
  replace either.
- Use the accepted display cells: windowed 1280×720 at effective scales 2/3,
  both languages; native-resolution fullscreen at scale 4 if actually offered.
  Record effective scale; unavailable scales are not executed results.
  J14 retains its additional HUD scale 1–6 investigation.
- Follow phases 1 preparation, 2 V1, 3 DS/restart, 4 V2, then 5 reference/soak.
  Account/equipment choices O4/O5/O7–O12 in the
  [owner packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md)
  have O4/O7/O8 selected by Root (R684); O5/O9–O12 still need the owner's
  accounts/UUIDs, second GPU, reference login, second person and time. Root's
  choices are procedures, not test results.
- I01 uses the scripted rehearsal plus recorded hand-played subset selected
  by Root under R684; recording and the owner's manual time remain PENDING. It still must show a
  representative base-to-endgame route; graph reachability alone is insufficient.
- All six production admissions stay off in v1.8 by R682, not an unanswered choice. J01–J09 use the disposable marked
  campaign world for gated actions. Fuel J23–J29 additionally need
  `-Dadvancedrocketrycommunity.escrowLoaderAdmission=true` and the release-test
  gate only on that copy. Record no-hook disabled behaviour separately.
  Missing properties/fixtures mean NOT_COVERED, never successful production admission.
- A restart row uses the same world for two restarts with state comparisons.
  Upgrade only copies; hash originals before and after. Preserve failures.
  C05/H04 have planned NOT_COVERED scope under O6/D9, not compatibility success.

## Per-result form (required for each row and every attempt)

```text
row_id / step / environment level:
candidate commit / JAR SHA-256:
Forge / JEI / Java / configurations / packs / fixture hashes:
machine / OS / CPU / GPU / driver / resolution / effective GUI scale:
tester / second participant / harness script commit:
start UTC / end UTC:
preconditions / actions / expected:
actual: PENDING
result: PENDING
media/log location / SHA-256: PENDING
finding ID, or NOT_COVERED reason and what would cover it: PENDING
```

Write the actual result records under
`D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/results/`.
Every final result needs a media/log reference and hash, tester and UTC time;
a bare “PASS” is invalid. Native paths and hashes are catalogued in the final
index, not mutable build directories. Client/server product ERRORs and
unexplained repeated WARNs fail; retain their exact lines.

## Row inventory

### A — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| A01 | Packaged fresh start and restart | DS | PENDING |
| A02 | Core electrolyzer-to-satellite flow | DS, V1 | PENDING |
| A03 | Beta-world upgrade and continued play | DS | PENDING |
| A04 | Assembly/disassembly/transfer forced-stop recovery | S2 | PENDING |
| A05 | Owner/passenger disconnect in flight | V2 | PENDING |
| A06 | Station invitation, rejection and transfer | V2 | PENDING |
| A07 | Online authentication and whitelist | V2 | PENDING |
| A08 | Four-hour joint reference soak | SOAK | PENDING |
| A09 | Reference tick/RSS budgets | REF | PENDING |
| A10 | 2048-block assembly/disassembly cost | REF | PENDING |
| A11 | Sixteen atmosphere devices and queue bounds | REF | PENDING |
| A12 | Real-GPU rocket size/cache/frame tests | V1 | PENDING |
| A13 | Core screens and effective GUI scales | V1 | PENDING |
| A14 | Earth/space/Moon sky, particles, sound | V1 | PENDING |
| A15 | Two-client rocket and passenger agreement | V2 | PENDING |
| A16 | Forge/JEI key visual smokes | V1 | PENDING |
| A17 | Restart, rejoin and resource identities | DS | PENDING |
| A18 | Ten first-connect trials per Forge lane | DS, V2 | PENDING |

### B — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-B.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| B01 | v1.0-world upgrade and core flows | DS | PENDING |
| B02 | Two orbit bodies with isolated station contexts | DS, V1 | PENDING |
| B03 | Route reload success and rollback | DS | PENDING |
| B04 | Restart preserves flight target | DS | PENDING |
| B05 | Concurrent independent destination selection | V2 | PENDING |
| B06 | Readable destination and locked-route feedback | V1 | PENDING |
| B07 | Reference CPU/memory regression | REF | PENDING |

### C — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-C.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| C01 | 100 idle machines and defined active load | REF | PENDING |
| C02 | Running-machine unload/reload and restart | DS | PENDING |
| C03 | Concurrent screen and port operation | V2 | PENDING |
| C04 | Piston/explosion/player structure teardown | DS, V1 | PENDING |
| C05 | Third-party pipes/capabilities (O6 scope) | DS | PENDING |
| C06 | Three screens, languages and error feedback | V1 | PENDING |
| C07 | JEI recipes agree with machine behaviour | V1 with JEI | PENDING |
| C08 | Remote and unauthorized screen refusal | V2 | PENDING |

### D — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-D.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| D01 | API fixture start, restart, dual join/reconnect | DS, V2 | PENDING |
| D02 | External cargo Earth–Moon round trip | DS | PENDING |
| D03 | Unsupported adapter refusal is readable | V1 | PENDING |
| D04 | Integration atmosphere boundary/tag reload | DS, V1 | PENDING |
| D05 | External oxygen armour/HUD/refill/reconnect | V1 | PENDING |
| D06 | Registered fuel restart and exact remainder | DS, V1 | PENDING |
| D07 | Captured engine/tank definitions survive change | DS | PENDING |
| D08 | Payload reload, claim and replay | DS, V1 | PENDING |
| D09 | Foreign/stale/out-of-range API intents | V2 | PENDING |
| D10 | Missing provider/mod refusal and reinstall | DS | PENDING |
| D11 | Optional-client combinations without server dependency | DS | PENDING |
| D12 | API reference CPU/tick/network/cache | REF | PENDING |

### E — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-E.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| E01 | Multi-body travel, restart and return | DS, V1 | PENDING |
| E02 | Discovery/rewards/fees with two accounts | V2 | PENDING |
| E03 | Private station and gas-surface authority | V2, DS | PENDING |
| E04 | Cold/heat/pressure/sunlight equipment feedback | V1 | PENDING |
| E05 | Star map, languages, scales and reload | V1 | PENDING |
| E06 | Two-GPU sky/fog/sun/stars/environment matrix | V1 on two GPUs | PENDING |
| E07 | No stale geometry/fog across transitions | V1 | PENDING |
| E08 | Body/catalog reload success and rollback | DS | PENDING |
| E09 | Station around an expansion body | DS | PENDING |
| E10 | Representative upgrade identities/resources/research | DS | PENDING |
| E11 | Sky resources and reference-system cost | V1, REF | PENDING |

### F — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-F.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| F01 | 10/100 stations, unload/reload and benchmark | DS, REF | PENDING |
| F02 | Concurrent independent station warps | V2 | PENDING |
| F03 | Warp owner/passenger disconnect/reconnect | V2 | PENDING |
| F04 | Docked rocket survives warp | DS | PENDING |
| F05 | Catalog removal/reload aborts warp safely | DS | PENDING |
| F06 | Non-member warp refusal | V2 | PENDING |
| F07 | Orbit, gravity, countdown, sky/cache and second view | V1, V2 | PENDING |

### G — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-G.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| G01 | 100+ mission restarts; 500/1000 load and flush cost | DS, REF | PENDING |
| G02 | Bounded offline mission catch-up | DS | PENDING |
| G03 | Two-player same-tick settlement | V2 | PENDING |
| G04 | Reward-write forced-stop cuts | S2 | PENDING |
| G05 | Mission chunk reclaim with no ticket leak | DS | PENDING |
| G06 | Mission/satellite GUI, visuals and video | V1 | PENDING |
| G07 | Two-client mission-state agreement | V2 | PENDING |

### H — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-H.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| H01 | Multiple players contend for a target | V2 | PENDING |
| H02 | Non-owner/non-operator refusal | V2 | PENDING |
| H03 | Stop inside an endgame processing batch | S2 | PENDING |
| H04 | Real protection mods (O6 scope) | DS | PENDING |
| H05 | Long device run without cache/ticket leak | SOAK | PENDING |
| H06 | Endgame effects, feedback and effective scales | V1 | PENDING |
| H07 | Per-system budgets and 60 ms flush | REF | PENDING |

### I — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| I01 | Recorded representative base-to-endgame route | DS, V1 | PENDING |
| I02 | Every machine kind coexists, unloads and scales | DS, REF | PENDING |
| I03 | All-old-ID v1.7 upgrade and two restarts | DS | PENDING |
| I04 | Four Forge × JEI packaged/client cells | DS, V1 | PENDING |
| I05 | Multi-hour automated item/fluid conservation | SOAK | PENDING |
| I06 | All screens/sample classes; every high-risk asset | V1 | PENDING |
| I07 | OBJ/baked models/multiblocks/armour correctness | V1 | PENDING |
| I08 | Languages, effective scales and resource reload | V1 | PENDING |
| I09 | OBJ frame times, three reloads, JEI latency | V1 | PENDING |
| I10 | Uninterrupted four-hour joint reference workload | SOAK | PENDING |
| I11 | New-machine policy and endgame permissions | V2 | PENDING |

### J — [execution checklist](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-J.md)

| ID | Planned check | Required environment | Result |
|---|---|---|---|
| J01 | Bay statuses and OWNER_PENDING_FULL | V1 | PENDING |
| J02 | Bay safe exit on packaged dedicated server | V2 | PENDING |
| J03 | Bay exit LAN: each client hosts | V2 on LAN | PENDING |
| J04 | Online bay passenger and S3 cases | V2 | PENDING |
| J05 | Bay barrier/writer and R-043 waiting costs | REF | PENDING |
| J06 | Cargo labels/status/fluid/cursor/reload | V1 | PENDING |
| J07 | Cargo owner/contention/passenger/reconnect V2 | V2 | PENDING |
| J08 | Cargo native reference benchmark | REF | PENDING |
| J09 | Shared bay/cargo work and capture quota | REF | PENDING |
| J10 | Cold-destination readiness and first-use cost | REF | PENDING |
| J11 | First-event progression flush on reference node | REF | PENDING |
| J12 | Code-drawn sky/black hole/Earth appearance | V1 | PENDING |
| J13 | Fixed-midnight Moon appearance | V1 | PENDING |
| J14 | HUD extremes/scales/mounts and two views | V1, V2 | PENDING |
| J15 | NEW sound producers, loop seams and boundaries | V1 | PENDING |
| J16 | Drill/jackhammer/railgun feedback and observers | V1, V2 | PENDING |
| J17 | World-first advancement participant variants | DS, V2 | PENDING |
| J18 | Off-world sleep/respawn/quorum/portal/restart | DS, V1, V2 | PENDING |
| J19 | Observatory/processor/survey V1/V2 | V1, V2 | PENDING |
| J20 | Tracking/trails/Moon falling/high-seat console | V1 | PENDING |
| J21 | Recheck every retained earlier client finding | DS, V1 | PENDING |
| J22 | Full C6 native restart campaign | DS, S2 | PENDING |
| J23 | Fuel/linker bilingual labels | V1; one owner-signed-in account on a real GPU | PENDING |
| J24 | Fuel amount/state matches authoritative totals | V1; one owner-signed-in account on a real GPU | PENDING |
| J25 | Fuel linker binding/stale/range/unloaded feedback | V1; one owner-signed-in account on a real GPU | PENDING |
| J26 | Fuel labels/scales/window/language/reload | V1; one owner-signed-in account on a real GPU | PENDING |
| J27 | Fuel competing-owner/device synchronized state | V2; two independent owner-signed-in premium accounts, two real clients on real GPU hardware | PENDING |
| J28 | Fuel passenger/assigned-seat busy refusal | V2; two independent owner-signed-in premium accounts, two real clients on real GPU hardware | PENDING |
| J29 | Fuel owner/passenger disconnect/reconnect recovery | V2; two independent owner-signed-in premium accounts, two real clients on real GPU hardware | PENDING |

## Open completion fields

Round dates, worker identities, frames and seals are recorded in W471/W472 and
[the media catalogue](videos-or-links.md). Every row actual, its final step-linked
receipt, remaining visual/audio judgment and independent review remain **PENDING**.
J01/J06/J23–J26 need attach mode with the owner's account (R686); second GPU,
PresentMon, languages/scales/JEI cells and V2 are not covered by these rounds.
R691's four narrow signatures do not supply these results.
The media catalogue is `videos-or-links.md`; visual, multiplayer, migration,
recovery and performance observations have separate reports. Fill once from
candidate-bound receipts and reference the same row IDs, rather than creating
contradicting results in each report. A production change, including approved
admission, makes a new candidate and triggers the plan's repetition rules.
