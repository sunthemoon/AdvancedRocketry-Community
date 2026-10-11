# Endgame configuration fixture isolation

2026-10-11. Root write scope: CommonConfig.java, SwitchOverrides.java, the
BlackHoleGenerator/Elevator/GravityField/LaserPhysical/Railgun GameTests,
SwitchOverridesTest.java and this task's new local records. Base is committed
fd88f71b5378296569d6bf0c24340e78429160bf in a separate fix worktree. Main owner
files, current status, recipes/assets, log policy, build/CI and other tests are
not part of this implementation write scope.

The base actually ran clean build and deterministic DataGen and then completed
619 required native tests. Its separate strict checker exited 1, reporting one
undeclared config-correction WARN; native stderr retains a watcher TOML parsing
failure for duplicate [endgame.railgun]. The sealed prior results are unchanged.
These observations do not prove the historical storage CME cause or an exact
config object/thread mutation history. Source fixtures still call persistent
BooleanValue.set while Forge monitors that same COMMON file.

Use the existing ephemeral SwitchOverrides mechanism for the existing endgame
switches, preserving ordinary loaded/default config behavior without an active
override. Replace the five fixture classes' temporary COMMON writes with
in-memory set and release at their existing AfterBatch hooks. Preserve all
product permissions, settlement, persistence, native assertions, batch names,
timings and log expectations. Observe raw/effective gravity values separately.
No watcher disabling, config-reload suppression or failure whitelist is allowed.

Add regression coverage for defaults, both temporary values, replacement and
clear, settings-field isolation, and memory-only loaded config behavior if
supported by the actual Forge API. Independent source review must precede
committed-source clean build, DataGen and full native/checker qualification in
this fresh worktree. Preserve both original worlds, all actual failures and
original receipts. No world is cleaned or reused for a second native command.
This is a local fixture/runtime qualification slice, not full C16 or release
acceptance. Human artwork, player acquisition graph, restart/client, historical
repository/strict failures, original storage CME and all Gates remain open.
