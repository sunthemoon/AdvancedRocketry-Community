# TASK W429: locate or rebuild the historical v1.0 and v1.3 to v1.7 JARs for the fixture worlds

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w429`, branch `codex/v1.8.0-w429` from the shared branch |
| Decision | Root decision R593 (QUEUE row W429) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) packages K02 and K03 and its note that the v1.0 and v1.3 to v1.7 host JARs were not found; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) sections 3.1 to 3.4 (FI01 to FI09 and the hash lookup); the release records `docs/releases/v1.0.0/` to `docs/releases/v1.7.0/` (each version's evidence names its JAR hash where recorded); the git tags and the commits those records name; the evidence tree `D:/GitHub/ARCE-Task-Evidence/` |
| Outputs | Outside Git, `D:/GitHub/ARCE-Task-Evidence/final-campaign/historical-jars/` with one folder per version (the JAR, its SHA-256, the commit, and how it was obtained), and `SHA256SUMS.txt`; the record `docs/work/v1.8.0-root-queue/records/W429.md` |
| Lock | A rebuild is a Gradle build: take the heavy-JVM lock with the WORKER-RULES loop (C6's campaign yields it for 120 seconds after each family), hold it only while Gradle runs, in a detached worktree per version that you remove afterwards. |
| Not allowed | Moving or changing existing evidence. Tags, pushes or any change to the shared branch's tracked files other than your record. Starting a server or client. |

## What to do

1. For each of v1.0.0, v1.3.0, v1.4.0, v1.5.0, v1.6.0 and v1.7.0: find the JAR the version's own evidence names (the
   release evidence, the verification records, the hosted runs they cite and their artifacts if still downloadable
   with the repository's CI helpers, and the evidence tree). A found JAR counts only if its SHA-256 equals the hash the
   version's evidence records.
2. Where none is found, rebuild from the exact commit the version's evidence names (or its tag), in a detached
   worktree, with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`, and record that it is a rebuild, its hash, and whether
   it equals the recorded hash (it may not, because hosted builds used another JDK). A rebuilt JAR is labelled
   "rebuilt, not the released bytes" everywhere it is used (choice C7).
3. For each version, say which fixture world (FI02 to FI09) it builds or migrates, and whether its JAR is the
   recorded one or a rebuild.
4. Run `python -B scripts/validate_repository.py` in your worktree; it must end with `0 failed`. Link only to tracked
   files; use code spans for paths outside Git.

## Rules

- Commit the record on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
