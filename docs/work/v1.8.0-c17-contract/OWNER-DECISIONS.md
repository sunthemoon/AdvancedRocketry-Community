# C17 owner decision receipt

Date: 2026-10-03. These selections apply to the alternatives in reviewed
ADR-065 revision 3, SHA-256
`92e706ae84e6565f605227f7794fb83850a23c4f2ca1915686d28c61fa72c321`.
They do not prove runtime implementation or accept unreviewed amendments.

| Choice | Owner-selected behavior |
|---|---|
| D1-A | Retain existing basic rocket metrics and item-fuel API; add independent actual-fluid propulsion, including distinct oxidizer/working-fluid accounting, with classic tier ratios. |
| D1-disassembly-A | Require explicit consent to the full typed propellant kind/amount/version vector before deliberate disposal on disassembly. Always restore ordinary cargo fluid. Refuse new typed host engines mixed with ADR-026 external engine/capacity roles; retain external-only and basic abstract compatibility. |
| D2-A | Continuous three-axis analytic rotation and logical altitude; do not move physical station blocks. Refuse settings that break linked/busy elevator endpoint conditions. |
| D3-A | Use one persistent transaction and receipt coordinator for rocket resource loading/unloading and satellite-bay deployment. Admit these interactions only after forced-stop recovery verification passes. |

D3-A was separately confirmed after revision 3. No added two-store crash
residual or same-tick-only substitute is approved. Exact shared schemas/protocols, checked writers and native S2
recovery fixtures retain the draft's admission gates. The draft is not an
accepted runtime contract merely because these product choices are recorded.
