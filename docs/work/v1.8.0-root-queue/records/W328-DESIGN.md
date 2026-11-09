# W328 design note: unknown file presence at startup

Task: [TASK-W328](../TASK-W328-PRESENCE-UNKNOWN.md), from W327's audit findings L1, L2 and L5
(`D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md`). Root decision R390 keeps the bay's
NOT_LOADED state for an unknown receipt presence; this note does not change that policy.

**Terms.** `CheckedSavedDataFile.presence` gives ABSENT only for a `NoSuchFileException` from a no-follow status
check, PRESENT for any entry, and UNKNOWN for any other failure. `Files.exists` reads UNKNOWN as absent.

## 1. Pre-start check: refuse startup (recommended)

`WorldDataMigrationService` will decide the presence of every managed file, and of the `data` directory, with the
no-follow check. UNKNOWN for any of them refuses startup before any read, backup, staging or commit, exactly as the
rejoin sidecar's existing rule does (`ARCE-BETA-2000`, typed with the file's type). The refusal hint will treat an
unknown ledger or receipt presence as present, so an escrow world gets the whole-world hint and never the
single-file restore hint.

I recommend this over passing UNKNOWN on to each store's own start:

- Only some stores handle UNKNOWN themselves: the station registry, the transfer journal and the classic progression
  root (W44 section 3.6, UNREADABLE), the rejoin sidecar (it already refuses startup) and the bay receipts
  (NOT_LOADED). The celestial, satellite-mission and endgame roots use vanilla's `computeIfAbsent`, which builds a
  fresh empty root for a path it cannot check. That is L2's latent replacement for three more files.
- The pre-start check already refuses a present root that it cannot read. A presence it cannot prove is the same
  state, and W44 section 3.6 already relies on the pre-start check refusing such roots.
- Carrying the pre-start result into the session needs a server-lifetime holder, which W44 section 3.6 rejected.

The stores' own handling stays as the second line, for a status check that passes before startup and fails at the
store's start.

| Item | Pre-start refusal |
|---|---|
| Scope | The server: the world does not start. Any world can be affected, including one with no ARCE data. No file is read past its status check, and none is created, moved, backed up or changed. |
| Duration | Until a start at which every managed path's status can be checked. |
| Logs | One ERROR per start attempt, from the existing `BetaWorldMigrationEvents` paths, naming the type and "presence is unknown". |
| Recovery | Fix the cause (permissions, a lock, storage) and restart. No offline repair is needed, because nothing changed. |

Today the same world starts in a worse state: the bay is NOT_LOADED (R-042's server-wide lock), W44 roots are held,
or three roots and the ledger start empty and could later replace the existing files.

## 2. Escrow ledger at `ServerStartingEvent`

A new `EscrowLedgerStartup.start`, called from `EscrowRuntime.starting`, classifies the ledger:

- A delegate that is already bound and a loaded root behave as today, and so does ABSENT, which gives NO_FILE.
- PRESENT without a root stays NOT_LOADED, as today.
- UNKNOWN without a root now stays NOT_LOADED. Today it gives NO_FILE.
- Both NOT_LOADED cases log one WARN with the file name and a reason: `ARCE_ESCROW_LEDGER_NOT_LOADED
  reason=LOAD_FAILED|PRESENCE_UNKNOWN file=advancedrocketrycommunity_resource_escrow.dat`.

While NOT_LOADED, the shared authority answers UNAVAILABLE for both consumers, so fuel, all four cargo kinds and the
bay refuse admission. `EscrowLedgerCreation` needs a reservation and `createForFill` needs NO_FILE, so no ledger is
created. The scheduler, settlement and reconciliation run only on a root, so nothing reconciles. Vanilla caches the
missing lookup for the session. This is exactly today's LOAD_FAILED behaviour. Scope: the server's escrow admission.
Duration: the session. Logs: one WARN per start. Recovery: a restart that loads the file or proves it absent. After
section 1, this only happens when the status check fails after the pre-start check.

## 3. Bay receipts (L1) and the ticket comment (L5)

- **Bay receipts.** The bay keeps NOT_LOADED (R390). Its WARN keeps the `ARCE_BAY_RECEIPTS_NOT_LOADED` token and
  gains `reason=LOAD_FAILED` or `reason=PRESENCE_UNKNOWN` and the file name. Only LOAD_FAILED says the file exists.
- **Ticket comment.** The Javadoc of `BayTransferRecovery.renewTickets` will say that REPAIR stops the census and
  restart-group tickets, but a checkpoint claim keeps renewing T's origin and destination tickets.

## 4. Not changed

- **Read-only gates.** These `RocketEscrowGate` checks read a missing in-memory root as "no ledger" in both
  NOT_LOADED cases: `escrowIdle`, `retirementUnprotected`, `bayNeutral` and `escrowLogicalLocked`. That is
  today's LOAD_FAILED behaviour, and after section 1 it is reachable only in the window above. Root should decide
  whether a follow-up is needed before any cargo switch is enabled.
- **Migration backup.** The backup's `_old` copy still uses `Files.exists`. This affects only backup completeness.

## 5. Proposed risk-register text (Root edits the register)

- **R-035, escrow ledger paragraph:** "W328：账本文件的存在性无法证明（无跟随状态检查失败且不是 NoSuchFileException）时，启动前检查拒绝整世界启动（ARCE-BETA-2000，每次启动一条 ERROR，给出整世界恢复提示），不读取、不创建、不改动任何文件，修复存储或权限后重启即可。若状态检查在启动前检查之后才失败，PROPULSION 委托在本次会话保持 NOT_LOADED：不创建账本，燃料、四种货物和卫星舱准入以及对账全部拒绝，范围为整个服务器，每次启动一条 WARN `ARCE_ESCROW_LEDGER_NOT_LOADED reason=PRESENCE_UNKNOWN`，能证明存在或不存在的重启即恢复。"
- **R-042, NOT_LOADED trigger:** "收据文件存在但未能加载（WARN `reason=LOAD_FAILED`），或无法证明收据文件不存在（`reason=PRESENCE_UNKNOWN`；W328 起通常由启动前检查拒绝启动，只在状态检查于启动前检查之后才失败时出现，此时不需要任何收据文件）。"
- **Pre-start check, in R-039 or a world-level entry:** "W328：启动前检查对全部受管根及 `data` 目录只把 NoSuchFileException 当作不存在，任何其他状态检查失败都拒绝整世界启动，与已有的 rejoin 侧车规则相同。可影响任何世界，包括没有 ARCE 数据的世界；持续到状态可检查的重启；每次启动一条 ERROR；修复后重启即可恢复。"
