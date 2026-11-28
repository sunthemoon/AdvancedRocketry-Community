# TASK W510: review W509's recaptures independently, above all J19's server readback

Root: Claude. Date: 2026-11-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w510`, branch `codex/v1.8.0-w510` from the shared branch |
| Decision | Root decision R734 (QUEUE rows W509 and W510), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Under review | The package `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/authoring-w509/` (read only), identified by its `delivery-inputs.json`, SHA-256 `b7bfa785...`, and [W509](records/W509.md). Its notes are in `authoring-w509/w509/`: `OBSERVATION-DESIGN.md`, `recaptures.json`, `j19-frame-map.json`, `estimates.json` and `changed-existing-files.json` |
| Sources | [TASK W509](TASK-W509-RECAPTURE-AUTHORING.md), [W505](records/W505.md), the checklists `records/CAMPAIGN-CHECKLISTS-*.md`, round 08's inputs under `phase2/authoring-w500/round-08/` (read only), from which W509 copied, and C6's source under `src/` |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W510.md` only |
| Time | Deliver by **14:00** local time |

## Why

A Codex session authored four recaptures for a later round. Three reuse the harness's existing actions. The fourth,
J19's readback, adds a new instrument: a read-only Java debugger client attached to the disposable test server
through a JDWP agent on a loopback port, which reads the controller's live menu data. Nothing like it has run in the
campaign. Review it independently before Root schedules it.

## What to check

1. **J19's observer.**
   - Does the wire allowlist allow only reads? Name any JDWP command it permits that could write, invoke a method,
     suspend a thread, load a class or change the debuggee, with file and line.
   - Is the agent bound to loopback on an ephemeral port, started only for the owned disposable server and gone when
     that server stops? Could the agent's flags change the server's behaviour beyond attaching a debugger?
   - Do the values it reads match what C6's `ResearchControllerMenu.refresh()` puts in the menu data, slot by slot?
   - Could a simpler server-side readback answer J19's oracle, for example `data get block` on the controller or an
     existing campaign command? Say which of the menu values C6 persists in the block entity's saved data and which
     it computes only in the menu. Recommend one approach, with its risk.
2. **A14, I04 and J18.** Each change keeps every original action, oracle, wait and timeout, and adds only what TASK
   W509 asks for. Check W509's changed-file list against round 08's copies.
3. **Tests.** Run the round's operator tests from W509's package and the harness tests
   `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` in your worktree, and say whether the new
   tests depend on the behaviour they claim to check.

## Outputs

The record lists:
- each finding, with file, line, a concrete case that goes wrong, and its severity;
- the recommended readback for J19, with reasons;
- the commands and their results;
- a verdict per item: schedule, schedule after named fixes, or replace.

## Rules

- Do not change the package under review or any file outside your record.
- Do not start Minecraft, Gradle, a JDWP client or any other JVM. Do not open a network port.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Never close, change or block a program you did not start.
- Commit the record on your branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
