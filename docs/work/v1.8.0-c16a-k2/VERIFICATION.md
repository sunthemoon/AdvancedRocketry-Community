# Private canonical NBT computation: integration evidence

Date: 2026-10-04. Development code committed and pushed at
`3f3d62aed3980186fe0acc9592cf93ca436405fb` on
`codex/v1.8.0-classic-content`. This is not a release candidate or Gate approval.

## Scope

The independently reviewed K2 delta adds only a package-private bounded NBT
encoder and its test. It sorts compound keys using the already reviewed K1
order, preserves native framing and returns newly owned bytes. Stable exclusive
input ownership is required. It does not implement resource hash consumers,
GuardTicket, full codecs, physical hatches, charged-mode interception or a world
writer. [Source acceptance](../v1.8.0-c16a-hatches/CANONICAL-BYTES-SOURCE-ACCEPTANCE-01.md)
records the exact author/reviewer identities and limits.

## Actual commands

Java 17, Forge 47.4.10, offline, no daemon, two workers, 2 GB Gradle heap:

```text
gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G clean build test runData
gradlew.bat --offline --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G runGameTestServer
```

- Build exits 0 in 193.987699 seconds: 1,750 JUnit cases, 329 suites, no failures,
  errors or skips. Compilation uses the recorded Gradle cache; tests execute.
- DataGen observes 771 files and zero repeat writes. Both non-API artifacts
  contain the exact generated resources.
- GameTest exits 0 in 242.978334 seconds: all 464 required cases pass. Its 61
  ERROR logger lines remain in the raw log; there is no clean-log or blanket
  waiver claim.
- The unchanged repository validator reports 45 passed and zero failures.
  Planning, provenance, machine resources, artifact and common/client checks
  exit 0; focused Python checks pass 90 cases. The validator's bounded literal
  Git exclusion protects the private documentation bundle, not test rules.
- Strict ledger validation exits 0. Ledger closure exits 1, with 186 PLANNED
  units and 154 REVIEW assets. The precommit `git diff --exit-code` exits 1;
  `git diff --check` exits 0. Those original failures remain failures.

Actual artifact identities:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Main | 5,440,794 | `796c9611ec8d655398e5a046bfe6cc3f81b2c7880a19c835ea9ed301a990373e` |
| Sources | 2,620,612 | `1368d531681032c3c70fa320fbe7697dff3f124e668c4f17176707220abef9d2` |
| API | 51,045 | `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da` |

The API is unchanged. These are newly built artifacts, not a relabelled prior
server cohort. No packaged native/server/client run used this new main JAR.

## Immutable evidence and storage

The compact packet stores commands, raw logs, actual XML, results, patches and
internal checksums without a duplicate complete source tree or JAR set:

- Location: `D:/ARCE-Task-Evidence/v1.8.0/k2-continuation-01/ROOT-INTEGRATION-21-COMPACT-01.zip`.
- 1,241,813 bytes, 423 entries; SHA-256
  `9a9de4bf0f1bf41c7dabbfbbbfac48a89acdcccc919f9526349c8b7f527955f0`.
- Internal manifest SHA-256
  `15959b5f7fc625a2694262fd6da99cb2204385d1192a2971d35076ac1de21d66`.
- The three immutable JARs are external at
  `D:/ARCE-Task-Evidence/v1.8.0/integration-072fbcf821ce4ceb9fbcb3d98ec27d41/integration-artifacts-21/`,
  with the names and hashes in the packet. This is local external storage,
  not a publicly uploaded release attachment.

Root verifies all packet CRCs, lengths, hashes and unique case-safe names.
Different-agent actual-evidence audit is complete and qualified below. It is
separate from the independent K2 source review and does not execute Java or servers.

## Different-agent evidence disposition

The [frozen report](reviews/integration-21-evidence-audit-01/REVIEW-01.md) records
no introduced Critical/High/Medium/Low findings in the exact integration-evidence
scope. Report SHA-256 is
`130e8a66861c58297902d5e99d262aa098c593534724e42583b96c6b87e517ad`;
its internal manifest SHA-256 is
`f2274a15bd216642201488bf84a4639ebb70c6dab8eeddd04cfb732db59ae6c1`.
Root verifies and copies all 63 loose files, 1,853,808 bytes, without a source,
JAR or ZIP duplicate. Independent evidence controls pass 12 cases; the unchanged
artifact validator independently exits 0 against the actual external main JAR.

The reviewer measures all raw XML and the 128 GameTest batch rows totaling 464.
It verifies all 771 generated entries in both non-API JARs, all 1,210 main Java
source members against the immutable code commit, unchanged old packaged members
and the byte-identical API JAR. Finite ERROR categories and original non-PASS
receipts remain. Reviewer locator/selector/parser failures and their separate
corrected executions are preserved, not replaced with passing originals.

This is not a literal 2,990-input Git-object equality claim: user-maintained
AGENTS.md and two then-pending historical documents differ or are absent in
the code commit. The tested gradlew.bat differs from its Git object by exactly
92 CRLF-versus-LF pairs; both identities remain, with no general normalization
waiver. Later documentation commits do not retroactively change the tested
code pin or cover later document edits. No new native/client, consumer, full
codec, Guard, persistence or version Gate is admitted by this evidence review.

## Preserved failures and boundaries

The original author run has 19 cases and one signed-zero fixture failure. Native
factory/load normalized its input before encoding. The corrected exact native
constructor fixture keeps the assertions and unchanged production code; it
proves byte emission, not naturally loaded negative-zero persistence. Final
author 20 cases and independent 28 cases pass. Delivery and packaging failures
remain archived separately.

User-maintained `AGENTS.md` changed during the build. Original artifact collection
exits 1 on that input drift, before GameTest starts. A separate R1 governance
receipt records only that file's new sections; all 2,989 other inputs remain
exact. The original 2,990-input manifest and failed observation are not rewritten.

The first whole-Temp relocation exits 1 on a hidden/read-only item. Subsequent
checked moves preserve 19 named completed runtime copies on D; original worlds,
repository evidence and unknown processes remain untouched. The receipt does
not claim complete deletion or byte-level validation from inventory counts.
Before GameTest, both drives exceed the recorded 10,000,000,000-byte threshold.

Older native results belong only to their original artifacts. Original allocation,
plain-JUnit registration and static-wrapper failures remain historical failures.
Whole-chunk save-refusal impact on unrelated changes, first-save/final-unload
proof, hostile resource callbacks, full codec admission, real clients/GPU and
all remaining content/Required Gates stay open. Historical oversized evidence
packages are not included in the phase commits; repository-wide evidence
externalization still needs completion.
