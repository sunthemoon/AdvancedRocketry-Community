# TASK W484: the campaign harness must not take a command-error reply as the expected reply

Root: Claude. Date: 2026-11-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w484`, branch `codex/v1.8.0-w484` from the shared branch |
| Decision | Root decision R705 (QUEUE row W484), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Write scope | `scripts/campaign_client/session.py`, `scripts/campaign_client/processes.py` (only if the fix belongs there), new tests in `tests/test_campaign_client_session.py` or a new `tests/test_campaign_client_replies.py`, and the record `docs/work/v1.8.0-root-queue/records/W484.md` |
| Time | Deliver by 18:00 local time. Tonight's run starts at 23:30 and needs this change merged |

## What happened

In round 05, session `i07-s1-w479-w480-r2`, request `i07-s1-w479-w480-r2-s12-landing-pad-face-grid` ran:

- a `fill` into an area that was not loaded, which the server answered with `That position is not loaded`;
- then a `require` with the command `execute if block 700 152 250 advancedrocketrycommunity:landing_pad run say W476_PAD`
  and the expect text `W476_PAD`.

The server rejected that command and logged two lines:

```text
Unknown or incomplete command, see below for error
...y W476_PAD<--[HERE]
```

`Session.perform` (`scripts/campaign_client/session.py`, the `require` branch) waits for the first log line that
contains the expect text. The echo line contains it, so the check was recorded as MET although the command never
ran and no landing pad existed.

Root scanned all 1,560 `require` results in rounds 04 and 05, and this is the only one. Any `require`, `server` with
`wait` or `expect_log` whose expected text also appears in its own command can be matched the same way.

## What to do

1. **Change the reply matching** so that a line the server prints for a rejected command is never taken as the
   expected reply. Such lines include:
   - the `Unknown or incomplete command` line;
   - the `<--[HERE]` echo;
   - `Incorrect argument for command`.

   Find the exact forms in the Minecraft 1.20.1 command error output that the existing harness logs show.
2. **For `require`:**
   - a rejected command fails that attempt at once;
   - the next attempt runs as before;
   - the last attempt raises the existing `PreconditionFailed` with a message that names the rejection.
3. **For `server` with `wait` and for `expect_log`:** a rejection line never satisfies the wait.
4. **Keep the rest unchanged.** Do not change any timeout, attempt count, interval or other wait behaviour.
5. **Tests.** Add Linux-safe unit tests with a fake log source, covering:
   - the I07 lines above;
   - a normal `say` reply;
   - an expect text that appears both in a rejection echo and, later, in a real reply;
   - each op kind.
6. **Rescan the sealed rounds.** Write a read-only scan over rounds 01 to 05 under
   `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/` that lists every recorded MET or satisfied wait
   whose receipt is a rejection line. Report the list in the record. Do not change any round.
7. **Run:**
   - the new tests;
   - `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` (118 tests before this change);
   - `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tests must be Linux-safe.
- Do not start any Minecraft client, server, Gradle task or other JVM. No change under `src/` (frozen at C6, R600).
- Do not touch rounds 01 to 06 or `round-06/tools/`, which another session is editing.
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
