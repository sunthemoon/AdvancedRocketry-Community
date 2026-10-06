# Guarded resource data-only checkpoint

Task: C16a-03b-GUARDED-RESOURCE-01. Status: implemented-unverified.
Integration base: `0cbbfb68446adf160a3a1007601e311173268313`.

## Scope and boundaries

The checkpoint adds `ClassicGuardedResourceAccess`, package-private guarded
overloads in `ClassicNativePayload`, `ClassicResourceBank` and
`ClassicResourcesCodec`, and eight declared tests in
`ClassicGuardedResourceAccessTest`. It is original NEW/MIT work, with no upstream
code or asset import. A supplied witness check is never retained and creates no
owner, ticket, capability or publication authority. Witness failures escape the
ordinary invalid-data catch. All bank envelopes are preflighted before native
stack decoding.

Existing public resource signatures and method bodies, schema, quantity and
structural limits remain unchanged. This is not physical hatch delivery. No
registration, world/service lookup, native owner admission, save writer, creative
interception, gameplay, network or resource-to-adapter dependency is introduced.

## Source review and exact integration

The technical proposal was adopted in the limited data-only scope after Root
review. The independent source review found no Critical/High/Medium issue and
one Low test-coverage gap. A separately reviewed test-only successor addresses
the authored coverage; it does not establish passing execution.

- [Proposal review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-guarded-resource-proposal-review-20261007-01/REVIEW-01.md):
  SHA256 `4f720643236ec47f2fb6e4ae3eb8fb2406fe87be99f259c60003a48db36edd94`.
- [Source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-source-review-20261007-3e8b6c/REVIEW-01.md):
  SHA256 `f846a82ff5d97d5a80b4917d9593eafc70040d96ee8cdedd9f37f329bac257f1`.
- [Test successor review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-test-review-20261007-f96bd2/REVIEW-02.md):
  SHA256 `ddcad36e8b7dc5e0bce669940f09455fb23a8d9122d4beac53759d1cc2f42af9`.
- [Five-file handoff](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-checkpoint-handoff-20261007-709fde/HANDOFF-01.md):
  SHA256 `c29c9a2639d3687f0e8fad3db31ddad70e1d6b11866ec019e045aa8f9c4edc63`.
- [Exact postimage map](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-checkpoint-handoff-20261007-709fde/OWN-FILES-01.json):
  SHA256 `fbe618b42e93e54a3a40fe53378d3811b3547f41abc914ff0121fc051738a533`.

Root's preflight verifies the three unchanged main preimages, two absent new
files, released author postimages, empty index, expected base and unchanged
user-owned AGENTS.md. Manual `apply_patch` integration produces all five exact
released hashes. The moving owner implementation and its task record are not
integrated. A normal WIP phase commit is not verified delivery or ledger closure.

## Actual development checks and remaining verification

Three bounded local attempts failed and remain separately retained:

| Attempt | javac / Jupiter / outer | Observed result |
| --- | --- | --- |
| [First](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-check-preparation-20261007-96e241/RESULT-01.json) | 1 / not launched / 1 | Two unchanged process-key sources were absent from the explicit compile list. |
| [Second](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-check-successor-20261007-5c4a82/RESULT-01.json) | 0 / 1 / 1 | Missing Netty initialization dependency; three BankKey methods passed and six containers failed before their test bodies. |
| [Third](D:/GitHub/ARCE-Task-Evidence/v1.8.0/guarded-resource-check-successor03-20261007-62c118/RESULT-01.json) | 0 / 1 / 1 | Missing ModLauncher initialization dependency; three BankKey methods passed and six containers failed before their test bodies. |

No new guarded-resource test body has actually run. There is no further local
dependency retry, Bootstrap edit or test relaxation. C: has less than 10 GB
available; no local full Gradle, GameTest or native server is started. New
temporary scripts and retained outputs use the external D: task-evidence tree.
The author's ended class outputs remain cleanup debt, not deleted by Root.

A clean hosted cohort bound to the resulting source commit must execute the
eight new methods and unchanged resource suites and retain all initialization,
container and domain failures. Hosted CI is pending, not inferred from static
checks or earlier commits. The preceding diagnostic source's 1,946 unit cases
and 509 GameTests with one required Tau failure remain separately bound to
`ca217afac7fc2034cf4740e18a0fe582af7285d7`; they do not test this checkpoint.

Real owner/FULL observations, callback invalidation, physical capabilities,
save/restart recovery and client validation remain open. No content-ledger
delivery, risk acceptance or G0-G9 approval occurs. The version remains
IN_PROGRESS / IMPLEMENTING.
