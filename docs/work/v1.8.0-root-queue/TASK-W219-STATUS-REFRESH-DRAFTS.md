# TASK W219: exact drafts that bring CURRENT_VERSION's open items up to date

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w219` (new; a detached checkout of the shared head named in your prompt) |
| Target | `docs/status/CURRENT_VERSION.md`, the section "What is still open" and any other passage of it that today's merges made wrong (leave `latest_code_commit` and `latest_hosted_regression` alone: Root keeps those) |
| Inputs | the QUEUE rows of W204 and W207 to W218 and their records in `docs/work/v1.8.0-root-queue/records/`; the W211 row and its record on `codex/v1.8.0-w211` (read it; it is still in progress); `docs/11-RISK-REGISTER.md` (R-030, R-035, R-040); `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md` sections 1.2 and 3 |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W205/W205-drafts.md` and its audit script |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W219/W219-drafts.md`, plus an audit script and its JSON result in the same folder |

Since W205's refresh, these merged on the shared branch (2026-10-30): W204 (N3-C's native refusal, stop and named-pad
cuts and the acknowledged-absence fix), W207 (W90's request costs), W208 (R-040 near-bound availability), W209 (an
escrow-monitor GameTest and the autosave), W210 (C1 admission sizing), W212 (relaunch attribution), W213 (the
ground-survey START cost), W214 (R-040 and ADR-074 notes), W215 (relaunch class initialization at startup), W216 (the
ground-survey preflight), W217 and W218 (their follow-up checks and R-035 and R-030 wording). W211 (B3-S6) is in
progress. Write exact `Target` / `Replace exactly` / `With` replacements so that the open-items text says what is true
now:

1. State what each merge closed or narrowed, citing merge commits and records as the file already does, in complete
   English sentences; for example "N3-C remains open under W204" is no longer true.
2. Keep every real gap: B3-S6 (W211, in progress, with its interim review's findings), W90's two full-observation
   first-use cases over 100 ms, R-040's interrupted, traffic, expiry and storage-fault cases, the S1 campaign and its
   three failed downgrade rows, the reference, soak and client (V1 and V2) evidence, the owner's pending decisions,
   and no approved Required Gate.
3. Keep historical results as history; never rewrite an old result or remove an earlier caveat that still applies.
4. Do not change any version or Gate status.

The audit script checks that every anchor is unique in the current shared-head file, that nothing overlaps, and that
the file's structure (headings, list items) is kept; run it and keep its JSON result.

## Not in this task

Any tracked file, QUEUE, COMPLETION-PLAN, ADRs, the risk register, the ledger and any change of a version or Gate
status.
