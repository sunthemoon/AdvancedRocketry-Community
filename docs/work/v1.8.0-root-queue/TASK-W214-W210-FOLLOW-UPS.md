# TASK W214: W210's follow-ups, R-040 and ADR-074 wording and a stable JFR frame list

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Part A is drafts
only, outside the repository; part B is a small committed fix.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w214` (new; from the shared head named in your prompt, which contains W210 at `cf90b64ef`) |
| Branch | `codex/v1.8.0-w214` (local commits only, part B; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W210-review/REVIEW.md` (SHA-256 `839aa4b7…6e411babdc7`): "Risk wording for Root" and finding W210-R2 |
| Inputs | `docs/work/v1.8.0-root-queue/records/W210.md`; `RocketTransactionAdmission` and `RocketTransactionSavedData` in `rocket/persistence`; R-040 in `docs/11-RISK-REGISTER.md`; `docs/decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md` items 2 and 3 |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W204-review/input/W204-drafts.md` and its audit script |
| Output | part A: `D:/GitHub/ARCE-Task-Evidence/root-queue/W214/W214-drafts.md`, an audit script and its JSON result; part B: commits on the branch |

## Part A: drafts (change no tracked file for this part)

R-040 is one Markdown table row, in Chinese, with 8 pipes. Write exact `Target` / `Replace exactly` / `With`
replacements, checking each claim against W210's code and record:

1. **R-040:** qualify "服务器线程只捕获和发布", "服务器线程只取不可变捕获……不构建 NBT" and "编码……都在 IO 线程完成" as
   statements about the checked file writer's capture and publication, not every journal operation. Add W210's
   RAM-only size table: an exact size per immutable resident, used only for the identical entry object, refreshed on
   load, write and remove, never an acknowledgement; and its synchronous costs on the owner thread (a first write or a
   new snapshot encodes one entry, a metadata update encodes metadata, a load sizes every resident), with W210's
   measured figures, including the small-root change. Mark R-040's statement that completion latency near the bound
   has not been measured as superseded in part by W208's and W210's development-host observations, keeping every
   unresolved case (interruption, traffic, expiry, storage faults, the owner's approval) open.
2. **ADR-074:** a dated implementation note under items 2 and 3 saying how "The server thread captures and publishes
   only" and "The IO thread does the rest" read after W210, without changing the accepted decision; "A capture builds
   no NBT" stays true.

R-035 needs no change (the review says so). Keep the row on one line with the same pipe count, in complete Chinese
sentences, without closing the risk or any Gate. The audit script checks that every anchor is unique in the current
file, that nothing overlaps, and that the row keeps 8 pipes; run it and keep its JSON result.

## Part B: W210-R2

In `scripts/w210_admission_summary.py`, select the JFR top frames with a stable tie-break (count descending, then
frame name) and add an equal-count cutoff test that fails first. Leave the retained W210 evidence unchanged; if you
regenerate a summary, write it as a new file beside the old one and say so in a short note appended to
`records/W210.md`.

## Verification

Part A: the audit script. Part B: the new test failing first, then `python -B -m unittest discover -s tests -p
'test_w210*.py'`, and `python -B scripts/validate_repository.py --require-approved-identity`. No Gradle, GameTest or
native run.

## Not in this task

Any production change, other risks, ADR acceptance or rewording of the decision, status files and the ledger.
