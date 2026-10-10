# GameTest pacing: independent review and integration 01

Date: 2026-10-10. Integrator: Root Codex. Development verification only; no Gate
approval, content delivery, release tag or acceptance-cursor change.

## Owner request and exact identity

The owner asked in this Codex conversation on 2026-10-10:
"抽空审核下claude处理的问题，无误则合并下分支", supplying
`D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/REPORT.md`.
The request authorizes independent review and conditional integration of the repair
branch, not new production work or reuse of the retained client world.

- Branch: `fix/v1.8.0-gametest-tick-pacing`.
- Source: `988352bb2098f257d0fbfb83be1403798505f453`.
- Actual tested/reviewed candidate: `f9bdc8004384b0e4c5fa56a506403668afa739cd`.
- Candidate tree: `d3e8a547b726dd8b152b978b91e43005616436c7`.
- Shared entry: `ea5ed98f69036148cd071e3a48bde9cb32753ab3`.
- Normal merge: `0f91c6ee042488f17bdbbba64dcbd8b09573db8b`, preserving both histories.

The merge's entire `src`, Gradle build/settings/properties and wrappers are equal
to the tested candidate. This is an exact runtime-input equality check, **not** a
Gradle/native rerun at the merge SHA or a document-sensitive strict-validation alias.
Only three Java test-support files and the author's handoff/evidence archive enter
through the merge. Unrelated dirty files, protected AGENTS and Task 172's untested
tree-transport prototype are neither staged nor adopted.

## Findings and design decisions

New-context Codex reviewer `/root/pacing_independent_review` finds no Critical,
High or Medium source/integration issue and recommends normal integration.
The actual diff and neighboring production/readiness fixtures were inspected.
All existing Tau/readiness assertions and original flight, retry and timeout tick
limits are preserved. Independent actual-pacer stub checks pass 13 cases; four
read-only mapped Forge class inspections confirm the relevant native task APIs.
These are not reviewer-launched Minecraft or packaged restart tests.

**L1, Low, retained:** Tau's comment claiming "at least ten seconds" is not a
guarantee. At persistent unreadiness, 200 attempts yield 199 calls, the first free,
and 198 paced intervals, about 9.9 seconds plus execution/scheduling time. The
attempt and timeout limits are not enlarged. This additive correction does not
modify the author's sealed archive or silently rewrite its evidence.

The helper supplies 50 ms intervals only to waiting test ticks and polls chunk
tasks; it does not replace the full server event-loop policy or preempt a long
native task. Tau deliberately warms its first pad with a fixture-owned ticket;
the separate readiness test retains cold/FULL-only/wait/blocked-pad/spawn coverage.
Production logic, schema, network, registry IDs, assets and fuel-loader behavior
are unchanged. Evidence supports this limited fixture correction, not the absence
of every possible timing-dependent production defect.

## Actual fresh commands and results

Java 17.0.7 / Forge 47.4.10 / Gradle 8.8, clean detached candidate checkout.
Each original has its own newly owned Windows Job assigned before resume, bounded
dual streams and a ten-second maximum drain. All 37 captured originals (five
Gradle runs, 30 metadata checks, two diff checks) exit zero with complete streams,
no overflow/observer error and unchanged declared inputs/HEAD/tree/status/index.

| Command | Exit / elapsed | Actual result |
|---|---|---|
| `gradlew.bat clean build --no-build-cache --no-daemon --stacktrace` | 0 / 204.537 s | 381 XML, 2192 tests, zero failures/errors/skips. |
| `gradlew.bat test --rerun-tasks --no-build-cache --no-daemon --stacktrace` | 0 / 178.007 s | Fresh forced execution, same counts. |
| `gradlew.bat runData --no-daemon --stacktrace`, twice | 0 / 25.865 and 21.146 s | Each 1241 files / 575641 bytes; complete inventories identical, excluding only ignored `.cache`. |
| `git diff --exit-code`, after each DataGen | 0 | Both empty. |
| `gradlew.bat runGameTestServer --no-daemon --stacktrace` | 0 / 218.489 s | All 597 required tests passed; Tau path and destination readiness both executed. |
| `git diff --check`, exact candidate | 0 | Empty. |
| Fresh inert archive/client/artifact/receipt audit | 0 | 36 archive payload hashes and coverage match; selected client inputs match their manifest. |
| Guarded owned-output cleanup | 1 | Cumulative inventory stops at sentinel 5001 against unchanged 5000 bound; **no deletion**. All new outputs remain. |

Native complete log: **62 ERROR / 161 WARN / 0 FATAL**, all unwaived. Compiler
warnings are retained. A test completion marker is not a clean-log or release Gate.
No budget, assertion, warning/error policy or test selection is relaxed.

Runtime JAR: 6085865 bytes, SHA-256
`5dfbddfc1f9862b95be052fb53ac3c3224613468dae1025ca7c3ee548ec90dc2`.
API JAR retains `aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b`.
Runtime archive entry bytes differ from the client's old artifact only in seven
classes belonging to the three changed test-support sources; packaging metadata
is not included in that entry-byte comparison. The old client's `e965c5fb...`
identity is not relabeled as a client test of this new artifact.

## Evidence custody and limitations

Root packet: `D:/GitHub/ARCE-Task-Evidence/v1.8.0/pacing-review-root-20261010/`.
Independent sealed packet:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/pacing-review-independent-20261010/REPORT.md`.
Final assigned read/import cutoff: `2026-10-10T13:24:16.373014Z`; final sealing
command returned successfully before Root moved HEAD.

Fresh inert verification checks the complete peer packet: 44 files including
manifest, 578411 bytes, exact coverage and hashes. Manifest SHA-256:
`97accc1f5e7cb7ad809d5664595cbb3a743aeea3820fadcdaf74847dbcc675d6`.
Actual final REPORT SHA-256:
`ba3ea24d875df01127e5542bbc568bb33a208a7f59209f5952cf78b30eaf2882`.
The peer's chat handoff used a stale REPORT hash and imprecise file count; the
sealed manifest and actual bytes agree. Root retains this communication discrepancy
in `independent-seal-audit.json`, without changing any sealed peer file.

Read/setup errors are retained rather than counted as tests: guessed README and
Main utility locations did not exist; broad displays truncated and were replaced
by focused/structured reads; an initial display-only client manifest matcher
omitted `./`; the first flat peer enumeration rejected an ordinary subdirectory.
The bounded recursive inert successor passes the same sealed packet. No archived
helper is executed, and no failed command is relabeled as a successful original.

The client report, three artifacts, three round summaries and selected raw logs
have matching hashes and compatible observations: 119/30/3 screenshots, zero
reported process exits, one first-join retry, and one vanilla superflat ERROR.
Its report remains scoped to Forge 47.4.22, one GPU and offline profiles. Full
visual/persistence/world custody is not newly accepted. The original world is
untouched. UI right-margin/feedback, first-connect, torch, sky and configuration
watcher issues remain open; this merge repairs none of them.

## Remaining scope, Gate status and rollback

Hosted Linux CI must confirm the merged tests; no green hosted result is asserted
by these local runs. Normal push of source/record commit `30e89b0a` starts
[hosted run 38056013604](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/38056013604),
observed `in_progress`, conclusion null, at that exact SHA. A web-tool URL failure
is followed by a successful direct GitHub REST query; no final or per-test hosted
result is inferred. Fuel-loader `count=0` needs its own v1.8 task. Existing whole
Python/strict failures, 62 native ERRORs, rejected output cleanup, full S1/S2,
V1/V2, progression, asset/content and all G0–G9 obligations remain open. v1.8 stays
`IN_PROGRESS` / `IMPLEMENTING`; the v1.0 acceptance cursor is unchanged.

Review/integration records are separate from deferred Task 172 preparation.
Rollback is a normal revert of the merge's test-support change with the same
regression checks, never a shared-history rewrite or deletion of retained evidence.
