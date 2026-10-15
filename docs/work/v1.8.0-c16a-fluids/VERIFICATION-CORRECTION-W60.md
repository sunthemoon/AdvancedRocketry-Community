# VERIFICATION.md: link correction (W60)

[VERIFICATION.md](VERIFICATION.md) still matches, byte for byte, the SHA-256 that its packet's
`checksums.txt` (in this folder) records. That list was never committed; it exists only untracked
in the owner's main checkout
(see the [external evidence manifest](../v1.8.0-external-evidence/MANIFEST.md)).
W60 therefore treats the file as sealed, leaves it unchanged (AGENTS.md section 10) and
records the corrected links here. This correction file is in no checksum list.

Reason: the strict validator
(`python -B scripts/validate_repository.py --require-approved-identity`) rejects Markdown
links to absolute paths and to files that Git does not hold (W58 audit, slice C19-D).
Elsewhere W60 rewrote each such link as its text followed by the path in a code span; a path
that exists only in the owner's main checkout also points to the external evidence manifest.
Read each line below in place of the link on that line of VERIFICATION.md.

| Line | Corrected text |
|---:|---|
| 122 | task archive (`root-evidence.zip`) (see the [external evidence manifest](../v1.8.0-external-evidence/MANIFEST.md)) |
| 123 | checksums (`checksums.txt`) |
| 124 | independent packet (`reviews/review-evidence.zip`) |
