# v1.8 implementer rules

These rules apply to every Codex implementer session that Root (Claude) dispatches
under [AGENTS.md section 13](../../../AGENTS.md). Your TASK file adds the specifics.
When the two disagree, the TASK wins.

## Before you start

- Read AGENTS.md sections 3, 5 and 13, your TASK file, and the ADR sections it names.
  Read other documents only when you need them. Do not read the 1,000-line status files
  in full.
- Work only in the worktree named in your TASK. Its branch already exists and is checked
  out. Do not touch the shared tree `D:/GitHub/AdvancedRocketry-Community` or any other
  worktree.
- Do not push, merge, rebase onto another branch, or create worktrees. Do not start other
  agents.

## How to work

- Implement the accepted ADRs directly. Do not write proposals, contracts, review records,
  or sealed evidence packets. Do not wait for a "frozen contract". When an ADR leaves
  something open, choose the most conservative reasonable behaviour, note it in your record,
  and carry on.
- Keep each commit to one logical change, with a message in the style of AGENTS.md
  section 6. Commit as you go. Root reviews the commits, not a final squash.
- Reuse existing code where it fits. Code that exists but is not registered (for example
  `machine/classic/adapter`) is optional. Use it if it helps, and do not rewrite it for its
  own sake.
- New art must be original, for example generated in code like the existing
  `datagen/V180*Art.java` providers. Never copy Minecraft, Mojang or Forge assets. Copy from
  the original Advanced Rocketry repository only when the ledger already lists the file.
- Central files: you may add your own entries to the registry classes (`registry/Mod*.java`),
  the language providers, and the data-generator entry `datagen/BootstrapDataGenerators.java`.
  Do not edit `docs/status/*`, the content ledger, `build.gradle`, `gradle.properties`,
  `.github/`, `AGENTS.md`, or another task's files.
- Hard rules (AGENTS.md section 5): never delete or weaken a test, an assertion, a timeout
  or a budget to make something pass. A failing test you cannot fix stays failing and goes
  in your record.

## Commands

Run Gradle with `--offline` from your worktree, using `gradlew.bat` in PowerShell.

- **Run freely:** `compileJava`, `test --tests <pattern>`, and other targeted unit tests.
- **Take the machine-wide lock first:** `runData`, `runGameTestServer` and a full `build`.
  These run one at a time on this machine:

  ```powershell
  $lock = 'D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm'
  while (-not (New-Item -ItemType Directory -Path $lock -ErrorAction SilentlyContinue)) { Start-Sleep -Seconds 60 }
  try {
    Set-Content "$lock\owner.txt" "<your TASK id> $(Get-Date -Format o)"
    .\gradlew.bat --offline runGameTestServer --console=plain
  } finally {
    Remove-Item $lock -Recurse -Force
  }
  ```

  Never remove a lock that is not yours. If the lock is older than three hours, stop and
  say so in your record instead of breaking it.
- **runData:** run it once, under the lock, whenever you change data generation. Commit its
  output together with the generator change.
- **GameTest:** this runs every test (about 640). Run it once at the end, under the lock.
  Then run `python -B scripts/check_gametest_log.py build/gametest/logs/latest.log`.
  - If you add a test that deliberately logs ERROR or WARN, add an expectation to
    `scripts/gametest_expected_log.json`, with the test and the reason.
  - If the checker fails on an entry that has nothing to do with your work, record it and
    do not chase it.

## When you finish

1. Write `docs/work/v1.8.0-root-queue/records/<TASK id>.md`, in full sentences and short.
   It covers:
   - what was done, with commit SHAs;
   - files changed by area;
   - each command you ran and its result (pass/fail counts);
   - the ADR choices you made where the ADR was open;
   - what is not done;
   - known risks.
2. Commit the record.
3. Make the last line of your final answer the record path.

If you are blocked before finishing, still write and commit the record, saying what blocks
you.
