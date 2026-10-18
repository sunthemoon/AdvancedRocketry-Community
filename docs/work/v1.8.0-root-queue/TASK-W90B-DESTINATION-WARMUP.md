# TASK W90b: the governed destination warm-up (R21 item 4, R22 amendments 1 to 4)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) and [records/W90A.md](records/W90A.md) first. You are
a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w90b` (new; from the shared branch at `e80069c6`, which has W90a merged) |
| Branch | `codex/v1.8.0-w90b` (local commits only; Root merges) |
| Design (frozen) | [records/W90-DESIGN.md](records/W90-DESIGN.md) revision 2, section 7 and the warm-up parts of sections 9 and 10, with Root decisions R21 (`D:/GitHub/ARCE-Task-Evidence/root-queue/W90/ROOT-DECISION-R21.md`, SHA-256 `1322ab425a6aa87397f42abf49aac11f444beba6c89c38f5e61ddd49db1b91ee`) and R22 (`D:/GitHub/ARCE-Task-Evidence/root-queue/W90/ROOT-DECISION-R22.md`, SHA-256 `a10c8cd36ead8d03e516bc70700c3417b05f5ec906356cf1962e99d4e52006f2`); R22's amendments and answers override revision 2 where they differ |
| Leaf | R22's "Leaves": W90b = the destination warm-up with amendments 1 to 4 and R22's answers 1 to 6 |
| Record | `docs/work/v1.8.0-root-queue/records/W90B.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W90B/` |

## What to do

1. **Recheck section 7 against `e80069c6`** (revision 2 read W11b2 at `379525ea`; W90a's record lists the moved sites).
   Stop and report if a difference changes the design.
2. **The warm-up** as section 7 and R22 give it: the `arce_rocket_warmup` ticket type; WARMING, READY and BOUND; one
   warm-up per logical rocket; the global cap and the per-player limit counting WARMING, READY and BOUND; the lifetime
   and deadline; `DESTINATION_LOADING` and `DESTINATION_LOADING_LIMIT` with English and Chinese text; the kept pad
   (amendment 1); the refusal of the stored starting request ID while the warm-up lives (amendment 2); the release
   rules of section 7.6, including disconnect and removal ending only WARMING and READY, the handover after a
   successful ACTIVE margin refresh, and a `discarded` callback after `journal.put` that releases only the warm-up
   (amendment 4); the fresh revalidation before F1 (section 7.7); and the logs and inspect lines of section 7.8.
3. **Operator settings** (amendment 3): the global cap (default 8, range 1 to 8), the per-player limit (default 2,
   range 1 to 2) and the lifetime (default 600 ticks, range 200 to 600), in the existing server or common config. A
   change applies to the next allocation; live warm-ups keep their values and deadlines.
4. **The service tick.** On the shared branch the transfer service's tick still returns early while the journal is
   blocked (W90a's record: `:284-292`); W91, which is not merged yet, removes that early return. Place the warm-up's
   polling, deadline and release so that they run every tick whether or not the journal is blocked, in a small hook
   that merges cleanly with W91's change.
5. **Tests** as revision 2 section 10 lists for the warm-up, and the tests that R22 amendments 1, 2 and 4 name: the
   kept pad in WARMING and READY with an earlier reservation gone, a separately invalidated candidate, the immediate
   replay, a completed-ledger rollover while the warm-up lives, a runtime reset, the trusted admin entry, the
   post-publication discard, the cap and the per-player limit, disconnect, the remote-copy refusal and the in-process
   restart. Replace W90a's test-only warm-up in its border-crossing GameTest with the real warm-up, keeping its
   assertions. Run each new class alone, the whole unit suite, `runData` with a clean diff, and one full GameTest with
   the unchanged log checker (add an expectation only for a line your new code drives), all on committed source and
   serially. Say whether you add a GameTest batch (the hosted log shows about 439 batch names; a run-order reshuffle
   comes past 768).

## Write scope and boundaries

New warm-up classes under `rocket/server` or `rocket/transfer`, the governed launch path in `RocketTransferService`
and `RocketGovernedAdmission` (small hooks only), the pad selectors and staging for the kept pad, the request codes and
result, the inspect command, the config entries, the language providers, new tests, W90a's border-crossing GameTest,
and the record. W91 (P1-F3) is changing `RocketTransferService` and the transfer journal in parallel: keep your edits
there to a few lines. No saved-format change, no change to an acknowledgement order, a proof, a lease or the replay
exclusion, no source warm-up, no new post-F2 census hold (R22 answer 5), and no relaxed assertion, limit or timeout.
Stop and report if one seems needed.

## Commands

As WORKER-RULES says. Other workers (W94B, W91I, W91N) also run tests; take the heavy-JVM lock for every GameTest,
`runData` and native run, and release it between runs when another worker waits. Check that C: and D: have more than
10 GiB free before each heavy start (C: has about 13 GiB). When your heavy runs end, remove your own lock and your
`build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses,
write the paths in your record and final answer at once.

## Not in this task

The measured cold, border-crossing and largest-margin costs and readiness times (R22 answer 6; the native benchmark
before admission), a process-restart run, W91, production admission, ADRs, the risk register, status files and the
ledger (Root).
