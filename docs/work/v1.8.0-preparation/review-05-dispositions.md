# C14 review round 5 — dispositions

Review of revision 5 of ADR-061/062 (`46d0a7f`) by the independent contract
reviewer ([report](reviews/REVIEW-05.md)): 0 Critical, 0 High, 0 Medium,
1 Low, 4 Info. Verdicts: **ADR-060, ADR-061, ADR-062 and ADR-063 accept**; the
Low and the Info notes are recommended for the next revision and block
nothing. They are answered in their own commits; the result is revision 6 of
ADR-061 (ADR-060, ADR-062 and ADR-063 are unchanged). The reviewer confirmed
that the archived reports are byte-identical to the originals.

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| L1 | Three small validator gaps around the new finding fields | Fixed: `releases` also covers relations to files excluded by an origin finding; records compared as resolved paths; every file decided by a plan row that cites a finding needs it; three tests | `f4c438e` |
| I1 | The posting cap limits relations through a very common tile | Stated in ADR-061 §4.8 known limits | `7ca35dc` |
| I2 | The known-limits test covered only the first limit | A blurred low-contrast texture added as an expected-`CLEAR` case | `e47b44c` |
| I3 | A `CLEAR` file could show a zero-strength best match | Such files now record no best match; verdicts and relations unchanged; results re-pinned | `563082d` |
| I4 | Survivors by design | No change: the CI `--check` and commit review cover them, as ADR-061 §4.2 and ADR-062 §7 state | — |
