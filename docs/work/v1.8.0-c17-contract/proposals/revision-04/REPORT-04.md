# C17 revision-4 decision-only author handoff

Date: 2026-10-03. Delegated Temp-only authorship, not independent review or ADR
acceptance. No independent verdict is prescribed by this report.

## Completed scope and exact identity

Created the [ADR-065 revision-4 proposal](ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md)
from the immutable root revision-3 snapshot. Only selected-branch/status and
accurate admission text changed; all other proposal bytes are unchanged under
the explicit replacement check. Status remains PROPOSED and acceptance fields
remain empty. The [decision receipt](OWNER-DECISIONS-04.md) records the root
receipt rather than inventing authorization.

Owned output directory:
`C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-contract-r4-b2e378bc196444a98b0822546749ba1a`.

| Identity | SHA-256 |
|---|---|
| Revised ADR-065 | `c50de31ea70a56749c55202bd75de258ff3d89a0ae4a9123ff8ee59e9d3545bb` |
| [Decision-only diff](revision-03-to-04.diff) | `9df8d940384580bf869a32f8515e268e61c4be131dad35ccceeea2107ec8e039` |
| [Local owner receipt](OWNER-DECISIONS-04.md) | `077b39bfcd87c915ca8c4b47d4e8b9cb7d746403d3cb0df91eb3255a48ca2770` |
| Frozen base ADR-065 revision 3 | `92e706ae84e6565f605227f7794fb83850a23c4f2ca1915686d28c61fa72c321` |
| Actual root C17 owner receipt | `279d7cb58682b0c0e4bc58abf93dce3a32effca4a7a94b12f5d4d246fe39c00c` |
| Actual root C18 owner receipt | `ed4352ee786435afa6573f3aede578528e9ae78914cbae2147fccae98f4d028f` |
| Preceding raw independent clock review | `f22b9736e99b554d8d45fbdc3ec1b37e38ebd1f1f53b2af2ee87f7a343c00821` |
| [Byte-preserved coverage companion](covered-ledger.csv) | `3094547f0e4636c74e64f85cbb8305082e8cbaaec10cc84911da12905bb00eeb` |
| [Byte-preserved source companion](upstream-source-checks.json) | `74362178f6393294fd067a859e47c12ce7526219c4aa9dde1f3142e9eef19277` |

Original root evidence remains unmodified:
`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c17-contract/proposals/revision-03`,
`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c17-contract/reviews/revision-03`,
`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c17-contract/OWNER-DECISIONS.md`
and `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18-contract/OWNER-DECISIONS.md`.
The root snapshot was HEAD `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6` on
`codex/v1.8.0-classic-content`, with retained dirty development work. The root
TASK and old review receipt retain historical pending-choice wording; this
derivative uses the newer actual owner receipt, without editing those records.

## Selected design boundaries

- D1-A: retain basic rocket metrics/item-fuel API and the proposed classic tier
  ratios; independent actual Fluid with separate oxidizer/working-fluid roles.
  No numeric retuning or implicit typed ADR-026 provider upgrade is selected.
- D1-disassembly-A: consent binds the complete typed vector before deliberate
  propulsion disposal; ordinary cargo Fluid always restores. Typed host plus
  external engine/capacity is refused; existing abstract compatibility remains.
  D1-disassembly-B typed-tank materialization is an unselected alternative.
- D2-A: continuous analytic three-axis rotation and logical altitude, without
  moving station blocks, retaining linked/busy elevator endpoint restrictions.
- D3-A: one persistent transaction/receipt coordinator for resource automation
  and satellite-bay deployment. Forced-stop recovery verification precedes
  interaction admission. D3-B's added torn-save residual and a same-tick-only
  substitute are not approved fallbacks.
- C18 D3's world-first recorded-participant branch is now selected. Only the
  status of its already-proposed joint first-event schema clause changed.

No C17 material product alternative remains pending in the proposal metadata.
That is not technical-contract acceptance or proof of feasibility. A change of
selected branch, numeric baseline or residual boundary requires a new owner
decision and applicable independent review.

## Retained technical, feasibility and shared-schema gates

The exact D3-A disk authority, forced writer/barriers, unknown-outcome handling,
receipt retirement and native S2 fault cuts remain a focused implementation
admission requirement. A vanilla save event is not a durable acknowledgement;
same-tick conservation and a clean restart are not the selected crash guarantee.
The existing conditional clock specification is unchanged and still needs Java,
native checked-write, restart/rollback, saturation and cache/session verification.

Shared C17/C18 boundaries still freeze once: station root 5/record 3 with the
same authority clock and source-outbox migration, station journal 3, sky payload
2 with C18 sky-kind plus C17 distance/phases/rates, celestial protocol 3 to 4 and
rocket-flight protocol 8 to 9. Equipment finder stays HEAD, consumes one of the
existing two slots and publishes only enabled in the immutable summary. Exact
wire fields/size bounds and migration/checked transition requirements are
unchanged; no extra bump is introduced. Required C16 deliveries must be verified.

C18 automatic pad/player durable writes (D4) are not proved or substituted here.
Shared first-event recording still needs actual source-coupled persistence and
native recovery evidence. Client V1/V2, survival progression, provenance/license,
dedicated-server/native persistence and performance gates remain open.

## Files, checks and commands actually executed

New/updated files are confined to the owned Temp directory: proposal, two
byte-copied companions, local receipt, author [static probe](probe-04.py),
[probe results](PROBE-RESULTS.json), diff, this handoff and file-hash inventory.
No repository, preceding proposal/review, runtime, asset or ledger file was
written. The source companion is preserved evidence, not a new upstream audit
or an import permission.

Commands were bounded/read-only except creating these owned Temp outputs:

| Command/check | Actual result |
|---|---|
| PowerShell `Get-Content`, `rg`, `Get-FileHash` on base, review and receipts | Exit 0; read source/proposal/review/owner identities and relevant clauses. |
| `git rev-parse HEAD`; `git branch --show-current` | Exit 0; identities reported above. |
| `git diff --no-index -- <base-ADR> <Temp-ADR>` | Exit 1 because the files differ; displayed the decision/status edits. |
| `python -B <Temp>/probe-04.py` | Exit 0. Verified pinned base/receipts/review hashes, the complete 22-replacement author edit scope, empty acceptance fields, unchanged companions and exact covered-row equality with the current ledger. |
| Python trailing-whitespace assertion on the revised ADR | Exit 0; no trailing-whitespace lines. |
| `git diff --no-index --check -- <base-ADR> <Temp-ADR>` | Exit 1 with no whitespace diagnostics; recorded as returned, not presented as an exit-0 Gate. |

The row check compares all fields for the 41 existing PLANNED C17 units; it does
not treat the count as implementation acceptance. The 26 retained source-check
records and their identity fields are unchanged. The whole-proposal replacement
assertion preserves everything outside decision/status spans, including all
numerical tables, budgets, migrations, clock arithmetic, recovery details and
wire definitions. It is an author static check, not independent semantic review,
a runtime test or native crash proof. No Gradle, Minecraft, native/GPU test,
asset generation/import, commit or tag was run.

## Uncompleted scope, evidence and next action

No runtime implementation, independent decision-only verdict, canonical ADR
acceptance, ledger closure or release approval is completed by this assignment.
Current v1.8 Required Gates are **not satisfied** by these artifacts. The exact
evidence is local to the links above, with original immutable provenance stated
in inline paths and hashes.

The root integrator should obtain independent review of this decision-only diff,
then decide canonical acceptance under the existing authorization. Subsequent
work stays within v1.8 and must freeze/verify the retained shared schema and
native durability leaf contracts before admitting their dependent interactions.
