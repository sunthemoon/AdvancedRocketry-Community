# CL18B-WORKSTATION-BOUNDS-03: author handoff

Date: 2026-10-08. Author: Claude (Opus 5.5), fresh delegated worker launched by Root through
`claude -p`, session `d1759931-9ca8-40c8-a622-f42291ec7d54` (from Root's `DISPATCH-01.json`). Not
Root, no persona, no nested delegation. Status: **author-returned, unreviewed**. No verdict or
acceptance is claimed.

## Completed scope

Three NEW files in `D:/GitHub/arce-v180-claude-equipment-bounds-20261008`, exactly the TASK-03
`write_scope`:

- `docs/work/v1.8.0-c18b-equipment/BOUNDS-PROPOSAL-03.md`
- `docs/work/v1.8.0-c18b-equipment/BOUNDS-TEST-DESIGN-03.md`
- `docs/work/v1.8.0-c18b-equipment/BOUNDS-HANDOFF-03.md` (this file)

Each is under 24 KiB by author estimate. No other repository file and no file in the evidence leaf
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-bounds-claude-author-20261008-01/` was written;
Root's dispatch files there are untouched. Exact bytes, SHA-256 values, patch and seal are Root's to
record after release, as TASK-03 directs.

## Not completed

- REVIEW-02 M2 (cached gravity alternative), G11 configuration change and saved maximum, count-two
  and stackable enchantment eligibility, numeric/schema choices and charging durability: outside
  TASK-03, left to the separate C18b successor.
- Every planned case (BT01-BT33) and every formal command: not run.
- Mandatory governance documents only partly read (see Reads).

## Key design decisions (proposals only)

1. "Operations can only shrink" is withdrawn; insertion admission is no longer proof of any later
   output.
2. Five bound domains (component C, owned trees O, outer keys K, unrelated U, whole root W) with
   named application points; the component ceiling never applies to armor.
3. Every held-state change, including plain menu insertion and withdrawal, screens and bounds its
   complete detached postimage with frame overhead, then refuses unchanged on any excess.
4. Serialized-at-rest slots: block-entity-private detached compounds are the authority; views and
   hand-outs are fresh materializations. This gives an in-contract closure argument without a save
   veto; the out-of-contract residual (reflection, direct foreign writes) is RP-1.
5. Load classification into SUPPORTED or RETAINED (six reasons), whole-root retention without split
   authority, lossless re-emission of retained input (RP-3), destruction preservation left to RP-2.
6. Slot 0 narrowed to built-in suit pieces without `ForgeCaps`, supported owned roots and no pending
   marker (A1-A4); A5 headroom is liveness only, never the safety argument.

## Observations

- REVIEW-02's 65,536 → 65,592 projection falsifies CONTRACT-02:197-203; the proposal uses it as BT10.
- Depth ceilings do not compose: with the reserve proposal's whole-root depth 16 and the fixed frame
  (owned root at depth 6, record tag at depth 8), the effective owned depth is 11 and the loose
  component depth 12, not 16 (BND-OPEN-1). This is an arithmetic observation from the published
  schema, not a native measurement.
- A component passing C at the full 16,384 bytes or depth 16 can never be installed, because record
  framing sits inside the O ceiling; INSTALL refuses it unchanged.
- P11's 1 MiB success is valid only for the private owned-update helper on worn armor; the same
  armor is refused by the workstation (BT16).
- Block-level protection cannot stop `/setblock`, `/fill` or chunk removal, so RETAINED preservation
  must be record-based or an accepted risk.

## Limitations

- Vanilla 1.20.1 behaviour relied on in BOUNDS-PROPOSAL-03 §4 (menu paths consult `mayPlace` and the
  container setter; lossless `ItemStack.of`/`save` without `ForgeCaps`) is from author knowledge and
  was **not verified** against mapped source; BT14, BT17 and BT18 and the native leaf must show it.
- The 56-byte growth is REVIEW-02's ASCII projection, not native serialization.
- Cited hashes (TASK-03, CONTRACT-02, REVIEW-02) are values recorded by Root or the reviewer; this
  author computed none.

## Reads

Read in full: TASK-03 (Root checkout); REVIEW-02; CONTRACT-02; TEST-DESIGN-02; TASK-02;
SUPPLEMENTAL-02.json; DISPATCH-01.json; OXYGEN-WITNESS-CLARIFICATION-01;
`ClassicDropQuarantine.java`. Read in part: reserve PROPOSAL-01 §2.4 and §3.3 (lines 99-124,
193-243); live Root `AGENTS.md` §9-§12 plus its heading list (the worktree copy, §1-§8, was supplied
in session context); `GuardedChunkSaves.java` declarations; `docs/status/CURRENT_VERSION.md:486-499`;
`docs/17-V1PLUS-QUALITY-BUDGETS.md` (NBT-limit search only). Searched without matches:
`docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md` for C18b/workstation.

**Not opened this session:** `PROJECT-CONFIG.md`, `PRODUCT.md`, `docs/01`, `docs/04`, `docs/05`,
`docs/06`, `docs/14`, `docs/16`, the full v1.8.0 version document, ADR-025/065/066, revision-01
equipment drafts, REVIEW-02's other evidence files, and reserve PROPOSAL-02. A reviewer should treat
any governance conflict as unchecked by this author.

## Commands and results

Tools used: Read, Glob, Grep, Write, and one Edit of this file. **No shell, Git, Python, JVM, javac,
Gradle, server, client, network, install, credential, cache, JAR or archive command was run**, as
TASK-03 requires. Three exploratory calls returned an error or nothing: a Grep on the nonexistent
`docs/versions/v1.8.0.md` (path error), a Grep with a brace `glob` over three docs (no matches), and a
Glob `docs/versions/v1.8*` (no files). A later Glob found the actual version document. Nothing else
failed. Formal commands `./gradlew clean build`, `runData`, `git diff --exit-code`,
`runGameTestServer`, S1, S2, V1 and V2: **UNVERIFIED**, not run, not waived.

## Impacts

None on source, tests, registry, configuration, network, save format, assets, ADR, risk, ledger,
status, CSV or AGENTS. If adopted, BOUNDS-PROPOSAL-03 replaces CONTRACT-02 §6 "Insertion admission",
the :197 wording, and TEST-DESIGN-02 W07, W08, W14 and P11 as mapped in BOUNDS-TEST-DESIGN-03 §4.

## Root integration needs

- RP-1 out-of-contract live disposition; RP-2 RETAINED destruction preservation (E2-OPEN-2); RP-3
  re-emission of over-bound input; RP-4 exact bounded measurer and frame constants (extends D-4); RP-5
  D4 pending-marker identity. None is adopted by these documents.
- BND-OPEN-1 depth composition (recommendation 21), BND-OPEN-2 armor `ForgeCaps` refusal, BND-OPEN-3
  built-in-only slot 0.
- Different-agent delta review of these three files, then Root freeze, before any source leaf.

## Gate and next step

No v1.8 G0-G9 Required Gate is satisfied by these documents. Next: an independent delta review of
BOUNDS-*-03 against REVIEW-02 M1 and the workstation part of L1; separately, the remaining C18b
successor for M2 and the other L1 cases.

## Interest release

No process, helper, lock, Git index, HEAD or branch interest is held; nothing runs in the background.
All read interests are released. This author will not edit these files after return; corrections go
to a new Root-registered task.

Handoff path: `D:/GitHub/arce-v180-claude-equipment-bounds-20261008/docs/work/v1.8.0-c18b-equipment/BOUNDS-HANDOFF-03.md`
