# TASK W112-review: independent read-only review of W112 (cargo C-P part 2) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. A Codex session wrote W112, so this is a fresh Claude
session with no context from the author (AGENTS.md section 13).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w112` at `c03c5143` (base `9f6668aa`; record commit `160b5971`) in `D:/GitHub/arce-v180-w112`. Read it with `git -C D:/GitHub/arce-v180-w112 diff 9f6668aa..160b5971`, `git show` and the file reader; never check out, commit or edit there |
| Task it answers | [TASK-W112](TASK-W112-CARGO-C-P2-RESUMABLE-PREPARATION.md), including its "Root decision" on the snapshot hooks |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 2.3, 2.4 and 3.2 item 5 and the C-P row of section 6; ADR-065 section 4.1.1 ("Whole-snapshot work admission"); [records/W106.md](records/W106.md) ("Blocking preparation finding") |
| Plan and record | `docs/work/v1.8.0-root-queue/records/W112-PLAN.md` and `records/W112.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W112/verify-c03c5143/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W112-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Byte-identical hashes.** The streaming content and structure digests in `RocketSnapshotHasher`,
   `RocketSnapshotPreparation` and `RocketStructureSnapshot` must equal the one-pass hashes for every input: same
   canonical writers, same order, same framing. Look for any input where a split point changes the bytes fed to a
   digest (a record split mid-write, a buffered length prefix, a palette written twice). Confirm every prior pin
   (C-I's and the schema-1 and schema-2 pins) is unchanged in the diff, and that the new pins come from the old path.
2. **The codec witness.** `RocketSnapshotNbtCodec` gained an immutable-snapshot witness that checks a decoded snapshot
   "without synchronously rehashing the expanded palette". The TASK forbids skipping a hash or payload check to fit the
   budget. Decide whether the witness is only used where the hash was already computed over the same canonical bytes,
   or whether some path now trusts a stored hash it never verified. Ordinary decoding must still hash.
3. **The budget.** At most one whole-snapshot reconstruction per server tick, 16 MiB of charged bytes and 16,384 block
   visits, with no carried-over credit; every payload copy, native encode or decode and hashing pass charged. Check
   that a reservation is made before the work runs, that the actual charge can never exceed the reservation, and that
   native capture suppliers run only inside a reservation (`c03c5143`). Check the fairness claim: a maximum-size
   continuation cannot be starved by small requests, and small requests are not blocked forever by a stuck cursor.
4. **The cursor.** RAM only, fixed memory (no retained whole canonical buffer), discarded on restart, unload, removal,
   cancellation, session close and server stop. Look for a path that keeps a cursor, or a lock, after its rocket is
   gone, or that leaks one across a server restart in the same JVM (GameTest and integrated servers).
5. **The lock.** While a cursor exists, `RocketRuntime.resourceMutationAllowed`, launch and disassembly refuse, and the
   prior snapshot and hash stay authoritative. Read-only lock queries must not create runtime state. Is there any
   mutation path (inventory, fluid, item capability, command) that bypasses `resourceMutationAllowed`?
6. **Publication.** The permit is privately issued, same thread, same tick and single use; publication revalidates the
   after snapshot against the frozen digest and publishes snapshot, resource state, revision and hash together, through
   a permit-gated method and never a public setter. Can a partial publication happen if one step throws?
7. **Eligibility.** W106's `EscrowCargoEligibility` runs before sequence allocation, queueing, reservation or mutation,
   and again at final capture when an input changed.
8. **Production inertness.** Nothing in production admits cargo yet. Confirm no new code path runs outside tests and
   fixtures, and that the escrow hooks (`EscrowCoordinator`, `EscrowRuntime`, `RocketEscrowGate`) leave existing
   PROPULSION behaviour unchanged. Note anything that will conflict with W27c1, which also changes `EscrowRuntime`
   (branch `codex/v1.8.0-w27c1`, read only), but review W112 on its own.
9. **Tests and limits.** No existing test, assertion, limit or timeout was removed or relaxed (AGENTS.md section 5);
   list every edited pre-existing test and say why each edit is legitimate. Flag any class over 500 lines.
10. **The record** against the code: does it claim anything the diff does not do?

## Rerun

Export the subject with `git -C D:/GitHub/arce-v180-w112 archive c03c5143` into a new folder under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W112-review/` and, there only, run with JDK 17.0.7
(`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: `RocketCargoPreparationTest`,
`EscrowCargoPreparedCaptureTest`, `EscrowCargoPermitUseTest`, `RocketStructureDigestTest`, and the existing
`RocketSnapshot*`, `RocketStructureSnapshot*` and `EscrowCoordinator*` unit tests. You may add a probe test in the
export to check a finding; keep it in the output folder. Run no GameTest, `runData` or server, and only one Gradle
process at a time: memory is tight while another worker runs GameTests. Delete the export's `build` and `.gradle`
folders afterwards and keep only logs.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W112-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
