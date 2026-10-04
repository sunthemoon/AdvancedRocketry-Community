# C17 contract revision 3 handoff

Date: 2026-10-03. Delegated author; root repository read-only.

## Completed range and files

[ADR-065 revision 3](ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md)
adds section 5.2.1's bounded private persisted orbital-clock contract and updates
heartbeat/interpolation requirements in response to independent M6.
[DISPOSITIONS-03.md](DISPOSITIONS-03.md) responds to that actual finding and
preserves the prior conditional eight-finding dispositions/owner restrictions.
Earlier revisions and independent reports remain unchanged.

Original independent review provenance:
`C:/Users/Administrator/AppData/Local/Temp/arce-v180-c17-review-r2-d2f35effb88246f084b6ce11e76650af/REVIEW-02.md`,
SHA-256 `709b32387b9ec031830eafde7dde1a10c1ae24f035935ee028ff2744b7aba61f`.
No absolute filesystem Markdown targets are embedded in these companion docs;
the original report remains bound by this hash, and local links are portable.

[artifact-hashes.json](artifact-hashes.json) records finished proposal/response/
probe/evidence identities. [covered-ledger.csv](covered-ledger.csv) remains exact
41 primary PLANNED rows, C17a 24 / C17b 9 / C17c 8; no content was reduced.
[upstream-source-checks.json](upstream-source-checks.json) remains the unchanged
26 approved pinned AR sources, with no code or asset import.

## Decisions and remaining work

Private station-root clock and record epochs are one candidate authority; runtime
server ticks advance it without getTickCount/world/wall time and no offline
catch-up. Root-4 migration initializes zero; malformed/epoch-ahead roots preserve
originals; unknown writes keep existing quarantine; overflow is explicit and
phase multiplication is modulo-reduced. Save/heartbeat/render sampling is bounded.
The normal autosave checkpoint can rewind unsaved visual rotation on abrupt stop;
no per-tick durable animation, physical station movement or solar penalty is claimed.

ADR remains PROPOSED. D2-A product behavior is owner-confirmed, not technical
approval. D1, D1-disassembly and D3 remain pending; exact D3 writer/barriers,
clock native save-order fixtures, API/protocol consumers, patterns/recipes/assets
and full implementation/Gates are still work. Conditional C18 first-event outboxes
join one shared station-5/journal-3 migration only after that policy is selected.

## Actual commands / tests / evidence

Read the independent M6 report, accepted ADR-041 and actual station SavedData/
payload/write-budget and satellite monotonic-clock baseline. Copied only this
author's revision-2 proposal/coverage/source-check files to a new revision-03
Temp child; applied manual document/probe edits via apply_patch.

Ran `python -B probe-03.py`: actual result in
[static-checks-03.json](static-checks-03.json). This static/model probe rechecks
exact ledger/source/protocol/API/wire identities and exercises specified clock
ordering/modular arithmetic/overflow/migration/restart examples. It does not
implement or execute a Java/Forge clock or checked writer. No Gradle/native/
GameTest/server/crash/benchmark/GPU/two-client command ran; no repository file,
runtime or test source changed. No subagent, OpenPet, upstream asset copy or
protected development-docs inspection occurred.

## Risks / Gate status / next scope

Independent revision-3 clock review is required before the affected contract
freeze. Proposed clock/writer integration still needs real migration/native
ordering fixtures; owner-pending resource choices cannot be inferred from a
review result. No Required Gate is met by this author handoff. Next v1.8 work:
independent rereview, explicit pending decisions, eligible shared contract freeze,
then scoped implementation and actual evidence.
