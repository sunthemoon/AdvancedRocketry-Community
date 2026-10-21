# v1.8 implementer rules

These rules apply to every implementer session that Root (Claude) dispatches under
[AGENTS.md section 13](../../../AGENTS.md), whether it is a Codex session (`codex exec`)
or a headless Claude session (`claude -p`). Your TASK file adds the specifics. When the
two disagree, the TASK wins. A Claude implementer does not use subagents, workflows or
any other agent.

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
- Historical outputs are immutable. Never edit a file under an older `src/generated/v*` root (anything
  but `src/generated/v1.8`), and never edit a sealed provenance or release-evidence record. To change
  a historical model or resource, write a v1.8 copy from a v1.8 provider and ask Root for the
  `build.gradle` exclusion of the old copy, as W52 did (W49F, 2026-10-14).
- Links in Markdown: link only to files committed in this repository, with relative paths. Write
  anything else (`D:/GitHub/ARCE-Task-Evidence/...`, a file you do not commit, a ZIP you keep
  outside Git) as a code span, and give an artifact's SHA-256 next to it. The strict validator
  rejects absolute and missing link targets (W58, 2026-10-15). Imported upstream assets go
  under `src/imported/v1.8/resources`, never `src/main/resources` (W52F).
- ADRs: never change a decision. The one exception is when AGENTS.md section 3.3
  requires you to document a protection that refuses saves. Then add a clearly labelled
  implementation note to the ADR and a risk-register entry. Put every other
  clarification in your record.
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
  $token = "<your TASK id> pid=$PID $([guid]::NewGuid())"
  # cmd's mkdir is atomic: exactly one caller creates the directory, every other gets a non-zero exit code.
  while ($true) { cmd /c mkdir "$lock" 2>$null; if ($LASTEXITCODE -eq 0) { break }; Start-Sleep -Seconds 60 }
  try {
    Set-Content "$lock\owner.txt" "$token $(Get-Date -Format o)"
    .\gradlew.bat --offline runGameTestServer --console=plain
  } finally {
    # Release only the lock this script took: its owner.txt must still start with this script's token.
    $owner = Get-Content "$lock\owner.txt" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($owner -and $owner.StartsWith($token)) { Remove-Item $lock -Recurse -Force }
  }
  ```

  Never remove a lock that is not yours. If the lock is older than three hours, stop and
  say so in your record instead of breaking it. (Changed by Root on 2026-10-22: W122 saw
  `New-Item -ItemType Directory` let two scripts take the lock in the same millisecond, and an
  unchecked `finally` remove another script's lock, so three heavy JVMs overlapped for about four
  minutes. Use the atomic `mkdir` and the owner check above.)

  A script that takes the lock several times in a row (for example one native case at a time) waits at least
  90 seconds after each release before it tries again, so a script that polls once a minute gets its turn.
  (Added by Root on 2026-10-22, after W132F waited behind W133's back-to-back native cases.)

  Release the lock only in the `finally` of the same script that took it, as above, and run
  that script as a detached process, not as a tool background job (the tool stops those after
  two hours). Never take the lock in one command and remove it in a later one: the tool has
  refused such separate removals (W98's and W109's locks, W106's and W109's GameTest worlds),
  and a left lock stalled every worker until Root removed it (about 80 minutes on 2026-10-20). Delete your
  `build/gametest/world` and export trees in the same script's `finally` for the same reason.
- **runData:** run it once, under the lock, whenever you change data generation. Commit its
  output together with the generator change.
- **Unit tests:** before your final record, run the whole unit suite once (`test`, with no
  `--tests` filter; no lock needed). Targeted runs miss shared checks such as the exact
  configuration count in `CommonConfigTest`.
- **Test isolation:** a new unit test class that touches Minecraft classes (`Level`,
  registries, items) calls `MinecraftBootstrap.initialize()` in a `@BeforeAll`, as the
  existing tests do. Run each new class alone once (`test --tests <Class>`). A class that
  passes only inside the full suite relies on test order.
- **New configuration keys:** a fresh configuration logs one "Incorrect key ... corrected
  from null" warning per new key and section. Give them their own exact expectation in
  `scripts/gametest_expected_log.json`, and exclude their section from the generic rule,
  whose maximum of 99 does not change (see the OXYGEN and ROCKET entries).
- **Pinned totals:**
  - If you add deliberate ERROR expectations to `scripts/gametest_expected_log.json`,
    update the total in `tests/test_check_gametest_log.py`
    (`test_repository_manifest_is_valid`), with a comment naming your additions. Then run
    `python -B -m unittest discover -s tests -p "test_*gametest_log*.py"`.
  - If you add a network channel, update `NetworkProtocolPinTest`'s channel-source count and
    `src/test/resources/network-protocols.txt`.
- **GameTest:** this runs every test (about 700). Run it once at the end, under the lock.
  Then run `python -B scripts/check_gametest_log.py build/gametest/logs/latest.log`.
  - If you add a test that deliberately logs ERROR or WARN, add an expectation to
    `scripts/gametest_expected_log.json`, with the test and the reason.
  - If the checker fails on an entry that has nothing to do with your work, record it and
    do not chase it.
- **No GameTest scheduling from inside a scheduled callback:**
  - Never call `helper.onEachTick`, `runAtTickTime`, `runAfterDelay` or `succeedWhen` from
    inside an `onEachTick` or `runAtTickTime` callback.
  - Why: vanilla iterates its fastutil schedule map while it runs those callbacks.
    `onEachTick` inserts one entry per remaining tick, which forces a rehash, and the server
    then crashes with `NullPointerException ... "this.wrapped" is null` in
    `GameTestInfo.tickInternal`. This happened in W21c's hosted run 38353212060. It can pass
    locally by luck.
  - Instead, register one `onEachTick` at test start, and move between steps with a mutable
    state variable.
- **Operator-only NBT:**
  - A block entity that stores an owner, an identity or a retained or guarded root must
    override `onlyOpCanSetNbt()` to return `true`.
  - Test it with an assertion like `ForceFieldGameTests` line 308.
  - Why: otherwise a creative player without operator rights can load any root through an
    item's `BlockEntityTag`. The W17 and W18 reviews both found this gap.
- **Background commands must end by themselves:**
  - Never start an interactive program: a bare `python` or `node` REPL, `tail -F`, or a
    watch loop.
  - Always pass `python` a script or `-c`, and give polling loops a time limit.
  - Why: on 2026-10-12 a worker's background `python` with no console became a REPL that
    printed the same traceback forever. It wrote 24.9 GB into the Claude temp folder and
    filled C:, and Gradle runs on C: then failed.
  - Before `runGameTestServer`, `runData` or a full `build`, check that C: and D: each have
    at least 10 GB free (AGENTS.md section 9). The Gradle home and the temp folders are on
    C:.
  - **A refused cleanup never stops your work.** If the tool policy refuses a delete
    (`rm -rf`, `Remove-Item`), write the exact paths into your record for Root and carry on.
    `runGameTestServer` deletes its own `build/gametest/world` before it starts; running it
    again is normal use, not a way around the refusal.

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
