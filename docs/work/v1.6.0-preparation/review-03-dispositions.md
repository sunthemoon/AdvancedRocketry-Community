# Dispositions of the third (targeted) independent contract review

The third review checked `ba275f0` (revision 3). Of the 18 round-2 items, 16 were resolved and 2 partial:
R2-L3 (vector names) and R2-L10 (evidence committed at acceptance). New findings: 0 Critical, 0 High,
0 Medium, 6 Low. Verdicts: ADR-049, ADR-050 and ADR-051 accept. The reviewer's composition search
(1,111,110 sequences with flushes, chunk saves, crashes and cancels) found no double payment outside
the stated residual, no freed instance and no lost reward. The unmodified report is
`round-3/REVIEW.md` in `independent-review.zip`. All six Lows are applied before acceptance.

| ID | Finding (short) | Disposition | Where |
|---|---|---|---|
| R3-L1 | The `REBIND_CONFLICT` bind-back is only flush-pending | Barrier flush added (it only follows an operator rebind) | ADR-050 §2; ADR-051 §7 |
| R3-L2 | Vectors still use `INVALID_LAYOUT` | Checker and all 8 vectors use `INVALID_COMPONENTS` | `check_examples.py`, `examples.json` |
| R3-L3 | Instance table misses the rebound owner cancel | Row added: owner cancel of a `rebound` mission goes to QUARANTINED | ADR-051 §2 |
| R3-L4 | Figures and lists lag the revision-3 shapes | Mission worst case about 3,804 bytes; rebind fields in the lifecycle reservation; legacy mission 597 bytes, 5.84 MiB of 6 MiB | ADR-050 §3, §7, §10 |
| R3-L5 | "Read from chunk storage" has no detectable signal | Only an ID or receipt present in a `ChunkDataEvent.Load`/`Save` tag for the terminal's chunk counts as persisted | ADR-051 §5 |
| R3-L6 | Flush-failure and inert-chip wording | A failed launch barrier keeps the package, and a later replay consumes it; `satellite blank-chip` operator command named | ADR-049 §6, §7 |
| R2-L3 (partial) | Code names in the vectors | Closed by R3-L2 | as above |
| R2-L10 (partial) | Evidence not in the reviewed commit | `VERIFICATION.md` and `independent-review.zip` are committed with the acceptance | this directory |
