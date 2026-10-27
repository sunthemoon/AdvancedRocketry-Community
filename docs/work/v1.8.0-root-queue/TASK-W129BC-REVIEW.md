# TASK W129bc-review: independent read-only review of the registry writer and the barrier callers

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w129bc` at `bb8e87b8d`, on `50a5aea17` (W129aM's merge of shared head `594cf3852` into W129a). Read `git diff 50a5aea17..bb8e87b8d` and `git log 50a5aea17..bb8e87b8d`. Review worktree `D:/GitHub/arce-v180-w129bc-review`, a detached checkout of `bb8e87b8d` |
| Task it answers | [TASK-W129bc](TASK-W129BC-REGISTRY-WRITER-AND-BARRIERS.md) |
| Design | [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) (accepted 2026-10-26, four conditions) and [records/W129-DESIGN.md](records/W129-DESIGN.md) revision 2, sections 2 to 7 |
| Record | `docs/work/v1.8.0-root-queue/records/W129bc.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129bc/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129bc-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The epoch rules.** Rules 1 to 7 and cases A to H of section 4.1. Every site of section 4.3 is stamped or checked
   before any mutation, including finite-long admission and refusal at MAX - 1 and MAX with RAM, items and FE
   unchanged. W119's no-mutation matrices are unchanged except the one named proof-test transition (`7690344c`):
   check that it keeps every safety check.
2. **The capture.** It is immutable and byte-identical to `save(CompoundTag)`, including at 16 MiB. Check
   `TerminalLocation` freezing and the collection and tag alias cases of section 2.1.
3. **The writer lifecycle** on the shared core. Check the stop path, final-target drain, budget, join before ownership
   release, watchdog interruption and the once-per-streak logging. Review the await-clock fix `79f2653a`, and check
   that awaits are reachable only on GameTest and release-test servers.
4. **Terminal barriers.**
   - The pending table: 256 entries, one per terminal and per satellite, data-action tokens.
   - REGISTRY_BUSY and REGISTRY_PENDING are answered before any mutation.
   - Slot locks through every item path, and breaking and explosion refusal.
   - At most 32 completions per tick, on loaded terminals only.
   - Holds for covered-but-unloaded, mismatched or missing terminals.
   - Normal-stop replay without effects.

   Look for any path that releases a usable package or chip without conservation, or that duplicates a satellite.
5. **Discovery** is never written before its paid receipt is committed (and `ecbbf19a`'s missing-mission replay).
6. **Data actions, rebind, release-test hooks and research's same-target seam** keep their tokens and results.
7. **Evidence.**
   - B1 and B3 are within docs/17, and the fair-permit measurement is reported, not enabled.
   - The native cuts R1 to R7 (both R7 variants), R3, R4 and discovery ran with two restarts each.
   - The runs used this branch plus W129aM's unmerged commits `50a5aea17..415e361b`: confirm the record says so.
8. **Nothing weakened.** Check the manifest total change (136 to 135) line by line, and the adapter-mod GameTest
   change. No saved format, schema, bound, budget or timeout changed.
9. **Merge.** List conflicts with W129a's head (`codex/v1.8.0-w129a`, `77ee8220e`), the shared head
   (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and W164 (`codex/v1.8.0-w164`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the 19 new or changed unit classes alone, the W119 matrices, and `SatelliteRegistryEpochTest` and
  `SatelliteRegistryProofTest`;
- the satellite, mission and rocket persistence packages.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; other workers
share it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle`
and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W129bc-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
