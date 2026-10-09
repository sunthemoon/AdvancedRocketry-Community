# Checksum input development checkpoint 16

Date: 2026-10-10 (Asia/Taipei). Status: IN_PROGRESS. Source implementation is
normally committed/pushed in separate candidate branches; its exact three final
postimages are integrated/pushed at Main a38f1dc96444f6e92479a350833bd38a7bdc893b.
Runtime still binds actual 0cefe86e, not a Main-SHA or documentation-sensitive run.
All v1.8 Required Gates remain open; no release/ledger/ADR acceptance follows.

## Source and scope

Base: ed06dcc8b2e0ee036b1f41ad35f4d57ea250490f. Normally pushed candidates:

| Source | Actual development result | Later disposition |
| --- | --- | --- |
| 5049c27af79dd8aee5aa2505f0d20c1be6c22a4e | Initial bounded local/Git helper and validator adaptation; Root 39 checksum methods pass. | Independent18 reproduces Medium sorting and Low explicit-output updater regressions; this candidate is not accepted. |
| 804c4a188ddc07a7cc3ba46ea483a47ee376cd26 | Relative POSIX sorting and intentional output bookkeeping; Root 42 methods pass. | Independent21 reproduces a Low relative/absolute depth inconsistency; not approval of later source. |
| 0cefe86e79a872fd4dc24cb81d851c5f74ed7104 | Separate root and relative path depth admission; Root 43 methods pass. | Independent25 complete with no material source finding; standard subset passes, broad/strict time out. Exact source integrated at Main a38f1dc9. |

The full base-to-latest diff has exactly three files, 995 insertions and 158
deletions: scripts/validate_release_checksums.py,
scripts/release_checksum_inputs.py and tests/test_validate_release_checksums.py.
The original fourteen test method definitions remain unchanged. No Java,
resource, build, registered ID, player data, upstream asset or license change.
The src tree remains a7b7d83a67fb02403f5aaee9c2d21987a06b1f6a.

## Preserved contract and bounded checks

[Task16](CHECKSUM-INPUT-TASK-16.md) remains normative for admission caps and
non-goals. Index membership plus local bytes and union coverage remain; metadata
loading still supports path-only historical callers. Optional physical artifact
verification retains its original canonical/hash/schema regeneration predicates.
Ordinary admitted inputs retain render bytes and updater side effects. The
latest root bound is 64 absolute root components and the target bound is 64
relative components, not 64 total absolute target components.

The helper checks every ancestor before ordinary local/tool file use, observes
selected/opened/final file identities and parents, and rediscovers the evidence
tree. Windows cross-API creation/change ctime differences are not compared as
equal semantics; full same-API before/after comparisons remain. Real installed
Git executable hard links are allowed only for the selected tool, not evidence
files. Git output/record/timeout admission fails instead of truncating success.

This is ordinary observed-change protection, not an atomic filesystem sandbox,
hostile same-identity/ancestor ABA proof, atomic stdout disk quota, descendant
process containment or interruptible blocking OS/archive work. Delegated ZIP
construction/decompression remains a separate resource qualification obligation.
No maximum-scale memory qualification or complete artifact resource closure is
claimed. CI Python 3.12 has not been run; local receipts pin CPython 3.13.15.

## Actual commands and failures

Root16 first runs the actual unchanged base checksum implementation/tests: 14
pass. Development failures from Windows cross-API ctime, a legitimate installed
Git hardlink and ambient short/long TEMP spelling are retained, not rewritten.
Owned TEMP resolves the latter fixture mismatch. Fixed 5049 results: checksum
39 pass, repository 148 pass, adjacent 39 run with four existing artifact skips,
and ordinary default validation 37 entries / 36 files, metadata-only pass.

Root16 broad unittest discover and strict --require-approved-identity each hit
their unchanged 180-second window: classification 124, actual child exit 1,
no final summary. Their taskkill receipts differ (0 and 255); the strict partial
termination error is preserved. Strict records thirteen phase entries/twelve
returns and ends inside check_v002_g4_applicability. Preceding Markdown failure
is retained. These observations do not establish checksum causality.

Root19's first standard driver is not used as unchanged-ceiling qualification:
it lacks the intended offline/cache flags and uses incorrect general ceilings;
its ledger filename is wrong (actual exit 2), provenance lacks the stricter flag,
and three collectors lack individual managed/PID receipts. Its build/separate
test each actually report 2192 cases/381 XML/zero F/E/S, both data diffs are empty,
and all 597 required native tests pass; each native log has 62 unwaived ERROR
headers/zero FATAL. The pipeline still ends 1; raw records remain unchanged.

Correct Root22 standard verification is actually at 804c4a18, not the latest
candidate or Main. All thirteen managed command/collector receipts end 0 using
the intended flags and original per-cohort ceilings. Forced clean build and
separate test each contain 2192 actual cases/381 XML/zero F/E/S; two forced data
runs/empty diffs, all 597 required native tests/151 batches, accepted ledger,
approved provenance and whitespace pass. Native takes 224.346346 seconds, ending
17:40:27.564915 UTC on 2026-10-09. Both native logs retain 62 ERROR/zero FATAL.

Latest Root24 fixed checks at 0cefe86e: checksum 43 pass, repository 148 pass,
adjacent 39 run/four existing skips, default metadata-only validation pass.
Root27 actually ends all thirteen latest-SHA standard command/collector receipts
0 at the unchanged per-cohort ceilings. Build/separate test each contain 2192
actual cases/381 XML/zero F/E/S, two forced data runs/empty diffs and all 597
required native tests pass; ledger/provenance/whitespace pass. Native takes
249.111722 seconds, ends 2026-10-09T18:01:50.644945Z, and both logs retain 62
ERROR/zero FATAL. Older runtime is not rebound to this latest-SHA execution.
Latest broad Python and strict each retain classification 124 / child exit 1
after 180.277479 / 180.244561 seconds, with no final summary. Taskkill exits
0 / 128 respectively; the latter preserves two unsupported descendant
termination errors. Strict again has thirteen entries/twelve returns, ending
inside check_v002_g4_applicability with preceding Markdown failure. A later
ordinary PID observation finds all six explicitly named timeout/taskkill PIDs
absent; it is not complete historical descendant termination or a retry.

## Independent review and evidence custody

Independent18 reviews the actual initial three-file diff and reproduces both
regressions with ordinary inputs. Its first compatibility observer exits 1;
the later recording observer is not a compatibility pass. Independent21 reviews
the finite correction, runs the genuine unchanged base implementation (14 pass),
and independently reproduces the depth inconsistency. Its first ten-probe
launcher retains one failed CRLF literal assertion; a distinct later observer
records the actual unchanged CRLF input and equal parent/candidate checksum
output, without relaxing or retrying that original assertion.

Read-only verifier26 recomputes every sealed payload and all receipt stream
hashes: leaf18 has 49 payloads/50 whole files/303144 bytes; leaf21 has 42
payloads/43 whole files/196677 bytes. Both manifests cover every payload exactly;
all 22 receipts/44 streams and archived Git diffs/source hashes verify. Its
report is sealed (8 files/141702 bytes); Root fully reads its findings and
remaining original18 observer-source replay gap. Independent25 is sealed:
58 payloads/59 files/408126 bytes; Root readback verifies every payload and exact
coverage. Its original eight path-length setup errors and four artifact skips
remain, with a distinct corrected thirteen-probe pass and explicit extended-root
qualification, not an ordinary unprefixed 128-component proof.

Root external leaves are beneath D:/GitHub/ARCE-Task-Evidence/v1.8.0, with exact
names in Task16. Local leaves have 4 MiB retained caps and standard leaves
100 MiB; files/streams are bounded. No source tree/world/JAR copies are retained.
Both disks exceed 10 GiB before sustained commands. Original user AGENTS hash
c2448e9357ec77d062ab52ecefbb24724fb5c767fb4955a4cd23ef0efbd8ff09 is preserved.

Read-only custody23 identifies Medium log retention, receipt coverage and proof
volume gaps and a Low surviving-target status gap in an unexecuted helper.
The prospective revision narrows targets, requires exact receipt-name coverage,
checks retained log sets/hashes again before removal, adds proof/leaf admission,
and refuses a surviving target. Static review28 and its distinct capacity
addendum complete; the latter reserves 16 MiB and bounds terminal tables before
removal, without rewriting the first-snapshot Low. Exact earlier helper bytes
are reconstructed and verify their originally observed hashes before retention.
Only the reviewed amended Root22 pair is executed once, exit 0: eight targets,
seven removed/one absent, 5776 files/273589917 logical bytes; four logs/904537
bytes retained and hash checked, no survivors, final tracked status empty.
Sixteen per-target full-chain proofs, process/result observations and raw stdout/
stderr are sealed: 847 payloads/848 files/14314188 bytes; manifest
053f0b08e174835d5bc3b680afb54b77a27484d2429759cf939e05a9fe2113d7.
Earlier Root19 outputs/development scratch and all peer outputs stay excluded.
Independent30 recomputes every Root22 payload/receipt/XML/log/proof and inspects
the final latest-SHA helper binding, establishing no new material finding.
Its own three audit-construction failures stay nonzero; the corrected observer
is distinct. Root22 lacks a separate external cleanup-launcher receipt; original
Root20 ownership remains an external dependency not directly read by review30.
Those narrower proof limits are retained, not adverse outcomes or silently fixed.

After the finite review, only the frozen Root27 binding executes once, exit 0.
Ten targets/nine removed/one absent, 5784 files/282599796 logical bytes;
four logs/904518 bytes retained/hash checked, twenty full-chain proofs, no
survivors, empty tracked status. Its explicit cleanup-launcher.json records
actual native host/PID/argv/timestamps/exit/stream hashes without inventing a
separate child shell. The first console hashtable Select-Object display shows
nulls, but the persisted JSON contains the actual fields and is reread unchanged.
Only the two exactly owned timeout scratch children are additionally removed;
remaining Root24 development/key-check scratch has zero files/only empty dirs.
Old Root5049/Root16/19, development and all peer/user/global/refused targets remain
excluded. Latest standard packet: 850 payloads/851 files/14327357 bytes, manifest
32473a69946437f18355e8d3dd2e8d7383f278572f538e908609fc9a255592de,
report d0b702a780ea3e985fda80ba3a8ab814b1283bb39058d900dcc24ffa6d28d208.
Latest local packet: 33 payloads/34 files/166032 bytes, manifest
e8ad0186233430ef52dbbbe298eba1c4c6bb2dfdc733fdeb638746e3842f2052,
report 1d8bf40405ccb7dd28e0d71a6c4b59ceed1660c991eb999508696d9d98df7668.
Independent31 completes the bounded actual latest-packet/source-alias review,
establishing no new material finding. It independently recomputes every latest
payload and all 21 managed receipts/42 streams, both 381-XML/2192-case unit
collections, native counts, all twenty full-chain proofs and four retained logs.
The three postimages, thirteen declared input mappings and src tree match
0ce and Main a38 exactly; declared gradlew.bat CRLF checkout bytes remain
distinct from literal Git LF bytes. This is not Main-SHA runtime or independent
remote-push verification. Original failures/skips and prior observer failures
remain. Its raw-stream note preserves and corrects normalized-text length
labels without editing the original observation. Root reads the complete report
and rehashes all twelve payloads/exact coverage: thirteen files/348166 bytes,
report b75de3a63273a722d67790eeb4d47936aac59beaa10f9ebb4851b816032f5b80,
manifest 4cc43cce09bea839480673fd1f3fe9ce9eacd70dc7f7eccf644655f93410ad69.
The sealed leaf is checksum-latest-packet-review-20261010-31 under the same
external evidence root. No helper, source test, live process query or cleanup
was executed by this reviewer; no Gate or historical custody closure follows.
Root22's later cache presence is not falsified; its earlier Root20 absence is a
distinct record.
R11-OPS01/R14-OPS01 and old refused targets remain untouched, not closed by this
new ordinary-custody result. Root22/latest/peer packets are sealed; obsolete
Root development leaves and excluded temporary outputs remain separate unfinished
operational records, not new accepted deliveries or permission for Root takeover.

## Remaining work

Independent latest-packet/source-alias review is complete; central records retain
all failures and excluded output obligations. Do not rerun the selected cleanup
or edit sealed payload. C19 broad/strict qualification, portable committed
evidence references and delegated
archive resources remain open. Sleep production, offline JSON resource execution,
physical hatch/save integration, actual client/GPU/multiplayer/restart and other
v1.8 content/Gates are not implemented or accepted by this checksum task.
