# TASK W227: exact drafts for the owner's scope decisions (D2, D3, D4, D6, D9, D11, D12)

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w227` (new; a detached checkout of the shared head named in your prompt) |
| Decisions | [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): D2, D3, D4, D6, D9, D11 and D12, each with its scope |
| Sources | `docs/work/v1.8.0-c18d-asset-closure/AUDIT-01.md` (sections 5 and 6, the per-row no-consumer evidence); `docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md`; ADR-061; the asset ledger and allowlist the C18d closure uses; `docs/work/v1.8.0-content-ledger.csv`; `docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md` (sections 10.5 and 13.5); `records/W172.md` or its inventory (`D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md`); `records/W38.md` (D11); the Known Issues location the version document or release docs name; W223's brief `D:/GitHub/ARCE-Task-Evidence/root-queue/W223/OWNER-DECISIONS.md` |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W219/W219-drafts.md` and its audit script |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W227/W227-drafts.md`, an audit script and its JSON result; plus `W227-rows.csv`, the exact per-row asset disposition list |

**Root decision R219.** The owner decided seven scope questions on 2026-10-31. Root needs exact, audited text for each
central file before applying it.

## What to do

1. **ADR-062 revision (D6, with D2, D3 and D4):** a dated revision section that moves to EXCLUDE exactly the rows the
   decisions cover, each named, with its no-consumer evidence from AUDIT-01: the 81 class E rows without a runtime
   consumer (D6), including the 45 sky rows (D4, the code-drawn sky accepted, confirmed later on a real GPU); the seven
   legacy translation rows (D2: v1.8 is English and Chinese, v1.9 may reopen them); the AR 16x old files (D3: W49's NEW
   replacements stay, old files EXCLUDED, class E rows excluded). Count the rows exactly, avoid counting any row twice
   across D2, D3, D4 and D6, and state the published appearance differences. The revision cites the owner record; it
   does not mark NEW art as reviewed (that is D7).
2. **The asset ledger, allowlist and porting-matrix changes** that follow, as exact drafts, with the re-pinned allowlist
   and the per-row list in `W227-rows.csv`.
3. **D9:** a revision of the version document's sections 10.5 and 13.5 to Forge × JEI for v1.8 (the four W188 cells as
   the compatibility evidence to rerun on the final candidate), the full modpack moved to v1.9, the reference workload
   and the four-hour soak kept, and the published support boundary.
4. **D11 and D12:** Known Issues entries: native unscaled fall damage for horses, donkeys, mules, llamas, camels and
   their passengers off-world (no new Mixin; W38's GameTest finding), and the fixed-midnight Moon, each to be confirmed
   in the real-client session.

Every draft keeps historical text historical, approves no Gate, and closes no ledger row the decisions do not close.
The audit script checks unique non-overlapping anchors, table and CSV structure, the row counts against AUDIT-01, and
that no row appears twice; run it and keep its JSON result.

## Not in this task

Any tracked file, D5, D7, D8 (a separate task), the bay decisions, risk acceptance, status files.
