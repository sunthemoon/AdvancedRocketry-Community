# Independent Source21 K2 integration evidence audit

## Findings and disposition

**0 Critical / 0 High / 0 Medium / 0 Low findings in the assigned frozen
integration-evidence scope.** The measured build, XML, static, GameTest and
external artifact evidence supports the compact packet's **development
computation integration** description with the qualifications below. This is
not an independent K2 implementation review, runtime replay, API admission,
release acceptance, or whole-version Gate verdict.

The reviewer previously reviewed Root's menu fallback and owner-mode inventory,
among other separate v1.8 work. The reviewer did **not** author K2 or perform
its earlier independent implementation review. Its source author's and source
reviewer's observations below are audited historical evidence, not new Java
executions by this reviewer.

## Exact input identity

- Compact packet: `D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip`,
  SHA-256 `9a9de4bf0f1bf41c7dabbfbbbfac48a89acdcccc919f9526349c8b7f527955f0`,
  **1,241,813 bytes / 423 entries**. All 422 manifested entries, sizes, SHA,
  unique safe paths and CRC verify. Manifest:
  `15959b5f7fc625a2694262fd6da99cb2204385d1192a2971d35076ac1de21d66`.
- Immutable code object: `3f3d62aed3980186fe0acc9592cf93ca436405fb`, tree
  `3fadd9b2e57093fa4c27ec4b599b7bd82e0ea0d5`. Named object queries, not
  moving HEAD, bind this audit. The recorded push has exit 0 and no force
  option; its raw log agrees with its receipt. Local HEAD and origin branch
  both named that code commit when observed. No remote network query was made.
- Named Root20 baseline archive:
  `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c16a-integration/ROOT-INTEGRATION-20.zip`,
  SHA `3b6176f549d8bde523df1245fe7c4902e4633f093c40c6bce87db78fbf364bd2`.
  CRC and all 3,402 manifested entries in its 3,403-entry archive independently
  verify. Its source manifest is byte-identical to the baseline manifest in
  the compact packet. Read in memory only, never copied wholesale.
- K2 author archive: `docs/work/v1.8.0-c16a-hatches/canonical-bytes-k2-source-01.zip`,
  SHA `3442f9b7c3fc06ff694c08f385a8cdfc8fc8b0a527a225b305e7cc7213788ace`;
  prior independent source review archive:
  `docs/work/v1.8.0-c16a-hatches/reviews/canonical-bytes-k2-independent-01.zip`,
  SHA `e4b46ec2657b66e88e8cf90c188bad5a7fe7c9f0a9f78c62906aa705bb0427e4`.
  Both CRC/complete manifests and actual committed two-file postimages verify
  in memory; see [prior packet crosscheck](K2-PRIOR-PACKET-CROSSCHECK-02.json).

## Source binding is qualified, not a 2,990-object commit claim

[Named-object measurement](SOURCE-MEASUREMENT-01.json) establishes:

- Root20 2,988 inputs → original Source21 2,990: exactly two NEW Java files,
  no removed or altered baseline input. `ClassicNbtCanonicalBytes.java`
  SHA `1aa5ad420a1288820f148ea55c149510c5206c7351c928186548cada54b3dfd6`;
  its test SHA `5b352b1a6818689b5ac30c505cbcfc3e3adc4014de97f26f168550b31f8cec55`.
- Original Source21 → R1 observation manifest: only externally maintained
  `AGENTS.md` differs. The original manifest, intermediate governance drift
  bytes and initial collector exit 1 remain distinct. R1 is **not** retroactive
  identity for the original whole baseline. Its observed AGENTS bytes are
  exactly the compact governance context snapshot.
- The named code commit agrees exactly with 2,986 R1 inputs. Three different
  objects and one missing historical-document object remain explicit:
  `AGENTS.md`; `docs/decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md`;
  `gradlew.bat`; and missing `docs/work/v1.8.0-c16a-hatch-core/API-ACCEPTANCE.md`.
  The two historical contract documents and external governance snapshot are
  not claimed to be committed by this **code** commit. Later Root documentation
  commits cannot retroactively change this evidence pin.
- For `gradlew.bat` alone, the tested Windows snapshot is 2,868 bytes,
  SHA `8e327fcb99d29ce0fe3ee2fec6e6a25de815a2df83a6a44a553dea89ffc92955`;
  the Git object is 2,776 bytes,
  SHA `18774a0ac0203f6893bf1db3291a0d77f06cd1b4dca2a43b51e7009da3e28cfa`.
  There are precisely 92 CRLF pairs versus 92 LF endings; replacing those
  CRLF pairs only yields the Git object. Both original identities remain
  recorded. This is not a general whitespace normalization waiver.
- All **1,210 main Java source** members in the external sources JAR are
  byte-identical to those named commit objects. No code/build-script/source
  export was created by the reviewer.

## Actual measured verification

| Root executed evidence | Independent measurement |
|---|---|
| Java17 offline clean build/test/runData | Exit 0; 193.987699 seconds between recorded start/end; raw `BUILD SUCCESSFUL` agrees |
| Actual XML | **1,750 tests / 329 suites / 0 failures, errors or skips**; each declared count and all 329 raw XML hashes agree with testcase/status elements and collector |
| K2 suite | **20 tests / 1 suite / 0 F/E/S**, the only added suite; Root20 has 1,730/328/0 F/E/S |
| DataGen raw output | Total 771, old 771, new-count field 772, stale removed 0, **written 0**; the new-count field is not claimed as 772 produced files |
| GameTest raw command | Exit 0, 242.978334 seconds; actual Forge GameTest userdev launch, **128 batch rows summing to 464** |
| GameTest terminal | Exactly one `464 GAME TESTS COMPLETE` and one `All 464 required tests passed` in native log lines 5546–5547; matching required-pass marker in Gradle log; subsequent server/players/world/chunk save and shutdown records |
| Five full static commands | Links, actual repository boundary21, planning, approved provenance and machine resources each exit 0; receipt log hashes/actual commands agree |
| Seven scoped commands | Strict ledger, common/client imports, 90 Python tests, artifact validation and diff-check exit 0; ledger closure **exit 1** and dirty diff **exit 1**, explicitly non-PASS |

The GameTest disk receipt records C free 10,264,489,984 bytes and D free
343,424,028,672 bytes before launch, each above its 10,000,000,000-byte
threshold. No new host launch was made for this audit.

The boundary wrapper uses the unchanged validator and applies the named private
directory exclusion only to untracked `ls-files` collection. Boundary21 versus
boundary15 differs only in output filename. The link17 helper versus link15
also differs only in output filename. These are **bounded wrapper executions**,
not an unmodified full-stock or release Gate claim. Link17 actually observes
675 files / 3,101 links / 0 errors and the intermediate AGENTS snapshot; it
does not cover later Root status/governance/document edits. Detailed raw
commands and exits are in [job measurement](JOBS-MEASUREMENT-02.json) and
[final checks](FINAL-CHECKS-06.json).

The scoped ledger closure retains 653 units, **186 PLANNED rows / 154 REVIEW
assets** and a FAIL result. Root's wrapper exit 0 for expected job outcomes
does not convert these two nonzero child commands into passes. Initial
AGENTS collection and evidence-relocation exit 1 are retained in selected
compact receipts, not independently rerun here.

## External artifact measurements

The three actual JARs were read directly at the compact SCOPE locations under
`D:/ARCE-Task-Evidence/v1.8.0/integration-072fbcf821ce4ceb9fbcb3d98ec27d41/integration-artifacts-21/`.
No JAR copy appears in this review bundle.

| Artifact | SHA-256 | Bytes / entries | Root20 content delta |
|---|---|---|---|
| Main | `796c9611ec8d655398e5a046bfe6cc3f81b2c7880a19c835ea9ed301a990373e` | 5,440,794 / 3,305 | Three K2 class members added; no removed or changed old file member |
| Sources | `1368d531681032c3c70fa320fbe7697dff3f124e668c4f17176707220abef9d2` | 2,620,612 / 2,661 | One K2 Java member added; no removed or changed old file member |
| API | `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da` | 51,045 / 48 | Entire JAR byte-identical |

CRC and unique members verify in each. All **771 exact v1.8 generated
resource inputs** agree in both non-API JARs, independently of the collector;
all old packaged recipes/resources/classes are byte-unchanged against Root20.
The unchanged committed `validate_build_artifact.py` was independently
executed against the actual main JAR, **exit 0** (metadata, notices, entry
paths and credential scan). See [artifact replay](ARTIFACT-REPLAY-05.json),
[raw output](artifact-replay-05.raw.log),
[artifact delta](ARTIFACT-MEASUREMENT-01.json) and
[correct v1.8 comparison](GENERATED-FRAMING-02.json).

## Finite ERROR contexts — not a clean log

The actual native GameTest log is
`4faba890bb108693e77d2f8ca04ea08e09c11cfdde5aee0fad1075103abf0095`,
1,646,986 bytes. **61 ERROR headers remain**. Each context is bounded by
the next real timestamped logger header, rather than inheriting later test
frames. [Context measurement and committed source lines](GT-CONTEXT-04.json)
establish the same category counts as the independently read Root20 log:

- 24 recipe guard refusals: actual synthetic save-event test calls at
  `RecipeSignatureSaveEventGameTests.java:37,40,62`, with sticky guard frames.
- 16 machine-menu protocol rejections: eight server / eight client version
  negatives, matching the unchanged explicit channel test.
- 13 Precision migration contexts: injected binding/one-shot save failures,
  their paired ChunkMap errors and three explicit disk-marker/root mismatch
  observations; test injector and retained-file assertions are source-pinned.
- Four satellite negative operations: actual deliberate pending-file directory
  obstacle / blocked registry test frames and assertions.
- Three unbound recipe errors correlate with the `recipe_signatures` batch and
  explicit unbound input/rebind test; these stackless rows are not an exact
  callback trace.
- One combustion guarded-save negative: actual oversized fixture test frame.

No K2 class appears in these ERROR headers or their captured project stack
frames. This does **not** prove universal absence of unrelated defects,
clean dedicated logs, native persistence, or a performance/soak waiver.

## Reviewer executions, preserved failures and limits

All work writes are confined to this fresh Temp directory. Commands actually
executed were Python intake/read/measurement scripts, named Git read-only
object/ref queries, the unchanged artifact validator replay and **12 neutral
evidence parser/identity controls (0 failures/errors)**. No Java, build,
DataGen, GameTest, native server, client, dependency or network operation
was independently launched.

Final successful scripts: `intake01.py`, `governance02.py`, `measure01.py`,
`audit04.py`, `gt_context05.py`, `replay_artifact05.py`, `final_checks06.py`,
`python -B test_evidence_controls07.py`, all exit 0. Earlier read helpers
also have their actual logs/exits. Reviewer wrong document filename,
historical generated-domain selection, timestamp selector, author manifest
filename and guessed GT class lookups are preserved in
[tool failures](TOOL-FAILURES.md); no failed observation is relabelled as a
Root production defect or passing execution.

Historical K2 XML independently confirms author **19/1 failure** followed
by **20/0**, and prior independent reviewer **28/2 suites/0 F/E/S**. Those
are not reexecuted here. Constructor negative-zero emission is not native
factory/load or persistence roundtrip fidelity. Package baseline identity and
regression passes do not supply missing K2 hash/codec/Guard consumers, world
authority, physical hatch/lathe, charged mode, real client/peer/GPU, crash,
restart or live native save tests. In particular, old **native20 is not
Source21 dedicated/native execution**. Current v1.8 all Required Gates
remain open; Root alone owns acceptance, ledger and status.

This loose report/log/result/manifest bundle contains no copied source tree,
JAR, baseline ZIP or duplicate review ZIP. Original selected inputs remain
at the exact locations above; [intake](INTAKE-01.json) and
[final checks](FINAL-CHECKS-06.json) bind the immutable bytes. The completed
scope is the compact integration-evidence audit only; remaining work belongs
to the current version's explicitly separate consumers and runtime/Gate leaves.
