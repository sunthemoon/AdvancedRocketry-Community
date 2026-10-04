# Root two-fixture independent static review

No introduced Critical/High/Medium/Low findings in the exact two-file proposal.
Existing Medium M1 current-orphan runtime classification remains open; changing
only fixtures does not repair the full GameTest result.

[Portable packet](root-fixture-independent-review-01.zip), SHA256
`b68e120352b75c74146d355a288382e0d5f7bfb58194be82e388fb334a9a7d09`,
304,555 bytes /39 entries; manifest
`7d32ff811c94aba3e356f6b31463957b802aa72043f2e6a0a8d583b88bcf0f31`.
Report SHA256
`5449325f3ba7cfbbe4d1fc3896b8e5b31a9f27872ff7decb11e4721f76020bed`.
Root verifies complete CRC/hash/size/path checks before copying.

Bootstrap adds the strict serializer's required type only. TransactionCut
copies the marker from the same actual PREPARED snapshot as its stale journal,
making a current-format orphan-cut fixture, not malformed marker input. This
does not prove legacy provenance. All108+20 old assertion calls, annotations
and numeric budgets remain; one marker-presence assertion is added. Check/apply/
Python static replay exits0;27 local links resolve. No Java/native run here.

Root applies exact postimages and seeds them into the author's baseline,
separate from revision04 owned changes. The paired runtime correction requires
independent exact-diff review and actual replay. Original456-complete/12-failure
rawGT evidence remains unchanged. No full-leaf/API/Gate admission follows.
