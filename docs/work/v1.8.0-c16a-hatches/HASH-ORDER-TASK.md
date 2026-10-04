# C16a-03b-01 hash-order prerequisite

Date:2026-10-04. Status:in-progress,technical proposal only.
Owner:delegated `/root/c18_contract`; independent reviewer assigned separately.
Root owns contract adoption and central integration.

Outcome:an exact deterministic native-tag key-order amendment for the frozen
hash-v1 contract,before its codec/hash implementation. The frozen lifecycle/hash
feasibility packet in `reviews/` documents that ordinary UTF-8 primary ordering
alone does not distinguish all Java strings. This task resolves the technical
specification,not physical writer authority or a persisted migration.

Read the accepted proposal02,phase01 values/native-shape constraints and the
feasibility packet. Preserve existing primary ordering and native tag distinctions;
state any proposed tie behavior or rejection and its supported-input effects.
Do not silently convert unsupported roots,normalize tag types,or invent a private
GuardTicket needed by deferred full-frame codecs.

Write scope:fresh Temp proposal/probes/evidence only. Root and all worktrees,
the25 admitted value/test files,Bank14,ADR/status and frozen packets are read-only.
No Java/Gradle/native/server launch,upstream import,protected document-bundle
access,commits/tags/push. Bounded Python/static inspection is allowed.

Acceptance:exact total-order or explicit rejection rule,byte/framing inputs,
Unicode/native modified-UTF implications,alias and list-type preservation,
determinism evidence and remaining native emission limitations. Independent
actual-proposal review precedes adoption or code writes. Existing native
retention failures and source-only corrections remain recorded.

- [x] Proposed rule and bounded consistency probes,not runtime code.
- [x] Independent review and [technical adoption01](HASH-ORDER-ACCEPTANCE-01.md);
  no unresolved C/H/M/L in the exact rule scope.
- [ ] Actual implementation/regression,not granted by this task.
