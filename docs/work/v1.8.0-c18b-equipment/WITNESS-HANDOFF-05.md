# CL18B-EQUIPMENT-WITNESS-05: author handoff

Date: 2026-10-08. Author: Claude (Opus 5.5), fresh delegated worker, not Root, reviewer or approver;
no nested agent. Status: **author-returned, unreviewed**; no verdict or acceptance is claimed. The
session ID is not visible to the author; Root records it from its dispatch.

## Checkout

Worktree `D:/GitHub/arce-v180-claude-equipment-witness05-20261008`, branch
`codex/v1.8.0-claude-equipment-witness05-20261008`. HEAD `0c6bc521` and a clean status come from the
session start-up context only; no Git command was run. No hash was computed, including REPORT-01's
stated SHA-256.

## Actions

Two NEW files, exactly the TASK-05 write_scope, each written once with the Write tool:

- `WITNESS-CORRECTION-05.md`: C4-M1 clauses (affected stacks, I-1..I-3 capture, step 2 binding,
  bounded step 6 comparison, SQ-3 extended, new SQ-4, limits) and C4-L1 clauses (phases P/F/C/N,
  callback-free F-C interval, persistence mark in C, COMMITTED result for N faults, SQ-2 restated);
  planned observations BT10, BT14, BT27a-d, BT28, BT35, BT36.
- `WITNESS-HANDOFF-05.md` (this file).

Bytes were not measured (no command). Author estimate: about 7.5 KB and 3.5 KB, under the 8 KiB and
4 KiB caps; the correction margin is small, so Root should measure. Nothing was written to the
evidence leaf or anywhere else.

## Reads (Read tool, full files, all before the first Write)

Main checkout: AGENTS.md, PROJECT-CONFIG.md, PRODUCT.md, docs/01, docs/04, V1.8.0 and V1.0.0 version
files, docs/05, docs/06, docs/16, docs/17, docs/14, ADR-060. TASK-05 in the draft-revisions worktree.
This worktree: COMMIT-PROPOSAL-04, COMMIT-HANDOFF-04, BOUNDS-PROPOSAL-03, BOUNDS-TEST-DESIGN-03,
CONTRACT-02, TEST-DESIGN-02, OXYGEN-WITNESS-CLARIFICATION-01. REPORT-01 in the reviewer leaf. Outside
the required list: the author's own memory note on the v1.8 worker protocol. Locating: Glob of the
equipment directory and of the OXYGEN-WITNESS name; no Grep. Not opened: any source, mapped
Minecraft/Forge source, cache, JAR, upstream file, reserve proposals, REVIEW-02/03, BOUNDS-HANDOFF-03.

## Not done or open

- No quarantine, truncation, save veto, R-021, bound/schema/numeric/config change or native-path
  admission. Retention, destruction (B§5.5), alias ownership (RP-6), SQ-1..SQ-4, WIT-OPEN-1/2,
  workstation implementation and the other whole-contract findings stay open.
- Platform behaviour used as motivation (a dirty route that notifies neighbours, capability
  serialization) is author knowledge, not inspected source; it is stated only as SQ dependencies.

## Commands and tests

Tools: Read, Glob, Write. No shell, Git, Python, JVM, Gradle, runtime, network, install, credential,
cache, archive, hash or seal command; no agent started. Every BT case, including BT27a-d, BT35 and
BT36, and every earlier case stays **UNVERIFIED**. `./gradlew clean build`, `./gradlew test`,
`./gradlew runData`, `git diff --exit-code`, `./gradlew runGameTestServer`, S1, S2, V1 and V2 were not
run: not waived, not NOT_APPLICABLE.

## Limits

- No clock tool: elapsed time against the 10-minute cap was not measured. Turns were not counted by a
  tool; Root has the session record.
- The client budget display showed about USD 1.54 spent after the correction was written, under the
  USD 3.00 cap.

## Gate and next step

No v1.8 G0-G9 Required Gate is satisfied. Next: a different-agent delta review of
WITNESS-CORRECTION-05 against REPORT-01, then Root disposition of SQ-2..SQ-4 and WIT-OPEN-1/2.

## Interest release

No process, helper, lock or background task is held. **All process, read, write, source, HEAD,
index, branch and worktree interests are explicitly released.** The author will not edit these files
again; corrections go to a new Root-registered task.

Handoff path: `D:/GitHub/arce-v180-claude-equipment-witness05-20261008/docs/work/v1.8.0-c18b-equipment/WITNESS-HANDOFF-05.md`
