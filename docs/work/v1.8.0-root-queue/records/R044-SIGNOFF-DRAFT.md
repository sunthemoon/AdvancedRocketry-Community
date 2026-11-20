# R-044's downgrade limit: draft sign-off text (restore from the complete backup)

| Item | Value |
|---|---|
| Status | **DRAFT, not signed.** Root wrote it on 2026-11-13 under Root decision R433 ([OWNER-DECISIONS-20261113](OWNER-DECISIONS-20261113.md)). R-044 stays unaccepted until the owner signs. On 2026-11-19 Root added section 5, the revised text for both format boundaries, which replaces section 1 for signing once candidate C4 repeats the root-3 case. The same day Root corrected item 4 of section 1: the Chinese text said the backup taken before the upgrade, but W176 and W336 restored the backup taken before the downgrade, as the English rendering already said. |
| Kept as failed | `cargo-G2-REUSE` rows 3 to 5 and `w336-downgrade` on candidate C3, W176's three downgrade rows, and W373B's `accounting-root3-old-jar`, with C6's reruns of these families, stay FAIL in every record before and after a signature. No assertion is relaxed or waived. |
| Register | The downgrade paragraph of R-044 in the [risk register](../../../11-RISK-REGISTER.md) (Root decision R138). |
| Evidence | [W176](W176.md), [W336](W336.md), C3's `cargo-G2-REUSE` and `w336-downgrade` ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), sections 2.1, 8.5 and 9.3), and W373B's `accounting-root3-old-jar` ([W373B](W373B.md); evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W373B/campaign2/accounting-root3-old-jar-attempt2/`). |

R555 (2026-11-20) supersedes C4 with C5 for the pending repetition and final packet; C4's record remains history.
R581 (2026-11-21) now selects C6 for that pending repetition and packet; C5's pre-run is history.

## 1. Proposed text for the owner to sign

> **R-044 降级限制签核（完整备份恢复）**
>
> 1. **范围。** 已用 v1.8 运行过（转移日志 schema 3）的世界，再用不支持 schema 3 的旧版 JAR 打开，属于不受支持的降级。只有历史 JAR `ba046688`（SHA-256 `d9bf5f08…a038bd`）经过实测；其他不支持 schema 3 的旧版 JAR 预计相同，但未测试。
> 2. **会发生什么。** 旧 JAR 拒绝启动，但在拒绝之前改写 `level.dat` 与 `level.dat_old`：首次去掉方块和物品注册项（W176 实测 12 个方块、11 个物品；W336 用其 JAR 实测 13 个、12 个，其中包括卫星舱与四种货物装置的 ID），并更新 LastPlayed；之后每次都更新 LastPlayed 并轮换 `level.dat_old`。地形、实体、转移日志、账本、收据和设备根的字节保持不变（W336：43 个其他世界文件不变）。
> 3. **影响范围与持续时间。** 整个世界，涉及卫星舱、四种货物装置和燃料加注机；不恢复备份就一直存在。
> 4. **唯一受支持的恢复方式。** 停服，用降级前的完整备份整体替换世界目录（所有 Level 的地形、实体与 POI 文件，全局数据、日志、账本与收据，玩家数据，以及 `level.dat` 和 `level.dat_old`），然后用 v1.8 启动。不得只恢复单个文件或单个存储。没有恢复就直接用 v1.8 打开改写后的世界，没有证据支持，不受支持。实测：恢复完整备份后所有哈希一致，候选 JAR 的两次后续启动都没有 ERROR，五个设备根都保留。
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

W373B measured the second boundary on 2026-11-19, before C4. Section 5 gives the revised text.

## 4. What Root does after a signature

1. Records the owner's words verbatim in a new owner-decision record, with the date and channel.
2. Changes R-044's downgrade paragraph from "风险接受仍待所有者" to the signed scope, and adds the acceptance (owner,
   reason, re-review version and condition) to ADR-065's W176 note (AGENTS.md section 5).
3. Adds the backup and downgrade instruction to the v1.8.0 release notes and upgrade instructions.
4. Updates the per-switch report and the status file. The two families keep their FAIL results. The report says the
   residual is accepted under the signed scope, and does not call it a pass.

Until then, R-044 is not accepted, and the two families are the failed families of an unaccepted risk.

## 5. Revised text for both format boundaries (draft, 2026-11-19)

W373B ran C3's JAR on a world whose escrow ledger the activated build had upgraded to root 3 (`OLD_JAR_REFUSAL` in
section 3 and observation O1 of [W373B](W373B.md)). Root also decoded the three
worlds that case archived after each refused start (`old-0-world.zip` to `old-2-world.zip`). In each, `level.dat`
differed from the previous one only in `/Data/LastPlayed`. `level.dat_old` was the previous `level.dat`. The block and
item registries kept all 1,137 and 1,506 entries. The text below replaces section 1 for signing. Candidate C6 runs the
same case on its own JAR. If C6's result differs from W373B's, Root revises the text again before it goes to the
owner.

> **R-044 降级限制签核（完整备份恢复，修订稿）**
>
> 1. **范围。** 已用 v1.8 运行过的世界，再用不支持其存档格式的旧版 JAR 打开，属于不受支持的降级。涉及两条格式边界：
>    - (a) 转移日志 schema 3。实测 JAR：历史 JAR `ba046688`（SHA-256 `d9bf5f08…a038bd`）。
>    - (b) 托管账本 root 3。支持托管、但不支持 root 3 的 JAR 会拒绝这样的世界，即使世界中没有受管转移记录。实测 JAR：候选 C3 的 JAR（SHA-256 `459669bf…f81744b`）。
>
>    其他旧版 JAR 预计与同一边界上实测的 JAR 相同，但未测试。
> 2. **会发生什么。**
>    - (a) `ba046688` 拒绝启动，但在拒绝之前改写 `level.dat` 与 `level.dat_old`：首次去掉方块和物品注册项（W176 实测 12 个方块、11 个物品；W336 用其 JAR 实测 13 个、12 个，其中包括卫星舱与四种货物装置的 ID），并更新 LastPlayed；之后每次都更新 LastPlayed 并轮换 `level.dat_old`。地形、实体、转移日志、账本、收据和设备根的字节保持不变（W336：43 个其他世界文件不变）。
>    - (b) C3 的 JAR 在载入世界之前以 `ARCE-BETA-2001`（账本使用未来的 schema 3）拒绝启动，进程退出码为 0。三次拒绝后账本字节不变，42 个世界文件中 40 个不变；每次改变的只有 `level.dat`（只有 LastPlayed 变化）和 `level.dat_old`（轮换为上一份 `level.dat`），没有删除任何注册项（W373B 实测）。
> 3. **影响范围与持续时间。** 整个世界，涉及卫星舱、四种货物装置和燃料加注机。(a) 不恢复备份就一直存在。(b) 世界数据只多了 LastPlayed 的更新和 `level.dat_old` 的轮换；不恢复升级前的备份，旧版 JAR 就一直拒绝这个世界。
> 4. **受支持的恢复方式。** 恢复一律整体替换世界目录：停服，用完整备份替换整个世界目录（所有 Level 的地形、实体与 POI 文件，全局数据、日志、账本与收据，玩家数据，以及 `level.dat` 和 `level.dat_old`）。不得只恢复单个文件或单个存储；迁移自动生成的 `advancedrocketrycommunity-backups` 目录只含账本和转移日志，不是完整备份。
>    - **继续使用 v1.8。** 用降级前的完整备份恢复，再用 v1.8 启动。实测 (a)：恢复后所有哈希一致，候选 JAR 的两次后续启动都没有 ERROR，五个设备根都保留。对于 (b)，也可以不恢复，直接用 v1.8 启动被拒绝的世界。实测：两次启动都没有 ERROR，账本仍为 root 3，遗留操作字节不变并仍处于保留状态。对于 (a)，不恢复备份就直接用 v1.8 打开被改写的世界，没有证据支持，不受支持。
>    - **回到旧版。** 用升级到 v1.8 之前的完整备份恢复，再用旧版启动；在 v1.8 上进行的一切都会丢失。实测 (b)：恢复的 root 2 世界在 C3 的 JAR 上通过数据检查，未完成的转移恰好完成一次。
> 5. **运营要求。** 升级到 v1.8 之前，以及任何降级尝试之前，都必须做完整世界备份。发布说明和升级说明写明这一点，写明降级就是恢复升级前的备份，并写明旧版 JAR 拒绝时进程退出码为 0，须以日志中的 `ARCE-BETA-2001` 为准。
> 6. **保留失败结果。** W176 的三条降级行、C3 的 `cargo-G2-REUSE` 第 3 至 5 行和 `w336-downgrade`、W373B 的 `accounting-root3-old-jar`，以及 C6 对这些族的重跑，继续记为失败；本签核不放宽、不豁免任何断言，也不把失败改为通过。
> 7. **不包括。** 本签核只接受上述降级残余风险。它不接受 R-044 的其他部分（不受支持的设备根、256 坐标共享池等），不开启任何生产开关，不批准任何 Gate、版本或发布。
> 8. **复审条件。** v1.9.0 发布前复审；转移日志 schema、托管账本 root、方块或物品注册 ID、或世界元数据的写入方式发生变化时，本签核失效，需要重新签核。
>
> 负责人：所有者。签核日期：＿＿＿＿

**English rendering (for the records; the Chinese text is what the owner signs):**

1. **Scope.** Opening a world that has run v1.8 with an older JAR that does not support its save format is an
   unsupported downgrade. There are two format boundaries:
   - (a) the transfer journal's schema 3. Tested JAR: the historical JAR `ba046688`;
   - (b) the escrow ledger's root 3. A JAR that supports escrow but not root 3 refuses such a world, even when the
     world has no governed transfer record. Tested JAR: candidate C3's JAR.

   Other older JARs are expected to behave like the tested JAR at the same boundary, but they were not tested.
2. **What happens.**
   - (a) `ba046688`: unchanged from section 1, item 2.
   - (b) C3's JAR refuses with `ARCE-BETA-2001` (the ledger uses future schema 3) before the world loads, and the
     process exits with code 0. After three refusals the ledger was byte-equal and 40 of 42 world files were
     unchanged. Each time only `level.dat` (LastPlayed alone) and `level.dat_old` (rotated to the previous `level.dat`)
     changed, and no registry entry was removed.
3. **Scope and duration.** The whole world, including the bay, the four cargo devices and the fuel loader. (a) lasts
   until a backup is restored. In (b) the world data gains only the LastPlayed update and the rotation; the older JAR
   keeps refusing the world until the pre-upgrade backup is restored.
4. **Supported recovery.** Every recovery replaces the whole world folder: stop the server and replace the whole folder
   with a complete backup (every Level's terrain, entity and POI files, global data, the journal, the ledger and
   receipts, player data, and `level.dat` and `level.dat_old`). Never restore single files or single stores. The
   `advancedrocketrycommunity-backups` folder that the migration writes holds only the ledger and the transfer journal
   and is not a complete backup.
   - **Staying on v1.8.** Restore the complete backup taken before the downgrade and start v1.8. Measured for (a):
     every hash matched after the restore, both later candidate starts logged no ERROR, and all five device roots were
     kept. For (b) the refused world can also be started on v1.8 without a restore. Measured: both starts logged no
     ERROR, the ledger stayed root 3, and the legacy operation stayed byte-equal and held. For (a), starting v1.8 on
     the rewritten world without the restore has no evidence and is unsupported.
   - **Going back to the older version.** Restore the complete backup taken before the upgrade to v1.8 and start the
     older version; everything done on v1.8 is lost. Measured for (b): the restored root-2 world passed the data check
     on C3's JAR, and the open transfer completed exactly once.
5. **Operations.** Take a complete world backup before upgrading to v1.8 and before any downgrade attempt. The release
   notes and the upgrade instructions say so, say that a downgrade means restoring the pre-upgrade backup, and say that
   an older JAR's refusal exits with code 0, so the log line `ARCE-BETA-2001` is what counts.
6. **Failures kept.** W176's three downgrade rows, C3's `cargo-G2-REUSE` rows 3 to 5 and `w336-downgrade`, W373B's
   `accounting-root3-old-jar`, and C6's reruns of these families stay failed. The signature relaxes and waives no
   assertion and turns no failure into a pass.
7. **Not included.** Unchanged from section 1, item 7.
8. **Re-review.** Before v1.9.0 is released. The signature lapses, and needs a new one, if the transfer journal's
   schema, the escrow ledger's root, the block or item registry IDs, or the way world metadata is written changes.

**Root's choices in the revised text, which the owner can change:**

- **Starting v1.8 on a world refused at boundary (b).** Root lists it as supported. A refused start at (b) changed
  only what every normal start changes (LastPlayed and the rotation), and two later starts were measured. C6 repeats
  the case. A narrower text drops the sentence, and then (b) also needs the pre-downgrade backup.
- **Items 1 and 8.** The same choices as in section 2 apply.
