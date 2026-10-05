# O3 owner-attribution correction disposition

Date: 2026-10-05. Integrator: Root. Status: adopted factual correction only.

## Exact replacement and authority

This independently reviewed correction supersedes only lines 9–13 of the sealed
`OWNER-COORDINATION-02.md` at
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-o3-typed-integration-20261005-72c143`.
The original is 7,320 bytes, SHA-256
`8cbfaf265d6058f14ce178ec9f14e01a68995a323840a50b662a56c0a3c8b2b5`;
it is not overwritten. Read the following paragraph instead of that introduction:

```text
The recorded question concerns a first-event-sensitive uncertain-write window:
ordinary saving may overwrite an already saved first award with old memory.
It asks whether old-data rewriting should pause until verified reload or repair,
with other actions retaining existing rules only under confirmed coherence.
It explicitly calls for ADR synchronization and recovery verification.
Known durable-commit/live-publication failure is not named in that question;
its treatment below remains proposed technical coordination, not extra owner
wording. This record uses Root's named transcription, not independent capture
of the owner's original conversation.
```

The [literal receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-terminal-root-integration-20261005-01/FIRST-EVENT-OWNER-REPLY-01.json)
remains 1,262 bytes, SHA-256
`0d676b9d3c55536fd48d773e1487d423c6fc9757ef9970615d4ee713c6fa911b`.
The owner's answer is “保护首次记录，暂停不确定窗口的旧数据重写（推荐）”.
It selects first-event-sensitive uncertain-window protection until verified
reload/repair; other actions retain existing rules only under proven coherence.
The receipt is Root's dated transcription, not an independently captured raw UI
export. This correction adds no owner answer or semantic decision.

## Review and actual verification

The [author correction](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-o3-attribution-correction-20261005-3bc621/CORRECTION-01.md)
is 4,707 bytes, SHA-256
`a7bf5cef057e2fb7a35ee95044c7bda8baae294a2a00563e80e6e09d865287fd`.
The [different-agent review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-o3-attribution-review-20261005-d8112a/REVIEW-01.md)
is 10,072 bytes, SHA-256
`07432baf5110e58b80955a3f9bee9f342b3ddcdcdfae320f7b8f8214bea52436`.
It identifies no introduced C/H/M/L within the exact paragraph/delta scope.
Its 27 fresh controls, including 12 malformed-patch negatives, pass; 37 named
inputs have no drift. Strict in-memory forward/inverse checks preserve all
other bytes. The virtual corrected owner document is 7,584 bytes /108 LF lines,
SHA-256 `acdf996fbbc5f93dd838b2d50ecb006bda95a19f435f8f4567b04774367e4d24`;
no whole replacement or original packet is copied.

Root reads the complete correction/report/review and actual receipt, then runs
a fresh Python 3.13.15 `-B` named-manifest intake: exit 0; author 15 payloads /
44,072 bytes /16 checksums and reviewer 14 /69,240 bytes /15 checksums verified.
[Root intake](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-o3-root-disposition-20261005-01/ROOT-INTAKE-01.json)
records the exact argv and metadata hashes. No old checker or Java runs.

## Disposition and unchanged prerequisites

Original Low L1 is resolved only in this versioned factual replacement. The
original finding, seals and failures remain unchanged. The complete O3 proposal
remains 60,041 bytes, SHA-256
`5447201f53996f180eb73fe87be74a18aba1b9bb5e456b6294c17dc911544d43`.
Known durable commit followed by failed coherent live publication remains a
proposed technical extension, not additional owner wording or an adopted policy.

Full typed/shared contract adoption, exact affected roots and release conditions,
ADR-065/066 amendments, risk registration, source/hold/writer permission and
actual recovery verification remain open. No R-021 acceptance, runtime delivery,
ledger change or G0–G9 approval follows from this correction. New output stays
in the project-parent D evidence directory; there are no new runtime copies.
