# v1.0.0 recovery evidence map

**Candidate coverage is incomplete.** Exact report/JAR associations appear in
[evidence-index.json](evidence-index.json).

| Area | Observed evidence | Boundary |
|---|---|---|
| Assembly/disassembly interruption | [Transaction matrix](../../work/v1.0.0-transaction-recovery/VERIFICATION.md): 25 process kills and 50 clean recovery/second-restart processes | Older `cf077ef7...` artifact |
| Legacy player-embedded vehicle | [Legacy passenger report](../../work/v1.0.0-legacy-passenger/VERIFICATION.md) | Repairs and strict migration warnings are retained; not a warning-free migration claim |
| Landed passenger continued use | [Continued-use report](../../work/v1.0.0-passenger-continued-use/VERIFICATION.md): real leave/rejoin/board, fuel loading and return, plus headless restart | Older `b41db06e...` artifact; disassembly is Forge evidence |
| Entity-readiness guard | [Readiness report](../../work/v1.0.0-passenger-readiness/VERIFICATION.md): Forge boundary regression and packaged resave | `a3fb6c73...`; no real asynchronous queue-drain observation |
| Logout cancellation | [Logout report](../../work/v1.0.0-passenger-logout/VERIFICATION.md): actual Forge logout event, queue isolation and fresh deadline | `569f41ab...`; FakePlayer setup, not a native socket disconnect |
| Controlled native queue | [Native reconnect report](../../work/v1.0.0-native-reconnect/VERIFICATION.md): two real players wait/recover once; pending owner disconnects without moving and rejoins successfully; saved rocket projection preserved | Same `569f41ab...`; JDI-controlled readiness, not genuine disk-latency or performance evidence |

Controlled native waiting, one-time recovery and pending-player cleanup now
have evidence. Remaining storage-specific checks concern genuine adjacent-chunk
arrival/current-seat selection and native expiry behavior. Preserve the
128-player capacity, four attempts/tick and 200-tick wait window.

Final-candidate restart and interrupted-flight evidence must retain entity,
block, inventory, fuel, passenger and journal conservation for every required
phase. A successful startup alone is not recovery acceptance. Never edit a
failed world into the expected state or replace retained failing logs.
