# CL18B-WORKSTATION-BOUNDS-03: test design

Date: 2026-10-08. Status: **planned cases only; none executed.** Derives from
[BOUNDS-PROPOSAL-03](BOUNDS-PROPOSAL-03.md) ("B§n"). Levels as in CONTRACT-02's TEST-DESIGN-02:
A0 pure JUnit (NBT through the repository's `MinecraftBootstrap` where needed), A1 GameTest with
real server players where possible, S1 packaged dedicated server with restarts, S2 forced-stop cuts.

Every refusal oracle below means: both stored slot compounds, both views, the player's inventory and
cursor, all owned and unrelated bytes are byte-identical before and after; nothing dropped, deleted,
split or partially moved; the client view is resynchronized from server state. Byte numbers marked
"native" are measured by the test and recorded, not predicted here.

## 1. Measurer and frame (A0)

| ID | Case | Expected |
| --- | --- | --- |
| BT01 | fixtures: empty root; two EMPTY slots; armor only; armor plus tank; nested component tags | measurer bytes equal the length `NbtIo` writes for the same compound minus the fixed outer header; nodes and depth equal a reference walk |
| BT02 | armor whose unrelated key holds a 1 MiB nested compound | measurer returns OVER_BOUND after counting at most 65,537 bytes or 1,025 nodes; no copy made; visit counter recorded |
| BT03 | frame constants | `F_B`, `F_N` and frame depth pinned from native fixtures; A5 arithmetic recomputed from them |
| BT04 | slot 1 component of depth 16; armor with owned depth 16 | root depth 20 and 21 (native); outcome per BND-OPEN-1: refused under `D_W` 16, accepted under 21; the chosen option's oracle binds |
| BT05 | each RETAINED reason as a fixture compound (FUTURE, CORRUPT, OVER_BOUND, UNKNOWN_ITEM, UNSUPPORTED_OWNED, PENDING) | no slot materializes; reason scalar set; `saveAdditional` re-emits a byte-identical compound |
| BT06 | slot 0 supported, slot 1 FUTURE | whole root RETAINED; slot 0 also not materialized |
| BT07 | SUPPORTED root at exactly 65,536 B, 1,024 nodes, `D_W`; then one over each | SUPPORTED; then RETAINED(OVER_BOUND), byte-identical re-emission |

## 2. Prepared postimage (A1)

| ID | Case | Expected |
| --- | --- | --- |
| BT10 | REVIEW-02 fixture: held root exactly 65,536 B, armor in slot 0, rootless empty tank in slot 1; INSTALL into `chest_oxygen` | refused unchanged; native postimage size recorded (REVIEW-02 projects 65,592); no record written; no foreign callback after the final recheck (test hook counter) |
| BT11 | padding chosen so the INSTALL postimage is exactly 65,536 B; then one byte more | accepted, record written, transfer slot empty; then refused unchanged |
| BT12 | REMOVE whose postimage exceeds W (fixture where stack framing exceeds record framing) | refused unchanged; module stays installed; position **not** `REPAIR_REQUIRED` |
| BT13 | armor insertion at the A5 unrelated maximum; then one byte more | accepted; then refused at insertion and the stack stays with the player |
| BT14 | over-bound stack offered by each insertion path: click, split click, drag, hotbar-number swap, offhand swap, quick-move in | each refused unchanged |
| BT15 | quick-move out and in with a full destination; with a stale slot (test hook); with an over-bound postimage | each: nothing changes; a passing plan commits completely |
| BT16 | P11 split. (a) Private owned-update helper on **worn** armor with a 1 MiB unrelated value: reserve commit succeeds, unrelated value same reference and byte-equal, 0 visits. (b) The same armor offered to slot 0 | (a) helper result only; (b) refused at insertion (B§2 A5/W). (a) is not workstation acceptance |
| BT17 | test hook mutates the slot view in place (adds a tag key) | next save writes the stored compound unchanged; view rebuilt before the next broadcast; the hook's key never persists |
| BT18 | test hook mutates a stack after withdrawal | block entity state (now EMPTY) unaffected; re-inserting that stack runs the full screen again |
| BT19 | slot 0 offered: external-provider armor; enchanted native armor; built-in suit with `ForgeCaps` (fixture capability); built-in suit with a future `arce_classic_equipment` | each refused at insertion (successor of W08; A2 row follows BND-OPEN-2) |
| BT20 | pending marker (D4 fixture key) on armor; on a tank | insertion refused for both; a held root given the key through `/data` loads as RETAINED(PENDING); INSTALL and REMOVE refused |
| BT21 | `equipment.classicEnabled` false with armor and an installed tank held | insertion and INSTALL refused; REMOVE into the empty transfer slot and withdrawal succeed; active, reserve and saved maximum unchanged; total gas conserved |
| BT22 | `/data merge block` making the root exceed 65,536 B | RETAINED(OVER_BOUND); `REPAIR_REQUIRED`; nothing dropped; save re-emits the merged compound byte-identically (subject to RP-3) |
| BT23 | menu open on a RETAINED root; every intent; every insertion and withdrawal path | only the reason scalar reaches the client (capture: no item content); every action refused; no chunk load |
| BT24 | SUPPORTED root: player break; explosion | each held stack drops exactly once, byte-equal to its stored compound |
| BT25 | RETAINED root: player break, explosion, `/setblock`, `/fill`, mover | **DEPENDENCY_BLOCKED on RP-2.** Required property once RP-2 exists: retained bytes survive or move to Root's durable record, never erased silently |

## 3. Durability (S1, S2)

| ID | Level | Case | Expected |
| --- | --- | --- | --- |
| BT30 | S1 | SUPPORTED root at exactly 65,536 B; two restarts | byte-identical; SUPPORTED |
| BT31 | S1 | each RETAINED reason, including input above 65,536 B; two restarts | byte-identical; RETAINED with the same reason; no materialization |
| BT32 | S2 | forced stop after an INSTALL commit, before the chunk saves | wholly before or wholly after (CONTRACT-02 W13); W holds in both |
| BT33 | S2 | forced stop after a refused over-bound INSTALL | root byte-identical to the pre-state |

## 4. Replacement map

| TEST-DESIGN-02 case | Successor here |
| --- | --- |
| W07 (oversized insertion only) | BT10-BT16 |
| W08 (install refused after insertion) | BT19 (refused at insertion, under BND-OPEN-3) |
| W14 (future root) | BT05, BT06, BT22, BT31; destruction BT25 |
| P11 (1 MiB unrelated success) | BT16 (a) helper oracle and (b) workstation refusal |
| missing pending-receipt and disabled-removal cases (REVIEW-02 L1) | BT20, BT21 |

## 5. Not covered and not executed

Not here: REVIEW-02 M2 (boots, B-cases), G11 configuration change and saved maximum, count-two and
stackable enchantment eligibility, charging durability, recipes, art and network schema; they stay
with the separate C18b successor. None of the cases above has been executed. No formal Gradle
command, GameTest, server or client was run by this task, which forbids it; every case is
**UNVERIFIED**, not waived and not NOT_APPLICABLE.
