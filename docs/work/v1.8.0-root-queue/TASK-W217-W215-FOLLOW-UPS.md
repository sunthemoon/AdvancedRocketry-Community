# TASK W217: W215's follow-ups, a path-derived class inventory check and R-035 wording

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Part A is a
committed test change; part B is drafts only, outside the repository.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w217` (new; from the shared head named in your prompt, which contains W215 at `a95a8d543`) |
| Branch | `codex/v1.8.0-w217` (local commits only, part A; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W215-review/REVIEW.md` (SHA-256 `8ee70c8c…ce1408b2b40`): finding W215-R1, "Static initializers and startup boundary", "Risk text and integration"; its bytecode probe (`DependencyMutation.java`, `mutation.init.gradle`, `dependency-mutation-test.log`) |
| Inputs | `records/W215.md`, `records/W212.md`; `resourceescrow/EscrowRelaunchClasses.java`, `EscrowRelaunchClassesTest.java`, `src/test/resources/relaunch-first-use-classes.txt`; R-035 in `docs/11-RISK-REGISTER.md` |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W214/W214-drafts.md` and its audit script |
| Output | part A: commits on the branch and a record `docs/work/v1.8.0-root-queue/records/W217.md`; part B: `D:/GitHub/ARCE-Task-Evidence/root-queue/W217/W217-drafts.md`, an audit script and its JSON result |

**Root decision R193.** W215 merged: it initializes W212's 55 measured mod classes at server start. Its review found
that `EscrowRelaunchClassesTest` only pins the list against a second fixed list, so a class newly used by the relaunch
path, or a listed class no longer used, passes unnoticed (the reviewer's probe kept 7 of 7 green).

## Part A: a regression tied to the relaunch path

Add a check derived from the actual relaunch entry points (for example a bytecode walk of the mod classes reachable
from the governed launch, checkpoint and flight-source entry points, bounded to this mod's packages and stopping at
stated boundaries; or a fresh-process relaunch inventory in a unit-level harness) that fails for a newly used mod
class missing from the list and for a listed class that the path no longer uses, and that names the class and asks for
an initializer re-audit. Keep the existing list and resource equality and missing-class checks. Show it failing first
with the reviewer's probe (a new inert class called from `EscrowGovernedLaunch.prepare`) and with a listed class
removed from use, then passing on the current source. The production list and `EscrowRelaunchClasses` change only if
the new check shows the current list is wrong; if it does, record why and re-audit each added class's initializer.

## Part B: R-035 drafts (change no tracked file for this part)

R-035 is one Markdown table row, in Chinese, with 8 pipes. Write exact `Target` / `Replace exactly` / `With`
replacements adding W215 to its warm-up narrative: startup initialization of the audited relaunch classes on
`ServerStartedEvent` (17.6 to 87.3 ms before logins, about 335 KB of Metaspace), that it runs in every world including
those with governed admission disabled, that a missing listed class deliberately aborts server start, the first-use
results (all nine timing-only and seven of nine full-observation cases within 100 ms; new strip 102.789 ms and
generated contained 103.517 ms over), and the part A check. Historical W91, W200 and W202 figures stay historical. Do
not close the risk or any Gate. The audit script checks unique, non-overlapping anchors and the 8-pipe row; run it and
keep its JSON result.

## Verification

Part A: the new test failing first, then `EscrowRelaunchClassesTest` and the `resourceescrow` package, and
`python -B scripts/validate_repository.py --require-approved-identity`. Part B: the audit script. No GameTest or native
run. Root's hosted run on the pushed branch covers the rest.

## Not in this task

Other production changes, the class list beyond what part A proves, budgets, ADRs, status files and the ledger.
