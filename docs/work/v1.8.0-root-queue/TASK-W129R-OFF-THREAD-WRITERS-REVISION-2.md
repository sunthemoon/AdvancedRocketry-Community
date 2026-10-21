# TASK W129R: revision 2 of W129's design note and proposed ADR-074, under Root decision R53

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [records/W129-DESIGN.md](records/W129-DESIGN.md),
[ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md), [records/W129.md](records/W129.md)
and the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W129-review/REVIEW.md` (SHA-256 `5f5ba8a1…cf11`) with its
model, probes and bytecode extracts in that folder, first. You are a new session. **This task revises documents only:
change no Java, script or test file.**

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w129r` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w129r` (local commits only; Root merges) |
| Output | revision 2 of `records/W129-DESIGN.md` and of ADR-074 (still **Proposed**), a "Revision 2" section in `records/W129.md` answering each finding |

## Root decision R53 (2026-10-22)

W129-review blocked the note and ADR-074 as implementation contracts and endorsed the direction (an off-thread checked
writer per file, a bounded reference capture, the snapshot-epoch rule). Revise both under every finding:

1. **W129-R1 (High):** the shutdown predicate covers both lifecycle flags (`server.isStopped() || !server.isRunning()`,
   as the transfer and escrow adapters already do); shutdown effect suppression holds on the exception path as well as
   at `ServerStoppingEvent`; correct the watchdog claim (`ServerWatchdog` tests `isRunning()`) and state what is
   guaranteed if the watchdog interrupts teardown. The test plan includes a thrown tick exception with
   `isRunning() == true` and `isStopped() == true`.
2. **W129-R2 (High):** the drain finishes only when a COMMITTED capture covers a final target sequence set after logout
   mutations, not at the first success; pending work and dirty state survive an older success; one shutdown retry budget
   spans the in-flight attempt and fresh attempts across `save` and `close`, and its exhaustion is reported. Replace the
   asserted worst-case durations with measured estimates or say they are unmeasured; never release world ownership while
   an IO thread may still replace files.
3. **W129-R3 (Medium):** list `SatelliteRegistryProofTest` lines 120 to 137 as a reviewed semantic transition in ADR-074
   (E becomes w, the later stamp equals w, the later mutation stays unproved and dirty, the next capture advances to
   w + 1), with its mutation moved to the owner thread behind a paused IO seam; every no-mutation matrix stays unchanged;
   correct the record's "every existing expected value unchanged" claim.
4. **W129-R4 (Medium):** the overflow check admits a stamped mutation only when an epoch strictly above the stamp it will
   receive, including the active capture's w, is representable; explicit capacity refusal, never wraparound or a
   permanently pending job; MAX − 1 and MAX fixtures in the test plan.
5. **W129-R5 (Medium):** the memory gate moves before W129a's merge (its new overlap with the transfer writer and the
   resident roots under Xmx4G), and the full three-writer measurement before W129b and W129c merge, with a paced tick
   surrogate recording missed deadlines and GC stalls; the stop-on-miss rule stays; a shared write permit is a remedy if
   the results need it.
6. **W129-R6 (Low):** `MissionPayload.TerminalLocation` freezes its position at construction (an implementation item in
   W129b with its test); the note audits nested mutable types in the captured roots.
7. **The review's Q1 to Q8 recommendations:** adopt them in the note, or say where and why you differ, for Root to
   decide.

Then Root has the revision re-reviewed by a fresh session focused on these seven items, decides ADR-074's acceptance and
queues W129a to W129c. Not in this task: code, ADR acceptance, the risk register, status files and the ledger (Root).
