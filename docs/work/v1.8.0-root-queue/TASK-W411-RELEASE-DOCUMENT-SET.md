# TASK W411: draft the v1.8.0 release document set as of candidate C5, with every open item explicit

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w411`, branch `codex/v1.8.0-w411` from the shared branch |
| Decision | Root decision R559 (QUEUE row W411) |
| Sources | [roadmap](../../16-POST-1.0-VERSION-ROADMAP.md) section 8 (the required files); [release gates](../../06-RELEASE-AND-ACCEPTANCE-GATES.md); [version document](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) (its Required Gates and sections 10 to 13); [master test plan](../../05-MASTER-TEST-PLAN.md); the v1.7.0 set in `docs/releases/v1.7.0/` as the model (RELEASE-EVIDENCE, GATE-STATUS, REQUIREMENT-MAP, PERFORMANCE, RECOVERY-MATRIX); the existing `docs/releases/v1.8.0/` files; [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) (inventory and evidence layout), [C5-SWITCH-REPORT](records/C5-SWITCH-REPORT.md), [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md), [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md), [W402](records/W402.md) (access matrix), [CURRENT_VERSION](../../status/CURRENT_VERSION.md) |
| Outputs | New files in `docs/releases/v1.8.0/`: `RELEASE-EVIDENCE.md`, `GATE-STATUS.md`, `REQUIREMENT-MAP.md`, `TEST-REPORT.md`, `MANUAL-TEST.md`, `MIGRATION-REPORT.md`, `PERFORMANCE.md`, `SECURITY-REVIEW.md`, `RECOVERY-MATRIX.md`, `VISUAL-VALIDATION-REPORT.md`, `MULTIPLAYER-REPORT.md` and `videos-or-links.md`; a record `docs/work/v1.8.0-root-queue/records/W411.md` |
| Write scope | Those new files and the record. Do not change `KNOWN-ISSUES.md`, `checksums.txt`, `development-artifacts.json` or `evidence-index.json` (list what they would need in the record instead). No status file, QUEUE, ADR, register, other record, code or test. |
| Not allowed | Marking any Gate, requirement, version or release as passed (AGENTS.md sections 5 and 7). Copying a development or historical result as candidate evidence. Any JVM, server or client. |

## What to do

1. **REQUIREMENT-MAP.md first.** One row per requirement of the version document (each checklist item of its
   sections, and each Required Gate), with: the evidence that will prove it (a C5 native family, a hosted GameTest
   class, a campaign checklist row with its ID, an owner decision, or a document), the evidence's current state
   (C5 hosted run passed; native PENDING on C5; campaign PENDING; owner decision PENDING; NOT_COVERED with its accepted
   reason), and a link to the record that says so. At the end, list every requirement that has **no planned
   evidence at all**: these are gaps Root must close.
2. **GATE-STATUS.md**: each Required Gate with status IN PROGRESS or BLOCKED (never PASSED), what it waits for, and
   the owner decisions it needs. The version's status stays as `CURRENT_VERSION.md` states it.
3. **TEST-REPORT.md**: the automated evidence that exists for C5 now (hosted run 44681973284: build, unit-test count,
   1,867 GameTests by shard; governance), and the native and campaign evidence still PENDING, by family and row.
4. **The other reports** (MANUAL-TEST, MIGRATION-REPORT, PERFORMANCE, SECURITY-REVIEW, RECOVERY-MATRIX,
   VISUAL-VALIDATION-REPORT, MULTIPLAYER-REPORT, videos-or-links): the structure the gates document and the campaign
   plan's section 6 ask for, filled with what is already decided (procedures, budgets from docs/17, the access
   matrix, the known recovery rules) and PENDING cells for every result. SECURITY-REVIEW summarizes the client-to-server
   policy from W402's access matrix and the decisions on its findings (R546), with the remaining review step named.
5. **RELEASE-EVIDENCE.md**: the index of the set, the candidate identity (C5) and what must be complete before a tag
   (AGENTS.md section 5: the tag only after this file is complete), with every missing piece listed.
6. Run `python -B scripts/validate_repository.py`; it must end with `0 failed` (if the validator has rules for release
   folders, follow them; if a rule demands evidence that does not exist yet, say so in the record and do not add the
   file it would fail on). Link only to tracked files on the shared branch; use a code span for anything else.
7. The record lists the files, the gaps found in step 1, and what each existing v1.8.0 file (KNOWN-ISSUES,
   checksums, the two JSON files) would need at release.

## Rules

- Commit on the branch in one commit (or one per file group) in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
