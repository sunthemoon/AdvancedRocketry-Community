# TASK W424: package K01, the sources and API JARs of candidate C6 and the API fixture mod against it

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w424`, detached at candidate C6 `c8fa16e16be87554f26c402546733df6555d15e6` (create it with `git worktree add --detach`); your record goes on a branch `codex/v1.8.0-w424` made from the shared branch in a second worktree `D:/GitHub/arce-v180-w424-rec` |
| Decision | Root decision R583 (QUEUE row W424) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K01 and choice C1 (with Root's section 8); [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) section 2 and FI11; the [API fixture mod's README](../../../compat-test-mod/README.md); `build.gradle` (the sources and API JAR tasks); QUEUE row "C6 run" (R581) for C6's identity |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/candidate/`: the locally built sources JAR, API JAR and release JAR from C6's commit, the independent fixture binary built as the README says against C6, their SHA-256 in `SHA256SUMS.txt` and an `IDENTITY.json`; the record `docs/work/v1.8.0-root-queue/records/W424.md` on your record branch |
| Lock | Candidate C6's native campaign holds the heavy-JVM lock and yields it for 120 seconds after each family. A Gradle build is heavy: take the lock with the WORKER-RULES loop (poll every 60 seconds, wait at most three hours), hold it only while Gradle runs, release it at once. Never break a lock you do not own. |
| Not allowed | Changing any tracked file in the candidate worktree. Publishing anything. Any server or client start. |

## What to do

1. In the detached candidate worktree, with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` and under the lock, build the
   release JAR, the sources JAR and the API JAR with the project's own tasks, and the API fixture mod as its README
   says (against C6's API JAR or local Maven publication). Record each command, its duration and exit code.
2. Compare the locally built release JAR's SHA-256 with C6's hosted JAR
   (`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`): equal or different, and if different, say
   which entries differ (timestamps, manifest, class bytes). The hosted JAR stays the tested artifact either way (C1).
3. Copy the four artifacts to the evidence folder with their hashes and an `IDENTITY.json` (commit, build commands,
   Java version, hosted JAR hash, local hashes).
4. Run `python -B scripts/validate_repository.py` in the record worktree; it must end with `0 failed`. Remove the
   candidate worktree's build output only after copying the artifacts; never remove the evidence.

## Rules

- Commit the record on your record branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
