# TASK W113: measure the options for cargo receipts that do not fit (a decision note for Root)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 3.3 ("Receipts and accounting", "Reservations" and "Size measurement prerequisite"), [records/W100.md](records/W100.md)
(C-K's size report), and W110-R1 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W110-review/REVIEW.md` first. You are a
new session. You write no product code in any repository worktree: this is measurement and a proposal.

| Item | Value |
|---|---|
| Subject | Shared branch `codex/v1.8.0-classic-content` at `adfc87fa` (fluid cargo C-K merged), plus W110's item kinds on `codex/v1.8.0-w110` at `76f73841` in `D:/GitHub/arce-v180-w110` (read only) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W113/DECISION-NOTE.md` (at most two pages), probes and logs beside it, and its SHA-256 on the last line of your answer |

## The problem

Every cargo operation is charged five rocket markers and a receipt of at most 2,048 bytes (`EscrowReceiptCodec`).
W110-review measured the longest Level ID for which a cargo receipt fits:

| Kind | Unguarded | Transfer-guarded |
|---|---:|---:|
| ITEM_CARGO_LOAD | 31 | 19 |
| ITEM_CARGO_UNLOAD | 28 | 15 |
| FLUID_CARGO_LOAD | 36 | 24 |
| FLUID_CARGO_UNLOAD | 33 | 20 |

The mod's own Level IDs are 30 to 36 characters (`advancedrocketrycommunity:moon` to `:tau_ceti_g`), and a landed
rocket keeps its transfer record until disassembly, so its cargo operations are guarded. Under the frozen bounds,
cargo on a landed rocket in a mod dimension never fits. The design says an allowance or format change returns to Root.

## What to deliver

1. **Where the bytes go.** For the worst guarded case of each kind and direction, with 0 to 4 retained markers plus
   the candidate, a byte breakdown of the receipt (and of the complete operation, head and root, which have their own
   12 KiB, 1 KiB and 1 MiB ceilings) by field: how many times each Level ID, UUID, signature, the actor name, the
   sticky reason, `before_choice`, `survivors` and `missing` appear, measured with the real encoders. Check fuel fills
   too: does a guarded fuel fill fit on the Moon today?
2. **The options**, each measured, not estimated:
   - (a) a larger receipt ceiling (for example 4 KiB), and what follows from it: root capacity in receipts, the
     complete-operation ceiling, Root-R6 headroom, Root-R8 ceiling charging, flight tables, any per-tick budget, and
     whether old fuel receipt bytes and pins stay identical;
   - (b) a tighter worst-case actor charge (what the actor is, where it comes from, and what bound is real: a player
     name is at most 16 characters, a command source name is not);
   - (c) a smaller marker allowance for cargo admission (how many markers a cargo operation really needs, given
     section 3.2's one live cargo operation per rocket);
   - (d) a receipt layout change (for example Level IDs written once), with its schema and dictionary consequences;
   - and a bound on Level ID length, if one is needed for any option to give a guarantee.
   For each: the longest Level ID that then fits, guarded and unguarded, for all four cargo kinds and the fuel fill;
   the code, test, pin and document changes; and the risks.
3. **A recommendation** that makes every cargo kind fit, guarded, in every Level the mod registers, with a stated
   margin, and that changes as little saved format as possible. Note that the escrow root is unpublished and production
   admission is disabled (R-035), so no saved world holds a receipt yet.
4. Note separately that a permanent size refusal is reported today as `ESCROW_FULL`, where design section 2.3 names
   `CARGO_TOO_LARGE`, and say where C-E should map it.

## How

Export each subject with `git archive` into a new folder under `D:/GitHub/ARCE-Task-Evidence/root-queue/W113/` and,
there only, add probe tests and run them with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and
`gradlew.bat --offline`. Run no GameTest, `runData` or server, and only one Gradle process at a time: memory is tight.
Delete each export's `build` and `.gradle` folders afterwards and keep the probes and logs.
