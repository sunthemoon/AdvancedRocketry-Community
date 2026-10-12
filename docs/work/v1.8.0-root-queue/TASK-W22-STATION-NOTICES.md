# TASK W22: station notices (ADR-065 section 5.3)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w22-station-notices` |
| Branch | `codex/v1.8.0-w22-station-notices` (local only; Root merges) |
| Ledger plan | C17b, the section 5.3 gaps that W07 left open (`records/W07.md`, "Not done") |
| Record | `docs/work/v1.8.0-root-queue/records/W22.md` |

This slice adds a SavedData, so AGENTS.md section 13 applies:
- **First commit:** a one-page design note, `records/W22-DESIGN.md`, before any code.
- **Before merge:** one independent read-only review, which Root dispatches.

## Goal

Close ADR-065 section 5.3:
1. **Translatable notices.** Warp start, commit and abort notices become translatable
   messages, using the existing `station.advancedrocketrycommunity.*` code keys in the v1.8
   language namespace, in English and Chinese. Tests assert code tokens, not English prose.
2. **Who is told.** Each notice names the affected station, and goes to:
   - authorized actors;
   - visitors currently inside the station's region.

   It never reveals other hidden regions' coordinates or member IDs. The countdown uses
   only the action bar.
3. **The offline member-notice SavedData:**
   - **Size.** One latest committed summary per station, at most 4,096 stations. It is not
     an event history.
   - **Acknowledgements.** At most 32 member acknowledgement revisions per station.
   - **Bounds.** A localized argument envelope of at most 512 bytes. A root at schema 1,
     depth 16 and at most 8 MiB.
   - **Updates.** A new station gets an empty summary. A new outcome replaces the summary
     in place, with an increasing revision.
   - **Membership.** A removed member's acknowledgement is removed, and no notice is sent
     without checking current membership.
   - **Login.** An unacknowledged member sees the latest outcome once at login, and the
     server then records the acknowledgement.
   - **Audit.** Audit details stay in the existing server audit.

## Read

- ADR-065 section 5.3 and sections 5.1 and 5.2 (station controls).
- ADR-046 UI-02.
- The W07 and W07F records.
- The station registry and its warp service.
- The existing station audit.
- An existing bounded SavedData with a schema and preflight, to use as a pattern.

## Tests

Section 5.3 requires these tests:
- byte-budget admission;
- schema;
- overflow;
- restart;
- removed-member privacy.

Also test:
- the visitor-in-region delivery;
- the hidden-region privacy;
- login acknowledgement, exactly once;
- the code-token assertions.

Pace any loading wait with `GameTestTickPacer`.

## Rules

- An undecodable root is kept verbatim and handled under AGENTS.md section 3.3. If the
  slice isolates data, add the risk entry and the ADR note text to your record. Root adds
  them.
- Do not add a configuration key unless the ADR asks for one. If you add one, follow the
  WORKER-RULES configuration-key rule.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Other station behaviour.
- Network channel changes. If a notice needs a packet, use vanilla chat and action-bar
  components.
- Status files and the ledger.
