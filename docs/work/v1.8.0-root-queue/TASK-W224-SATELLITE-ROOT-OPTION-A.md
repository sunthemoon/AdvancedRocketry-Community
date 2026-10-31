# TASK W224: refuse a satellite registry root that cannot be saved again (D15, option A)

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w224` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w224` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W224.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W224/` |
| Decision | D15 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): "A：拒绝启动并给出具体诊断（推荐）" |
| Design | `docs/work/v1.8.0-root-queue/records/W173-DESIGN.md` (sections 1 to 4; option A is section 3.A) |
| Background | R-030 in `docs/11-RISK-REGISTER.md`; ADR-050 and ADR-074; `records/W177.md` and `W180.md` (identifier bounds already enforced); `records/W216.md` (exact resident sizes); AGENTS.md section 3.3 (a mechanism that refuses to start or save needs its risk entry and ADR note first) |

**Root decision R214.** The owner chose W173's option A: when a loaded satellite registry root can be decoded but its
canonical re-encoding no longer fits the bounds, the world refuses to start with one bounded diagnostic naming the
record, and the original file is kept for offline repair. Today such a world loads and its satellite changes are lost
at the next restart. This is a persistence slice with a start refusal.

## What to do

1. **Design note first, committed before code** (one page), building on W173: the exact check (re-encode the decoded
   root with the canonical encoder and the existing root and section bounds, at load, once), where it runs in the
   startup order and on which thread, the diagnostic (one bounded log line naming the record kind and ID and the bound
   it exceeds; no unbounded output), what the player and the server operator see, that the original file is never
   rewritten or moved, the recovery (stop, back up, repair offline, restart), its cost at the bound (W173 measured about
   106 ms median; say whether W216's resident sizes can serve instead of a full re-encode without weakening the check),
   and what it does not cover (later lifecycle growth, unknown-key loss, as W173 records). Then the exact R-030 and
   ADR-050 drafts for Root, outside the repository, in the format of
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W218/W218-drafts.md` with its audit script.
2. **Implement it** with tests that fail first: a root that decodes but re-encodes over the bound refuses to start
   with the diagnostic and leaves the file byte-identical; a root at the bound and a normal root load as before; a
   migration that produces such a root is caught the same way; the diagnostic stays bounded for a root with many such
   records.
3. **A native check** under the heavy-JVM lock on a JAR built from committed source: a dedicated server started on
   such a world refuses with the diagnostic and leaves the file unchanged; the repaired world starts; a normal world
   starts and restarts unchanged.

## Verification

The new tests alone, failing first; the satellite persistence and mission packages; the whole unit suite; the
satellite GameTest batches three times under the heavy-JVM lock; the native check. Root's hosted run on the pushed
branch stands in for a complete GameTest. An independent review follows before merge.

## Not in this task

Options B, C and E, lifecycle growth after load, other registries, budgets and bounds, status files and the ledger.
