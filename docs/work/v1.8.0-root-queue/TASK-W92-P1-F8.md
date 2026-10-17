# TASK W92: P1-F8, the durable disassembly record and the retained claim, from the frozen W85 design

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w92-p1-f8` (from the shared branch after the W11b2 merge) |
| Branch | `codex/v1.8.0-w92-p1-f8` (local commits only; Root merges) |
| Design (frozen) | [records/W85-DESIGN.md](records/W85-DESIGN.md) revision 3, the minimal option ("Design M"), frozen by Root on 2026-10-16 (merged `6f353d90`) |
| Leaf row | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) section 5: P1-F8; section 1's P1-F8 row |
| Record | `docs/work/v1.8.0-root-queue/records/W92.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W92/` |

W11b2 is now on the shared branch (merge `eff6eff3`); production admission stays disabled. W91 (part-1 N2 and P1-F3)
runs in parallel in another worktree and also changes the transfer journal; keep your journal changes small and
self-contained so that Root can merge both.

## What to do

1. **Recheck the design against the merged code first.** W85 read the code before the W11b2 merge. Confirm in your
   record that `RocketDisassemblyService.executePlayer` and `disassembleForReleaseTest` still release the landed
   reservation before `execute`, and that the sites W85 section 4 names are where it says. If a site differs in a way
   that changes Design M, stop and report it.
2. **Implement Design M** (W85 section 4): keep today's order (T's checked removal before any restored block), the
   no-change pre-checks, the one-way restoration latch, the queued write-back that keeps T and its locks until
   acknowledged, separate terrain and entity columns with the cooldown cut, zero-fuel requests, and the
   `DISASSEMBLY_PENDING` status branch with its English and Chinese text. No saved-format change beyond what W85
   section 4 names; if it names one, implement exactly that with its schema version.
3. **Tests** as W85 section 7 lists, including the native cuts on both sides of restoration, removal acknowledgement
   and release, a failed removal writer and two restarts (W85 section 5's crash points).
4. Run the whole unit suite, one full GameTest with the unchanged log checker (add an expectation only for a line your
   new test drives), and the native cuts. If you add a GameTest batch, say so (about 376 batch names now; about 384
   reshuffles the run order).

## Write scope and boundaries

`RocketDisassemblyService` and the sites W85 section 4 names, the language providers for the new status text, new
tests, and the record. No change to an acknowledgement order or a proof outside Design M, and no relaxed assertion,
limit or timeout. Stop and report if one seems needed.

## Commands

As WORKER-RULES says. **While `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` exists and its `owner.txt` names
`W11B2-C54`, start no Gradle and no Java at all** (continuation 54's benchmark needs a quiet machine); read and write
code meanwhile. After it is gone, compile and run unit tests freely, and take the heavy-JVM lock for
`runGameTestServer` and native runs. Check that C: and D: have more than 10 GiB free before each heavy start (C: has
about 13 GiB). If the tool policy refuses to let you remove your own lock or world, write the exact path in your record
and final answer; Root removes it.

## Not in this task

P1-F3 and N2 (W91), W90a and W90b, W11b3's other leaves, production admission, ADRs, the risk register, status files
and the ledger (Root).
