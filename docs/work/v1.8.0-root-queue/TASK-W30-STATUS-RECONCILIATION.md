# TASK W30: draft the v1.8 status reconciliation (a proposal for Root)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w30-status` |
| Branch | `codex/v1.8.0-w30-status` (local only) |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W30-STATUS-PROPOSAL.md` and nothing else |

Under AGENTS.md section 13, status files and the ledger are Root's. **Do not edit**:
- `docs/status/*`;
- the content ledger;
- ADRs;
- the risk register.

Write only the proposal file. Root reviews it and applies it.

## Why

`docs/status/COMPLETION-PLAN.md` and `docs/status/CURRENT_VERSION.md` predate the Root
handover on 2026-10-11. Since then, Root has merged many v1.8 slices into
`codex/v1.8.0-classic-content` through `docs/work/v1.8.0-root-queue/QUEUE.md`. The plan still
shows, for example, C16b and C16c as not done, although W05 and W10 merged them. Older
pre-handover items (C18a-SLEEP-01, C18a-AIRLOCK-01, C18d-SKY-01 and others) have unclear current
state.

## What to produce

1. **A per-chunk table for C16 to C19.** For each line item in `COMPLETION-PLAN.md`:
   - its current state: delivered, partly delivered, superseded, or open;
   - the evidence: the merge commit on the shared branch, the hosted CI run, and the review
     REVIEW.md with its SHA-256, all taken from QUEUE.md and the merge commit messages;
   - what remains open. Typical open items are V1/V2 visual and multiplayer evidence,
     dedicated-server restarts, and S2.
2. **The content ledger.** For each row affected by the v1.8 merges, the proposed new status
   and its evidence. Find the ledger file, and do not change it.
3. **Pre-handover items.** For each older in-progress or unverified item, find what exists:
   - its branch and worktree;
   - whether its work is merged into the shared branch, superseded by a later Root slice, or
     still pending.

   Propose a disposition for each: close as superseded (citing what superseded it), merge,
   or re-dispatch.
4. **A replacement for `CURRENT_VERSION.md`.** Propose a short current-state text: one current
   figure per metric, following AGENTS.md section 10's hygiene rules. Gates and the version
   status are **not** marked passed; only the owner does that.
5. **The remaining v1.8 work** that the queue does not cover yet, if you find any, each with
   the ADR section it comes from.

Cite a commit SHA or file path for every claim. If something cannot be verified, say so. Do
not guess.

## Not in this task

- Editing any status, ledger, ADR or risk file.
- Code.
