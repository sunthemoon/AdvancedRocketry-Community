# TASK W65: a bytecode sidedness scan of the shipped jar (W62 slice S3)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w65-sidedness-scan` (from the shared branch) |
| Branch | `codex/v1.8.0-w65-sidedness-scan` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W65.md` (new) |
| Specification | `records/W62.md` section 7, row S3, and section 5 |

Write `scripts/check_client_bytecode.py`, an allowlist and unit tests, as W62 section 5 proposes.

- The scan reads the built jar's class files. It finds direct and transitive references from common
  or server code to `net.minecraft.client.*` or Forge client-only classes.
- It allows the reviewed `DistExecutor` and client-event sites through the allowlist, with a reason
  for each entry.
- It ignores data-only classes in client packages that never load client classes.
- Run it on a jar built from the shared branch, and record every hit and every allowlist entry.
- Write the workflow line for Root.

## Commands

- The new Python tests (`python -B -m unittest tests.<module>` from the repository root, with `TEMP`
  and `TMP` set to `C:/Users/Administrator/AppData/Local/Temp`).
- One real run as the slice says, with its output in `D:/GitHub/ARCE-Task-Evidence/root-queue/W65/`.
- At the end, delete your generated `build/gametest/world`, native copies and old jars with a plain
  `rm -rf`, and make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths;
  Root deletes them.

## Not in this task

- Workflows, `build.gradle`, status files, the ledger and ADRs (Root). If the slice needs a workflow
  line, write the exact line in your record; Root adds it.
- Any production behaviour change. If a test finds a production defect, stop and report it with the
  evidence. The exception is a fix of a few obvious lines: give it its own commit and say so.
