# Star-system and warp contract review record

Date: 2026-09-30. Branch: `codex/v1.5.0-orbital-station-warp`.
Reviewed commit: `82568b85f5b5f088fad6a6488bebe00043204b18` (ADR-043/044 drafts).
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
