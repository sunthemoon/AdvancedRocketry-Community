# CL18B-EQUIPMENT-01: equipment module contract and test-design draft

Date: 2026-10-07. Milestone: v1.8.0 / C18b. Task type: contract/test design.
Owner: Claude, delegated external worker. Integrator/sole Git writer: Root/Codex.
Reviewer: a different read-only Codex session on actual draft return.
Status: READY for draft authoring after publication; production implementation
remains DEPENDENCY_BLOCKED. Contract status: proposed, not frozen. No author
start/model execution or delivered source is inferred.

## Registered checkout and ownership

- Base branch: codex/v1.8.0-classic-content.
- Actual base commit: a65dcbf68143ce63af3b2205b0c36af02eaae0e8.
- Branch: codex/v1.8.0-claude-equipment-contract-20261007.
- Worktree: D:/GitHub/arce-v180-claude-equipment-contract-20261007.
- Evidence: D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-equipment-claude-author-20261007-01/.
- Read/hash the published Root-checkout TASK; it is newer than and absent from
  the fixed worker base. Read the live user-maintained Root AGENTS read-only.

Entire repository write_scope: three NEW files in this worktree:
- docs/work/v1.8.0-c18b-equipment/CONTRACT-01.md
- docs/work/v1.8.0-c18b-equipment/TEST-DESIGN-01.md
- docs/work/v1.8.0-c18b-equipment/HANDOFF-01.md

No other writes, including TASK, source/tests, model/API, equipment providers,
life-support authority, registry/config/network/save, shared machine adapters,
player-file hooks, transactions, DataGen/resources/assets, build, provenance
approvals, AGENTS, status/log/ledger/CSVs or Git mutation. All remain Root's.
Do not implement modules or use a fake refill/transaction as a working device.

## Exact assigned units and observable output

Twenty fixed CL18B-EQUIPMENT CSV units:
`block:suitWorkStation`, `config:CATEGORY_GENERAL.jetPackForce`,
`config:CATEGORY_GENERAL.lowGravityBoots`,
`config:CLIENT.EnableAtmosphericNausea`, `config:OXYGEN.spaceSuitO2Buffer`,
`config:OXYGEN.suitTankCapacity`, `enchantment:spacebreathing`,
`event:PlanetEventHandler.fogColor(RenderFogEvent)`, `item:jetPack`,
`item_variant:itemUpgrade/0`, `item_variant:itemUpgrade/1`,
`item_variant:itemUpgrade/2`, `item_variant:itemUpgrade/3`,
`item_variant:itemUpgrade/4`, `item_variant:itemUpgrade/5`,
`item_variant:pressureTank/0`, `item_variant:pressureTank/1`,
`item_variant:pressureTank/2`, `item_variant:pressureTank/3`,
`keybinding:toggleJetpack`.

Produce proposed exact unit mappings, slot/module-kind table, bounded owned
payload and detached-stack mutation/eligibility contract, config/numeric table,
server summary and client effect/input boundaries. Cite current implementations
and accepted decisions; distinguish ADR proposals from frozen numbers/ports.
Identify missing dependencies before recommending atomic implementation leaves.

Respect D1: existing external provider/API/HUD active oxygen remains 0..2,000;
finite auxiliary storage is built-in only, debit/credit are one bounded owned
item commit, no gas mirror or mutation on query/simulation/HUD. Numeric tiers,
multiplier and balances need their own review; the owner's mechanism choice
does not automatically approve every ADR proposal. No zero-cost infinite air.

Define workstation two transfer slots and fixed armor module positions, real
component transfer with gas/durability/unknown unrelated data preserved, no
automation armor editor and no second persisted workstation gas mirror.
Include rejected slots/duplicates/pending transactions/unsupported roots,
shift-click against detached copies and ordinary removal conservation. C17's
beacon finder shares existing HEAD positions; do not add or register it again.
Shared summary/interface remains Root-owned, with no C17/C18 dependency cycle.

## Inputs, dependencies and non-goals

Read mandatory governance/current-version docs, fixed allocation CSVs, ADR-025,
ADR-066 sections 2/4/5.1/5.2/8/9 and accepted C17 interoperability clauses,
current provider/oxygen/item/menu/movement/client input implementations/tests,
and their provenance records. Before upstream inspection read UPSTREAM, NOTICE,
docs 02/08; inspect only existing local pinned permitted sources, no new
download/copy/import/cache/archive or source approval.

Root owns D4 player-file/source durable charging feasibility and raw-data
preservation, equipment common/API summary, authoritative debit/refill lifecycle,
protocol/registration and machine recipes. These are not proven by same-tick
stack edits or a mock receipt. Do not start automatic pad charging, invent
save certainty or alter preservation/refusal policy. Any unsupported root/write
refusal proposal must describe object/chunk/Level impact, duration/log/recovery
for Root ADR/risk review; this draft does not accept R-021.

Jetpack/motion is server-authoritative and gas-backed, with bounded input and
validation; sensory effects use server state without changing environment
truth. Enchanted host eligibility is a separate ADR-025 additive proposal, not
permission to register every unenchanted armor item or steal external roots.
Mining tools (the three CL18B-TOOLS units), other HUD panels, assets and actual
rendering are outside this task; return any interop need as a dependency.

## Allowed commands and evidence

Use the owner's existing interactive Claude session; Root launches/authorizes
no separate paid CLI/API run. Report actual model/session/time/usage observations
versus unknowns, no credential inspection. Check status/HEAD/branch/worktrees
before writing; do not take over others' changes. Start HANDOFF with actual start,
TASK hash and checkout/scope; checkpoint at 90 minutes if unfinished. Each
repository draft <=128 KiB, own evidence <=10 MiB.

Allowed: finite PowerShell file reads/hashes/listing, rg, read-only Git status/
rev-parse/branch --show-current/worktree list/diff/show/ls-files, scoped manual
edits and explicit Python -B stdlib text/CSV/hash/diff/link checks, not the
pyenv shim. Scripts/logs and process-local TEMP/TMP/TMPDIR stay in this owned
evidence leaf/temp. No javac/java/JVM/Gradle/server/client, cache/archive,
network/download/install, nested agents, global settings, others' cleanup,
Git writes, source implementation or ledger/Gate updates. Needed extra
commands are reported, not executed without a new scoped task.

## Acceptance, return and release

CONTRACT covers all twenty units, slot/gas/ownership/state/numeric/proposed
interface boundaries and actual dependency evidence, with unresolved choices
explicit. TEST-DESIGN covers finite gas conservation/debit/query purity,
config snapshot changes, invalid/unknown/over-limit data preservation,
install/remove/full slots/inventory/shift-click, pending transactions, external
provider precedence, beacon interop, toggle/motion/fog authority and cleanup.
Specify future native dedicated restart/forced-cut/player-file tests and V1/V2
separately; a planned fixture or plain schema round-trip cannot prove durability.

HANDOFF records actual new-file diff/hashes, commands/exits/raw failures,
executed versus planned checks, license/config/schema/network/save impacts,
Root integration requirements and remaining decisions. Preserve sealed inputs,
release all reads/processes/HEAD/index interests. Independent draft review and
Root contract freeze precede any fresh atomic implementation assignment. No
content promotion, parent completion, asset approval or v1.8 G0-G9 verdict.
