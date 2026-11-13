# Plan for the final real-client campaign (v1.0 to v1.8)

| Item | Value |
|---|---|
| Status | **DRAFT plan, not started.** Nothing in this plan has run, and nothing in it is passed, accepted or approved. Root reviews it before it goes to the owner. The campaign starts only when the owner confirms the start ([OWNER-DECISIONS-20261109](OWNER-DECISIONS-20261109.md)). |
| Written by | W359, a read-only headless Claude session, on 2026-11-13, for [TASK-W359](../TASK-W359-FINAL-CAMPAIGN-PLAN.md) under Root decision R437 ([OWNER-DECISIONS-20261113](OWNER-DECISIONS-20261113.md)). |
| Base | Branch `codex/v1.8.0-w359` at `8e734616c`. |
| Owner's instruction | "最终实机战役先准备计划，联机和性能仍放最后" (2026-11-13). Earlier: the campaign is suspended and multiplayer and performance come last (2026-11-04, [OWNER-DECISIONS-20261104](OWNER-DECISIONS-20261104.md)); the owner confirms later when the full campaign starts (2026-11-09). |
| Every estimate | Every number of hours or days below is an **estimate** by W359, unless the row names a source. No estimate is a commitment or a bound. |

## 1. Names used in this plan

- **Candidate** means the one hosted build whose JAR all final evidence uses (Root decision R386). **C3** is the
  current candidate, commit `9a15e1f42`. **C4** is the next one, built after W357's implementation, W358 and W360
  merge (R438). The campaign uses C4 or a later candidate, called **Cn** below.
- **Levels.** They follow section 2 of the [quality budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md):
  - **V1**: one real client on a real GPU.
  - **V2**: two real clients.
  - **DS**: the packaged dedicated server with one real client, including restarts (the budgets' S1).
  - **S2**: a forced stop of the packaged server and its recovery. No client is needed.
  - **REF**: a measurement on the reference node (4 vCPU, 8 GiB, Debian 12).
  - **SOAK**: the reference workload for at least four hours on the reference node.
- **Driver.** *Scripted* means the real-client harness or a native runner can drive the row and a person only reviews
  the screenshots and logs. *Owner* means a person must play or judge it by hand. *Both* means the harness sets the
  scene and a person performs or judges the decisive step.
- **The harness** is the scripted real-client driver outside Git, `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/`
  (section 3).
- **Wnnn** is a task record in this folder. **Rnnn** is a Root decision. **R-0nn** is an entry of the
  [risk register](../../../11-RISK-REGISTER.md). **Dn** is an owner decision of
  [2026-10-31](OWNER-DECISIONS-20261031.md).
- **Source abbreviations in the inventory.** "V1.4 §10.2" is item 2 of section 10 of that version's document under
  `docs/versions/`. "REL-1.3 M130-02" is a row of that version's `MANUAL-TEST.md` under `docs/releases/`. "GATE-1.5 G7"
  is the "remaining acceptance" cell of that Gate in that version's `GATE-STATUS.md`. "docs/15 §13" and "docs/17 §4"
  are sections of the [remote and visual rules](../../../15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md) and
  the quality budgets.

## 2. What the campaign is, and what it is not

- **It is** the single run of every real-client, dedicated-server, reference-node and soak item that v1.0 to v1.8
  left open, on one candidate. The owner decided on 2026-10-06 that these items run once, in one final campaign on
  the final candidate (TASK-W359, "Why"). [ADR-018](../../../decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md)
  schedules the full tests after the original machines and dimensions are implemented.
- **It is not** a Gate approval. The campaign produces evidence. The owner marks Gates, versions and releases
  ([AGENTS.md](../../../../AGENTS.md) sections 5, 7 and 13).
- **It does not replace** the items below, which the version documents also require and which need people other than
  the owner and Root. They are listed here so that they are not forgotten:
  - an installation from the public instructions by a person who did not take part in development (V1.0 §6 step 11
    and §14; REL-1.0 `MANUAL-TEST.md`);
  - an independent review of Gates G0 to G9 (V1.0 §6 step 9);
  - the clean rebuild from the candidate commit and, after approval, from the tag (docs/17 §9).
- **The owner's resources** (TASK-W359): two independent premium Minecraft accounts for online-mode V2, a machine
  with a second GPU category for V1, and a Debian 12 or equivalent reference node. Account credentials never pass
  through an agent. The owner signs in on the launcher; no script reads, stores or types a credential.

## 3. The existing harness and what it lacks

W359 read `PLAN.md`, `REPORT.md` and `scripts/` of `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/` and ran none
of it.

- **What it does.** `scripts/live_session.py` starts a packaged Forge dedicated server from a template and one or two
  real clients from a read-only launcher runtime. It sends keys and mouse input to each client window
  (`scripts/window_control.py`), runs server console commands, takes native F2 screenshots and writes
  `results.jsonl`, `summary.json` and the logs per round. Requests are JSON files dropped into a folder. Rounds on
  2026-10-10 ran one client, two clients (English with GUI scale 3 as operator, Chinese with scale 2 as a normal
  player) and a restart. Root's single-player test of 2026-11-04 onward (R309) used the same scripts for eight
  rounds; its report is `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`.
- **What it lacks for the campaign.** Each gap is a preparation step in phase 1.

| Gap | Why it matters | Preparation step |
|---|---|---|
| It runs Forge 47.4.22. | The release baseline is Forge 47.4.10 and the compatibility lane is 47.4.23 ([test plan](../../../05-MASTER-TEST-PLAN.md) section 12). | P3 |
| Its clients use offline profiles on loopback. | V2 and V1.0 §10.7 need online mode, a whitelist and real accounts. The owner must start the two signed-in clients; the scripts can only attach to windows that already exist. | P3, P7 |
| It has no JEI client profile. | V1.8 §10 requires the four Forge × JEI cells with a real client. | P3 |
| It records FPS only through F3 screenshots. | docs/17 §8 asks for average FPS, 1% low and the first stall. | P3, decision O8 |
| It runs on the development PC only. | The second GPU category and the reference node are other machines. | P6, P7 |
| Its fixture world and batches are from older JARs. | Evidence must come from Cn. | P2, P4 |

## 4. Inventory of deferred items

**Method and limit.** The rows come from the documents named in TASK-W359. The version documents and release
folders give the rows of v1.0 to v1.7 and the v1.8 checklist. For v1.8's slices, 201 of the 457 other Markdown
records in this folder mention V1, V2, the campaign or the reference node, most of them only to say that none ran.
W359 searched them for these words and read the passages that name a concrete check. The inventory therefore has one row per system
with its source records, not one row per sentence. Preparation step P4 expands every row into numbered steps and
re-reads the row's sources in full; a check found there that this table lacks is added, not dropped.

No row below has passed. "Earlier scoped evidence" in a source never counts for Cn.

### 4.1 v1.0.0, Stable Core MVP

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| A01 | V1.0 §10.1; REL-1.0 M01 | A fresh packaged dedicated server starts, stops and starts again; the right version shows; no side-loading failure. | DS | Scripted |
| A02 | V1.0 §10.2; REL-1.0 M02 to M06, M08, M09; docs/15 §13 scenes | The core flow in a new world: electrolyzer, life support and a sealed room that is opened and restored, a legal rocket with an invalid one refused, the Moon and back, a station, research and a satellite mission. | DS, V1 | Scripted |
| A03 | V1.0 §10.3; REL-1.0 M11 and `MIGRATION-REPORT.md` procedure | The v0.9 Beta world upgrades on a copy, reopens twice and play continues; originals and backups are intact. | DS | Scripted |
| A04 | V1.0 §10.4; REL-1.0 `RECOVERY-MATRIX.md` | A forced stop at each transaction phase of assembly, disassembly and transfer, then recovery, on Cn. | S2 | Scripted |
| A05 | V1.0 §10.5; REL-1.0 M05, M07 and `MULTIPLAYER-REPORT.md` | Owner and passenger on two real clients; the passenger disconnects in flight and reconnects; seats and rocket agree on both clients. | V2 | Owner |
| A06 | V1.0 §10.6; REL-1.0 M08 | A player without operator rights; station invitation, rejection and ownership transfer; requests without permission. | V2 | Both |
| A07 | V1.0 §10.7; REL-1.0 `MANUAL-TEST.md` closing note | Online mode, the whitelist and normal authentication, recorded without any credential. | V2 | Owner |
| A08 | V1.0 §10.8 and §13.5; REL-1.0 `PERFORMANCE.md`; [GATE_STATUS](../../../status/GATE_STATUS.md) | The four-hour reference workload: no OOM, no watchdog, no resource gain or loss, no monotonic memory growth. | SOAK | Scripted, with the owner's node |
| A09 | V1.0 §13.1; docs/17 §4 | Mean, P95 and P99 MSPT and RSS against the budgets under the reference workload. | REF | Scripted |
| A10 | V1.0 §13.2 | A 2,048-block rocket's assembly and disassembly time and main-thread spike. | REF | Scripted |
| A11 | V1.0 §13.3 | Sixteen atmosphere devices and several sealed rooms without an unbounded queue. | REF | Scripted |
| A12 | V1.0 §11.2, §11.3, §13.4 and §13.6; docs/17 §8; REL-1.0 `VISUAL-VALIDATION-REPORT.md` | On a real GPU: the environment record; 64-, 512- and 2,048-block rockets at first display, in flight and after a dimension change; FPS, 1% low, first stall; cache release after removal, dimension change and resource reload. | V1 | Both |
| A13 | V1.0 §11.4; docs/15 §13 | The electrolyzer, oxygen, rocket, station and satellite screens at GUI scales 2, 3 and 4, windowed and fullscreen, with no control out of reach. | V1 | Scripted |
| A14 | V1.0 §11.5 | The Earth, space and Moon skies, particles, sounds and models agree with the server's state. | V1 | Both |
| A15 | V1.0 §11.6; REL-1.0 M12 | Two real clients watch the same rocket and passengers and see the same state. | V2 | Both |
| A16 | V1.0 §11.7; docs/15 §13; test plan section 12 | The full visual flow on Forge 47.4.10; a key smoke on 47.4.23; JEI present and absent once each. | V1 | Scripted |
| A17 | REL-1.0 M10; docs/15 §13 | The server restarts and the client enters again; identities and resource totals are compared. | DS | Scripted |
| A18 | REL-1.0 `MULTIPLAYER-REPORT.md`; GATE_STATUS "retained handshake-timeout review"; harness `REPORT.md` section 2 | The first-connection timeout: how often it happens on Cn, on each Forge lane. | DS, V2 | Scripted |

### 4.2 v1.1.0 and v1.1.1, expansion kernel

v1.1.1 has no row of its own: its section 4 defers the long soak, the real GPU and the two-client run, and the rows
below cover them.

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| B01 | V1.1 §10.1 | A v1.0 world upgraded; the Earth and Moon, station and satellite flows continue. | DS | Scripted |
| B02 | V1.1 §10.2, §11.2 | Two stations in the shared space dimension, around Earth and the Moon; environment and permissions do not mix; each shows its own orbit body. | DS, V1 | Scripted |
| B03 | V1.1 §10.3 | A route data pack reloads; a failed reload rolls back. | DS | Scripted |
| B04 | V1.1 §10.4 | A restart during a player's flight keeps the target. | DS | Scripted |
| B05 | V1.1 §10.5 | Two players choose different targets at the same time without mixing sessions. | V2 | Both |
| B06 | V1.1 §11.1, §11.3, §11.4 | The destination screen shows no internal ID; unknown and locked routes give readable feedback; the old Earth and Moon flow is unchanged; a data-pack test body is marked as test content. | V1 | Scripted |
| B07 | V1.1 §13.1, §13.4 | No notable MSPT or memory regression in the full regression workload. | REF | Scripted |

### 4.3 v1.2.0, machine and multiblock kernel

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| C01 | V1.2 §10.1, §13.1, §13.5 | 100 idle machines and the defined number of active ones against the budgets. | REF | Scripted |
| C02 | V1.2 §10.2 | Chunk unload and reload, and a server restart, with machines running. | DS | Scripted |
| C03 | V1.2 §10.3 | Two players operate the same screen and the same port at once. | V2 | Both |
| C04 | V1.2 §10.4, §11.3 | A structure broken by a piston, an explosion or a player comes apart safely; the formed and unformed models match their collision. | DS, V1 | Scripted |
| C05 | V1.2 §10.5 | Input and output pipes and common third-party capabilities. This needs a third-party mod; see decision O6. | DS | Scripted |
| C06 | V1.2 §11.1, §11.2, §11.5 | Three machine screens at GUI scales 2, 3 and 4; feedback that points at a missing part; long recipe names, both languages, out-of-range numbers. | V1 | Scripted |
| C07 | V1.2 §11.4 | The JEI recipe display matches what the machine does. | V1 with JEI | Scripted |
| C08 | V1.2 §12.2 | A remote screen and a player without permission are refused, shown with a second real client. | V2 | Both |

### 4.4 v1.3.0, public API and compatibility

These rows need the API fixture mod built against Cn (P2).

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| D01 | V1.3 §10.1; REL-1.3 M130-01 | The mod and the fixture mod start fresh and restart; both players connect and one reconnects; the diagnostics are right. | DS, V2 | Both |
| D02 | V1.3 §10.2; REL-1.3 M130-02 | An external cargo container rides Earth, Moon and back and is disassembled with exact contents. | DS | Scripted |
| D03 | V1.3 §11.2; REL-1.3 M130-03 | An unsupported container and a rejected provider give a readable reason in the assembler; the source is unchanged. | V1 | Scripted |
| D04 | REL-1.3 M130-04 | A room with an integration-provided boundary follows state changes and a tag reload. | DS, V1 | Scripted |
| D05 | V1.3 §11.3; REL-1.3 M130-05 | External oxygen armour in vacuum: debit, refill and reconnect; the HUD and equipment look right. | V1 | Scripted |
| D06 | V1.3 §11.1; REL-1.3 M130-06 | A registered fuel in a partly filled tank survives a restart; the remainder is returned once; names and icons are right. | DS, V1 | Scripted |
| D07 | REL-1.3 M130-07 | Registered engine and tank definitions change across a restart; an old rocket keeps its captured values. | DS | Scripted |
| D08 | REL-1.3 M130-08 | A registered satellite payload; a reload with the menu open; a claim and a replayed claim. | DS, V1 | Scripted |
| D09 | V1.3 §10.5; REL-1.3 M130-09 | A second player who is not the owner tries inventory, rocket and terminal operations; stale and out-of-range intents. | V2 | Both |
| D10 | V1.3 §10.3; REL-1.3 M130-10 | On disposable copies: the provider registration is omitted, then the fixture mod; the documented refusal and the recovery after reinstalling. | DS | Scripted |
| D11 | V1.3 §11.4; REL-1.3 M130-11 | The declared optional-client combinations; the server runs without the optional client integration. | DS | Scripted |
| D12 | GATE-1.3 G7 | CPU, memory, tick, network and cache measurements on the reference node. | REF | Scripted |

### 4.5 v1.4.0, planetary expansion

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| E01 | V1.4 §10.1; REL-1.4 MT140-01 | Travel to several bodies in a row, a restart and the return, with terrain, inventory, fuel and environment recorded. | DS, V1 | Scripted |
| E02 | V1.4 §10.2; REL-1.4 MT140-02 | Two real clients with separate accounts: discovery locked before and available after, private reward balances, concurrent fees, repeated claims. | V2 | Both |
| E03 | V1.4 §10.4; REL-1.4 MT140-03 | A private station before and after shared discovery, as member and as non-member; a surface target on the gas giant is refused. | V2, DS | Both |
| E04 | REL-1.4 MT140-04 | Cold, heat, pressure and sunlight under open sky, with equipment and in supplied sealed rooms; the player messages. | V1 | Scripted |
| E05 | V1.4 §11.2; REL-1.4 MT140-05 | The star map: nodes, the station list, quotes, focus, a reload while open; both languages, small and large windows, GUI scales. | V1 | Scripted |
| E06 | V1.4 §11.1, §11.5; REL-1.4 MT140-06 and `VISUAL-VALIDATION-REPORT.md` | Each body's sky, sun, stars and fog by day and night; water, lava, blindness and darkness; a resource reload. On at least two real GPU categories. | V1 on two GPUs | Both |
| E07 | V1.4 §11.3; REL-1.4 MT140-07 | Rapid world changes, reconnects and profile reloads leave no geometry or fog of the previous body. | V1 | Scripted |
| E08 | V1.4 §10.3; REL-1.4 MT140-08 | A data pack reload that succeeds and one that fails while running; a body removed and restored with its routes. | DS | Scripted |
| E09 | V1.4 §10.5 | A station around a body added in v1.4. | DS | Scripted |
| E10 | REL-1.4 MT140-09 | Representative upgrade worlds: stable IDs, station orbits, rockets and cargo, research. | DS | Scripted |
| E11 | V1.4 §13.2; GATE-1.4 G7 | The sky does not rebuild resources every frame; reference-hardware CPU, memory, tick, network and cache measurements. | V1, REF | Scripted |

### 4.6 v1.5.0, orbital stations and warp

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| F01 | V1.5 §10.1, §13.4; GATE-1.5 G7 | Ten or more stations with concurrent records and chunk unloading; the 10- and 100-station benchmark; a native multi-station load and reload. | DS, REF | Scripted |
| F02 | V1.5 §10.2 | Two stations warp to different targets at the same time. | V2 | Both |
| F03 | V1.5 §10.3 | During a warp the owner disconnects and reconnects; a passenger is online, then offline. | V2 | Owner |
| F04 | V1.5 §10.4 | A docked rocket is saved and restored through a warp. | DS | Scripted |
| F05 | V1.5 §10.5 | A target removed or reloaded in the catalog aborts the warp safely. | DS | Scripted |
| F06 | V1.5 §10.6 | A request from a non-member is refused. | V2 | Both |
| F07 | V1.5 §11.1 to §11.5, §13.5 | The orbit body, star, sun and gravity hints; the warp countdown, status and failure feedback; several stations do not mix; position and sky cache before and after a warp; GUI scales; what the second client sees. | V1, V2 | Both |

### 4.7 v1.6.0, satellites and missions

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| G01 | V1.6 §10.1, §13.3; GATE-1.6 G7 | More than 100 active missions across a restart; 500 missions within the budget and 1,000 as the extended load; the flush cost of ADR-050 section 2 on reference hardware. | DS, REF | Scripted |
| G02 | V1.6 §10.2 | A player offline for hours gets a bounded catch-up. | DS | Scripted |
| G03 | V1.6 §10.3 | Two players settle in the same tick. | V2 | Both |
| G04 | V1.6 §10.4 | A forced stop before and after the reward write, on Cn. | S2 | Scripted |
| G05 | V1.6 §10.5, §10.6 | Mission instance chunks unload and are reclaimed; no chunk ticket is left. | DS | Scripted |
| G06 | V1.6 §11.1 to §11.4; GATE-1.6 G8 | The satellite assembly and mission screens and their progress; the star-map target matches the body; the asteroid and gas-giant visuals; failure, cancel and expiry feedback; a mission gameplay video. | V1 | Both |
| G07 | V1.6 §11.5 | Mission state on two clients. | V2 | Both |

### 4.8 v1.7.0, endgame systems

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| H01 | V1.7 §10.1 | Several players contend for the same target. | V2 | Both |
| H02 | V1.7 §10.2 | A non-owner and a player without operator rights are refused. | V2 | Both |
| H03 | V1.7 §10.3 | The server is stopped inside a block-processing batch, on Cn. | S2 | Scripted |
| H04 | V1.7 §10.5; GATE-1.7 G4 | Protection mods: an adapter test or the matrix with real mods. This needs third-party mods; see decision O6. | DS | Scripted |
| H05 | V1.7 §10.6, §13.5 | A long device run leaves no ticket or cache; no watchdog on the reference node. | SOAK | Scripted |
| H06 | V1.7 §11.1 to §11.5; GATE-1.7 G8 | Target selection, the danger confirmation, progress and failure; the laser, black-hole, gravity and elevator visuals do not hide key feedback; several devices visible at once; GUI scales and readability. | V1 | Both |
| H07 | V1.7 §13.1; GATE-1.7 G7 | Each system's MSPT, memory and ticket budget; the 60 ms flush budget of ADR-054 section 7 on reference hardware. | REF | Scripted |

### 4.9 v1.8.0, the version checklist

[W62](W62.md) classified these checklist items as "needs the final campaign" (its class B).

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| I01 | V1.8 §10.1, §11.5; W62 item 10.1 | One technology route from vanilla resources to the combustion generator, the classic machines, a Moon flight, a station warp and one endgame device, on a dedicated server, recorded on video with the build hash. | DS, V1 | Owner (decision O4); a scripted rehearsal first |
| I02 | V1.8 §10.2, §13.1; W62 items 10.2, 13.1 | Many v1.8 machines of every kind in one world, with chunk unloading; then the same layout scaled up on the reference node, idle and active. | DS, REF | Scripted |
| I03 | V1.8 §10.3; W62 item 10.3 | A v1.7 world that holds every placeholder and old ID loads unchanged on Cn, with two restarts. | DS | Scripted |
| I04 | V1.8 §10.4, §10.5 as revised by D9; [W188](W188.md) | The four cells of Forge 47.4.10 and 47.4.23, each with and without JEI 15.56.0.205: a packaged start, the recipe counts per category, a restart, and on a real client the recipe display and the usage lookup. | DS, V1 | Scripted |
| I05 | V1.8 §10.6; W62 item 10.6 | Long automated production: the item and fluid totals are conserved over a multi-hour run. | SOAK | Scripted |
| I06 | V1.8 §11.1, §11.4; W62 item 9.2 | Every new screen, model, texture and sound sampled, and the high-risk assets all checked; no missing texture or model, on screen or in the client log. | V1 | Both |
| I07 | V1.8 §11.2; [W241](W241.md), [W244](W244.md), [W249](W249.md), [W255](W255.md), [W256](W256.md), [W258](W258.md), [W260](W260.md), [W261](W261.md), [W265](W265.md) | The imported OBJ and baked models on a real GPU: the six machine models' overlap and placement, the etcher, the motors' UVs in every facing, the saw blade and its axle, the nuclear motor, the standalone fuel tanks, the landing-pad grid, the laser gun in both hands; multiblocks and armour. | V1 | Both |
| I08 | V1.8 §11.3 | Both languages, the GUI scales and a resource reload across the v1.8 screens. | V1 | Scripted |
| I09 | V1.8 §13.2 to §13.4; W62 items 13.2 to 13.4 | FPS and 1% low against the vanilla baseline with the complex OBJ models and multiblocks in view; the F3+T reload time and heap, three times; the time to open each classic JEI category and to search. | V1 | Both |
| I10 | V1.8 §13.5 as revised by D9 | The reference workload of docs/17 section 4 for at least four hours, with the JEI configuration and the four-cell coverage stated. An empty server or W188's short matrix does not count. | SOAK | Scripted |
| I11 | V1.8 §12.1, §12.3; W62 items 12.1, 12.3 | With two real clients on a dedicated server: the new machine packets follow the shared policy; a non-owner and a station member try the endgame devices. | V2 | Both |

### 4.10 v1.8.0, by system

| ID | Source | What is checked | Level | Driver |
|---|---|---|---|---|
| J01 | [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md) section 3.4 | The satellite bay's menu statuses and the OWNER_PENDING_FULL text. | V1 | Scripted |
| J02 | [W254-V2-CHECKLIST](W254-V2-CHECKLIST.md) sections 1 to 4 and 6; R298; Q1 of [OWNER-DECISIONS-20261101](OWNER-DECISIONS-20261101.md) | The bay's safe exit with two real clients on the packaged dedicated server: both roles, a witness, competing exits, the fallback and its single fall safeguard, disconnect, death and portal, authority and chunk-load checks. | V2 | Owner, on the campaign gate of [W269](W269.md) |
| J03 | W254-V2-CHECKLIST section 5 | The same exit on a LAN world, each client hosting once. The LAN fixture route is a prerequisite that the checklist leaves to Root. | V2 on LAN | Owner |
| J04 | C3-SWITCH-REPORT section 3.4; [W325-MATRIX](W325-MATRIX.md) | The bay's online-passenger cases and S3 with real clients. | V2 | Owner |
| J05 | C3-SWITCH-REPORT sections 3.4 and 9.2; R-043 | On the reference node: the cost of D6 and the barrier, of D3 and the registry writer, and the real waiting times of the 23 rows of section 9.2, with more than 3 slots and 2 claims. | REF | Scripted |
| J06 | [W11CD-DESIGN](W11CD-DESIGN.md) leaf C-V; C3-SWITCH-REPORT section 4.4 | Cargo devices on a real GPU: labels, the fluid, status and cursor display, GUI scales, a reload, the visual refresh after cargo changes. | V1 | Scripted, on the gated world (decision O2) |
| J07 | W11CD-DESIGN leaf C-V; C3-SWITCH-REPORT section 4.4 | Cargo with two real clients: ownership, competing devices, the preparation and pending state on both clients, passengers, a disconnect. | V2 | Both, on the gated world (decision O2) |
| J08 | W11CD-DESIGN leaf C-B; C3-SWITCH-REPORT section 4.4 | The cargo benchmark: the budgets, the native reference load apart from microbenchmarks, each device's cycle time. | REF | Scripted |
| J09 | R-043; C3-SWITCH-REPORT section 9.2 rows 6 and 14; R438 | The shared cargo work budget and the capture quota under concurrent bay and cargo load. | REF | Scripted |
| J10 | R-035; C3-SWITCH-REPORT section 4.4; [W206](W206.md), [W207](W207.md); R438 | W90's cold-destination cost and readiness time, and the first-use checkpoint cases that exceeded 100 ms on the development host ([CURRENT_VERSION](../../../status/CURRENT_VERSION.md)). | REF | Scripted |
| J11 | R245 in OWNER-DECISIONS-20261101; KI-V180-SAVE-01 in `docs/releases/v1.8.0/KNOWN-ISSUES.md` | The first-event progression flush (139.8 ms on the development host) measured on the reference node. If it exceeds the budget there, an asynchronous flush becomes a designed task before release. | REF | Scripted |
| J12 | D4 of OWNER-DECISIONS-20261031; [W43](W43.md) | The code-drawn sky, the black hole and the Earth disc on a real GPU: the owner confirms the look; colours, sizes and mote density may need tuning. | V1 | Owner |
| J13 | D12 of OWNER-DECISIONS-20261031; KI-V180-MOON-01 | The fixed-midnight Moon as published, confirmed on a real client. | V1 | Owner |
| J14 | [W16](W16.md), "Visual and listening evidence still required" | The HUD panels in both languages, every gauge extreme, GUI scales 1 to 6, anchors and offsets, live configuration and resource reload; the worn jetpack, tank and visor mounts. With two clients: another player's mounts and independent HUD layouts. | V1, V2 | Both |
| J15 | W16; OWNER-DECISIONS-20261104 (D5) | A listen on Cn: loop seams, the 30- and 32-block start and stop, the 32-loop cap, reload, pause and logout. The owner passed the ten sounds themselves on 2026-11-04; that listen was on an earlier JAR. | V1 | Owner |
| J16 | [W87](W87.md) | The drill bodies, the refusal and progress banner, and the jackhammer's feedback when the server disables it. With two clients: a non-owner sees the refusal in the open screen, and railgun effects reach observers. | V1, V2 | Both |
| J17 | [W44I](W44I.md) "Not done"; [W44-DESIGN](W44-DESIGN.md) rows FM10a to FM10d and the FX rows | The 14 real-client variants of the world-first advancements, with a participant and a non-participant. | DS, V2 | Both |
| J18 | [W37B](W37B.md), "D1 evidence achieved and left for the final campaign" | Sleeping and saved points off-world: packaged restarts of rows V02, V04 to V07 and V13; the compass and clock presentation (V11); two real clients for V13; the multi-player quorum, time, weather and portal rows V03, V08 to V10 and V12. | DS, V1, V2 | Both |
| J19 | [W21A-DESIGN](W21A-DESIGN.md) section 7 | The observatory, the data processor and survey jobs on a real GPU and with two clients. | V1, V2 | Both |
| J20 | [W189](W189.md); [W311](W311.md) | Rocket tracking and trail alignment with real player movement; Moon slow falling; the flight console used from a high seat, and the named-pad refusal on the console. | V1 | Both |
| J21 | CURRENT_VERSION, "V1 and V2"; harness `REPORT.md` section 4; `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md` | Every finding of the earlier real-client rounds checked again on Cn: findings 1 to 5 of 2026-10-10 and the single-player rounds' findings queued as W280 to W282, W284 to W296 and W301. | DS, V1 | Scripted |
| J22 | CURRENT_VERSION, "S1"; [OWNER-BRIEF-20261103](OWNER-BRIEF-20261103.md) item 5; R386, R438 | The full restart campaign on Cn: all families of `scripts/run_v180_integrated_campaign.py` and the report refilled from Cn. | DS, S2 | Scripted |

## 5. Phases

The order is the one TASK-W359 gives. Phases 4 and 5 come last, as the owner decided. Phase 5 follows phase 4
because the soak needs the two player connections that phase 4 proves, and because a defect found by two players is
cheaper to fix before a four-hour run than after it.

**Rule for all phases: scripted first, the owner second.** In each phase the scripted rows run first. The owner's
hand-played rows start only when the scripted rows of that phase have no open Critical or High finding on the same
candidate. This keeps the owner's hours for a candidate that is likely to stay.

**Failure handling, common to all phases.** Severities follow docs/17 section 3.

| What happens | What is done | What repeats |
|---|---|---|
| The harness or a fixture fails, not the product | The failed attempt is kept and named. The harness is fixed outside the JAR and the row runs again. | That row. |
| A Low finding | It is recorded with its screenshot or log and goes to the known issues. The phase continues. | Nothing. |
| A Medium finding | It is recorded and queued. The phase continues. Before a release it is fixed, or the owner signs a limited waiver (docs/17 section 3). | If fixed: see the next row. |
| A Critical or High finding, or any fix that changes `src/main`, `src/generated` or the build files | The phase stops for the affected system. The fix merges after a green hosted run, and that run's build becomes the next candidate (R386). | All scripted rows of every phase already run, on the new candidate, and the restart campaign J22. The owner's hand-played rows repeat in full, unless the owner chooses decision O3's narrower rule. |
| A budget of docs/17 is exceeded on the reference node | The measurement is kept as failed. Either the cause is fixed (a new candidate, as above) or an ADR changes the budget under docs/17 section 10, reviewed by a session that did not implement it. | Phase 5 for the affected measurement, and the soak if the fix touches the server tick. |
| A row cannot run (a missing fixture, a missing mod, a machine not available) | It is recorded as NOT_COVERED with the reason. It is never recorded as passed. | Nothing until the cause is removed. |

No test, assertion, timeout or budget is removed or relaxed to obtain a result (AGENTS.md section 5).

### Phase 1: preparation

| Aspect | Content |
|---|---|
| Environment | The development PC (RTX 3070 Laptop GPU, Ryzen 5 5500, 32 GB, per the harness report), the evidence disk D:, hosted CI. The second-GPU machine and the reference node are checked, not yet used. |
| Entry | The prerequisites of section 7 are met: candidate Cn exists, its restart campaign ran, and the owner has confirmed the start and answered decisions O1 to O9. |
| Exit | P1 to P9 below are done; a dry run of one scripted round passes on Cn on Forge 47.4.10; the owner has the checklists. |
| Failure | A preparation step that fails blocks only the phases that need it. A defect found by the dry run follows the common table. |

Steps for Root or a worker session (scripted or written work):

- **P1. Pin the candidate.** Record Cn's commit, hosted run, JAR name, size and SHA-256, and the SHA-256 of the
  sources and API JARs. Every machine receives the JAR by copy and its hash is checked before each run.
- **P2. Build the fixtures.** The v0.9 Beta world archive (REL-1.0 `MIGRATION-REPORT.md` gives its hash), a v1.0
  world, representative v1.3 to v1.7 worlds, the v1.7 world with every placeholder ID (W62 item 10.3), the many-machine
  layout (W62 item 10.2), the API fixture mod against Cn, three rockets of 64, 512 and 2,048 blocks, and the reference
  workload world of docs/17 section 4. Every source world is kept unchanged; runs use copies.
- **P3. Retarget the harness.** Server templates and client profiles for Forge 47.4.10 and 47.4.23, each with and
  without JEI 15.56.0.205; a mode that attaches to client windows the owner started; a frame-time capture (decision
  O8); batches regenerated for Cn.
- **P4. Write the checklists.** One numbered step list per inventory row, in the form of the test plan's section 11
  (ID, preconditions, steps, expected, actual, screenshot or video, tester, date, build hash). The row's sources are
  read in full here. Root reviews the lists against the inventory.
- **P5. Prepare the campaign world for the bay and cargo sessions.** The disposable copy, the marker file and the two
  JVM properties of W254-V2-CHECKLIST section 0.1, with the two account UUIDs the owner supplies. Prepare the LAN
  fixture route for J03.
- **P6. Bring up the reference node.** The acceptance list of docs/15 section 18, under a normal user, never root.
  Record the provider, plan, OS, vCPU, memory, swap, free disk and CPU steal (docs/15 section 15).
- **P7. Prepare the second-GPU machine.** A client install for Forge 47.4.10 with Cn, and a way to copy screenshots
  back with their hashes.
- **P8. Create the evidence root** of section 6 and check free space: at least 10 GB on C: and D: (AGENTS.md
  section 9).
- **P9. Dry run.** One scripted round of phase 2 on Cn. It is a rehearsal and not evidence.

Steps for the owner:

- Confirm the start and answer decisions O1 to O9 (section 7.3).
- Give the two accounts' UUIDs (not the credentials) for the whitelist and the campaign property.
- Give a normal-user SSH login on the reference node, or run the setup commands from a script Root supplies.
- Say when the second-GPU machine and a second player, if any, are available.

Time (estimates): the owner 2 to 3 hours. Root and workers 5 to 8 working days. Machine time about 6 hours, apart
from Cn's restart campaign, which took 12 hours 31 minutes on C3 (C3-SWITCH-REPORT section 2.1).

### Phase 2: single-player V1 on the real GPU

| Aspect | Content |
|---|---|
| Environment | One real client on the development PC against the packaged server on loopback, as the harness does; Forge 47.4.10 for the full pass, 47.4.23 and the JEI profiles for the smokes; GUI scales 2, 3 and 4, windowed and fullscreen; English and Chinese. Then a reduced pass on the second-GPU machine. Record OS, CPU, GPU, driver, resolution, GUI scale and JVM for each. |
| Rows | V1 rows: A12 to A14, A16, B06, C06, C07, D03, D05, E04 to E07, E11 (the sky), F07, G06, H06, I06 to I09, J01, J06, J12 to J16, J19 to J21, and the V1 half of the rows marked "DS, V1". |
| Entry | Phase 1's exit. |
| Exit | Every row has PASS, FAIL or NOT_COVERED with its reference; no open Critical or High finding; the owner has judged the rows marked Owner or Both; the second GPU category has its reduced pass (E06 in full). |
| Failure | The common table. A purely visual fix (a texture, a model, a text) still changes the JAR, so it makes a new candidate; such fixes are collected and merged together to limit the number of candidates. |

Steps for Root or the harness:

- Run the scripted V1 batches round by round: materials and machines, life support, rockets of three sizes, skies of
  every body, stations and warp, satellites and missions, endgame devices, the v1.8 machines and equipment, the bay
  and cargo screens on the gated world, the API fixture rows.
- Capture the frame-time and memory figures of A12 and I09 with the vanilla and Forge empty-scene baselines.
- Build contact sheets per row and a list of client-log lines that mention a missing model or texture.
- Fill the result table and give the owner the sheets, grouped by row.

Steps for the owner:

- Review the contact sheets and mark each as agreed or not.
- Play the judgement rows by hand in one sitting: the sky, black hole and Earth disc (J12), the Moon (J13), the
  listen (J15), the models and armour (I07), the endgame visuals (H06), and a free look at the new art (I06).
- Record the technology route I01, or approve the scripted rehearsal as its basis (decision O4).
- Repeat E06 and a short tour on the second-GPU machine.

Time (estimates): the owner 3 to 4 hours of review and judgement, 2 to 3 hours on the second-GPU machine, and 6 to
10 hours for the recorded technology route if it is played in survival. Machine time 10 to 14 hours of scripted
rounds on the main PC and about 2 hours on the second machine.

### Phase 3: dedicated server with one real client, restarts and persistence

| Aspect | Content |
|---|---|
| Environment | The packaged dedicated server on the development PC with one real client; the four Forge × JEI cells; copies of the upgrade worlds. Offline loopback is enough here; online mode belongs to phase 4. |
| Rows | A01 to A04, A17, A18, B01 to B04, C02, C04, C05, D02, D04, D06 to D08, D10, D11, E01, E08 to E10, F01 (the native part), F04, F05, G01 (the restart part), G02, G04, G05, H03, H04, I02 (the world part), I03, I04, J17 and J18 (their single-client and restart parts), J22. |
| Entry | Phase 2 has no open Critical or High finding on Cn. Phase 3's scripted rows may start while the owner's phase 2 judgement is still open. |
| Exit | Every row has a result; every restart row has at least two restarts of the same world with the compared state; every upgrade ran on a copy with the original's hashes unchanged; the server logs have no ERROR from the mod and no unexplained repeated WARN (test plan section 6). |
| Failure | The common table. A failed upgrade or recovery keeps the failed copy and never touches the original fixture. The three historical-JAR downgrade checks are expected to fail under R-044 and stay failed (R428, R429); they are not new findings. |

Steps for Root or the harness:

- Run each row's server start, scripted client actions, save, stop, restart and comparison.
- Run the forced-stop rows A04, G04 and H03 with the existing native runners on Cn. No client is needed for them.
- Run the four-cell matrix I04 with the real client's recipe and usage screens.
- Compare state before and after each restart with the existing field-level projections, and list every difference.

Steps for the owner:

- Review the restart comparisons and the upgrade reports, about one page per row group.
- Optionally play one restart by hand on a copy of the phase 2 world.

Time (estimates): the owner 1 to 2 hours. Machine time 14 to 20 hours, apart from J22.

### Phase 4: multiplayer V2 with two real clients

| Aspect | Content |
|---|---|
| Environment | The packaged dedicated server with `online-mode=true`, `white-list=true`, `enforce-whitelist=true` and `enable-rcon=false`, reachable only from the two clients (loopback, a LAN address, or the SSH tunnel of docs/15 section 11). Two real clients signed in by the owner with the two accounts, on one or two machines. For J02 to J04 and J07, the disposable marked world of W254-V2-CHECKLIST section 0.1. A LAN world for J03. Clocks on all machines set to UTC and synchronized. |
| Rows | A05 to A07, A15, B05, C03, C08, D01, D09, E02, E03, F02, F03, F06, F07 (the second client), G03, G07, H01, H02, I11, J02 to J04, J07, J14 and J16 (their two-client parts), J17 to J19 (their two-client parts). |
| Entry | Phases 2 and 3 have no open Critical or High finding on Cn; the owner's hand-played rows of phase 2 are done; both accounts connect to the whitelisted server in a five-minute check. |
| Exit | Every row has a result with both clients' logs and the server log; the campaign record holds what W254-V2-CHECKLIST section 7 lists; a V2 result for the bay exit has both the dedicated server and the LAN world. |
| Failure | The common table. A step that needs two people at the same instant and had only one is NOT_COVERED, not failed. A third client connecting to the campaign world closes the gate; that session is void and repeats. |

Steps for the owner (and a second player, decision O5):

- Sign in on both clients. No agent sees or stores the credentials.
- Sitting 1, the core flows: A05 to A07, A15, B05, C03, C08, D01, D09 and I11.
- Sitting 2, planets, stations, missions and endgame: E02, E03, F02, F03, F06, F07, G03, G07, H01, H02 and the
  two-client parts of J14, J16 to J19.
- Sitting 3, the bay and cargo on the campaign world: J02, J04 and J07, then the LAN steps J03.
- Record both clients on video for the bay checklist, as its section 0.2 asks.

Steps for Root or the harness:

- Start and stop the server, copy the worlds, write the marker file and check the startup lines of W254-V2-CHECKLIST
  section 0.1.
- Set each scene with console commands before the owner's step, so that the owner plays only the decisive actions.
- Where the owner allows it, send scripted input to the two signed-in windows for steps that need the same server
  tick on both clients.
- Collect the logs, match each step to its server lines and fill the result table during the sitting.

Time (estimates): the owner 10 to 14 hours in three sittings. Within that, the cargo session is 2 to 3 hours by the
design's own estimate (W11CD-DESIGN leaf C-V), and W359 estimates the bay checklist at 3 to 4 hours and its LAN part
at 1 to 1.5 hours. Machine time is the same hours, plus about 2 hours of setup.

### Phase 5: performance, the reference node and the soak

| Aspect | Content |
|---|---|
| Environment | The owner's Debian 12 node with 4 vCPU and 8 GiB, a normal user, Java 17, `Xmx4G` at most, the server bound to loopback and reached through an SSH tunnel. The two clients of phase 4 connect for the reference workload (docs/17 section 4), or an equivalent the owner accepts (decision O7). Host sampling by the commands of docs/15 section 15. |
| Rows | A08 to A11, B07, C01, D12, E11 (the reference part), F01 (the benchmark), G01 (the load), H05, H07, I02 (the scaled layout), I05, I10, J05, J08 to J11. |
| Entry | Phase 4 has no open Critical or High finding on Cn; the node passed P6; no X01 or X02 run uses the node at the same time (section 7.2). |
| Exit | Every budget of docs/17 section 4 has a measured value with its workload; the soak ran at least four hours without OOM, watchdog, unexpected forced chunks or monotonic memory growth, or its failure is recorded; R-043's waits have measured figures; the performance report states hardware, JVM, commit, JAR hash, configuration, workload, sampling time and every failure. |
| Failure | The common table, with its budget row. A soak that fails or is interrupted is kept and a full new run of at least four hours is needed; partial runs are not added together. |

Steps for Root or the runners:

- Day 1, benchmarks: the per-version measurements (A09 to A11, B07, C01, D12, E11, F01, G01, H07, I02), the cargo
  benchmark J08, the timing families and the concurrent bay and cargo load J05 and J09, and J10 and J11.
- Day 2, the soak: build the reference workload (two players, 16 oxygen devices or sealed rooms, 10 station records,
  100 satellites or missions, two active rockets, one 2,048-block assembly and disassembly, one dimension transfer,
  periodic saves and restarts), with the automated production of I05 and the long device run of H05 inside it, and run
  it for at least four hours.
- Sample tick times, RSS, GC and forced chunks for the whole run and compare the item and fluid totals at the end.

Steps for the owner:

- Keep the node available and exclusive for the two days.
- Connect the two clients at the start of the soak and leave them connected, or approve the equivalent (O7).
- Read the performance report.

Time (estimates): the owner 3 to 6 hours, most of it attendance at the start and end of the soak. Machine time 18 to
26 hours on the node: about 3 hours plus 1 item hour for the cargo benchmark (the design's estimate,
section 6 of [INTEGRATED-C1-REPORT-20261109](INTEGRATED-C1-REPORT-20261109.md)), about half a day for R-043's timing
by the same source, 4 to 6 hours for the per-version measurements,
and 5 to 6 hours for the soak with its build-up.

## 6. Evidence layout

**Outside Git**, the working root is `D:/GitHub/ARCE-Task-Evidence/final-campaign/<Cn short SHA>/`:

```text
candidate/            JAR, sources and API JARs, identity files, artifacts.sha256
checklists/           the step lists of P4, as run
fixtures/             source worlds and the fixture mod (never modified), with hashes
phase1-prep/          the dry run, node acceptance, machine records
phase2-v1/<machine>/<round>/    server.log, client logs, results.jsonl, summary.json,
                                screenshots/, sheets/, frametime/
phase3-ds/<row>/      logs, pre- and post-restart state, upgrade reports
phase4-v2/<sitting>/  server.log, client-a.log, client-b.log, video index, step table
phase5-ref/<run>/     host record, raw tick, memory and GC samples, workload description
results/              RESULTS.md and results.json (one row per inventory ID), FINDINGS.md
SHA256SUMS.txt        every file under this root
```

- **File names.** Screenshots are `<row ID>-<step>-<client>-<language>-<scale>.png`. Logs keep their native names
  inside a folder named for the row or round. Videos are `<row ID>-<client>.mkv`.
- **Each result row records:** the inventory ID; the level; the candidate commit and JAR SHA-256; the Forge and JEI
  versions; the machine; the tester (the owner, a second player, or the harness with its script commit); the start and
  end time in UTC; the result, PASS, FAIL or NOT_COVERED; a reference to at least one screenshot, video or log
  excerpt with its SHA-256; for FAIL, the finding number; for NOT_COVERED, the reason and what would cover it.
  A row with no reference is not PASS.
- **Sealing.** When a phase ends, `SHA256SUMS.txt` is recomputed and the phase folder is not changed again. A
  correction is a new file that names what it corrects, and the checksum list is recomputed with the reason
  (AGENTS.md section 10). Failed attempts are kept beside the later ones.
- **Cleaning.** After each run the server copies, exported trees and temporary archives are deleted; logs, results,
  lists and the source worlds are kept (AGENTS.md section 9).

**In Git**, under `docs/releases/v1.8.0/`, the campaign writes the report set of the
[roadmap](../../../16-POST-1.0-VERSION-ROADMAP.md) section 8: `MANUAL-TEST.md`, `VISUAL-VALIDATION-REPORT.md`,
`MULTIPLAYER-REPORT.md`, `PERFORMANCE.md`, `MIGRATION-REPORT.md`, `RECOVERY-MATRIX.md`, `TEST-REPORT.md`,
`checksums.txt`, `evidence-index.json` and `development-artifacts.json` ([W71](W71.md) names the last three). The
reports of v1.0 to v1.7 each get a pointer to the campaign's result rows for their inherited items, so that no
version has a second, contradicting state
([release gates](../../../06-RELEASE-AND-ACCEPTANCE-GATES.md), last section).

**Size limits** (AGENTS.md section 9): no committed file over 50 MB, and no more than 100 MB for the campaign's
evidence in Git. Committed: the result tables, the reports, the checksum lists and at most a small set of reduced
contact sheets. Not committed: videos, raw screenshots, worlds, raw samples and JARs. They stay on D: or go to a
release attachment or other external storage, and the reports give each one's location and SHA-256. The checksum
lists name only files inside the evidence root or committed files.

## 7. Prerequisites

### 7.1 Work that must finish first

| Prerequisite | State on 2026-11-13 | Source |
|---|---|---|
| Per-fluid signed accounting: W357's design note, a read-only review by a different engine, then the implementation | W357 dispatched; no production code yet | R434 |
| The uncovered bay rows built natively (B6.8, B10.6, B11.8, B11.9, CAP.3, and B11.7's UNKNOWN part and B17.3 if a deterministic control exists) | W358 dispatched | R435 |
| The other uncovered rows (item before-write and store-veto cuts, R-031's ledger root, the vanilla-dismount row, a presence-unknown seam, an item-device hold GameTest) | W360 waits for a free Codex slot | R438 |
| Candidate C4: the hosted build after those three merge, every campaign family run again on it, and the per-switch report refilled | Not built | R438, R386 |
| The fixes for the single-player rounds' findings (W280 to W282, W284 to W296, W301) merged, or each listed as a known issue | Per [QUEUE](../QUEUE.md) | CURRENT_VERSION, "V1 and V2" |
| The V1.0 §2 prerequisite: a release owner, an independent reviewer and an installer who did not take part | Open | V1.0 §2 |

### 7.2 Owner decisions already asked, which change the candidate or the campaign

| Decision | Effect on the campaign | Source |
|---|---|---|
| Each of the five switches (the bay and the four cargo kinds): enable or keep off | Enabling a switch changes production code or constants, so it makes a new candidate. With the switches off, J01 to J09 run only on the gated campaign world, and they show the gated path, not production admission. If a switch is enabled after the campaign, its rows repeat on the new candidate. The safest order is to decide the switches before phase 4. | OWNER-DECISIONS-20261109; OWNER-DECISIONS-20261113 |
| R-044's downgrade limit: sign [R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md) or not | The campaign does not depend on the signature. With or without it, the historical-JAR downgrade checks stay failed in the campaign's results. If the owner signs, the upgrade instructions that the installer follows must state the complete-backup rule. | R433 |
| X01 to X03: which isolated tests run, and the confirmation before each destructive step | They are a separate plan, [X01-X03-ISOLATION-PLAN](X01-X03-ISOLATION-PLAN.md), and not part of this campaign. Its option E2 uses the reference node with loop devices; if the owner chooses E2, those runs must not overlap phase 5, because they would disturb the measurements. Their results on Cn may come before or after the campaign. | R436 |
| The escrow fuel loader's admission | Handled separately (2026-11-09). If it changes production code, it makes a new candidate like any switch. | OWNER-DECISIONS-20261109 |

### 7.3 Decisions the campaign itself needs from the owner

W359 gives a recommendation for each; Root may change it before the plan goes to the owner.

| ID | Decision | Recommendation |
|---|---|---|
| O1 | The start of the campaign, and whether phases 2 and 3 may start before the switch decisions. | Start phases 1 to 3 on C4 as soon as its restart campaign has run; decide the switches before phase 4. |
| O2 | Whether the bay and cargo real-client rows (J01 to J09) run on the gated campaign world while the switches are off. C3-SWITCH-REPORT section 3.4 says the owner must decide how the real-client cases are driven. | Yes, on the disposable marked world of R298, which is the only path that admits real clients today. |
| O3 | After a fix makes a new candidate, whether the owner's hand-played rows repeat in full, or only the rows whose systems the production diff touches, with both JAR hashes recorded. | The narrower rule, with Root listing the touched systems from the diff and the owner approving the list each time. Scripted rows always repeat in full. |
| O4 | Whether the recorded technology route I01 is played in survival from raw resources, or the scripted rehearsal with a recorded hand-played subset is enough. | A hand-played survival route, since V1.8 §10.1 asks for the route from the base to the endgame; the scripted rehearsal shortens it by removing dead ends first. |
| O5 | Who operates the second client in phase 4: the owner alone with two accounts, or a second person. Some steps need both players in the same second (W254-V2-CHECKLIST step 2.2). | A second person for sitting 3 at least; otherwise scripted input to the second window, which must be stated in the record. |
| O6 | Third-party mods: V1.2 §10.5 (pipes and capabilities) and V1.7 §10.5 (protection mods) need other mods, while D9 limits v1.8's declared support to Forge × JEI. | Record both rows as NOT_COVERED in v1.8 with D9 as the reason and move them to v1.9's modpack work, unless the owner names specific mods. |
| O7 | The soak's "two real players or a verified equivalent" (docs/17 section 4): the two accounts connected for four hours, or scripted equivalents. | The two real clients, left connected and driven by the harness, so that nobody has to play for four hours. |
| O8 | The tool for 1% low and first-stall figures (docs/17 section 8), since F3 gives only an average. | A frame-time capture tool the owner already trusts on the GPU machine; the report names the tool and its version. |
| O9 | Which machine is the second GPU category, and whether E06 alone or a wider subset runs there. GATE-1.4 to GATE-1.7 G8 ask for at least two real GPU categories. | E06, H06, I07 and a fifteen-minute tour of A12 on the second machine. |

## 8. Totals

All figures are W359's estimates, apart from those marked with a source in section 5. They assume one candidate for
the whole campaign.

| Phase | Owner's hours | Machine hours | Root and worker days |
|---|---|---|---|
| 1. Preparation | 2 to 3 | about 6, plus about 12.5 for Cn's restart campaign (C3's measured time) | 5 to 8 |
| 2. V1 on the real GPU | 5 to 7, plus 6 to 10 if the technology route is played in survival | 12 to 16 | 3 to 4 |
| 3. Dedicated server and restarts | 1 to 2 | 14 to 20 | 3 to 4 |
| 4. V2 with two clients | 10 to 14 | 12 to 16 | 3 to 4 |
| 5. Reference node and soak | 3 to 6 | 18 to 26 | 3 to 4 |
| **Total** | **21 to 32, or 27 to 42 with the survival route** | **about 75 to 97** | **17 to 24** |

**Calendar.** About four to five weeks from the day the prerequisites of section 7.1 are met and the owner confirms
the start: one to two weeks of preparation, then about one week each for phases 2 and 3 together, phase 4 and
phase 5. Phase 4's length depends on when the owner and a second player have three free sittings.

**Each new candidate** adds about three to five days: the hosted run, about 12.5 hours for the restart campaign, the
scripted rows of the phases already run, and the owner's rows that decision O3 selects. Two new candidates would
bring the calendar to about six or seven weeks.

**Not in these totals:** the work of section 7.1 before the campaign, X01 to X03, the installation by an uninvolved
person, the independent review, and the release steps after the owner's approval.

## 9. How this plan was made

- **Read.** TASK-W359, WORKER-RULES and AGENTS.md; the master test plan, the release gates, the quality budgets, and
  sections 11, 13, 15 and 18 of the remote and visual rules and section 8 of the roadmap; sections 10 to 13 of the
  version documents of v1.0 to v1.8 (v1.0 from section 6 on); the release folders of v1.0 and v1.3 to v1.8 (Gate
  status, manual tests, and the v1.0 multiplayer, visual, performance, migration and recovery reports); GATE_STATUS and
  the "what is still open" part of CURRENT_VERSION; the owner-decision records of 2026-10-31 to 2026-11-13 and the
  owner brief; C3-SWITCH-REPORT sections 1 to 4.4 and 9 to 11; section 6 of the C1 report; W254-V2-CHECKLIST in full;
  W62's sections 10 to 13; the passages of W16, W21A-DESIGN, W37B, W43, W44I, W44-DESIGN, W87, W189, W241, W311 and
  W11CD-DESIGN that name a campaign check; the heads of the X01 to X03 plan and the R-044 draft; and the harness's
  `PLAN.md`, `REPORT.md` and the head of `scripts/live_session.py`.
- **Searched, not read in full.** The other records of this folder, with `grep` for the campaign, V1, V2 and the
  reference node. COMPLETION-PLAN and the rest of CURRENT_VERSION were searched for the same words. The version
  documents' sections 1 to 9 and 14 to 19 (apart from v1.0's), the v1.5 to v1.7 requirement maps and the eight rounds
  of the single-player report were not read. Preparation step P4 closes this gap row by row.
- **Not run.** No Minecraft, Gradle, GameTest, native server, harness script or unit test. The only commands were
  `git` reads, `ls`, `grep`, `sed`, `wc` and the repository validator.
- **Verification.** `python -B scripts/validate_repository.py --require-approved-identity`, run with this file
  staged: 45 passed, 0 pending, 0 warnings, 0 failed. Every link target in this file exists in the tree.
- **Choices where the task was open.** The S2 level is added to the task's list, for the forced-stop rows that need no
  client. The inventory is per system for v1.8's slices, with the limit stated in section 4. The scripted-first rule
  and decision O3 are W359's proposals for limiting the owner's repeated hours.
- **Record.** TASK-W359 allows one output file, so this section replaces the separate worker record of WORKER-RULES.
- **Known risks of this plan.** The estimates have no measured basis except where a source is named. The harness
  changes of P3 are untested. An inventory row may hide several checks that only P4 will separate, so the totals may
  grow.
