# R-021 factual correction: qualified review disposition

Date: 2026-10-04. Status: verified factual correction only.
Task: C16a-R021-DOC01. No ADR, risk, runtime or Gate acceptance.

The owner's scoped conversation reply is "授权仅同步 R-021 和 ADR 说明（推荐）".
It permits only the R-021 correction and a proposed explanatory ADR amendment;
other risk rows, save behavior and risk acceptance are excluded. Root publishes
this as a separate documentation phase after K3 documentation commit
`44b394fead60d3d451dc11869a5b2cb730974ab9`.

## Exact corrected scope

- [R-021](../../11-RISK-REGISTER.md) SHA-256 is
  `8216d438497ed9158db7f46d296dc426ef67f20d0a8fb937a397e6450c2e9133`.
- The [standalone explanation proposal](ADR-064-SAVE-REFUSAL-EXPLANATION-PROPOSAL-01.md)
  is 9,207 bytes, SHA-256
  `f6ca5fd460346969c707b6fd0fb15b7ae4ea6ea2a45db002c9e498005ada3bad`.
  It remains **PROPOSED**, not accepted ADR text.
- All 3,215 bytes outside R-021 match the owner-edited baseline exactly:
  SHA-256 `dab7044a8a8f7e20927fe5eb5a5dff15156be7ec0de0a7ea08f56ec02a362aa2`.
  Root additionally verifies those bytes equal the committed risk register.
- Accepted ADR-064 revision 5 remains byte-identical: 35,702 bytes, SHA-256
  `789522c7c982209df660745442ca96fa1f83cc57b29c7f9692b609219c7c37d0`.
  Code remains fixed `1ece7e9d2515003ca705b5634ef654a6bcae8f89`, with no source,
  budget, schema, ownership or native admission changes.

## Independent review and immutable evidence

The [frozen independent review](reviews/disclosure-correction-01.zip) records
zero introduced Critical/High/Medium/Low candidate findings in factual disclosure
scope. Its report SHA-256 is
`307375ed1549126349f7d2355652c6df5bc0e1547050262dc08072f87c69671e`;
original manifest SHA-256 is
`ee2a8d16e359f6fe0e20d917f7eca29ec4826472ca0991d770d8c34a733c2fa8`.
Root verifies all 26 manifested payloads plus the original manifest and creates
a new compact 27-entry, 57,201-byte packet; SHA-256 is
`9f92cb03accd9cb365773cb13312abaeb5a50f0e7645973d811c32edc24b838c`.
The reviewer's original loose evidence is unchanged; no full source or JAR is
exported. The reviewer does not create that ZIP or execute Java/native servers.

Final independent checks pass 10 cases with no failure/error/skip and exit 0;
18 relevant code/test objects and the accepted ADR are exact. The first check
exits 1 with 10 cases/one failure when Root changes the proposal header during
review. It remains a failure. A separate content-addressed comparison verifies
only that status line changed; the new entrypoint checks the announced final
pin without weakening factual assertions. Original audit failures are unchanged.

The separate `R021-DISCLOSURE-PUBLICATION-01.zip` preserves final Root links,
planning/strict-ledger/whitespace commands, source/postimage checks, owner
authority and small intake/baseline evidence for this publication. It does not
relabel the original K3 final-check packets as coverage of later documents.

## What is and is not resolved

The original M1/M2 missing facts are addressed in the corrected row and reviewed
proposal: six BEs/four consumers, non-byte triggers, 257th distinct-denial Level
saturation, sticky lifetime, paired historical ERROR logging without a quota,
unload and independent-store limitations, and unproved recovery are disclosed.
The original impact audit remains immutable; its scope is not reclassified as
a current native reproduction.

**R-021 is still open and unaccepted.** The accepted ADR is not amended by this
proposal. Current-artifact mixed-coordinate, lifetime/unload, saturation,
cross-store, logging-volume and backup-copy repair controls remain unrun.
No reset/quarantine/discard/automatic stop/read-only policy is selected. This
correction does not admit GuardTicket/shared writers, physical hatches or any
current-version Gate. Only the allocated factual documentation leaf is verified.
