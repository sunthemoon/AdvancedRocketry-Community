# TASK W269-fix: the campaign gate review's corrections (W269-R1 to R5)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w269` (existing; branch `codex/v1.8.0-w269` at `3f639257a`) |
| Task it answers | [TASK-W269](TASK-W269-V2-CAMPAIGN-GATE.md) (R298) and its review `D:/GitHub/ARCE-Task-Evidence/root-queue/W269-review/REVIEW.md` (SHA-256 `3ebf5a25f1854487a78eff2785b361b5c05bee7551b9cc004a97a11008e86bf9`, APPROVE-WITH-CHANGES); read all of it |
| Record | Append a "W269-fix" section to `docs/work/v1.8.0-root-queue/records/W269.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W269-fix/` |

**Root's correction to R298's wording**, recorded in QUEUE: option 1 keeps production bay admission off everywhere,
but on a marked campaign server with the hooks property the full release-test gate is open, and that gate is also D0's
bay deployment admission (ADR-065: admission stays off "outside GameTest and the full release-test gate"). So on the
campaign world D0 admits a bay rocket a named client launches, as it already does for the facility's test players.
This is the intended campaign behaviour; the records must say so exactly.

1. **W269-R1 (Medium).** Reword `records/W269-DESIGN.md`, `records/W269.md`, `records/W254-V2-CHECKLIST.md` section 0.1
   and the ADR-065 note draft (a new draft beside the old one, which stays as history) so they state what the opened
   gate reaches: the single release-test gate behind the gated op-only command families, and D0's bay admission on
   that world. Add a checklist line that any `ARCE_BAY_CLAIM` other than the campaign's own steps is a deviation to
   record. Add a test that pins the D0 consequence: with a valid selection and marker, a named client's bay transfer
   reaches D0's gated admission; with the property unset or the marker missing, it does not.
2. **W269-R2 (Low).** The checklist and the note require `online-mode=true`, a whitelist of exactly the two accounts
   with `enforce-whitelist=true`, and a server nobody else can reach; say why (offline mode does not authenticate the
   UUID).
3. **W269-R3 (Low).** Tests for a same-length marker with wrong bytes, a directory with the marker's name and a
   symbolic link to a correct marker (skip only where the platform cannot create links, and say so), each failing
   against a length-only check.
4. **W269-R4 (Info).** In `ReleaseTestPlayers.ownsAllConnectedPlayersOr`, check `owned(...)` first so the unset path is
   literally the old expression.
5. **W269-R5 (Info).** Update the five Javadoc comments; in the checklist, note that a refused startup still exits
   with status 0 (judge it from the log) and how to write the marker byte-exactly, with its SHA-256.

## Verification

The gate tests (failing first where new), the bay, escrow, release-test and first-use dependency tests, the whole unit
suite, the full GameTest run under the heavy-JVM lock, the four native cases again on a JAR built from the committed
source, and the repository validator.

Commit on the branch; do not push. Root reviews, pushes for a sharded run, and the reviewer's engine rechecks.

## Not in this task

The V2 campaign, real clients, production admission switches, other gates, fixture behaviour and the ADR text itself.
