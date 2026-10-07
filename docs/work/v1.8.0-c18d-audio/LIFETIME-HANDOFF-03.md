# CL18D-AUDIO-LIFETIME-03: author handoff

Status: **author-returned, unreviewed**. No verdict, contract freeze or Gate acceptance is
claimed or requested by this file.

## 1. Start record

- TASK: `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18d-audio/TASK-03.md`, read from
  the Root checkout. It is not present in this worktree at its base commit. Its SHA-256 as
  recorded by Root in
  `D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-audio-lifetime-claude-author-20261008-01/DISPATCH-01.json`
  is `6871601d7f186944f51a4faa35f081077c40055ac9e49562e9f542115dfe80ce` (registration commit
  `a8219e75d19d61d3056e3901dc99491d14f20b7b`). The author did not recompute it: no hashing
  command was available or allowed.
- Dispatch (from Root's DISPATCH-01.json, not re-verified): `claude -p`, session
  `d09df0d3-5861-48d1-911c-ae98cea8ef95`, started 2026-10-07T19:39:12Z (2026-10-08 in
  Asia/Taipei), tools Read, Glob, Grep, Write and Edit, budget cap USD 4.00, 48 turns.
- Model: `claude-opus-5-5`. Usage: the client budget display showed about USD 2.1 used before this
  file was written; the final figure is in Root's stdout capture, not known to the author.
- Worktree `D:/GitHub/arce-v180-claude-audio-lifetime-20261008`, branch
  `codex/v1.8.0-claude-audio-lifetime-20261008`. The session's injected status snapshot showed a
  clean tree with latest commit `2e397a49` (the fixed base). The author ran no Git command, so
  this is the harness snapshot, not an author observation.
- Owned scope: the three new files below. The evidence leaf above holds Root's dispatch
  records; the author wrote nothing there, because TASK-03's write_scope names only the three
  repository files and assigns hashing and sealing to Root.

## 2. Result

| File | Content |
| --- | --- |
| [LIFETIME-PROPOSAL-03](LIFETIME-PROPOSAL-03.md) | disposition of R2 M1-M4 and L2; validity separated from eligibility; always-on bounded discovery with a fixed frame and a progress-preserving cursor; batch merge admission; first-in-first-out validation with a derived 16-tick bound; Level-object input stamping; per-tick charges and counters; ports P1-P5 with the P2 discovery contract; C2 test changes; transfers |
| [LIFETIME-TEST-DESIGN-03](LIFETIME-TEST-DESIGN-03.md) | six independent instruments; 5 retention, 4 rediscovery, 5 progress, 4 fairness, 3 accounting, 3 reset, 2 hard-limit property, 2 port-conformance and 2 real-client cases; mapping of superseded C2 cases; none executed |
| LIFETIME-HANDOFF-03 (this file) | start record, reads, rationale, limits, commands, impact, remaining needs, release |

Sizes are estimated by the author to be below 24 KiB each; Root measures the actual bytes.

## 3. Rationale and limits

- **M1.** Distance is an eligibility property of the listener, not a validity property of the
  object; removing on distance was the defect. Retention costs nothing extra because the
  registry is already bounded at 1,024 and the merge evicts by distance from the current
  listener.
- **M2.** Running discovery every tick removes the flag whose clear condition R2 broke, and also
  covers missed binder callbacks. The alternative (generation-long drop memory) is documented and
  left to Root under A-OPEN-12; it is not silently rejected.
- **M3.** Fixing the frame at pass start and never resetting on movement gives a progress bound
  of `ceil(W / 128)` ticks per pass and `2T` discovery latency. The bound is in terms of the
  port's measured charge `W`, because no native enumeration cost is assumed.
- **M4.** A queue without snapshots gives the 16-tick bound by a short counting argument
  (P §4); R2's counterexample becomes T-M4-01 with expected tick 17.
- **L2.** Every traversal, copy and resume skip is charged; admission is one sort per tick at
  most. The comparator bound in T-W-01 is a derived proposal number.
- Limits: all bounds are hand derivations. Real packet, tick, reload, `Clone` and enumeration
  order, sound-engine behaviour and client tick time are unknown. The P2 mechanism is not
  chosen. A moving entity can wait up to 10 ticks for C2's periodic selection trigger.

## 4. Reads

Read in full: TASK-03; Root's DISPATCH-01.json; independent R2 REPORT-01 (by path; its SHA-256
`aa94db20...b6950771` from TASK-03 was not recomputed); CONTRACT-02 and TEST-DESIGN-02 in the
original author tree; TASK-02 in this worktree;
`src/main/java/.../celestial/visual/AmbientController.java` at the base; the worktree AGENTS.md
(sections 1-8, supplied by the session context).

Read in part: HANDOFF-02 (lines 1-60); main AGENTS.md sections 9-12 (lines 233-312) and its
section list; PROJECT-CONFIG.md (lines 1-40); ADR-066 §7.1 (lines 782-805); targeted searches of
docs/17 and the v1.8.0 version document for audio budgets.

**Not read (deviation from TASK-03's "section 2 mandatory docs"):** PRODUCT.md; docs 01, 04,
05, 06, 14, 15 and 16 in full; the rest of the v1.8.0 version document and docs/17; draft-01
CONTRACT, TEST-DESIGN and HANDOFF; the CLI AUDIO-REVIEW-02; the producer source files (their
facts are taken from C2 and R2, both of which cite them). The author prioritised the
lifetime-specific inputs within the 15-minute and 48-turn limits. A reviewer should treat
governance compliance beyond the cited sections as unverified. No upstream file was copied, so
UPSTREAM, NOTICE and docs 02/08 were not needed.

## 5. Commands and checks

No command was run: no shell, Git, Python, Java, JVM, Gradle, hashing, network, install,
cache, archive or JAR access. Tools used: Read, Glob, Grep, Write and Edit only. Edits were made
only to the three new files, before this handoff was finished. No patch, checksum or seal was
produced by the author; per TASK-03, Root records hashes and the seal after release.

**Unrun, not waived:** every T- case in LIFETIME-TEST-DESIGN-03; a reviewer's re-derivation of
the expected values; JVM/Gradle build, DataGen, cleanliness and GameTest; the real-adapter port
conformance T-P-01/02 and the real-client T-V-01/02; any line-ending, link or byte-size check
of these files.

## 6. Impact

- On C2 (proposal level only): replaces §4.2's `overflowed`-gated sweep, §4.4's
  distance-removal and snapshot round-robin, §4.5's stamping and order within a tick, §4.7's
  work bound, and adds the discovery budget to §9. C2's other sections are unchanged.
- On TEST-DESIGN-02: S04/S05 gain "retained"; R04-R06, V02, W05 superseded; W02's recovery
  path changes (mapping in the test design §9).
- On Root: port P1 needs the Level-object stamp (A-OPEN-10); port P2 needs a mechanism and
  conformance evidence; A-OPEN-12 gains the always-on policy choice and the 128 discovery
  budget.
- No source, test, asset, registration, network, save, build, ADR, ledger, status or CSV file
  is changed. No server carrier, rate allowance or placeholder is proposed.

## 7. Remaining needs

1. Independent review of the three files, including re-derivation of the bounds in P §3.2 and
   §4 and of every expected value in the test design.
2. Root decisions: always-on discovery versus generation-long drop memory; the 128 budget; the
   P2 mechanism; P1 stamping.
3. Transferred, still open: R2 M5 (W01, O03, S10) and the S06 timing note; R2 M6 coverage; R2
   L1 configuration-reload clearing, which must also reset the discovery frame and cursor; R2
   L3 one-shot ingress and moving sound position; R2 L4 evidence; reload ordering (A-OPEN-11);
   C2 A-OPEN-1..12 with their owners.
4. A consolidated successor contract, if Root accepts this leaf, so that C2 and this proposal
   are not read as two competing texts.

## 8. Release

The author holds no process, file handle, lock, Git HEAD, index or branch interest. Nothing was
written outside the three files named above. Root may record hashes, seal, move HEAD or remove
this worktree. The next step is Root's intake and an independent review; no next-version work is
recommended.

Handoff path: `D:/GitHub/arce-v180-claude-audio-lifetime-20261008/docs/work/v1.8.0-c18d-audio/LIFETIME-HANDOFF-03.md`
