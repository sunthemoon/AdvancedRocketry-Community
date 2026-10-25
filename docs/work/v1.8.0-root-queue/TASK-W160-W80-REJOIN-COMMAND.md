# TASK W160: W80-rejoin part 2, the `release-absent` command, the `rejoins` view and the combined matrix

Root: Claude. Date: 2026-10-25. Read these first:
- [WORKER-RULES](WORKER-RULES.md);
- the frozen design [records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3): sections 3, 6 and 7 and Appendix A,
  with the R106 and R108 corrections in section 4;
- the leaf records [records/W157.md](records/W157.md), [records/W158.md](records/W158.md) and
  [records/W159.md](records/W159.md);
- the W80 note in ADR-065 and the W80 part of R-035.

You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w160` (new; from the shared branch at the commit named in your prompt, which has W157 to W159) |
| Branch | `codex/v1.8.0-w160` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W160.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160/` |

## What to do

1. **The command.** `/arce rocket recover <transfer> release-absent`, as design section 3 describes:
   - Every refusal in section 3 refuses without changing anything.
   - Submitting the same pending transfer again reports its release status instead of adding a batch.
   - The command replies once W80 has admitted or refused the release.
   - It drives W158's admission, the same path as the gated internal entry point, and loads no chunk outside the
     server-recorded slots.
2. **The permission policy** from design section 7 (R8, N7, N11):
   - The leaf's `requires` needs level 3.
   - The handler also calls `source.hasPermission(3)` itself, before any read of the transfer or the sidecar. This
     execution-time check is mandatory.
   - Tick, load and scheduled functions and command blocks are refused at execution. Operators, functions an operator
     runs, the console and RCON are accepted.
   - Do not add any origin-provenance mechanism, reflection, stack inspection or command rewriting.
3. **The view.** The read-only `/arce rocket rejoins [player]` lists the pending release and placement states. It
   shows at most eight entries per response, caps UUID lists at 16, and writes nothing.
4. **Player-facing text** goes in `en_us` and `zh_cn`. New log lines get exact manifest entries.
5. **W159-R4 (Low, from the W159 re-review).** Add an exact entry for `ARCE_W159_SHUTDOWN_CLEAN` to the native log
   manifest. It must occur once for the H1 and H2 C20 seed runs and zero times elsewhere. Make the reusable log audit
   check the parsed message and its count, with tests for a missing, duplicate, nonzero and out-of-scope occurrence.

## Limits

- Legacy records only. Governed records stay refused.
- No Mixin, access transformer, reflection or offline player-data writer.
- No new saved format.
- No change to the journal root, the sidecar schema, bounds, budgets, timeouts or protocols.
- Each step in its own commit, with a test that fails before it.

## Verification

- **The command matrix** at compile level 4, from Appendix A:
  - players at levels 2, 3 and 4;
  - the console, RCON, command blocks and minecart command blocks;
  - tick, load and scheduled functions;
  - an advancement reward function and an operator's function;
  - nested `execute` for each of these.

  Automatic origins are refused at execution with no sidecar change. The operator's function is accepted.
- **Command GameTests** covering:
  - every section-3 refusal;
  - a repeated submission;
  - the view's limits;
  - a basic H1 two-rider release followed by rejoin;
  - one H2 release.
- **Combined native matrix.** From the campaign table in Appendix A, list which rows the W157, W158 and W159 evidence
  already covers. Run the remaining rows through the command, each with its counted starts, and give the mapping in
  your record. This includes the dedicated server with `function-permission-level=2`, which must reject a datapack
  function containing the command at load. Declare its `Failed to load function` ERROR line in that run's
  expected-log manifest only. Stop and report before running more than 60 counted starts.
- Each changed class alone, the whole unit suite, and one complete GameTest with the unchanged log checker.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W152G shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes. This is a protocol and admission slice, so a fresh Codex session reviews it before
merge.

## Merge order (Root)

W160 merges only after W152 (sealed staging) and a small cross-guard task (W162) are on the shared branch. W162 makes
release planning and its boundary refuse a rocket held by sealed staging. Do not implement that guard here.

## Not in this task

- The sealed cross-guard (W162).
- Governed records.
- Two-real-client evidence, which waits for the owner's final campaign.
- Power-loss durability.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.