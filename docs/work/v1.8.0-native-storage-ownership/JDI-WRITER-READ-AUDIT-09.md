# Native writer/read coverage checkpoint

2026-10-11. Product base: 5267f77cab1f60f9655031efd5291238ab0f5f8c.
Fixed clean observation checkout: 32cf63eaf6f65fc79753b3e0b010f2902c341efc.
This checkpoint records static findings and external offline tooling only. It
does not deliver a product fix, a new native run or any Required Gate approval.

## Findings

Independent inventory of the selected local CompoundTag.class (20,860 bytes,
SHA-256 4d7a1086bcd83817c6495bb068bff403df04bc9c04d03b3998e795e40a2cd6eb)
identifies 16 Map.put-calling methods. Fifteen typed overloads bypass generic
put. Existing five entry hooks omit typed/view/raw-map/pre-admission operations
and writer normal/exceptional outcomes. HashMap.clear increments modCount even
when empty; replacement need not. Structural-field observation alone does not
prove stable values, completed mutation or full writer coverage.

Observation 03 retains six normal read returns but only five arm payloads;
context 5's arm was aggregated away. No exact missing event number/time is
reconstructed. Its new disk-read root 40297/map 40298 was not admitted by the
old collector. The three published roots and three serialization entries remain
historical counts, not completed durable saves. Selected local bytes do not
prove hosted loaded-class identity or ordinary concurrent overlap.

## Scoped correction and actual checks

The external collector now admits a qualified nonempty read-return CompoundTag
using the context's previously captured worker metadata. It does not reacquire
worker/storage/path/mailbox objects, change root/request/event limits, expand
writer hooks, read key contents, invoke/pin/mutate a target or copy/serialize NBT.
Duplicate identity keeps the initial binding; changed map remains a reported gap.
Collector SHA-256:
8ed14538b3a1d67653b54f686f3ecc0ade95911e96b4b974a1362d8999efb556.

Root actually compiled 29 external classes (188,113 bytes), ran the unchanged
17-case suite and the new 33-case actual-handler/proxy suite. All commands and
original process exits were 0; all 50 cases passed. Original times were
1.2971376000205055 / 0.23908720002509654 / 0.32678519998444244 seconds. Each new
process Job verified 512 MiB before resume, with 256 MiB heap, 30+10-second
execution/retirement windows and 256 KiB per stream. Complete capture, no
overflow/error and zero owned descendants are recorded. No Minecraft/native,
GameTest, Gradle, target attachment or hosted CI was run for this correction.

Independent source, suite and result reviews retain narrower path coverage:
successful read-entry capture and fresh publication admission are not tested
end-to-end after the refactor; the snapshot mutation assertion does not
independently guard defensive copying; conversion return uses an already
admitted root. Root read the static reviews but executed without first writing
a separate disposition note. The later report is not a pre-execution approval.
These omissions are not closed by the passing cases.

## Evidence and remaining work

External evidence is retained at:

- `D:/GitHub/ARCE-Task-Evidence/v1.8.0/storage-writer-coverage-20261011-04`:
  reports, selected class/member identities, actual commands and support-copy
  bindings. Copied helpers are evidence, not an assertion of self-contained
  reproduction inputs.
- `D:/GitHub/ARCE-Task-Evidence/v1.8.0/storage-jdi-read-admission-20261011-04`:
  exact sources/preimages, independent reviews, compile/class bindings, all
  original receipts and logs, and the later Root disposition.

Both precede any separately scoped new native observation. Earlier sealed
observation and records packets remain unchanged. Main's protected owner files,
foreign processes and all worlds are untouched. No expected ERROR/WARN rule,
product assertion, timeout, schema or ADR is relaxed. Read-only path/search
errors and intermediate static failures remain disclosed in the evidence.

The initial record-review cutoff preceded result-review delivery and packet
sealing. Its two chronology findings are preserved. RESULT-REVIEW-01.md was
delivered afterward and verified the finite execution/class bindings. Root's
later wording correction and sealing are not pre-reviewed execution. Subsequent
Root-owned class/cache cleanup was rejected before execution and was not retried;
the 29 small compiled classes/cache remain in the correction packet. Original
process-retirement receipts are unchanged. A support-report revision detected by
the record review's binding check is retained as a failed intermediate check,
not hidden or treated as a passed earlier input.

Root later sealed and reverified both packets: writer audit 33 files / 594,117
bytes, manifest SHA-256
4eadb5e06769cc140b85fc0731a80eb89da80df60b422925c94b748771d15936;
read correction 109 files / 510,297 bytes, manifest SHA-256
eaac918c54c4c20e71d4b6e4548e6a9325a6147deaad2e64b8b0e569e35014b0.
Counts exclude each manifest. These final checks are Root post-review work.

Original CI 38064322640's root-map CME is still open: pending terrain context
mutation is statically reachable, not an identified branch/object/thread/culprit.
This cannot identify a BlockEntity or Save listener from a contemporaneous batch
label. Full writer coverage, causal linkage, cohort/checker and all G0-G9 remain
open. v1.8 stays IN_PROGRESS / IMPLEMENTING; no release/tag approval is made.
