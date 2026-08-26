# Codex Task 02 — Implement the next incomplete version

Read:

1. `PROJECT-CONFIG.md`
2. `AGENTS.md`
3. `docs/status/CURRENT_VERSION.md`
4. `docs/status/GATE_STATUS.md`
5. `docs/04-VERSION-ROADMAP.md`
6. the document for the first version that is not `PASSED`
7. `docs/05-MASTER-TEST-PLAN.md`
8. `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`
9. system-specific architecture/provenance documents

Implement only that version, or one reviewable PR slice of it.

Before coding:

- inspect the repository and existing implementation;
- list completed, missing, and contradictory requirements;
- list explicit non-goals;
- state save/network/provenance impact;
- split the work if the whole version is too large.

During implementation:

- preserve server authority and hard limits;
- do not copy unapproved sources;
- add tests with the feature, not later;
- do not introduce future-version frameworks;
- keep common/client sides separated;
- update work logs and status evidence.

At the end:

- run all feasible required commands;
- record actual outputs;
- leave failed tests visible;
- mark only `READY_FOR_AUDIT`, never `PASSED`;
- use the `AGENTS.md` completion report format.
