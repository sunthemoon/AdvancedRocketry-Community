# X03-D: operator guidance for the whole-world repair after FF_REBUILD_SOURCE

Root: Claude. Date: 2026-11-19. **Status: APPROVED and PUBLISHED on 2026-11-24 (Root decision R681, under the owner's
delegation in [OWNER-DECISIONS-20261124](OWNER-DECISIONS-20261124.md)).** The operator text of section 2 is published,
with the approval wording, in the [endgame operator guide](../../../ENDGAME-OPERATOR-GUIDE.md), section "Whole-world
repair after a FLOOR_CONFLICT fence"; this record keeps its history and the full refusal table.

| Item | Value |
|---|---|
| Decision | Root decision R513 (QUEUE row W387) |
| Design | [W367-DESIGN](W367-DESIGN.md), revision 3: section 3 (the procedure), section 4 (root 2 and root 3) and section 7 (the first draft of this text) |
| Evidence so far | X03-B on root 2 ([W380](W380.md), merged as `97576e2be`) and X03-C on the accounting-enabled build ([W387](W387.md)), each with native accepting runs and read-only reviews (W380R, W380R2 and W387R). These are development evidence. |
| Final evidence | The X03 family `cargo-X03-REPAIR` on candidate C4, with both world origins (R463, answer 3); candidate C6 replaced C4 (R581), and C6's rerun passed with both origins (R616, R632) |
| Tool | `scripts/repair_v180_rebuild_source.py`, modes `plan`, `apply` and `census` |

## 1. What changed since the first draft

The first draft (design section 7) was written before the accounting activation. Five facts from X03-B and X03-C
change what an operator meets.

1. **The ledger is root 3 on the activated build.** The plan pins the stamps of every managed root for the chosen
   profile, and `apply` checks them again. On the activated build the operator passes `--profile enabled`; any other
   profile makes the stamps disagree with the world and refuses.
2. **A world last run on an older build is upgraded by the qualifying start.** Planning before that start refuses
   with `ROOT_NOT_CURRENT:resource_escrow`. The qualifying start (design section 3.1, step 2) migrates the ledger from
   root 2 to root 3, keeps its ledger ID, writer generation and retired state, writes the migration backups and logs
   the fence for the transfer again. The complete backup is taken after that start, so it includes the migration
   backups. W387 ran this natively on a world seeded by the last root-2 build. A ledger newer than the build (a
   future root) is also refused with `ROOT_NOT_CURRENT`.
3. **The account is UNKNOWN without a witness, and that is expected.** Without a historical before-witness the plan
   reports `UNKNOWN_BASELINE` and no number. The tool never invents a historical amount, account, receipt or
   operation. A supplied witness that verifies gives a signed discrepancy; a nonzero discrepancy, or a witness that
   does not verify, refuses.
4. **A second rocket refuses in any order.** A world with a stale copy of the rocket reports
   `SECOND_PHYSICAL_ROCKET` whichever copy the files list first.
5. **The census checks the vanilla raid files strictly.** After each start of the repaired copy, the census accepts
   a change to the nine raid files of the vanilla and mod dimensions only when the file already existed in the
   backup and only its `Tick` counter grew. Otherwise it refuses with `RAID_FILE_SET_CHANGED`,
   `RAID_TICK_DECREASE_OR_TYPE` or `RAID_OBJECT_CHANGED` (W380R, finding 7). Vanilla saves a dimension's raid file
   every 200 game ticks, about 10 seconds. A dimension whose raid file was never saved before the backup, for example
   after a qualifying start shorter than 10 seconds on a world that never saved it, meets
   `RAID_FILE_SET_CHANGED` at the first census. The draft text therefore asks for at least 10 seconds of running in the

## 2. The operator text (draft)

> **When this applies.** Your server log shows the line
> `ARCE_TRANSFER_FENCED transfer=<id> reason=FLOOR_CONFLICT repair=offline-whole-world` for one transfer, and the
> rocket it names is standing still at its launch position. Worlds on current builds, where production cargo
> admission is off, are not expected to reach this state.
>
> **Before you start.** Stop the server, disable automatic restart and keep players out. Do not delete the transfer
> entry, reset an epoch, restore one store or rebuild the historical tank. Use only the reviewed repair tool and the
> build it was approved with. You need free space for three copies of the world, plus 10 GiB free on each drive the
> tool uses.
>
> **Qualifying start.** Start the unrepaired world once on that build, with players excluded. Load the rocket's
> chunk and the device's chunk with `forceload add`, wait for the fence line for the transfer, keep the server
> running for at least 10 seconds after it has finished starting, remove the forced chunks with `forceload remove`,
> and stop cleanly. If the world was last run
> on an older build, this start also upgrades its saved data; that is expected and repairs nothing.
>
> **Backup and plan.** Make a hash-verified backup of the complete world: every dimension, player file, ledger,
> journal, backup folder and metadata file. Run the tool's `plan` on the backup with the log of the qualifying start
> and `--profile enabled`. Read its identities, amounts, its account (UNKNOWN unless you supplied a verified witness)
> and its exact edit, which removes one journal entry, before you confirm the plan digest.
>
> **Apply and verify.** `apply` writes a fresh copy and changes only the transfer journal. Run `census` on the copy
> before any start. Then start the copy three times in isolation, each a separate server process: load the two chunks,
> run `save-all flush`, wait for its completion message, remove the forced chunks, stop cleanly, and run
> `census --after-start` after each stop. No start may log a fence line or a blocked store. These starts advance the
> copy's game time, so the validated copy is slightly ahead of your original.
>
> **Replace the whole world.** Promote only the entire validated copy while the server is stopped, and keep the
> original and the backup. Never copy only the repaired journal into a live or different world. Restarting alone is
> not a repair, and the historical snapshot is not a refund.
>
> **If the tool refuses.** Your world has another shape, or a step went wrong. Keep the server stopped, keep the
> original and the backup, and ask for a separately reviewed repair. Do not work around a refusal. The table below
> lists the reasons you are most likely to meet.
>
> This procedure is available only after its tool and native evidence are approved, and it does not enable disabled
> production features.

## 3. Refusal reasons an operator is likely to meet

Every refusal leaves the original and the backup unchanged. A partial copy after an `apply` error is discarded,
never repaired piece by piece. The complete list is in design section 3.5.

| Reason | Meaning | What to do |
|---|---|---|
| `ROOT_NOT_CURRENT:<store>` at `plan` | A store's format is not the build's current one. Before the qualifying start this is expected for a world last run on an older build. | Do the qualifying start, take a new complete backup, and plan again. If it persists after that start, or the store is newer than the build, stop and ask. |
| `PLAN_ROOT_STAMPS_DIFFER` | The plan was made for another profile or build. | Plan again with `--profile enabled` on the approved build. |
| `FENCE_EVIDENCE_MISSING`, `FENCE_REASON_DIFFERS` | The log given to `plan` has no fence line for this transfer, or a different reason. | Give the log of the qualifying start. A different reason is a different problem: stop and ask. |
| `SECOND_PHYSICAL_ROCKET`, `ADDITIONAL_ROCKET_IN_FOOTPRINT` | A second rocket with the same identity, or another rocket at the destination. | Stop and ask; this repair does not choose between copies. |
| `PASSENGER_ASSIGNMENT` | A player or entity is still assigned to the rocket or the flight. | Stop and ask. |
| `DESTINATION_IN_TARGET_CHUNK` | The world has the shape where the source was kept, not rebuilt. | Stop and ask; that shape needs a different repair. |
| `WITNESS_INVALID`, `RESOURCE_DISCREPANCY` | A supplied witness did not verify, or shows a loss or gain. | Plan without the witness (the account is then UNKNOWN), or stop and ask about the loss. |
| `UNVALIDATED_AUTHORITY:<field>`, `COMPETING_ENTITY_AUTHORITY` and the other authority reasons | Another escrow record, account, receipt or claim refers to the same rocket or device, or cannot be checked. | Stop and ask; the tool never deletes records to make a world fit. |
| `INSUFFICIENT_SPACE`, `WORLD_LOCKED`, `PATH_OVERLAP` | Not enough free space, a running server or locked world, or overlapping folders. | Correct the environment and run the step again. |
| `STALE_PLAN`, `INPUT_CHANGED` | The original or backup changed after the plan. | Make a new backup and plan again. |
| `MIGRATION_DURING_VERIFICATION` | A store was upgraded during a start of the copy, so the qualifying start did not finish the upgrade. | Discard the copy, start again from the qualifying start. |
| `RAID_FILE_SET_CHANGED` | A raid file appeared that the backup did not have, usually because the qualifying start was shorter than 10 seconds. | Discard the copy, keep the original and the backup, and ask. Do not add or delete raid files by hand. |
| `RAID_TICK_DECREASE_OR_TYPE`, `RAID_OBJECT_CHANGED` | A raid file's counter went backwards or another field changed. | Stop and ask. |
| `MANAGED_ROOT_CHANGED:<store>`, `TARGET_CHANGED:<target>` | A managed store, the rocket or the device changed during a start beyond the allowed clocks. | Stop and ask. |

## 4. Limits of this repair

- It repairs one shape: a FLOOR_CONFLICT fence after FF_REBUILD_SOURCE (a fluid unload whose source was rebuilt),
  with no related escrow authority and an empty fluid-account list. It is not a general FLOOR_CONFLICT repair, a
  settlement, a refund or a store restore.
- Its raid check knows the nine raid files of the vanilla and mod dimensions. Raid files of other mods'
  dimensions are reported by the census, not judged.
- After a start, the census is semantic, not byte-for-byte: it reports every changed file for the operator to read
  and decides on the rocket, the device, the managed stores and the files above. The native family's exact file
  lists exist only for its fixed test world.

## 5. Before publication

1. Candidate C4's X03 family passes with both world origins.
2. The owner approves this text (an item of the final packet).
3. Root then records the result in the W326 matrix row X03 and in the risk register's R-035 entry, and publishes
   the approved text as operator documentation. Production cargo admission stays off until the owner decides it
   separately.

Status on 2026-11-24 (R681): condition 1 is met on C6, which replaced C4 (R581), by the rerun that passed both world
origins (R616, R632); condition 2 is met by Root's approval under the owner's delegation; step 3 is done in the same
commit: the W326 matrix row X03, R-035 and the endgame operator guide. Production cargo admission stays off in v1.8
(R682).
