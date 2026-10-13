# TASK W21c: observatory and astrobody data processor (blocks, menus, runtime)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w21c-observatory` |
| Branch | `codex/v1.8.0-w21c-observatory`. It is based on W21b (`8c22fce3`, not yet reviewed or merged) plus the shared branch (`8d6188d3`). Local only; Root merges after W21b |
| Contract | ADR-066 section 6.1; [records/W21A-DESIGN.md](records/W21A-DESIGN.md), the W21c slice; the frozen fixtures `src/test/resources/fixtures/v180/machine_patterns/{observatory,astrobody_data_processor}.json` |
| Design review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W21a-review/REVIEW.md`, SHA-256 `0c76c2d4f9d125ca1af75ee96e35ddbd46ba914aaabbfddf5f9d3f32d01dfb50`. Read the W21c conditions 1 to 4 |
| Record | `docs/work/v1.8.0-root-queue/records/W21C.md` |

W21b, the registry schema 4 model, is under independent review at the same time. If that
review changes W21b, Root merges the fix into this branch. Use W21b's model as it is; do not
change it except where W21c's contract requires, and record each such change.

## Goal

Make the observatory and the astrobody data processor playable:
- **Blocks:** the two multiblocks on the v1.2 kernel, built from the frozen patterns in all
  four rotations.
- **Observatory:** START, survey progress and completion, all as W21b registry mutations.
- **Processor:** analysis selection, claim, progress and credit, also as W21b mutations.
- **UI:** menus.
- **Content:** recipes, models, original art, and English and Chinese text.

## Root's decision on the power plugs (review W21c condition 2)

The hatch runtime only recognizes `ClassicControllerBlockEntity` as an owner
(`ClassicHatchBlockEntity.loadedOwner()`). Choose in this order:
1. **Preferred:** the research controllers *are* `ClassicControllerBlockEntity` instances.
   They use a research machine profile, and the registry job stays the only owner of survey
   and analysis state; the controller banks hold only FE. Use this if it needs no change to
   the kernel's physical FE ownership or exclusivity.
2. **Otherwise:** a narrow owner interface in the kernel package, implemented by
   `ClassicControllerBlockEntity` and the two research controllers.
   - The hatch recognizes the interface.
   - Debit stays inside the kernel through a kernel-owned accessor.
   - There is no public or unchecked debit, and the plug does not become shareable.
   - Physical FE ownership, exclusivity, stale-claim retirement and generation behaviour are
     unchanged.

Record which option you took and why. Test:
- stale ownership;
- overlapping controllers;
- unload and reload;
- removal;
- generation changes;
- retained invalid roots.

## Required corrections from the design review

- **R1:**
  - a bounded, server-lifecycle-owned identity directory for loaded observatories and
    processors, kept independently of the ACTIVE and RUNNING indexes;
  - a collision quarantine with an explicit scope and an authoritative source, covering
    retained READY and PROCESSED jobs, idle copies, claim and rebind, unload and reload, and
    copies across Levels;
  - copy tests before START and after completion, processor copies, and an explicit owner
    rebind.
- **R3:**
  - a fixed wire layout of at most 16 DataSlots;
  - masked low and high 16-bit halves for progress, duration and aggregate FE;
  - round-trip tests at 32,767, 32,768, 65,535, 65,536, 72,000 and the FE maximum.
- **R4:** the offline-repair-only refusal shows in inspect, menu and operator text.

## Tests and evidence (review condition 3 and 4)

- **GameTests:**
  - formation;
  - sky, weather and station;
  - FE;
  - instance visibility;
  - credit exactly once;
  - copy, rebind and cancel;
  - fairness;
  - authority.

  Use a deterministic DataGen comparison for the frozen layouts. Pace loading waits with
  `GameTestTickPacer`.
- **Dedicated restart and fault evidence.** Compare the actual IDs, progress, FE and research
  balances, including:
  - a failed flush (WAITING_SAVE);
  - waiting on a rebind claim;
  - a lost completion or credit flush.

  State the existing FE loss limitation; do not describe it as crash-atomic FE.
- **Operator-only NBT.** Both controllers override `onlyOpCanSetNbt()`, under the W23 rule,
  and are added to its classification test.

## Rules

- Write the proposed ADR and risk-register text (the collision-isolation scope, and R-027's
  device-root scope) in your record. Root adds them.
- No class over 800 lines.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker. Report the totals.
- The native restart and fault runs, with evidence under 100 MB.

## Not in this task

- The advancements (section 6.2).
- Discovery.
- The satellite bay.
- Status files and the ledger.
