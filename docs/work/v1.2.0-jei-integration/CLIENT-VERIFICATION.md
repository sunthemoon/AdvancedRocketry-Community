# V120-INT-02 — scoped JEI client inspection

```yaml
status: verified
date: 2026-09-26
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
uncommitted_worktree: true
artifact_sha256: 92821ec3273a7b87c2892003ca5321f8cf65105a41572a3f730c3582d67c2000
forge: 47.4.10
java: 17.0.7
jei: 15.56.0.205
jei_sha256: 6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c
renderer: NVIDIA GeForce RTX 3070 Laptop GPU, driver 566.36
resolution: 960x540
gui_scale: 2
language: en_us
authentication_tested: false
full_visual_gate_passed: false
```

## Method and actual session

A single native packaged client connected to a fresh, loopback-only packaged
server (`127.0.0.1:50945`). Both installed the exact artifact above; only the
client installed JEI. The existing isolated official runtime was hash-checked
and reused without reading a launcher account or modifying an existing world.
The session ran from 10:54:59 to 11:00:02 UTC, including startup, UI inspection
and normal shutdown. No reference-load, remote, authenticated, two-client,
multi-resolution or full visual campaign was run.

Only this session's owned GLFW window received input. Screenshots are native
Minecraft F2 captures, not reconstructed images or desktop-wide captures.
The server console placed two fresh controllers and moved the test player.
The controllers intentionally remained structurally incomplete: their menus
can still expose recipe help, so formation/production was not re-tested here.

The initial below-ground position was inside terrain. The unsuccessful dark
capture remains in the record; a bounded empty area and stone floor were then
created in the disposable world. This was fixture setup, not a machine fix.

## Observed behavior

| Check | Actual evidence |
|---|---|
| Optional registration | Final client registration logs Electrolyzer 1, Rolling 1, Precision 2; initial pre-world registration contains zeros and is not used as completion evidence |
| Rolling menu click | Clicking the actual progress region opens Rolling's JEI category; [menu](native/observations/rolling-menu-visible.png), [recipe page](native/observations/rolling-jei-from-menu.png) |
| Rolling amounts | 2 iron ingots + 100 mB water -> 8 iron bars; 100 t / 20 FE/t; [water tooltip](native/observations/rolling-fluid-tooltip.png) confirms 100/100 mB |
| Precision menu click | Its actual progress region opens the Precision category; [menu](native/observations/precision-menu.png), [two recipe layouts](native/observations/precision-jei-from-menu.png) |
| Precision two-input recipe | Slots 0/1 show 2 iron / 2 redstone; one Advanced Circuit and two redstone torches; 20 t / 40 FE/t |
| Precision five-input recipe | Slots 0–4 show iron 2, redstone 2, gold 1, quartz 1, copper 1; one comparator; 30 t / 50 FE/t |
| Catalysts | The category catalyst items and tooltips identify [Rolling Machine](native/observations/rolling-catalyst-tooltip.png) and [Precision Assembler](native/observations/precision-catalyst-tooltip.png) |
| Output identity | Native tooltips identify [Redstone Comparator](native/observations/precision-comparator-tooltip.png) and [Advanced Circuit](native/observations/precision-circuit-tooltip.png) |
| JEI-absent server | Server mod inventory contains only ARCE; it starts, accepts the client, saves and stops normally |
| Cleanup | Client PID 14752 and server PID 21648 both exit 0; no retained owned process; driver reports no action or cleanup error and did not reach its loop deadline |

Independent review inspected the source, screenshots, logs and hashes and
found no blocking issue for INT-02's specified default-recipe/menu scope.
All 11 PNG hashes and sizes match their receipt records and actual native
client screenshot files. This is a scoped UI observation on a real GPU, not
completion of the version's V1 or V2 requirements.

## Warnings and unverified boundaries

Client and server full logs contain **zero ERROR/FATAL lines**. They retain
fresh-config corrections and standard Forge library/asset/sound/shader
warnings. The local server's offline warning is expected for this isolated
fixture. One movement warning follows the large console teleport.

JEI initially warns that the unknown server does not supply JEI recipes, then
stops and initializes again; the second registration contains the nonzero
machine counts. Both sides use the same artifact and no custom recipe pack.
This run therefore does not establish server-only datapack, recipe reload or
cross-version behavior, and the warning is not removed from evidence.

At this size Precision's category title uses an ellipsis. Advanced Circuit's
tooltip retains the old v0.1.0 development-component wording, and the atmosphere
HUD remains visible behind the upper-right JEI area. These are non-blocking
presentation observations, not a blanket claim that all GUI layout is correct.
Other scales/languages, full machine production and the complete visual matrix
remain outside this check.

## Driver review and reproducibility

The command run was:

```powershell
python -B -u docs/work/v1.2.0-jei-integration/native/session.py
```

The exact executed driver is preserved as
[`session-executed.py`](native/observations/session-executed.py) (8,727 bytes).
Its command receipts are in `actions-0001.json` through `actions-0006.json`;
the separately issued owned-window mouse moves have `external-*.json`
receipts, including each resulting F2 file hash.

Review found two harness defects: an archive error could interrupt later
process cleanup, and deadline/stop checks were only outside the queued-file
loop. The maintained [driver](native/session.py) now catches each archive
error and checks stop/deadline between files and actions. These changes were
made **after** this successful session: syntax is checked, but failure-path
runtime coverage is not claimed. The original session's normal exits do not
prove those branches. Action-level operations keep their existing individual
timeouts; the loop deadline is not a whole-process hard kill deadline.

## Evidence

Post-session `gradlew.bat clean build test runData runGameTestServer --offline
--no-daemon` passed in 2m27s. Compilation and the 123-suite/600-test JUnit result
were restored from Gradle cache; all 121 Required GameTests actually ran in a
fresh world and passed. DataGen wrote 0 files. Rebuilt JAR SHA-256 is identical
to the inspected artifact. `validate_v120_machine_resources.py` passes for all
9 blocks; `git diff --check` passes. Full/generated-only `git diff --exit-code
--stat` both return 1 for the existing uncommitted work and two generated
language additions, not a clean-tree Gate pass.

- [Session summary](native/observations/summary.json), [native audit](native/observations/audit.json)
- [Build/GameTest log](native/observations/clean-build-gametest.txt), [cached JUnit suite summary](native/observations/junit-summary.json)
- [Client full log](native/observations/client-full.txt), [server full log](native/observations/server-full.txt)
- [Environment F3 capture](native/observations/environment.png)
- [SHA-256 manifest](native/observations/SHA256SUMS)

Short build/resource check results are recorded in the
[implementation log](../v1.2.0-implementation-log.md). No schema, recipe,
production Java, public protocol, asset provenance or release Gate changed.
