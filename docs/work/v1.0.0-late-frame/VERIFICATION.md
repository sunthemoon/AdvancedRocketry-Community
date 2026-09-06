# v1.0.0 controlled late flight-console frame delivery

Date: 2026-09-06. Task: `V100-VISUAL-CONSOLE-LATE-FRAME`.
Result: **five scoped native handler checks pass** on the unchanged development
artifact. v1.0 remains **IN_PROGRESS**; this is not full network, V1/V2 or release
approval. No product Java, protocol, persistence, dependency or resource changed.

## Identity and scope

- Branch `codex/v1.0.0-stable-core`, base `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`;
  inherited uncommitted work is preserved. No commit, tag, push or release.
- JAR `advancedrocketry-community-1.20.1-1.0.0-dev.jar`, 1,255,571 bytes,
  SHA-256 `46cb577653981a50638ff3d2bc32d0407dc4adaa5d7092fe73e65ae82bcd229c`.
  Protocol 4, schema 2. All 738 previously inventoried source inputs are unchanged;
  [inventory](verification/source-inventory.json) and the rebuilt JAR are checked.
- Local Windows, Java 17.0.7, Forge 47.4.10, JEI absent, offline test profile
  `V100Visual1`, NVIDIA GeForce RTX 3070 Laptop GPU, native 1280x720 screenshots.
- New files are external verification harnesses, retained logs/screenshots and
  this report/audit. Canonical task, gap and current-version records are updated.
  No upstream code/assets, production test hooks, bytecode transformation,
  authentication configuration, remote host or long-load workload is involved.

## Why and how this exercises the real boundary

Source review found the existing handler resolves the current player and menu
when called, requires `RocketFlightMenu`, and compares both container and rocket
entity IDs. The channel registers this S2C consumer on the main thread. Existing
unit tests only check the ID predicate, not an actual closed/replaced menu.

An external JDK JDI probe attaches **only to the owned client's loopback debug
port**. A handler-entry breakpoint captures an actual decoded network frame and
its current menu on `Render thread`, then resumes normal ingress. Before testing,
the probe checks that native delivery has set `planReceived`.

It retains that real envelope's IDs, but deliberately constructs a **synthetic,
distinguishable payload**: Moon plan and bounded fuel quotes 111/222/333. This is
not a claim of replaying unchanged wire bytes. Distinct values/objects make an
incorrect write observable instead of allowing an equal-value no-op to look like
rejection. The driver uses native mouse/Escape input to close/reopen the menu and
a dedicated-server kick to remove the player. At client tick breakpoints it calls
the unchanged production handler on `Render thread`, checks object identity and
plan/quote/received-state fields, and resumes immediately.

The matching-ID positive control must apply both synthetic fields; it then
restores the original snapshot **through the same handler**, not by assigning
private fields. No synthetic quote is submitted as C2S authority or used to
launch. No server resource/world state is repaired to satisfy an assertion.

JDI requires invocation on an event-suspended thread; requests are disabled
before invocation and event sets resumed in `finally`. The probe releases retained
references and detaches on exit. These choices follow the
[JDK 17 ClassType invocation contract](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/ClassType.html)
and [EventSet resume contract](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jdi/com/sun/jdi/event/EventSet.html).
This is a debugger-assisted functional check, not timing/performance evidence.

## Actual native results

Successful attempt: **03:54:30–03:55:15 Asia/Shanghai**, about 45 seconds including
startup/cleanup. [Summary](verification/native-3/summary.json),
[probe output](verification/native-3/probe-full.txt),
[client](verification/native-3/client-full.txt),
[server](verification/native-3/server-full.txt).

Captured packet object 52, old menu 81, container 1, rocket entity ID 179.
Reopened menu object 7073 has container 2 and the same rocket 179. Its original
plan object 7074 and quote object 7075 survive all rejection cases and the
positive-control restoration.

| Check | Actual observation |
| --- | --- |
| Closed menu | Current menu is not a flight menu; delayed delivery neither reopens it nor mutates the retained old flight menu |
| Same rocket reopened | New container 2 rejects container 1's delayed payload; both old/current menu state remain unchanged |
| Wrong rocket | A frame with the current container 2 but rocket ID 180 is rejected; both snapshots remain unchanged |
| Matching IDs | Current container/rocket accepts both distinct plan and quotes; handler restores the exact original snapshot afterwards |
| Disconnected player | After an intentional kick, client player is null; delivery does not create a player/menu or modify the retained menu |

Native screenshots were inspected, not inferred from filenames:
[open](verification/native-3/01-open.png),
[closed](verification/native-3/02-closed.png),
[reopened](verification/native-3/03-reopened.png),
[intentional disconnection](verification/native-3/04-disconnected.png).
Reopened console shows FUELED, fuel 1000/1000 and Moon quote 372, not probe quotes.

Server PID 7400, client PID 17624 and external probe PID 19768 exit 0. Strict
existing client/server log scanning passes; native logs and screenshot hashes
are retained. The dedicated runtime loads a copy of the previously stopped
presentation fixture: same entity `b78255b4-1f3c-4180-b513-3018f388dc6a`, snapshot
`42b80f05279e11debb63897848889c462cf5fb42cd49e0d55fe9652b8419c912`, FUELED/1000.
No flight intent occurs. The same-JAR explicit post-cancel dedicated restart is
already bound in [the preceding report](../v1.0.0-console-presentation/VERIFICATION.md).
This new run is not another passenger/cargo recovery test.

## Commands and retained failures

| Command | Actual result |
| --- | --- |
| JDK `javac --add-modules jdk.jdi .../ArceLateFrameProbe.java` | Exit 0; final exact source archived with successful attempt |
| Python external `arce-late-frame-session.py`, attempt 1 | Exit 1 before launching Java: copying an entire configured server conflicts with exclusive startup identity file creation; [log](verification/driver.txt), [summary](verification/native/summary.json) |
| Same harness, attempt 2 | Exit 1 after actual native ingress capture: external debugger's constructor lookup included inherited constructors; probe exit 1, client/server exit 0; [log](verification/driver-2.txt), [probe](verification/native-2/probe-full.txt) |
| Same harness, attempt 3 | Exit 0, all five checks; [driver](verification/driver-3.txt) |
| `gradlew.bat clean build --no-daemon` | Exit 0, 17 s; [log](verification/clean-build.txt) |
| `gradlew.bat test runData --no-daemon` | Exit 0, 23 s; 75 archived XML suites, 391 tests, zero failures/errors/skips; [log](verification/test-data.txt) |
| `gradlew.bat runGameTestServer --no-daemon` | Exit 0, 1 m 36 s, all 44 required tests passed; [log](verification/gametest.txt), [raw native log](verification/gametest-native.log) |
| `git diff --exit-code -- src/generated` | Exit 0; [generated diff](verification/generated-diff.txt) |
| `git diff --check` | Exit 0; [whitespace check](verification/diff-check.txt) |
| `git diff --exit-code` | Exit 1, inherited implementation/documentation remains uncommitted; [worktree diff](verification/worktree-diff.txt) |
| `python docs/work/v1.0.0-late-frame/verification/audit.py` | See [actual scoped receipt](verification/audit-result.txt) |

Attempt 1 was corrected by copying a pristine runtime and only the stopped world
and operator list. Attempt 2 was corrected by requiring each reflected method
to be declared by the requested class. No game assertion, timeout or log filter
was relaxed. All attempted runs remain separate; terminal process results were
confirmed before fresh attempts. Native clients in attempts 2 and 3 join without
manual reconnect. This does **not** resolve the earlier handshake timeouts.

New tests are five external native-handler scenarios; no new JUnit/GameTest
method or product code was needed. The archived driver/probe/helper sources
describe the exact disposable paths, fixture dependencies, input, assertion and
cleanup operations. They are internal evidence tools, not release JAR contents.

## Limits and remaining acceptance

- Tests call the handler with a controlled frame after actual menu lifecycle
  changes. They do not delay bytes inside Netty, reorder TCP, exercise container
  ID wraparound, or reuse both IDs after a new connection.
- One real client is sufficient for this receive-boundary check, not V2 passenger
  acceptance. No whole-version visual/network/security approval is inferred.
- Previously observed initial handshake failures remain unresolved. No long
  historical Python suites, load testing or online-authentication setup repeated.
- **All v1.0 Required Gates are not yet satisfied.** Frozen candidate identity,
  broader native gameplay, remaining acceptance and human release approval remain.
  Continue with native owner/passenger transfer and reconnection within v1.0.
