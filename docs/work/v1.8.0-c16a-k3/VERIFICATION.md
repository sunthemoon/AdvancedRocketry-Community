# Private canonical NBT digest: integration evidence

Date: 2026-10-04. Development code is committed and non-force pushed at
`1ece7e9d2515003ca705b5634ef654a6bcae8f89` on
`codex/v1.8.0-classic-content`. No release candidate or Gate is approved.

## Scope and source identity

Exactly two new files add a package-private SHA-256 calculation over unchanged
K2 canonical bytes and its tests. [Qualified source acceptance](SOURCE-ACCEPTANCE-01.md)
records independent source review and narrower author/reviewer checks. The
calculation has no resource consumer, GuardTicket, public API or persistence
writer. K1/K2 eligibility, limits and stable exclusive input ownership remain
unchanged. Finite test cases do not prove general collision freedom, and the
unavailable-provider branch is statically reviewed rather than fault-injected.

The tested development snapshot, named Source22, has 2,992 pinned inputs:
all 2,990 inputs of the preceding governance-qualified K2 snapshot are unchanged,
plus the two reviewed additions. Root verifies the committed two-file postimage
and all snapshot inputs after the actual build and phase commit. This is not
literal equality of every input to a Git object: user-maintained AGENTS.md and
the separately disclosed wrapper line endings retain their original identities.
The source JAR's 1,211 main Java members match the named code commit.

## Actual commands and results

Java 17, Forge 47.4.10, offline, no daemon, two workers, 2 GB Gradle heap:

```text
gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G clean build test runData
gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G runGameTestServer
```

- Build exits 0 in 179.501121 seconds: 1,765 JUnit cases in 330 suites, with
  zero failures, errors or skips. Both compilation tasks use the recorded cache;
  test executes. All 771 generated files remain exact, with zero repeat writes.
- GameTest exits 0 in 240.231154 seconds: all 464 required cases pass. The
  native-runtime log is 1,645,636 bytes, SHA-256
  `c2971afd007ae07986031817379567133bf5639ddc9070be9cef7aa5903213ea`.
  All 61 ERROR logger lines remain disclosed, not a clean-log or blanket waiver.
- Focused Python checks pass 90 cases. Common/client separation, strict ledger,
  planning, approved provenance, machine resources and artifact validation exit
  0. `git diff --check` exits 0. Ledger closure and the original precommit
  `git diff --exit-code` each exit 1; neither is relabelled as passing.
- Fresh full repository validation exits 0 in 343.217632 seconds: 45 passed,
  zero pending/warnings/failures. It uses the unchanged validator with only the
  literal private untracked-directory Git inventory exclusion; assertions,
  tracked inputs and budgets are not relaxed. Original validator, AGENTS.md and
  external risk-register pre/post hashes are exact. This bounded wrapper is not
  an unmodified direct-command or release-Gate claim. The earlier draft link
  check covers only its individually pinned inputs. Publication requires a
  separate final current-link/scoped-validator check; its commands, results and
  input pins are preserved in `FINAL-DOC-CHECKS-02.zip` alongside this record.
  The immutable `FINAL-DOC-CHECKS-01.zip` predates the later owner-allocation
  status update and is not relabelled as coverage of those new bytes.

Actual artifacts are preserved externally, not duplicated into Git:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Main | 5,441,840 | `26356660ef03e9e938067e8bb53ac085290300cdf947a27d0d85dd6c1355682a` |
| Sources | 2,621,320 | `b7ae68c8066261d3d23fe5bddf3cce84a3967bd93d178f3c075db3395d0c4582` |
| API | 51,045 | `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da` |

The main and sources JAR each add exactly the new helper member; every old
member remains byte-identical to the preceding artifact. Both contain all 771
exact generated resources. The whole API JAR is unchanged. No packaged native
server, real client, GPU, multiplayer or crash-recovery run uses this new main
JAR; Gradle GameTest is not that separate acceptance.

## Evidence and storage boundary

Original commands, logs, XML, results and input manifest are external at
`D:/ARCE-Task-Evidence/v1.8.0/k3-integration-01/`; the immutable JARs are in its
`artifacts/` directory. These are local external records, not uploaded release
attachments. The [compact Root regression packet](ROOT-INTEGRATION-22-COMPACT-01.zip)
contains 404 entries, 741,221 bytes; SHA-256 is
`ac9b47b78f4cbce140b235974d4e68e2587083c87159e9ec19a634b48a35081d`,
and internal manifest SHA-256 is
`00c51fdff0573284a1a177cec7bfacde078a91704feb972f4bad2e594add7325`.
Root verifies all CRCs and manifested lengths/hashes. Only recorded commands,
raw logs/XML, results and input pins are included, not artifacts, complete
sources or another agent's evidence bundle. Its draft link inputs do not claim
coverage of subsequent documentation edits; final links are checked separately.

The [independent source packet](reviews/source-review-01.zip) has 147 entries,
1,093,973 bytes and SHA-256
`1b36df461aa12cab58ba4dbfd9c6a1a24bfd022d55fbe5c04372818e1c6265a1`.
Its original internal manifest SHA-256 is
`f7f0dc8cf5f78213456c70694ff39d5ef165968cc13d6966e796f59f5aadf133`.
Root verifies every original payload and CRC. This newly compacted packet does
not rewrite the reviewer's original loose evidence, package a complete source
tree or include JARs. Different-agent Root regression evidence audit is complete
and qualified below; it is separate from source review and runtime replay.

## Independent evidence disposition

The [frozen different-agent regression audit](reviews/integration-evidence-01.zip)
records no introduced Critical/High/Medium/Low integration-evidence discrepancy
in its exact completed-command/artifact scope. Report SHA-256 is
`1771d4c397e1b02efab8d73a4d7c1aa7cae06a617f471b70e5b60272f594b017`;
original internal manifest SHA-256 is
`ea1025f33fe07cd51ff01586d7d55f2c984948a686a10b793413a69e609aba33`.
Root verifies all 118 manifested payloads plus the original manifest/checksum
companions. The newly Root-compacted packet has 120 entries, 855,925 bytes,
SHA-256 `e0c54a81d7a64a187ee05ab96f84ffff16660ea3388cb3ba074b759702bd7da3`.
The reviewer's loose original is unchanged; the reviewer did not create that ZIP.

The reviewer independently measures raw XML, both completed GameTest logs,
fresh repository/static receipts, all old/new JAR members, the fixed commit's
1,211 main Java blobs and new test, and the preserved source/regression packets.
Its parser controls include 13 source/XML/ZIP cases and 10 strict GameTest
marker cases; these are not additional product tests. Original failed locator
and CRLF parser runs remain failures beside corrected separate executions.
No Java, GameTest or native server is replayed by this evidence auditor.

A separate [documentation review](reviews/documentation-review-01.zip) records
two Low task-progress observations, not code findings. Root corrects the stale
pending summaries without changing the calculation contract; the original
report and input drift remain preserved. Report SHA-256 is
`af3e4eb6deff76221778a9f1e257ee9224c9edb03b51b798fdfbb3b0ce5ffb6f`;
internal manifest SHA-256 is
`b1f969d50d8ca8f6b9f086231b9f20f0593bb81dadc8f03511fd35fc1e4146b6`.
Its new Root-compacted packet has 73 entries, 311,612 bytes, SHA-256
`2a542ab49c4957f2b58c25c908d695fd900b6855bba2e3a97b719109a320d9bf`.
That reviewer authored K3 earlier: this is explicitly not source self-review or
a replacement for the separate source reviewer and final evidence auditor.
The [separate correction addendum](reviews/documentation-postimage-01.zip)
records no new findings in the checked postimages and confirms the original
Low corrections and actual first final-check packet. Its report SHA-256 is
`8a13e1befa335a09fd59aa37554a51fd3a3053177082504ce2bc9ef428e6d84b`;
manifest SHA-256 is
`803dcce57e1e63b68bebd9155d2c779b25fbccc000a2893d39baa53aaabb7474`.
Root preserves all 21 payloads and two companions in a new 23-entry, 30,009-byte
packet, SHA-256 `7367eb4f58ecdb58e7ee91a21704da20b3b5a7217c9ee9482ab56e7472465ef1`.
The later narrowly allocated R-021/ADR work is explicitly outside that frozen
postimage check and is not adopted as a K3 save-policy or runtime change.

## Open findings and acceptance limits

The separate [save-refusal disposition](../v1.8.0-c16a-save-guard/SAVE-REFUSAL-DISPOSITION-01.md)
retains two Medium disclosure/ADR findings against unchanged common guard code:
the 257th distinct refused chunk expands denial throughout a ServerLevel, and
historical refusal emits paired ERROR headers without a guard log quota. Dirty
state does not prevent unload or ensure cross-store durability. No reset,
quarantine, stop, discard or repair policy is selected or accepted here.

Source-review tooling/parser failures and their corrected separate executions
remain preserved. Historical failures and oversized local archive debt are not
erased by this successful build. User-maintained AGENTS.md, newly external risk
register edits and the private document bundle are not silently integrated.

Full hash/frame/native codecs, resource consumers, Guard lifecycle and
first-save/final-unload proof, physical hatches, charged-mode interception,
acquisition and C16b-C19 remain unfinished. The ledger still has 186 PLANNED
units and 154 REVIEW assets. v1.8 is IN_PROGRESS /IMPLEMENTING; G0-G9 and
inherited release acceptance remain open.
