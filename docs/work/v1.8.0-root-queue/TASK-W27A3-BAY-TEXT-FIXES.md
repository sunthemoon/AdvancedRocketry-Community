# TASK W27a round 3: text fixes N1 to N9 in the satellite bay design

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first. This is design text
only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w27a-bay-design` (round 2, `81ab46f3`) |
| Branch | `codex/v1.8.0-w27a-bay-design` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W27a2-review/REVIEW.md`, SHA-256 `c9ef87a19da6f34cbff98305576bd55445ca2790dc2b5d23d010931eb1b9c30d`. Verdict APPROVE-WITH-CHANGES: N1 and N2 Medium; N3 to N9 Low |
| Deliverables | An edited `records/W27A-DESIGN.md`, with a short "Round 3 changes" section at its end, and a short record `records/W27A3.md` |

Read the whole round-2 review. The protocol is approved. Fix only the specification gaps the
review names, as the review recommends.

1. **N1 (Medium; it must be fixed before Root freezes the W11b2 hook).**
   - Define exactly how a transfer claimed through `RocketTransferBayRecoveryGate` is
     classified. A bay deployment must be neither logged as an offline-repair fence nor allowed
     to block every station warp.
   - The claim must be queried **before** ticketing and classification, not only at a final
     boundary, so that ordinary recovery is never starved.
   - Restate the hook contract precisely, as the text Root will paste into W11b2's scope.
2. **N2 (Medium; it must be fixed before W27c).** Rewrite the unknown-registry-write rule so that
   the ADR-050 save epoch never advances past mutations that are not in the file. Keep the
   ADR-051 and W21b epoch rule, and close the duplicate-reward path the review describes.
3. **N3 to N9 (Low).** Fold each into the text as the review recommends, or move it explicitly
   to the named slice (W27b to W27d) with its test. These are:
   - N3: neutral-source-only admission;
   - N4: the Risk C consequences;
   - N5: REJECTED receipts holding retained slots;
   - N6: the receipt index loaded at startup;
   - N7: slice dependencies and size;
   - N8: the ADR text for the terminal behaviour change;
   - N9: the bookkeeping cost.

Keep everything the review approved. Commit the edit and the record.

## Not in this task

- Code and tests.
- ADR and risk-register edits.
- Status files and the ledger.
