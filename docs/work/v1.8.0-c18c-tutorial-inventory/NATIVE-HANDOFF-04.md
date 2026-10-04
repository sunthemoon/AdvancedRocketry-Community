# C18c native Task04 phase01 handoff

Date: 2026-10-05. Worker: delegated implementer. Integrator: Root.
Scope: pure Python primitives only; independent source review is still required.

Candidate03 adds one explicitly clarified parser behavior to candidate02:
empty or ASCII U+0020-only physical lines have no logical entries. Raw bytes/hash
still retain those spaces. Controls, comments, data, escapes, ceilings, bindings,
acknowledgement grammar and command selection do not change. One old `b" "`
negative subcase is replaced by positive coverage of the clarified rule; all
other original assertions are preserved and three focused methods are added.
Candidate01/02 postimages, patches, reports/manifests, old 36/0 and CLI exit2
receipts remain immutable. Candidate02's stable public wording remains; this
candidate's Root clarification is recorded only in internal task/evidence notes.

## Completed scope

The isolated worktree remains at committed base
`d04a116e6e6001f166bbf5e8f31fb78623a6b392`, branch
`codex/v1.8.0-classic-inventory-native`. Four new files implement and document
only the phase01 boundary in [progress](NATIVE-PROGRESS-04.md).

- `scripts/run_v180_classic_inventory_smoke.py` decodes bounded raw properties,
  retains immutable bytes and a newly owned immutable logical map, checks pure
  configured/boot/live/stopped bindings, selects the sole seed command and
  parses the exact source-derived single/multiple acknowledgement payloads.
- `scripts/test_run_v180_classic_inventory_smoke.py` contains 39 pure unit tests
  with additional bounded subcases, including all 121 canonical ChunkPos pairs
  in both native acknowledgement forms. These are parser tests, not 242 native
  observations. The raw C0/DEL/C1 interpretation follows Root's explicit
  2026-10-05 task message; supported escaped controls remain distinct.
- This handoff and [progress](NATIVE-PROGRESS-04.md) declare authority, write
  inventory, unfinished work and evidence. No existing tracked file is changed.

All code is newly authored for this test-only slice. Named existing source and
normative proposal facts were read for behavior; no upstream code/art is copied.

## Original candidate01 verification

`python -B -m unittest discover -s scripts -p test_run_v180_classic_inventory_smoke.py -v`
executes 36 tests, exits0, reports 0.019 seconds and no failures/errors/skips.
The command uses process-only D TEMP/TMP/TMPDIR. Static source/scope and patch
replay checks, postimage hashes and named input postchecks are recorded in the
small loose evidence manifest, not inferred from the unit result.

Direct script invocation intentionally exits2 with an explicit incomplete-driver
message. It cannot launch Java, configure/copy a host, issue even the fixed
command, create a receipt or claim two-host completion. This refusal is tested;
it is not a failed native run or an executable native fixture.

Failures retained separately: the first inline intake command had a quoting
SyntaxError before reads; corrected file-based intake exits0. An earlier guessed
`INTERNAL-MANIFEST.json` lookup was corrected to the actual
`EVIDENCE-MANIFEST.json`. One progress-edit apply_patch context mismatch made no
change; its corrected edit succeeded. None is relabelled as a parser/native pass.

## Not completed and limits

No Java main/test files, central registration, actual copied-world driver,
bounded filesystem/reparse read, logger-origin/freshness transport, JSON/NBT
record parsing, cache reflection, ownership receipt, player construction/join,
listener/reload/stopped records, native host selection or disposal is implemented.
The properties functions accept already-read bytes; they do not prove that a
future file read is limited to 16,384 bytes plus one overflow observation.

JSON root-depth/object-key/node accounting remains undecided. The progress
record identifies existing validators' concrete conventions but does not adopt
them for protocol03. A properties logical comparison is not a canonical config
hash, native gameplay setting change or permission to repair an ineligible input.
Acknowledgement coordinates never affect commands; the parsed last changed
ChunkPos is not a count, authoritative origin, fresh event, membership or load
observation. Runtime deadlines and all original native oracles remain unchanged
and unexecuted, not silently renewed or waived.

Root's 2026-10-05 clarification defines blank lines as empty or only ASCII
U+0020. Candidate03 applies that exact rule after unchanged C0/DEL/C1 refusal.
TAB/form-feed/NBSP/other Unicode whitespace are not blank, and indented comments
remain unsupported. No property data is trimmed. The old candidate02 empty-only
choice and refusal assertion are retained in immutable previous evidence, not
used as normative authority or relabelled as compliant with the clarification.

## Candidate03 verification

With the new tests and unchanged candidate02 production, fresh 39 methods
exit1 with seven `PROPERTIES_SEPARATOR` error records. The minimal blank-line
condition correction then gives fresh 39 methods /0 failures/errors/skips,
exit0, unittest 0.020 seconds. Red/green exact Python input copies and raw logs
are separate. The old 36/0 results remain candidate01/02 results only. New
postimages, creation patch, candidate02-to03 patch, actual CLI exit2 and
baseline/prior-payload postchecks are separately bound in candidate03 evidence.
This partial parser result cannot establish any actual native host observation.

## Integration and rollback

Root can review/apply exactly the four new postimages after independent replay.
No central integration is needed to use these pure primitives in source tests;
central hook registration must be separately assigned after fixture source exists.
Rollback is removal of these four newly introduced files only, before any
dependent driver is admitted. There are no source-world/config/player-record
writes to undo. This worker does not stage, commit, push or delete its worktree.

The next C18c work remains the exact adopted Task04 fixture/driver dependencies
listed above, including a reviewed explicit JSON accounting definition. Current
v1.8 Required Gates and normal-server/client/physical API acceptance are not
satisfied by this partial handoff.

## Evidence location

Compact loose evidence is under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-native-impl-01-598dc248af`.
It contains raw commands/logs/exits, intake and named input pins, four source
postimages, a creation-only patch, patch replay result, final scope/postcheck,
report and manifest. It excludes whole-source export, build/JAR/runtime copies,
old sealed archive duplication, native worlds and C outputs.
