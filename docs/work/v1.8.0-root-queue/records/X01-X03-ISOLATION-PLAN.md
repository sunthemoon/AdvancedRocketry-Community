# Isolated test plans for the cargo residuals X01, X02 and X03

| Item | Value |
|---|---|
| Status | **PLAN, not started.** Root wrote it on 2026-11-13 under Root decision R436 ([OWNER-DECISIONS-20261113](OWNER-DECISIONS-20261113.md)). No destructive step runs until the owner confirms it; the steps that need confirmation are marked **[confirm]**. |
| Rows | [W326-MATRIX](W326-MATRIX.md), rows X01 to X03; [C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), section 8.6. All three are NOT_COVERED on C3. |
| Candidate | The runs use the candidate of the final evidence (C4 under R438, or a later one), never a locally built JAR. A dry run may use any JAR, but a dry run is not evidence. |
| Effort unit | Working days of one worker session, with machine time counted separately. |

## 1. What the three rows are

- **X01: OS crash and power loss.** The native cuts stop the Java process. Data that the operating system has accepted
  but not yet written to the disk survives a process stop, and it can be lost in a crash or a power loss. No run has
  tested that loss.
- **X02: storage, cache and close failures that cannot be observed.** E1 and E3 show that the mod holds on the storage
  failures it can see. They do not show what happens when the storage reports success but loses the data, or fails
  in a way that no Java call reports.
- **X03: the operator's whole-world repair after REBUILD_SOURCE (fluid unload).** `FF_REBUILD_SOURCE` ends in a
  protected FLOOR_CONFLICT. The register's recovery is an offline whole-world repair, but no run has carried one out.

## 2. Environment for X01 and X02

This machine has no hypervisor: Hyper-V and WSL are both off (checked 2026-11-13; virtualization is enabled in the
firmware). Both rows need a Linux kernel with device-mapper and FUSE, on disks that may be damaged. The options:

| Option | What it is | Covers | Owner action |
|---|---|---|---|
| **E1 (recommended)** | Enable Hyper-V on this PC and create a disposable Debian 12 VM, about 60 GB on D:. A second disposable Windows VM is optional. | X01's replay and real power-offs; X02 in full; NTFS behaviour if the Windows VM is added | **[confirm]** Enabling Hyper-V changes the system and needs an administrator and a reboot. Other virtualization software may then behave differently. |
| E2 | The owner's Debian 12 reference node, with loop devices only | X01's replay and X02's fault injection, but no real power-off | **[confirm]** Device setup needs root on the node (Minecraft and Gradle still run as a normal user, docs/15). Loop devices do not touch the node's own disks. |
| E3 | Do not cover X01 and X02 natively; keep them as residuals for the owner to accept | Nothing | A risk decision, not a test |

Every option keeps the test worlds on their own loop or virtual disks. Nothing runs against this PC's disks or the
node's root disk.

## 3. X01: OS crash and power loss

**Isolation.** The world lives on its own ext4 filesystem (xfs as a second pass) on a loop file inside the VM. A
crash or power-off damages only the VM and its loop files. The originals and hashes of every world are kept outside
the VM.

**Method A: deterministic replay (main evidence, no power cut).**

1. Put the world's filesystem on a `dm-log-writes` device, which records every write, flush and FUA in order.
2. Run a selected set of the existing native cuts on the candidate, unchanged, and mark the log at each cut's
   operation:
   - the bay's D1, D4 and D7 receipt writes;
   - the fluid and item cargo C2, C6 and C7 writes;
   - the ledger's first creation (N1);
   - the transfer journal's write and replacement;
   - one registry write.
   That is about 10 cases.
3. Replay the log to each flush point inside the marked window (the crash points a power loss could produce), about 10
   to 20 per case. Mount each replayed image and start the candidate twice. Apply the case's existing oracle:
   - each store is wholly old or wholly new;
   - no rocket, fluid or item is duplicated or lost;
   - protected roots are byte-identical;
   - any hold or REPAIR is one the design allows.
4. Also replay a small number of points that drop unflushed writes between two flush points: the reordering a disk
   cache may do.

**Method B: real power-offs (confirmation; destructive to the VM).**
**[confirm]** About 20 hard power-offs of the Linux VM (Hyper-V "Turn Off") at random times inside the same
operations. Each is followed by a boot, `fsck`, two candidate starts and the same oracle. With the optional Windows
VM, about 10 more cover NTFS and the Windows directory-flush limit that W11b2 records.

**Result.** A row per case and crash point: PASS, FAIL, or a hold that the design allows. A FAIL is a defect to fix
and test again. No oracle is relaxed.

**What stays uncovered.** A host power loss, and disk firmware that ignores flushes. The second is X02's subject;
this plan cannot cover it on real hardware.

| Part | Estimate |
|---|---|
| VM, JDK and the native runner ported to Linux | 1 to 1.5 days |
| `dm-log-writes` capture, replay and oracle integration | 1.5 to 2 days |
| Method A machine time (about 10 cases × 10 to 20 points × 2 starts) | about 1 day, unattended |
| Method B, after confirmation | 0.5 day |
| Report | 0.5 day |
| **Total** | **about 4.5 to 5.5 working days plus about 1 day of machine time.** On E2 there is no Method B: about 4 days. |

## 4. X02: storage, cache and close failures that cannot be observed

**Goal.** A classification, not a pass or fail. For each failure kind, X02 records one of:
- **(a) seen and held:** the mod sees the failure and holds or refuses as designed;
- **(b) silent but caught later:** the failure is silent, but a later cross-check catches it (for example, a ledger
  that falls behind the journal goes to REPAIR);
- **(c) silent and uncaught:** the failure is silent and nothing catches it.
Kind (c) stays a documented residual for the owner. Finding one does not relax an oracle.

**Isolation.** Faults go only into the device or FUSE mount that holds the test world, inside the VM (E1) or on a
loop device (E2). The VM's root disk is never a target.

| Failure | How it is injected | What is checked |
|---|---|---|
| Write returns an error (EIO) | `dm-flakey` with `error_writes`, or `dm-dust` bad blocks | The writer reports FAILED or UNKNOWN; the hold, log and retry are as designed |
| Write accepted, then lost (a cache that lies) | `dm-flakey` with `drop_writes`, then remount and restart | Whether cross-store checks catch the stale store, or the loss is silent |
| `fsync`, `rename` or `close` fails | A FUSE pass-through filesystem that fails chosen calls (for example `unreliablefs`) | `FileChannel.force`, the atomic move and the close: whether each reaches the writer's outcome |
| Disk full (ENOSPC) | A small filesystem filled during the write | Refusal and recovery after space is freed |
| Remount read-only during the run | `mount -o remount,ro` | The hold, and recovery on restart |
| Read returns corrupted bytes | Flip one byte in a copy, or `dm-dust` | The checksum or codec refuses; no silent acceptance |

Each failure runs against three stores: the ledger, the bay receipts and the transfer journal. The registry is a
fourth where its writer applies. Every case is restarted twice after the storage is healthy again.

**[confirm]** Fault injection writes damaged data deliberately, to test worlds only, and needs root in the VM or on
the node for the device and FUSE setup.

| Part | Estimate |
|---|---|
| Fault devices, FUSE layer and per-failure scripts | 1.5 to 2 days |
| Runs (about 6 failures × 3 or 4 stores × 2 restarts) | 0.5 to 1 day |
| Classification report | 0.5 to 1 day |
| **Total** | **about 2.5 to 4 days.** It reuses X01's VM and runner, so it costs about a day more if it runs before X01. |

## 5. X03: the operator's whole-world repair after REBUILD_SOURCE

**Isolation.** Everything runs on this PC, on worlds that `FF_REBUILD_SOURCE` produces. The repair works on a copy;
the original world and its hashes are kept. It needs no virtual machine and no fault injection, and it damages no
storage, so it is not a destructive test. It edits saved data, so it needs a design note and a separate read-only
review first (AGENTS.md section 13). The procedure is the owner's to approve before it is published as operator
guidance.

**Method.**

1. **Design note.** What the operator repairs and with what:
   - the escrow floor and record;
   - the transfer journal entry;
   - the rocket entity's saved form.
   The repair runs either through the existing audited settlement in a maintenance start, or through an offline tool
   with strict checks. The note picks one and gives the steps, the checks before and after, and the refusal
   conditions. It follows the register's recovery rule: keep every file and hash, repair a copy, two saves and
   restarts, then replace the whole world.
2. **Read-only review** by a different engine.
3. **Tool and native case.** A new family runs `FF_REBUILD_SOURCE`, applies the procedure to a copy, then starts the
   candidate twice. The oracle:
   - the cargo is conserved (5,000, 4,000 and 1,000 mB, as in W142);
   - no rocket or tank is duplicated;
   - the FLOOR_CONFLICT is cleared;
   - an ordinary unload and launch work afterwards;
   - every file the procedure did not name is unchanged.
4. A negative control: the same procedure with one check skipped must be rejected by the oracle.

| Part | Estimate |
|---|---|
| Design note | 0.5 to 1 day |
| Read-only review | 0.5 day |
| Tool or settlement path, and the native case | 1 to 1.5 days |
| Run and report | 0.5 day |
| **Total** | **about 2.5 to 3.5 days.** The C1 report's 1 to 2 days covered the procedure only; the native demonstration adds about a day. |

## 6. Order and what the owner confirms

1. X03 can start once W357's design has settled the fluid ledger's format, so that the repair is written once. It
   needs no confirmation.
2. **[confirm] The environment for X01 and X02:** E1, E2 or E3.
3. With E1 or E2: build the harness and run dry runs on healthy disks (preparation).
4. **[confirm] X01 Method A and X02's fault injection**, together or one at a time.
5. **[confirm] X01 Method B** (E1 only).

All three together, with E1: about 9.5 to 13 working days of worker time, plus about 1 day of machine time. X03 can
run in parallel with the VM work. With two workers that is about 1.5 weeks of calendar time, alongside the other open
work (R434, R435 and R438).
