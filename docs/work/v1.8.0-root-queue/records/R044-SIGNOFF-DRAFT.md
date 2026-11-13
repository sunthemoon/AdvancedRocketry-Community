# R-044's downgrade limit: draft sign-off text (restore from the complete backup)

| Item | Value |
|---|---|
| Status | **DRAFT, not signed.** Root wrote it on 2026-11-13 under Root decision R433 ([OWNER-DECISIONS-20261113](OWNER-DECISIONS-20261113.md)). R-044 stays unaccepted until the owner signs. |
| Kept as failed | `cargo-G2-REUSE` rows 3 to 5 and `w336-downgrade` on candidate C3, and W176's three downgrade rows, stay FAIL in every record before and after a signature. No assertion is relaxed or waived. |
| Register | The downgrade paragraph of R-044 in the [risk register](../../../11-RISK-REGISTER.md) (Root decision R138). |
| Evidence | [W176](W176.md), [W336](W336.md), and C3's `cargo-G2-REUSE` and `w336-downgrade` ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), sections 2.1, 8.5 and 9.3). |

## 1. Proposed text for the owner to sign

> **R-044 降级限制签核（完整备份恢复）**
>
> 1. **范围。** 已用 v1.8 运行过（转移日志 schema 3）的世界，再用不支持 schema 3 的旧版 JAR 打开，属于不受支持的降级。只有历史 JAR `ba046688`（SHA-256 `d9bf5f08…a038bd`）经过实测；其他不支持 schema 3 的旧版 JAR 预计相同，但未测试。
> 2. **会发生什么。** 旧 JAR 拒绝启动，但在拒绝之前改写 `level.dat` 与 `level.dat_old`：首次去掉方块和物品注册项（W176 实测 12 个方块、11 个物品；W336 用其 JAR 实测 13 个、12 个，其中包括卫星舱与四种货物装置的 ID），并更新 LastPlayed；之后每次都更新 LastPlayed 并轮换 `level.dat_old`。地形、实体、转移日志、账本、收据和设备根的字节保持不变（W336：43 个其他世界文件不变）。
> 3. **影响范围与持续时间。** 整个世界，涉及卫星舱、四种货物装置和燃料加注机；不恢复备份就一直存在。
> 4. **唯一受支持的恢复方式。** 停服，用升级前的完整备份整体替换世界目录（所有 Level 的地形、实体与 POI 文件，全局数据、日志、账本与收据，玩家数据，以及 `level.dat` 和 `level.dat_old`），然后用 v1.8 启动。不得只恢复单个文件或单个存储。没有恢复就直接用 v1.8 打开改写后的世界，没有证据支持，不受支持。实测：恢复完整备份后所有哈希一致，候选 JAR 的两次后续启动都没有 ERROR，五个设备根都保留。
> 5. **运营要求。** 升级到 v1.8 之前必须做完整世界备份；发布说明和升级说明写明这一点，并写明降级就是恢复这份备份。
> 6. **保留失败结果。** W176 的三条降级行、C3 的 `cargo-G2-REUSE` 第 3 至 5 行和 `w336-downgrade` 继续记为失败；本签核不放宽、不豁免任何断言，也不把失败改为通过。
> 7. **不包括。** 本签核只接受上述降级残余风险。它不接受 R-044 的其他部分（不受支持的设备根、256 坐标共享池等），不开启任何生产开关，不批准任何 Gate、版本或发布。
> 8. **复审条件。** v1.9.0 发布前复审；转移日志 schema、方块或物品注册 ID、或世界元数据的写入方式发生变化时，本签核失效，需要重新签核。
>
> 负责人：所有者。签核日期：＿＿＿＿

**English rendering (for the records; the Chinese text is what the owner signs):**

1. **Scope.** Opening a world that has run v1.8 (transfer journal schema 3) with an older JAR that does not support
   schema 3 is an unsupported downgrade. Only the historical JAR `ba046688` was tested. Other such JARs are expected to
   behave the same way, but they were not tested.
2. **What happens.** The old JAR refuses to start, but first it rewrites `level.dat` and `level.dat_old`. The first time,
   it removes block and item registry entries and updates LastPlayed. W176 counted 12 blocks and 11 items; W336 counted
   13 and 12 with its JAR, including the IDs of the bay and the four cargo devices. Each later start updates LastPlayed
   and rotates `level.dat_old`. Terrain, entities, the transfer journal, the ledger, receipts and device roots stay
   byte-identical; W336 found 43 other world files unchanged.
3. **Scope and duration.** The whole world, including the bay, the four cargo devices and the fuel loader. It lasts
   until the backup is restored.
4. **The only supported recovery.** Stop the server and replace the whole world folder with the complete backup taken
   before the downgrade. The backup covers every Level's terrain, entity and POI files, global data, the journal, the
   ledger and receipts, player data, and `level.dat` and `level.dat_old`. Then start v1.8. Never restore single files
   or single stores. Starting v1.8 on the rewritten world without restoring the backup has no evidence and is
   unsupported. Measured: after the complete backup was restored, every hash matched, both later starts of the
   candidate logged no ERROR, and all five device roots were kept.
5. **Operations.** Take a complete world backup before upgrading to v1.8. The release notes and the upgrade
   instructions say so, and say that a downgrade means restoring that backup.
6. **Failures kept.** W176's three downgrade rows, C3's `cargo-G2-REUSE` rows 3 to 5 and `w336-downgrade` stay failed.
   The signature relaxes and waives no assertion and turns no failure into a pass.
7. **Not included.** The signature accepts only this downgrade residual. It does not accept the rest of R-044
   (unsupported device roots, the shared 256-coordinate pool and the like). It enables no production switch and
   approves no Gate, version or release.
8. **Re-review.** Before v1.9.0 is released. The signature lapses, and needs a new one, if the transfer-journal
   schema, the block or item registry IDs, or the way world metadata is written changes.

## 2. Choices the owner can make in the text

- **Item 1, which JARs.** The draft names every JAR without schema-3 support and says only `ba046688` was tested. A
  narrower version names only `ba046688`. The owner can also ask for more old JARs to be tested first. Each costs
  about half a day: one W176-style native run per JAR.
- **Item 4, starting v1.8 without the restore.** The draft calls this unsupported, because nothing tests it. If the
  owner wants it supported, it needs its own native case before signing, about half a day to a day: start the
  candidate on the rewritten world and check the registry remap, the device roots and the ledger.
- **Item 8, re-review.** The draft ties re-review to v1.9.0 and to changes in the format. The owner can choose a
  different version.

## 3. Effect of W357's planned ledger change (added 2026-11-13)

W357's design (Root decision R440) moves the escrow ledger to root 3. Once that is implemented, every escrow-aware
older JAR, C3 included, also refuses a world whose ledger is root 3, even when the world has no governed transfer
record. That is a second refusal reason next to the transfer journal's schema 3. Whether such a refusal also rewrites
`level.dat` has not been tested; candidate C4 adds native downgrade cases for it. Item 8 of the text above would
make a signature given now lapse when root 3 lands. Root therefore recommends signing after C4's results, with item 1
naming both boundaries and item 2 citing C4's evidence. Root will give that revised text with the C4 report. The text
above stays the draft for the current format.

## 4. What Root does after a signature

1. Records the owner's words verbatim in a new owner-decision record, with the date and channel.
2. Changes R-044's downgrade paragraph from "风险接受仍待所有者" to the signed scope, and adds the acceptance (owner,
   reason, re-review version and condition) to ADR-065's W176 note (AGENTS.md section 5).
3. Adds the backup and downgrade instruction to the v1.8.0 release notes and upgrade instructions.
4. Updates the per-switch report and the status file. The two families keep their FAIL results. The report says the
   residual is accepted under the signed scope, and does not call it a pass.

Until then, R-044 is not accepted, and the two families are the failed families of an unaccepted risk.
