# v1.0.0 packaged Forge/JEI development verification

Date: 2026-09-05. Branch: `codex/v1.0.0-stable-core`.
Base: `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`; uncommitted development tree.
The matrix execution is verified; v1.0 and G6 remain **IN_PROGRESS**. This is
not a frozen RC, an independent review or human release approval.

## Exact artifact and scope

`advancedrocketry-community-1.20.1-1.0.0-dev.jar`: 1,238,476 bytes, 766 entries,
SHA-256 `cf077ef750149f6b957ac4e8dc8a7da8e5cb91eafbe80703859157aac34a49b8`.
Both Forge builds reproduce these exact bytes. No mod Java, schema, protocol,
ID, resource or gameplay behavior changed in this slice.

The new tools launch production `forgeclient`, not ForgeGradle userdev or
source directories. Every client and server receives a hash-checked copy of
the same packaged mod. Java/userdev environment injection is rejected. Clients
use fresh, isolated game directories; only their owned visible GLFW window
receives a close message. The private servers bind to loopback, use offline
mode and do not read an existing launcher account or credential. Prior PCL
login reported by the owner is not retested or reclassified as candidate proof.

The two dedicated-server lanes do **not** install JEI; present/absent refers to
the optional client dependency. The matrix therefore also exercises client JEI
with a JEI-free dedicated server. It does not claim server-side JEI installation
or mixed-Forge client/server combinations.

## Final matrix results

Two complete serial runs use separate fresh worlds and clients:
[attempt 7](verification/attempt-7/summary.json) and
[attempt 8](verification/attempt-8/summary.json).

| Client / matching dedicated Forge | Client JEI | Each run |
|---|---|---|
| 47.4.10 | 15.56.0.205 | Native modded connection, server join/leave, one synchronized ARCE recipe, clean client exit |
| 47.4.10 | absent | Native modded connection, server join/leave, explicit JEI absence, clean client exit |
| 47.4.23 | 15.56.0.205 | Native modded connection, server join/leave, one synchronized ARCE recipe, clean client exit |
| 47.4.23 | absent | Native modded connection, server join/leave, explicit JEI absence, clean client exit |

Across these two final runs: eight real client processes and eight dedicated
processes, all exit 0. Each server saves with `save-all flush`, stops and opens
the same world for a clean restart. All final console logs have zero
ERROR/FATAL and zero client linkage or unknown-recipe-category findings. The
checker requires the JEI recipe receipt after the native connection receipt;
an absence cell does not invent a recipe-count observation.

Native OpenGL logs identify **NVIDIA GeForce RTX 3070 Laptop GPU**, OpenGL
4.6.0 / NVIDIA 566.36. This is hardware-rendered connection evidence, not an
inspection of GUI content, rocket sizes, sky, FPS or multiplayer permissions.
The host reports AMD Ryzen 5 5500 (6 cores / 12 logical processors), about
32 GiB RAM, Windows 11 and Java 17.0.7. It is not the four-vCPU/eight-GiB
reference-load environment. Test clients use 960x540, GUI scale 2, render
distance 4, simulation distance 5 and an FPS cap of 60; no FPS claim is made.

Fresh Forge default-config warnings, vanilla sound/shader warnings and JEI's
warning that the server does not provide JEI recipes remain in the native logs.
JEI subsequently observes the synchronized ARCE recipe. These observations are
not substituted for the actual per-run warning counts.

## Retained failures and unresolved risk

All attempts retain the source actually executed, console output and summary.
Native final logs from attempts 1-6 were additionally copied after termination;
attempts 7-8 automatically retain native logs for every process/cycle.

| Attempt | Actual result and disposition |
|---|---|
| [1](verification/attempt-1/summary.json) | Harness expected an incomplete Forge log line. Native output includes the fixed MCP version. The checker now requires that complete line; no timeout increase. |
| [2](verification/attempt-2/summary.json) | Harness rejected the official LWJGL `windows/x64/org/lwjgl/...` DLL entry layout before launching a client. Extraction now recognizes only this bounded x64 layout or a bare DLL name and still rejects traversal/other layouts. |
| [3](verification/attempt-3/summary.json) | Inherited vanilla JAR filename did not match the official Forge `ignoreList`, causing a duplicate Minecraft JPMS module. The verified inherited bytes now use the selected version's filename. |
| [4](verification/attempt-4/summary.json) | Client joined and synchronized JEI; close selection counted both the hidden GLFW message window and the visible game window. Selection now requires the owned PID and one visible GLFW window. |
| [5](verification/attempt-5/summary.json) | Four cells and clean lifecycles completed, but the harness set client simulation distance to 4, below the native minimum of 5. The final setting is 5; the checker was strengthened to reject external as well as project ERROR/FATAL. This preliminary result is not the final zero-error matrix. |
| [6](verification/attempt-6/summary.json) | Baseline cells passed; the 47.4.23 + JEI client timed out during login. Server debug output sent the 22 Forge handshake messages; the client recorded the initial connection but no corresponding modded-connection receipt. The disconnect occurred before ARCE recipe synchronization. |
| [7](verification/attempt-7/summary.json), [8](verification/attempt-8/summary.json) | Complete four-cell, zero-ERROR/FATAL serial runs with unchanged timeouts and exact same JAR. |

**Attempt 6 is unresolved.** A Gradle invocation overlapped that attempt, but
there is no evidence establishing it as the cause. Subsequent passes do not
erase the failure or establish that it cannot recur. The open handshake task
requires connection-stage diagnostics/repetition during the remaining v1.0
client/load work; no speculative mod change or timeout relaxation was made.

The first unquoted PowerShell `-Pforge_version=47.4.23` build invocation was
parsed as version `47` and failed configuration. Its log is retained; quoting
the whole property argument selects 47.4.23 and the build/tests pass.

The standalone bootstrap provenance CLI also failed on v1.0: its automatic
historical selector matched only later **v0.x** versions and therefore compared
the new tree to v0.0.2 imported targets. The global repository validator already
used the accepted historical commit. A narrow dispatch correction now selects
that same commit for later major versions. Five new regressions fail before the
fix and pass after it; the actual `--require-approved-review` command passes.
Bootstrap versions, explicit commit overrides and pending/approval-digest guards
remain tested. No historical file, hash, review digest or approval is modified.
At the initial matrix checkpoint the broader 95-case bootstrap suite was still
running, and only 136 distinct targeted cases had completed (101 server harness
+ 35 compatibility/dispatch cases). Its subsequent successful completion is
recorded below and in the updated checkpoint status; no running log is treated
as a final result.

## Runtime provenance and verification

Official installer SHA-1 values are checked against the
[Forge download index](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html):
47.4.10 `66bfea9963bfa60d88bab6b2750e74a958392715`, 47.4.23
`ed31ce02ac69176f34353235cb2508d5a0f1e088`. The selected Minecraft 1.20.1
metadata SHA-1 is `19f5ae58f9c31bd3b0923cb822e99e3162bd62ab`; JEI's published
Maven SHA-1 is `7f64b7f8fde7f001ef054dabe3618a8780ba2650`.

[Runtime inventory](verification/preparation/runtime-inventory.json) records
372 installed runtime files; all 3,598 asset-index records were rechecked by
size/hash. Downloaded game, Forge and JEI files stay outside the repository;
only metadata and compressed installer logs are included here. Preparation
executed the archived `executed-runtime-r1.py`; later launcher/profile checks
executed the helper archived separately with each matrix. The final tightened
preparation entry point was not rerun as a new installation, and that distinction
is not hidden by replacing its executed source.

[Matrix-time source inventory](verification/source-inventory.json): 721 inputs,
all 718 previous runtime/test/harness inputs unchanged during the matrix. The
three additions are the runtime helper, matrix runner and Python tests. The
later CLI correction and its test are identified separately in the
[final source inventory](verification/final-source-inventory.json), rather than
rewriting the executed matrix snapshot. The artifact audit and content manifest
validate the unchanged JAR, including notices and credential scans.

## Tests and commands

See [exact commands](COMMANDS.md) and [raw verification logs](verification/checks).
Both Forge lanes pass `clean build`, 362 Java tests / 70 suites, and all 44
required GameTests. Baseline Java results are cache-restored; the new 47.4.23
build executes all 12 tasks. DataGen on both lanes leaves generated resources
unchanged. A final default-lane `clean build` restores the ordinary development
outputs and again produces the same JAR.

The new compatibility module has 27 tests, including owned-window selection,
native-layout/hash checks, account-free argument construction, metadata bounds,
log authenticity/order and blocking external errors. Its final 30-case run also
includes the three unchanged historical compatibility-collector tests. All 101
existing server harness cases pass: 131 distinct targeted Python cases in total.
Strict repository validation passes 45 checks; planning, artifact, common/client
and celestial-boundary checks pass. Full expanded Python-suite execution is not claimed;
the earlier 735-test full run remains a separate development slice.

The previously running extended bootstrap suite subsequently completed: all
95 cases pass in 2664.700 seconds, exit 0. The [complete log](verification/checks/bootstrap-full-tests.txt)
is retained. Five routing tests overlap the prior 35-case run, so this adds 90
distinct cases to the earlier 136 targeted total (226 total). This is completion
of the bootstrap module and routing regressions, not a new full-repository
Python-suite result. The matrix-time and final source inventories above remain
unchanged; later CI wiring is a separate development slice.

## Modified files and remaining work

- `scripts/v100_compatibility_runtime.py`, `scripts/run_v100_compatibility_matrix.py`
- `tests/test_v100_compatibility_matrix.py`
- `scripts/validate_bootstrap_provenance.py`, `tests/test_bootstrap_version_dispatch.py`
- This report, command record and generated verification bundle
- Active implementation log, stabilization gap audit, current/Gate status and
  v1.0 provenance record; prior release evidence is untouched

v1.0 still needs current-artifact CI binding, the unresolved handshake review,
four-hour representative load, actual core GUI/rocket/sky and two-player
behavior acceptance, frozen-candidate reruns, independent review, an uninvolved
installation test and human release approval. No commit, push, tag, publication,
historical Gate rewrite or v1.1+ feature is performed by this slice. Temporary
Git histories created by provenance unit tests are not project commits.
