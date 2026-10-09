# Offline JSON resource measurement: actual owner choice

Date: 2026-10-09 (Asia/Taipei). Source: the actual submitted asynchronous reply
in the active Root Codex conversation. The reply contains no submission timestamp.
Question item ID:
`["request_user_input_async","call_c34bdc752f8b4c79af95e3be976c17e3",0]`.

Exact question presented:

> 用户，离线 JSON 资源验收的内存测量窗口需要明确：是否采用“Python 从启动启用 tracemalloc 到最终退出前采样”的 traced 峰值，加上“原始进程句柄在退出后读取”的 Windows 原生生命周期峰值？两项仍各限 256 MiB；但 traced 不覆盖启用前和采样后的解释器分配，不能称为全生命周期 traced 证明。采用时会记录 ADR 修订并独立审核，不直接算验收通过。

Owner's exact submitted reply:

> 采用明确的双窗口并记录 ADR 修订（推荐）

This selects the two explicitly different measurement windows and the stated
ADR amendment/independent review procedure. The recommendation suffix is part
of the actual answer, not evidence inferred from a UI default.
[ADR-069](../../decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md) records the
precise normative amendment. [Adoption69](MEMORY-WINDOW-ADOPTION-69.md) records
later completed independent review and limited contract adoption, not implementation.

Both peaks retain independent 268435456-byte ceilings. The traced observation
excludes allocations before tracing starts and after the final pre-exit sample;
it is not a birth-to-exit Python-allocation proof. The native counter is a
post-exit lifetime peak working set, not a replacement traced counter, private
commit measurement, all-native-allocation inventory or hard allocation limit.
Generated input and decoded result are not excluded from their applicable windows.

This answers only the previously pending measurement-window choice. It does
not approve a helper/launcher, protocol implementation, any resource-case result,
whole-driver adoption, quota, B1/R1/M1/M2 sleep behavior, production dimension
change, risk/waiver acceptance or any Required Gate. R64-01's normative treatment
requires completed independent review and an explicit integration disposition;
resource execution and R64-02's future launcher deadline remain separate work.
Original frozen documents, reports and sealed evidence are not edited.
