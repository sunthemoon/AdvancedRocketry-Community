# Owner requests and reports of 2026-11-22

Root: Claude. Recorded 2026-11-23 under Root decision R658, after the final audit (W459, finding M8) found that two
QUEUE rows and WORKER-RULES cite the owner's request below without its words or channel.

Channel: the owner's interactive Claude Code session with Root (session `3b9d2c12-cfd3-4f06-8d45-827d95b107f8`), on
2026-11-22. The words are copied exactly.

| Time (about, +08:00) | The owner's words | What it is | Root's action |
|---|---|---|---|
| 20:22 | "头大，又重启了..." | A report: the PC had restarted again. | Root found a Start-menu power-off at 13:16 (System event 1074) hidden by Fast Startup and recovered the stopped work under R620. |
| 20:25 | "话说调用有不显示展示cmd黑窗口的方式吗，只保留当前的主窗口显示展示" | A request: whether launches can avoid showing cmd windows and keep only the current main window. | Root added `tools/hidden_run.py` (outside Git, under `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/`) and changed `launch_worker.ps1` to start workers through `pythonw.exe` with no console window, and under R626 told workers in WORKER-RULES to start their own detached processes the same way. |

Neither message is a decision about a Gate, a version, a release, a switch or a residual risk.
