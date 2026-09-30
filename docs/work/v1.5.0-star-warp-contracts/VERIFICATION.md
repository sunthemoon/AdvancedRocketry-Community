# Star-system and warp contract review record

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Reviewed commits: `82568b85f5b5f088fad6a6488bebe00043204b18` (ADR-043/044 drafts) and
`7180885` (ADR-044 revision 2, re-review).
This is a documentation and contract record, not runtime evidence or a Gate approval.

An independent contract reviewer read the drafts against the v1.5 plan,
AGENTS.md and ADR-028/031/037/038/040/041/042, and checked feasibility in the
code. Its unmodified report and evidence are in `independent-review.zip`. The
reviewer ran Gradle only in a deleted export of `2fb9bce` (Part B).

## Verdicts

- **ADR-043 (star systems as root trees): accept with changes.** All six
  required changes are applied:
  - warp targets aligned with ADR-044;
  - the compatibility impact of the 16-root limit and the cross-system route rule
    on the initial load, with operator guidance and a required test;
  - system membership derived at each reload, with warp cost computed at commit;
  - packaging of the superseded satellite definition;
  - placeholder star values, and no discovery required for the star;
  - evacuation from a missing orbit body (ADR-044).

  ADR-043 is **ACCEPTED**, with an acceptance record.
- **ADR-044 (warp as logical relocation): rejected as written; revision 2
  submitted.** The architecture is kept. The three High findings are addressed
  as follows:
  - **H1 (energy was paid after the commit):** energy now lives only in the
    station registry (root field `warp_energy`), so a warp is one checked write
    that debits the balance and changes the orbit together. A full crash-cut
    matrix is included.
  - **H2 (core identity):** cores are stateless terminals, so there is no core
    identity, debt or settlement.
  - **H3 (rocket rule):** the in-motion rule uses only the transfer journal
    (phases `PREPARED` to `SOURCE_REMOVED`, and `COMMITTED` while still
    descending). It makes no entity query and fails closed when the journal is
    not operational. Landed reservations do not block.

  The Medium and Low items are addressed in the revision text:
  - root schema 4 and its migration;
  - the entry command with a ray-picked core;
  - confirmation, countdown limits and abort rules;
  - evacuation;
  - a kill switch;
  - a plan traceability table;
  - disclosure that shared-Space systems are physically connected;
  - a port interface for rocket state;
  - `amends` headers;
  - normalizing the current body at launch, and rejecting stale quotes;
  - cost bounds.

  **Revision 2 is PROPOSED and needs re-review before any warp runtime code.**
- Part B confirmed `2fb9bce` with only Low/Info notes. Those notes are applied
  in `30b9b11` ([capacity verification](../v1.5.0-station-capacity/VERIFICATION.md)).

## ADR-044 re-review (revision 2) and acceptance of revision 3

A second independent reviewer re-reviewed revision 2 at `7180885`, read-only (no
Gradle, no build, no repository writes). Its unmodified report is in
`independent-review-2.zip`.

- **Verdict: accept with changes.** No Critical or High finding remains. The
  earlier High findings H1-H3 are resolved. Five Medium findings (N1-N5) and
  several Low findings needed contract text only.
- **Revision 3 applies all nine required changes:**
  - N1: a registry-level `isOrbitRelocationOf` transition and
    `checkedRelocation`, with one synchronized publish (no half-published
    commit path);
  - N2: charge is folded into the balance every 200 ticks, not dirtied every
    tick;
  - N3: settled `PREPARED` records do not block, and warps fail closed only
    until recovery has classified every record;
  - N4: one commit per server tick, a 100-tick cooldown, confirmation caps and
    a minimum cost of 100,000 FE;
  - N5: cores accept energy only in the Space Level, on the server thread,
    honour `simulate`, and report 0 stored;
  - N6: a schema table for roots 1-4 (amends ADR-040);
  - N7: consent to cost and class, an actor recheck at commit, and a no-route
    warning;
  - N8-N13: a corrected crash-cut matrix and disclosure, removal of the stale
    quote rule, the restated invariant, and "gravity does not follow";
  - a plan traceability table, the warp core recipe and a COMMON config.
- **ADR-044 revision 3 is ACCEPTED** with an acceptance record in the ADR. WARP-01
  is verified as a contract. WARP-02 (root schema 4, balances, relocation
  transition, crash-cut tests) may start. No runtime code exists yet.
