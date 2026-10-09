# Passive sleep observation contract review disposition 18

Date: 2026-10-09. Status: in-progress; no executable freeze or packaged launch.
This updates the pending driver/wire work in [checkpoint 08](SOURCE-REVIEW-STATUS-08.md).
The isolated source remains 5b45132000ade1e3c8d130a6b8ccb29f07535124;
Main's production sleep, dimension, spawn, time and air code remains unchanged.

## Complete reviews and disposition

Root reads the complete [independent contract review 16](OBSERVATION-DRIVER-INDEPENDENT-16.zip)
and [primary wire qualification 17](OBSERVATION-WIRE-QUALIFICATION-17.zip) before
their one-time seals. They are static inspections, not executed parser/tests or
native outcomes. Task 16's two Medium and one Low remain open:

| Finding | Required before executable assignment |
|---|---|
| Task16-R01, Medium | Pin actual stdout character encoding, Unicode/unpaired conversion and line framing to the exact JDK/argv/route; ASCII historical output is insufficient. |
| Task16-R02, Medium | Define finite capture/overflow storage, reserved failure receipts and write/flush/ENOSPC/stop behavior. A deadline does not bound spool volume; lost capture cannot pass. |
| Task16-R03, Low | Freeze native NBT modified UTF-8 and gzip member/trailer/exact-consumption rules, including bounded skipped strings. |

Task 17 establishes that the pinned patched Forge Bootstrap does not call its
retained stream-wrapper method. Raw println is the supported recipe; no logger
wrapper fallback is qualified. Actual later process charset is still open.
Native translation arguments that are not Components serialize as JSON strings,
including Java null/Boolean/numeric values. Style fields are flattened; combined
ResourceLocation strings can exceed a namespace/path field's separate cap.

The same primary inspection identifies an additional source-preflight boundary:
ClickEvent and EntityTooltipInfo can have overriding nested receivers, which
5b451320 does not reject before native serialization/getter behavior. No custom
receiver has been executed in the twenty-row fixture. Ordinary nested receiver
depth fits 64 (container depth at most 32), but this does not establish a universal
union or derive the proposed whole-trace 65536 node/token cap. Defensive parser
refusal must be a separate capture failure, not a native verdict or source closure.

The native codec chain statically establishes SpawnDimension as StringTag.
Spawn coordinates/forced/angle are IntTag/ByteTag/FloatTag; missing Spawn maps to
overworld/null/+0.0/false only for fresh actors. Native load can coerce numeric
tags and fall back on an invalid dimension; an exact typed offline parser must
not claim that native load rejects every value it rejects. StringTag uses Java
modified UTF-8 and has a logged empty-string fallback on oversized writes.
Actual saves, disk fields, flush/reacquisition and restart remain unexecuted.
Getter projections, per-row bed outcomes and restored teardown/logout saves
remain distinct evidence domains.

## Separate bounded assignments

- Task 19, status: in-progress. A fresh isolated worktree based on 5b451320 is
  assigned only Trace/native-chat preflight and optional focused helper/tests.
  It must reject unsupported nested receivers before overridable behavior,
  preserve ordinary receivers and all existing caps. Only three source compiles
  are authorized; tests, GameTest, driver and production code are not. Source
  identity, review and actually executed tests must be recorded separately.
- Task 20, status: in-progress. Static revision of the proposed driver/parser
  contract addresses these findings and keeps Task 19 unresolved until evidence
  exists. No executable/parser implementation, test, Java discovery or server is
  permitted. A revision is not automatically accepted or runtime-authorized.

No previous budget is enlarged, source finding waived, twenty-case result
prefilled or production sleep implementation assigned. B1/R1/M1/M2/D1 and the
owner's dimension/spawn/time freeze condition remain open. [Decision 03](OWNER-DECISION-03.md)
is the exact accepted ancillary scope, not broader migration or gameplay authority.

## Evidence and immutable clarification

The [Root publication checkpoint 07](OBSERVATION-ROOT-PUBLICATION-07.zip) covers
records through Main 30944465, not the later Task 16/17 conclusions. Its first
seal helper fails before manifest creation on the older publication-01 receipt
schema; a separately retained helper succeeds. An additive clarification is
necessary: its REPORT's claim that all command receipts include an owner AGENTS
digest is too broad. Original publication-01 commit/push receipts have raw-log
SHA and tracked status but no log_bytes/owner_agents_sha256 fields. Later
receipts have those fields; the final audit verifies current owner hash and old
raw-log hashes without reconstructing absent historical evidence. The original
sealed REPORT/receipts are unchanged. Root retains this correction separately.

| Archive | Compressed /expanded bytes | SHA-256 |
|---|---:|---|
| OBSERVATION-ROOT-PUBLICATION-07.zip | 68897 /120247 | 39fb79d03f971e693724de937dfa9f9fb2c2659d205d432e34285b92c786b760 |
| OBSERVATION-DRIVER-INDEPENDENT-16.zip | 314409 /861868 | 03db3c83f28223b9abd82edbe72c45b64a709f2f2788fc72dd4fc69ce4e73cb4 |
| OBSERVATION-WIRE-QUALIFICATION-17.zip | 551389 /2664384 | 935b097f36afe7d3dec435d23a90df9cde87dde65a0be4920ecc3c35bace35c9 |

Every covered leaf-relative hash, case-unique safe member, CRC and payload is
checked before/after rehosting. Task 16 explicitly excludes its post-seal result
and commands/52-seal receipts/streams from manifest coverage; Task 17 excludes
its post-seal result/stdout/stderr. Archive SHA covers all included members.
Root07 has no uncovered post-seal payload. Seventeen sleep archives total
5677163 compressed /32447830 expanded bytes, within repository evidence limits.
Static preparation failures/capture gaps are disclosed in the original reports.
No complete runtime library/source/world or peer build output is packaged.

This record runs no build, test, GameTest or observation. Actual earlier 2192
JUnit/twice DataGen/588 GameTest results remain candidate-specific in checkpoint
08; strict 180-second TIMEOUT, 62 ERROR /0 FATAL, denied peer-output retention and
fifteen historical untracked-ZIP link occurrences remain open. No cleanup retry
or ownership takeover occurs. No item, asset, ledger, Required Gate or release is
approved; all twenty observation rows remain unexecuted.
