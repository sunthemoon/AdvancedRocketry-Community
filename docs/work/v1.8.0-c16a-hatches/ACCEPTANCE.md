# C16a-03 Fluid retention and bank identity acceptance

Date: 2026-10-03. Authority: the recorded owner decision and conditional
authorization to accept independently reviewed final contracts with no
unresolved Critical/High/Medium findings. This is not a version Gate verdict.

ADR-064 revision 3 accepts only the owner-selected Fluid-hatch retention
amendment; the other technical sections, public IDs, schema versions and
numeric limits remain those of revision 2. Revision 2 source SHA-256 is
`d8ecc264170047fe62644dee01f2fc1101c56ceb5df1f250d8224486205f7014`.
The independently reviewed [complete proposed r3 raw source](PROPOSED-ADR-064-R3.raw.txt) SHA-256 is
`78995a706ca71c3c54af7eab67cc1f953e28f2cd3718c0e7f63e7aaa376b8097`.
Acceptance changes its header/history metadata, not its reviewed technical text.

Independent full-r3 report SHA-256:
`8dde1a5e5aa1ae7b29d0ac6346d1e49c205e9888f048f9772d7b952e0186a7d9`;
report manifest `70d86e412960cafc95fbbda73c214e2688939d1a0b24eee6e2c10a30c131bad9`.
The leaf's original rotation contradiction was corrected and independently
re-reviewed; final leaf source SHA-256 is
`9cf12fc94224dd4162cdc413a8b32660896e5707fad6ef528d4c1783efddc9f5`.
Its final independent report SHA-256 is
`e17cf7967bd0c8a7d9a73a84d264b1398d858e18255960eb0a99629d46286e53`.

These texts admit implementation of the bounded bank-domain/codec sub-leaf,
not downstream C16b/c writers. Actual Java APIs, strict field/structural limits,
native before/after metadata plans and facade/lifecycle contracts need their
own actual-diff review/tests before they freeze. No formation, cross-chunk
conservation, native restart, UI, GPU or whole-v1.8 completion is inferred.
