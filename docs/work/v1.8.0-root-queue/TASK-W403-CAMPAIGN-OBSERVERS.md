# TASK W403: the campaign's observers, evidence root and reference-node check (package K15)

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w403`, branch `codex/v1.8.0-w403` from the shared branch |
| Decision | Root decision R537 (QUEUE row W403) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md): package K15 in section 4.2, choices C4, C5 and C10 in section 3, Root's changes in section 8; [FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) section 6 (evidence layout); [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) sections 4 and 8; [docs/15](../../15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md) sections 15 and 18; the existing per-tick sampler in `satellite/command/ReleaseTestCommands.java` (it reads the server's last 100 tick times and reports p95, p99 and max) and the average in `endgame/command/ReferenceLoadReleaseTestCommands.java` |
| Outputs | A package `scripts/campaign_observe/` (with `__init__.py`), one entry script `scripts/run_campaign_observer.py`, a Bash script `scripts/campaign_observe/ref_acceptance.sh`, tests `tests/test_campaign_observe_*.py`, the record `docs/work/v1.8.0-root-queue/records/W403.md` |
| Write scope | Those files only. No product code (`src/`), no other script, test, record, ADR, register, status file or QUEUE. |
| Not allowed | Starting a Minecraft server or client, Gradle or GameTest: the heavy-JVM lock belongs to candidate C4's native runs. Attaching `jcmd` or JFR to any running JVM. Installing anything. |

## What to build

1. **Server tick sampler.** Find how the release-test commands are enabled on a packaged server and what each
   prints. Build a sampler that, given a server console channel (a callable that sends a command and returns the
   reply lines; W399's harness provides one, so take it as a parameter), asks for the last 100 tick times often
   enough that no tick is missed (every 100 ticks or sooner, with overlap removed by tick number if the output
   allows, otherwise state the limit), and writes raw samples to a CSV. From the samples it reports the tick count,
   mean, p95, p99 and max MSPT, and each run of consecutive ticks longer than a threshold given as an argument. It
   reports figures; the docs/17 budgets are printed beside them as the reference values, not applied as a pass or
   fail by the tool. If no existing command gives per-tick times without a product change, say so in the record,
   build the sampler on the average only, and mark p95 and p99 as not available; do not change product code.
2. **Process sampler.** For a given process ID: resident memory (Windows through the process memory counters via
   `ctypes`; Linux through `/proc/<pid>/status`), CPU time, and, only for a server JVM the campaign started, the
   heap after `jcmd <pid> GC.heap_info` at an interval. Samples go to a CSV with UTC times. Never call
   `VM.command_line`, `VM.system_properties` or any `jcmd` command that prints the JVM's arguments: a client's
   arguments hold its access token. Keep an allowlist of the `jcmd` commands the tool may run.
3. **JFR procedure.** Functions that start and stop a JFR recording on a server JVM the campaign started (the
   `jcmd` JFR commands with a bounded duration, size and an output path under the evidence root) and summarize a
   recording with the JDK's `jfr print --json` for GC pauses (count, total, max) and the Minecraft tick-time events
   if present. Test the summarizer on a small hand-made JSON file, not a real recording.
4. **Host record.** A function that writes the host record of docs/15 section 15 (provider and plan as given
   arguments; OS, vCPU, memory, swap, free disk measured; CPU steal measured on Linux from two `/proc/stat` readings,
   reported as not applicable on Windows) together with the run fields (commit, JAR SHA-256, Forge, Java, command,
   UTC start and end, exit code).
5. **Evidence root.** A subcommand that creates `D:/GitHub/ARCE-Task-Evidence/final-campaign/<short SHA>/` (the root
   and the short SHA are arguments) with the folders of the plan's section 6, writes `candidate/IDENTITY.json` from
   given values, and refuses when the root exists, unless `--check` is given, which only verifies the layout. It
   first checks at least 10 GiB free on each drive it names (AGENTS.md section 9) and refuses otherwise. A second
   subcommand writes `SHA256SUMS.txt` for a folder, and verifies one.
6. **Reference-node acceptance** (`ref_acceptance.sh`, run later on the Debian node by a normal user): it checks,
   read-only, each item of docs/15 section 18 that a script can check (not root; SSH key login is configured for the
   user; Java 17; swap of at least 8 GiB or a recorded OOM protection; tmux present; the linger setting; the evidence
   and backups folders; no VNC or RCON port listening on a public address), prints PASS, FAIL or MANUAL per item
   with the evidence line, and writes the host record of item 4. It installs and changes nothing. Items a script
   cannot prove (tmux detach and resume, the client tunnel, the build and GameTest runs, the packaged server's start
   and restart) are MANUAL with the command to run. Check it with `bash -n` from Git Bash in a test that is skipped
   when Bash is missing.
7. **Client observation steps.** A short section in the record, not code: how the campaign observes a client's heap
   and caches around a resource reload and a dimension change using only the allowed `jcmd` commands on a client
   the harness started, and why it does not attach to the owner's signed-in clients.

## Checks

- Unit tests for the parsing and the figures (tick output, heap output, `/proc` text, JFR JSON, percentiles on
  known samples, the threshold runs), the evidence root (create, refuse, check, free-space refusal with a fake disk
  reading), the checksum writer and verifier, and the `jcmd` allowlist refusing the forbidden commands.
- Run `python -B -m unittest discover -s tests -p "test_campaign_observe_*.py" -v` and
  `python -B scripts/validate_repository.py`, which must end with `0 failed`. Do not run the full Python suite.
- The record lists each output, how the tick sampler gets its data and its limit, the commands and results, and
  what is qualified only when it first runs on C4 (the sampler against a real server under the lock).

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
