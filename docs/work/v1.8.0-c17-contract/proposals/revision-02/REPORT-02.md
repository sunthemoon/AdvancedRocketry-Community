# C17 contract revision 2 report

## Completed scope

Revised the C17 author's Temp-only proposed ADR-065 in response to all eight
independent findings (5 Medium / 3 Low), with per-finding dispositions. Revision 1
is untouched; repository is read-only. No runtime, asset import, acceptance,
ledger closure or version Gate is claimed.

Artifact directory:
`C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-contract-489381f9d14b45928ec89711c681ed16/revision-02`.

Hashes are recorded in `artifact-hashes.json` after author revisions and static
checks. Proposal is `ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md`;
responses are `DISPOSITIONS-02.md`; exact rows are `covered-ledger.csv`.

## Exact coverage and dependencies

Unchanged 41 primary PLANNED units: C17a 24 / C17b 9 / C17c 8, field-equal to
current repository C17 ledger rows and all explicitly named in section 12.
The copied coverage CSV is byte-identical to revision 1. Internal MERGED rows
follow owners, not blanket asset import/closure authority.

C17a depends on actual C16 materials/fluids/recipes, current ADR-026/027 component/
fuel compatibility and checked snapshot/assembly/flight/transfer/disassembly
authority. C17b depends on checked station commits/warp, elevator conditions and
shared protection API. C17c depends on moving-resource authority, satellite
admission, pattern/output interfaces and C18b helmet integration. Original/approved
assets, C16d reachable recipe graph and unchanged G0-G9 remain prerequisites.

Joint C18 freeze must replace its exclusive visor/hover HEAD description with
the accessory eligibility table including beacon_finder (same two slots/max-eight
modules), and reference the single station context schema-2/celestial protocol-4
transition with singularity sky-kind. C17 does not define C18 equipment/motion
packet IDs or claim C18's unmodified revision-1 table meets this prerequisite.

## Key decisions and incomplete scope

**Owner-confirmed D2-A:** analytic continuous three-axis sky rotation and logical
altitude, no physical blocks/players moved; conservative changed-setting refusal
while elevator linked/busy; no new solar orientation semantics. Confirmation is
not acceptance of the complete ADR, codec, checked transition or implementation.

**Still pending:** D1 numerical/Fluid representation and its explicit external
component compatibility limit; D1-disassembly complete-vector deliberate disposal
versus actual native carrier restoration; D3 durable coordinator versus explicitly
extended two-store torn-save boundary. Recommended disposal reuses existing scalar
consent semantics but cannot be treated as owner-approved for actual Fluid. Exact
D3 disk authority, source acknowledgment and receipt-retirement proof still block
dependent automation admission. Raw/future/unknown resource retention is unchanged.

New technical boundaries include complete-vector immutable consent, independent
bay receipts surviving live decommission, immutable sealed force-field state
regardless of source-chunk availability, exact current API/protocol transitions,
joint HEAD module requirements, and count+byte+block-visit reconstruction budgets.
All are proposed and awaiting independent rereview. Force-field API addition is
minor 1.8 -> 1.9; channels are celestial 3 -> 4, flight 8 -> 9, classic_controls 1.

## Tests required, not executed

Added contract tests for bank-vector disposal/cargo distinction, external integrated
roles and scan order, bay decommission/source-restore/retirement cuts, actual finder
workstation and private equipped/held behavior, source/field cross-chunk sealing
and supplied-room invalidation, old/new API listeners, maximal integrated wire
frames and full-snapshot allocation/GC/MSPT/fairness. Existing A0/A1/S1/S2/V1/V2
and provenance/migration/native unsupported-root tests remain required.

There are no new runtime tests in the repository. No Gradle/native/heavy process
or performance campaign ran; the specified budget is not a measurement result.

## Actual commands and evidence

Read-only `Get-Content` / `rg` of proposal, independent review, exact actual
protocol table, API versions/enum/event, ADR-021/054, component definitions,
RocketDisassemblyService, SatelliteMissionRegistry, flight codecs and limits.
Early guessed `com/...` class paths and two guessed file locations were absent;
`rg --files` found their actual `io/github/...` paths and the factual claims use
those corrected sources. No missing guessed path was treated as behavioral evidence.

Copied only this author's revision-1 proposal/coverage/source-check files into a
new Temp revision-02 directory, then used apply_patch for author document edits.
Ran the Temp static probe: exact coverage/IDs, inherited manifest/source hashes,
PROPOSED/owner-pending metadata, current protocol/API baselines and packet-size
arithmetic. Its actual results and command identities are in `static-checks-02.json`.
`git diff --no-index --check` of the original and revised Temp ADR returned 1
for differing files with no whitespace-error diagnostic; the static probe also
asserted no trailing whitespace. This is not the repository release diff gate.

Approved upstream remains AR commit c5cd5af62fc07cd4e0d24f06a16033f181c47c04,
26 source files matched manifest/inventory; no copied implementation/assets,
unapproved LibVulpes or protected development-docs inspection. Static source
identities are evidence for the factual baseline, not a runtime delivery.

## Risks, gates and next scope

Owner-pending semantics, exact D3 durability proof, matching C18 contract updates
and independent rereview remain open. Revision 2 fills author-side gaps but is
not an independent no-findings verdict. No Required Gate is satisfied. Parent may
assign independent revision-2 rereview, then accept only eligible reviewed contract
sections before implementing the related v1.8 leaves. No work outside v1.8 is proposed.
