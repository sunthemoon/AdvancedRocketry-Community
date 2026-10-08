# Coupled hatch candidate 23: disposition

Date: 2026-10-08. Root-authored successor of the private assignment research.
Base/source: `a01703366c51a0e06788b95a1793b312dfa8a9a4`.
Status: revision2 independently reviewed; documentation checkpoint only.
Candidate remains PROPOSED/not frozen and not source-assignment-ready.

[Task23](COUPLED-CONTRACT-TASK-23.md), [candidate](COUPLED-CONTRACT-23.md) and
[verification matrix](COUPLED-VERIFICATION-MATRIX-23.md) cover the complete
ordinary placement/removal, both LOADs, early/late save and terminal/lifecycle
boundary together. No Claude, production source/build/hook/registration change,
physical writer, new public/durable identity, ADR/risk acceptance or Gate waiver.
The two original Medium assignment prerequisites and O1/O2/O3 remain open.

## Primary inspection and verification

Root's NEW external evidence leaf is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/hatch-coupled-contract-root-20261008-01`.
An independent read-only Codex reviewer works in
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/hatch-coupled-contract-independent-20261008-01`.
No original sealed artifact is amended. The original independent review is
packaged as [COUPLED-CONTRACT-INDEPENDENT-23.zip](COUPLED-CONTRACT-INDEPENDENT-23.zip):
139 entries /617,797 bytes /SHA-256
`fcb15a08a5cbda72e40992f2f2e62303f6328e9c953fc6201fbedd838cae8a26`.
Its REPORT SHA-256 is
`9e363684b564261f6ddf2a32c734c9bd42546eba9bb2af28d813a8db57b99d18`.

## Original findings and revision2

Original candidate SHA-256 is
`ce353fb442edd2610d01a730127c41345f10e9fa6b8b9736df3831685ff91159`;
original matrix is
`d5a79dee8bd87e1eed8cacded1a9aec5b3b2909adf298ca343637c502e441323`.
The fresh actual-draft review identifies two Medium assignment ambiguities and
one Low native nomenclature error, not demonstrated runtime regressions:

- M1: distinguish normal temporary loadJoinCandidate cleanup in prepareLoaded's
  finally from invalidation of the completed operation-owned receipt.
- M2: model already-delegating, unselected placement separately from selected
  cell state; placement selection cannot start the outer native invocation again.
- L1: the pinned native method is updatePlacementContext, not getPlacementContext.

Root's revision2 explicitly transfers the successful second LOAD's captured
identity into a distinct operation-owned receipt, retains normal temporary
cleanup, names explicit later withdrawal/retirement/checkpoint invalidation and
keeps availability false. It separates Level invocation/selection order and
no-selection/nested cleanup, and corrects the native method. Existing installed
calls owner.isRemoved; the candidate now explicitly requires a new private
local publication tail rather than treating that existing method as callback-free.
Positive post-finally/negative invalidation and unselected invocation cases are
added to the unexecuted matrix. No actual runtime test changes or proof follow.

The fresh revision reviewer retains complete old/new snapshots and diffs,
independently checks the resulting whole and reports no new introduced contract
finding. M1/M2/L1 are addressed at specification level only; no runtime execution
or earlier assignment prerequisite is thereby proved/closed. The two prior full
Medium prerequisites remain open regardless of these corrected ambiguities.

The [revision review archive](COUPLED-CONTRACT-REVISION-INDEPENDENT-23.zip) has
67 entries /382,489 bytes /SHA-256
`a98b60c36daa3aa47fb6b7561453273ff52fd8ea6d201923de003cdc40fd9556`.
Its REPORT SHA-256 is
`3a011910a4ec33f549f4471a41acbd08b05a631031bdc01b8b38467ce8770eff`.
The frozen candidate/matrix hashes are respectively
`4749db7b3931a6ca98efdb8c98f4deaecefeb0f2a1e0ea6114a78ff7869f5d5a`
and `8e4a78463a1c7da138810dc690784cce0d41582413a42669c043c2c4bd8a19b2`.
The correction names updatePlacementContext rather than the historical
getPlacementContext wording; no old sealed record or evidence is rewritten.

## Actual task23 checks

Root's eleven named javap commands exit0; revision3 native/source selectors
pass 24/24 and the initial new UTF-8/link/whitespace/source-preservation checks
pass 31/31. Original selector failure and revision2 22/24 failure are retained;
they select wrong methods/constructor, not different native evidence. The
corrected revision3 assertions are unchanged and the actual extended constructor
is selected. Ledger/provenance and git diff --check exit0.

Root strict validator exits1: 44 PASS /one inherited broken-link check. Original
independent review separately confirms 24/24 static selectors and all eleven
primary disassemblies byte-identical, plus bounded static native invocation/seam
facts; its normal strict rerun exits1 with the same 44/1 result after an original
180-second launcher timeout, which remains preserved. The unchanged strict log
of Root is 26,803 bytes /SHA-256
`26957c232ef8a3d33132ae6c51bd5b9846992708a4862df691f29f40dc0573f9`.
The original independent strict log has a different byte identity, SHA-256
`32d28fc9a5553bcddde509311b80211680210e3208bda7d76f402a2b181baf53`;
equal 44/1 counts do not mean equal inputs/logs. Recorded concurrent Root-owned
status/disposition changes are disclosed, not called zero total document drift.
These strict runs are of their recorded working inputs, not revision2 runtime
verification. There is no task23 failing link target in that category.

The original independent reviewer performs one explicitly authorized native
PowerShell cleanup of its two own source read caches, removing 269,521 logical
bytes after pin/excerpt retention. No physical free-space increase is measured;
no complete reproducible source, game/server/world copy or old denied target is
packaged/removed. Its original failed selectors/reads/launcher remain sealed.

The revision reviewer freshly inspects 18 bounded native members, all byte-identical
to the original independent outputs, and 16 fixed source pins; corrected controls
pass 40/40 after preserving an initial class-wide LOAD-count failure (39/40).
It verifies the original 137-payload/138-checksum seal and current draft hygiene.
No ledger/provenance/strict or runtime regression is rerun by that focused review;
their previous actual commands stay separately attributed. No source cache or
disposable game/server/build directory is created by the revision review.
Both independent reviewers release all read/HEAD interests and write no repository
file/index/HEAD. Root's final scoped UTF-8/local-link/whitespace/source-preservation
checks and archive hash/budget verification are recorded in the root evidence.
Root primary commands/results/pins and final scoped checks are packaged in
`COUPLED-CONTRACT-ROOT-23.zip`. Each archive carries its enumerated in-package
manifest/checksums; no full reproducible source is repackaged. The three evidence
archives and new task records are committed together without release acceptance.

The explicit JDK17 pinned-native inspection reads eleven <=512KiB named members
and runs eleven javap commands, all exit0. Mapped Forge47.4.10 JAR SHA-256 is
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`;
bundled Mixin0.8.5 JAR SHA-256 is
`ca15a907e4d1f6b38cefed51d7df96a83347dcfb0bc8ced53111519e991d887d`.
Only static facts are established. New contract runtime proofs are NOT_RUN.

The primary records correct plausible but unverified assumptions: the FULL raw
event uses actual LevelChunk; its capability attachment type is correct, but
attachment emptiness and isNewChunk cannot admit a generated/disk-Proto origin.
Actual Proto handoff is still unspecified/unimplemented; it is not solved by
the three ordinary-player wrappers. The candidate names exact private birth,
two fresh LOAD/provisional receipt and callback-free publication modifications,
while leaving the outcome/bounds, authentic origin and final-writer proof open.

Generic clean build, runData, generated empty diff and runGameTestServer are
NOT_RUN in this documentation-only task, not NOT_APPLICABLE and not reruns of
this SHA. Prior source7f development results remain associated only with
qualification22. No server/build disposable directory or process is created.
Source worlds and inherited/unowned/previously denied cleanup targets are
untouched. No owner policy response is recorded; all v1.8 G0-G9 remain open.
