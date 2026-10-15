# REPORT.md: link correction (W60)

[REPORT.md](REPORT.md) is sealed: [SHA256SUMS.txt](SHA256SUMS.txt) in this folder lists it.
W60 therefore leaves it unchanged (AGENTS.md section 10) and records the corrected links
here. This correction file is in no checksum list.

Reason: the strict validator
(`python -B scripts/validate_repository.py --require-approved-identity`) rejects Markdown
links to absolute paths and to files that Git does not hold (W58 audit, slice C19-D).
Elsewhere W60 rewrote each such link as its text followed by the path in a code span; a path
that exists only in the owner's main checkout also points to the external evidence manifest.
Read each line below in place of the link on that line of REPORT.md.

| Line | Corrected text |
|---:|---|
| 19 | ADR-069:17-29,38-52,90-107 (`D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L17`) |
| 20 | Decision07:9-20,29-35 (`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18a-sleep/OWNER-DECISION-07.md#L9`) |
| 21 | independent64 R64-01:9-33 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/REPORT.md#L9`) |
| 28 | ADR-069:73-88 (`D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L73`) |
| 29 | independent64 R64-02:35-55 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/REPORT.md#L35`) |
| 30 | protocol02:257-289 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input61-proposal02.md#L257`) |
| 68 | ADR-069:33-69 (`D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L33`) |
| 77 | complete37:218-224 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input37-proposal.md#L218`) |
| 78 | protocol02:181-191,217-247 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input61-proposal02.md#L181`) |
| 91 | ADR-069:20-23,71-77 (`D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L20`) |
| 92 | disposition52:12-19,31-58 (`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-c18a-sleep/OFFLINE-CORE-CONTRACT-FREEZE-52.md#L12`) |
| 93 | protocol02:291-315 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input61-proposal02.md#L291`) |
| 104 | ADR-069:79-111 (`D:/GitHub/AdvancedRocketry-Community/docs/decisions/ADR-069-V180-OFFLINE-JSON-MEMORY-WINDOWS.md#L79`) |
| 143 | actual50 child:17-21,49-54 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input50-child-static.md#L17`) |
| 144 | actual50:8-40 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input50-report.md#L8`) |
| 145 | actual55:7-57,264-267 (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-json-resource-protocol-independent-20261009-64/input55-report.md#L7`) |
| 160 | CURRENT_VERSION:106-117 (`D:/GitHub/AdvancedRocketry-Community/docs/status/CURRENT_VERSION.md#L106`) |
| 161 | COMPLETION-PLAN:354-362 (`D:/GitHub/AdvancedRocketry-Community/docs/status/COMPLETION-PLAN.md#L354`) |
| 162 | implementation log:3-14,36-41 (`D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-implementation-log.md#L3`) |
| 166 | INPUTS.tsv (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-memory-window-adr-independent-20261009-69/INPUTS.tsv`) |
| 180 | COMMANDS.md (`D:/GitHub/ARCE-Task-Evidence/v1.8.0/sleep-memory-window-adr-independent-20261009-69/COMMANDS.md`) |
