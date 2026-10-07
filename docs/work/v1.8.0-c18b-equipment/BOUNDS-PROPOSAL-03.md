# CL18B-WORKSTATION-BOUNDS-03: prepared-output bounds and preservation proposal

Date: 2026-10-08. Milestone: v1.8.0 / C18b. Status: **proposed, not frozen**; the workstation stays
`DEPENDENCY_BLOCKED`. Author: Claude, fresh delegated worker (not Root), session
`d1759931-9ca8-40c8-a622-f42291ec7d54`. Task: `docs/work/v1.8.0-c18b-equipment/TASK-03.md` in the
Root checkout. Its SHA-256 `a74e0c72…6cd6963` and registration commit `a8219e75` are the values
Root recorded in this leaf's `DISPATCH-01.json`; this author computed no hash. Worktree base
`2e397a49c92a323eb1886a538e226765e7f4edc9`.

This document answers [REVIEW-02](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-independent-20261008-02/REVIEW-02.md)
M1 and the workstation part of its L1 against
[CONTRACT-02](D:/GitHub/arce-v180-claude-equipment-contract-20261007/docs/work/v1.8.0-c18b-equipment/CONTRACT-02.md)
(SHA-256 `283b6c74…8dae99e` as recorded in REVIEW-02). It proposes successor text only for
CONTRACT-02 §4.4/§4.5 scope wording and §6 "Insertion admission"; all other CONTRACT-02 text is
unchanged and remains under its own review. It adopts **no** truncation, silent omission, save veto,
R-021 extension or quarantine mechanism, and grants no source task.

## 1. Dispositions

| Finding part | Disposition | Where |
| --- | --- | --- |
| M1a: "operations can only shrink" (CONTRACT-02:197-203) | **Withdrawn as false.** REVIEW-02's projection (65,536 → 65,592 bytes on install, framing only) is a counterexample. Insertion admission is no longer used as proof of any later output. | §3 |
| M1b: armor versus component bounds ambiguous (:197) | **Clarified.** Five separate domains with named application points; armor is never measured against the component ceiling; unrelated armor values are bounded only by the whole workstation root. | §2 |
| M1c: no prepared-output admission | **Proposed.** Every operation that changes held state, including install, remove, quick-move and plain menu insertion and withdrawal, screens and bounds its complete detached workstation postimage, frame included, and refuses unchanged on any excess. | §3 |
| M1d: closure of later persisted outputs | **Proposed, limited to in-contract writers.** A serialized-at-rest slot representation makes every persisted root a product of screened commits. The out-of-contract residual is named, not hidden. | §4 |
| M1e: current oversized or non-faithful roots | **Classified; preservation left to Root.** Read-side classes, lossless re-emission and refusal are proposed; destruction preservation and the residual live case are Root dependencies RP-1..RP-3, not adopted here. | §5, §6 |
| M1f: W07 only tests insertion; P11 ambiguity | **Successor cases** BT10-BT18, with P11 split into a private-helper oracle and a workstation refusal oracle. | BOUNDS-TEST-DESIGN-03 |
| L1 (workstation part): pending refusal, disabled safe removal | **Restored** as rules (§5.4) and cases BT20-BT21. | §5.4 |
| L1 (other parts): G11 config change and saved maximum; count-two and stackable eligibility | **Not handled here.** Config/numeric choices and enchantment eligibility stay with the separate C18b successor, as TASK-03 directs. | handoff |
| M2 cached gravity alternative | Out of scope for this task. | — |

## 2. Bound domains: armor versus component

Sizes are exact encoded native NBT bytes, including every entry's type byte, name length and name
bytes and every compound end byte, counted by a bounded measurer that stops at the ceiling plus one
(RP-4). Accounter estimates are not a substitute. Depth counts the domain's own root as 1; nodes count
every tag once (reserve proposal §2.4 convention).

| Domain | Contents | Ceiling | Applied when |
| --- | --- | --- | --- |
| C component | tag of one loose module or tank stack | 16,384 B / depth 16 / 256 nodes; native-shape screen; no `ForgeCaps`; lossless preflight (CONTRACT-02 §4.5 rules 1-5 unchanged) | insertion into slot 1; source of INSTALL; output of REMOVE |
| O owned armor trees | `arce_space_suit_oxygen` plus `arce_classic_equipment` with every position record and record framing | 16,384 B / depth 16 / 256 nodes, counted from each owned root | every write of an owned tree: INSTALL, REMOVE, reserve commits on worn armor |
| K outer key map | the armor stack tag's own keys | 256 keys / 16,384 key-encoding bytes | every write of the armor tag |
| U unrelated armor values | every outer value except the two owned keys | **no own ceiling**; never traversed by the private owned-update helper on worn armor | only through W while held in the workstation |
| W whole root | `arce_suit_workstation` exactly as `saveAdditional` would write it | 65,536 B / 1,024 nodes / depth `D_W` (BND-OPEN-1) | every prepared postimage (§3), and the load screen (§5) |

Replacement for the ambiguous sentence at CONTRACT-02:197:

> Armor in slot 0 passes the K screen, has supported-or-absent O trees, and passes A1-A5 below. A
> module or tank in slot 1 passes C. The component ceiling never applies to armor. Every operation's
> complete postimage passes W (§3). Passing insertion does not promise that a later INSTALL fits.

Armor admission into slot 0 (author proposals; each is a narrowing, not a new capability):

- **A1** only built-in `space_suit_*` pieces, count 1. External-provider and enchanted armor cannot
  take modules (CONTRACT-02 §3, §7), so holding them gives no function and only adds retained state.
  CONTRACT-02 W08 becomes an insertion refusal.
- **A2** the armor's saved form has no `ForgeCaps` key, as for components. A live capability can
  re-serialize larger or differently at any later save, which defeats closure. Compatibility cost:
  built-in suits carrying another mod's attached capability cannot enter the workstation (they stay
  with the player unchanged). If Root rejects A2, §4's closure no longer covers capability growth and
  RP-1 becomes mandatory rather than residual (BND-OPEN-2).
- **A3** no unsupported, future or pending owned root. The workstation never becomes a carrier for
  roots it cannot operate on; such armor keeps its bytes where they already are.
- **A4** the whole saved stack passes the native-shape screen and a save/read/save lossless preflight
  within the W bounds.
- **A5 headroom (liveness, not safety):** with the armor alone in slot 0, its measured unrelated
  part plus the O ceiling (16,384 B, 256 nodes), the C ceiling (16,384 B, 256 nodes) and the fixed
  frame constants `F_B` bytes and `F_N` nodes must fit 65,536 B and 1,024 nodes. Then in-contract
  INSTALL and REMOVE sequences cannot strand a module at the ceiling. The effective unrelated budget is
  about 32 KiB minus framing. A5 is arithmetic about the schema and is **not** relied on for safety:
  §3 still checks each actual postimage, so an arithmetic error causes refusal, never excess output.

## 3. Prepared-postimage admission

Applies to every server path that changes what the block entity holds: slot 0/1 insertion by click,
split click, drag, hotbar-number swap and offhand swap; withdrawal; quick-move in both directions;
INSTALL; REMOVE. The block entity exposes no vanilla `Container` to commands or automation, no item
capability, and no other setter (CONTRACT-02 W10); Root confirms that list at implementation.

1. **Capture witnesses:** menu and container identity, block entity identity and generation, both
   stored slot compounds by identity, enabled state, the player's affected slot or cursor.
2. **Prepare** detached postimages of both slots from the stored compounds and the incoming stack's
   screened saved form. No live held object is mutated.
3. **Run every foreign callback** (stack save, capability lookup, item rebuild for preflight) on the
   detached copies only.
4. **Measure** the complete postimage root, frame included, with the bounded measurer; abort counting
   at ceiling plus one.
5. **Check** W, and C, O and K where the operation touches those domains. Any excess refuses.
6. **Recheck** all captured witnesses (OXYGEN-WITNESS-CLARIFICATION-01 limits: captured witnesses
   only; no promise about unrelated foreign effects or restored intermediate values).
7. **Publish** by swapping the stored compounds and rebuilding the views; no callback runs after step 6.

**Refusal is atomic and unchanged:** both stored compounds, both views, the player's inventory and
cursor, and all owned and unrelated bytes stay byte-identical; nothing is dropped, deleted, split or
partially moved; the client is resynchronized from the unchanged server state. A REMOVE that would
exceed W (record framing smaller than stack framing) is a plain refusal; the module stays installed
and the position is not marked `REPAIR_REQUIRED`, because nothing is faulty.

Worked case from REVIEW-02: a held root of exactly 65,536 bytes with armor in slot 0 and an empty
rootless tank in slot 1; INSTALL into `chest_oxygen` adds record framing (projected +56, 65,592). Step
5 refuses; the root stays 65,536. The 56-byte figure is the reviewer's ASCII projection; the native
number is pinned by BT10. Under A5 that starting state is not reachable by in-contract insertion; the
case is still tested because A5 is not the safety argument.

## 4. Serialized-at-rest slots (closure argument)

- Each slot holds `EMPTY` or one block-entity-private, detached `CompoundTag`: the admitted stack's
  saved form produced in §3 step 2. That compound is the authority.
- The menu sees a **view** `ItemStack` materialized from the compound after every commit. A view is
  never persisted. If the view no longer equals its last materialization (an in-place mutation by any
  code path that bypassed §3), it is discarded and rebuilt from the compound before the next menu
  broadcast; the mutation is not written.
- Withdrawal, quick-move out, REMOVE into the player path and destruction drops hand out a **fresh**
  materialization; no reference to a stored compound ever leaves the block entity.
- `saveAdditional` writes the stored compounds verbatim inside the fixed frame.

Claim, limited to in-contract writers: a stored compound changes only through a §3 publish whose
postimage passed W and the native-shape screen, and no other code holds a reference to it; therefore
every root this block entity writes is within W and native-faithful, and no save-time check, omission
or veto is needed for it. Facts this relies on, **not verified here** (BT17-BT18 and the native leaf
must show them): vanilla menu paths consult `mayPlace` and the container setter before moving a stack;
`ItemStack.of` and `save` of a stack without `ForgeCaps` round-trip byte-identically (checked per
stack at runtime by the preflight in any case).

Residual (out of contract): reflection or another mod writing the block entity's private fields
directly. It cannot be detected within a bounded budget at save time and is not claimed. It is RP-1.

## 5. Currently retained roots

### 5.1 Load classification

Before deserialization, the load path screens the received `arce_suit_workstation` compound with the
bounded measurer and the reserve proposal §3.3 shape rules. Classes:

| Class | Condition | Treatment |
| --- | --- | --- |
| SUPPORTED | schema 1, exact frame, both slots supported under A1-A4 and C, W within bounds | stored compounds set from detached copies; views materialized |
| RETAINED | any of: schema > 1 (FUTURE); frame or entry shape wrong (CORRUPT); W exceeded or screen aborted (OVER_BOUND); unknown item ID (UNKNOWN_ITEM); unsupported owned root (UNSUPPORTED_OWNED); pending marker (PENDING) | the received compound reference is kept unchanged; **no** slot materializes, including a slot that would pass alone (no split authority); menu shows `REPAIR_REQUIRED` with one reason scalar; every intent and menu move refuses; no item content is sent to clients |

`/data merge block` and `/data modify block` reach the block entity through this load path, so they
classify rather than bypass bounds. Unknown items are retained raw instead of being turned into air.

### 5.2 Save of a retained root

The block entity re-emits the received compound unchanged: no omission, no truncation, no
normalization, and no save veto is invoked. An OVER_BOUND root is therefore written larger than
65,536 bytes, but only at its received size; the block entity never grows or produces such a root.
Whether ARCE may re-emit over-bound input this way is **RP-3** for Root. The alternatives are a
quarantine record (RP-2 family) or the unadmitted veto; omission and truncation are excluded.

### 5.3 Sources of retained or non-faithful state

| Source | Path | Covered by |
| --- | --- | --- |
| older or newer build, corrupt file | load | §5.1, §5.2 |
| `/data` commands | load | §5.1, §5.2 |
| removed mod (unknown item) | load | §5.1 |
| attached capability re-serializing larger | live | excluded by A2; else RP-1 mandatory |
| unrelated references shared with outside code | live | excluded by §4 (detached private compounds; fresh views and hand-outs) |
| reflection or direct foreign writes | live | RP-1 residual |
| configuration change | — | none: W, C, O and K ceilings are fixed schema-1 constants, not configuration |

### 5.4 Disabled and pending state

- `equipment.classicEnabled` false: new insertion and INSTALL refuse; withdrawal and REMOVE into the
  empty transfer slot stay allowed when their §3 postimage passes (reserve proposal §3.3: safe
  removal does not require enabled). Active, reserve and saved maximum bytes are unchanged.
- Pending marker (whatever key Root's D4 contract defines) on armor or component: insertion refuses
  (A3 for armor; added rule for components); a held stack cannot gain one, because no charging path
  touches workstation stacks (CONTRACT-02 §6); one found on load gives RETAINED(PENDING).

### 5.5 Destruction

- SUPPORTED: break and explosion drop one fresh materialization per held stack, exactly once, byte-
  equal to the stored compound.
- RETAINED: break, explosion, `/setblock`, `/fill`, chunk removal and block movers would delete the
  retained bytes. Block-level protection cannot stop commands or chunk removal, so preservation must
  be record-based or an explicitly accepted risk. That choice is **RP-2** (CONTRACT-02 E2-OPEN-2);
  until Root decides, the block is not enabled, as CONTRACT-02 §6 already states.

## 6. Root preservation dependencies (none adopted)

| ID | Dependency | Options for Root (not chosen here) | Blocks |
| --- | --- | --- | --- |
| RP-1 | disposition of a live root made non-faithful or over-bound out of contract (and capability growth if A2 is rejected) | accept documented residual; bounded save-time recheck plus a reviewed `GuardedChunkSaves` veto with its R-021 scope; Root quarantine record | enablement if A2 is rejected; otherwise residual acceptance |
| RP-2 | preservation of RETAINED bytes across break, explosion, commands, chunk removal and movers | Root quarantine record with bounds and offline recovery; or explicitly accepted risk | enablement |
| RP-3 | permission to re-emit received over-bound input unchanged | accept; or route it to RP-2 | load path |
| RP-4 | exact bounded encoded-size measurer with early abort, and pinned frame constants `F_B`, `F_N`, frame depth | extend the D-4 component admission helper | every check here |
| RP-5 | D4 pending-marker identity | D4 contract | §5.4 refusal |

Insertion and postimage bounds are not evidence that any future output was written; RP-1..RP-3 are
what make retained-state preservation true, and none of them is assumed.

## 7. Open choices raised here

- **BND-OPEN-1 depth composition.** By the §2 convention the fixed frame nests as root 1, `slots` 2,
  entry 3, `stack` 4, stack `tag` 5, owned root 6, position record 7, record `tag` 8. An owned tree of
  depth 16 reaches root depth 21; a loose component of depth 16 in slot 1 reaches 20. Under the
  reserve proposal's "whole root depth 16, more restrictive wins", the effective owned depth is 11 and
  the loose component depth 12, which silently shrinks the per-domain contract. Options: set `D_W` to
  frame depth plus the domain ceiling (21), or state the reduced per-domain depths explicitly. Numeric
  choice for Root; this proposal recommends 21 so that domain ceilings mean what they say.
- **BND-OPEN-2** A2 capability refusal versus compatibility (§2).
- **BND-OPEN-3** A1 built-in-only slot 0 versus holding any armor (CONTRACT-02 W08 as written).
- Within O, a component that passes C (depth 16, 16,384 bytes) can never be installed, because the
  record framing sits inside the 16,384 O ceiling. INSTALL refuses it unchanged; whether C should be
  tightened to O minus record framing is a numeric choice left to the C18b successor.

## 8. Impact

No source, test, registry, configuration, network, save, asset, ADR, risk, ledger, status or AGENTS
change. No S1, S2, V1 or V2 result. This proposal passes no v1.8 G0-G9 Gate.
