# C19 standard launcher correction41

Date: 2026-10-10. Status: terminal standard subset succeeds; independent44 complete.
Owner: Root. Version:v1.8 under ADR-060.
This is a separately recorded harness correction after terminal failure, not a
silent retry of Task39 or a changed source/test/budget.

## Actual predecessor and correction

Task39's build-01 child27700 exits1 in0.1079785 seconds, emitting only
`The syntax of the command is incorrect.` No Gradle/test XML/JAR is produced;
the empty collection is not a passing test cohort. Ledger, provenance and
whitespace independent commands return0; dependent standard commands are not
executed. All five receipts retain source/helper/input identities and outcomes.

A separate read-only two-command launcher observation uses the same pinned
Python and actual cmd.exe /d /c ver. With the executable spelled
`C:/Windows/System32/cmd.exe`, exit1 reproduces the same syntax message; with
`C:\Windows\System32\cmd.exe`, exit0 prints the installed Windows version.
This observed difference motivates returning to the backslash executable spelling
used in the historical successful standard capture. It is not a production
regression, new Java/Gradle behavior claim or permission to discard the failure.

## Frozen source and output scope

Use the unchanged fixed607 checkout/source from Task39; no HEAD/index/source/
configuration/test edits or Main integration. Recheck all originally assigned
candidate outputs remain absent before launch. Fresh retained evidence leaf:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-object-standard-root-20261010-41.
Fresh runtime root:
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-object-standard-runtime-20261010-41.
The initial helper sources from Root39 are copied exactly and hash-compared;
an explicit new shim only assigns this fresh scratch root. The new driver changes
only the cmd.exe executable spelling and attempt namespace, retaining the same
tasks, flags, JDK, ceilings, capture policy and result collection semantics.
Every copied/shim/driver helper is bound in actual command receipts.

Task39's retained helper/receipt/streams and original scratch are not edited or
cleaned. New standard results cannot relabel its build failure as pass. All
standard timings remain2400/1200/1200/1800 seconds as applicable; full Python's
original180-second failures remain open and are not rerun by this task.
Disks >=10 GiB, evidence <=100 MiB, files <=50 MiB, polled streams <=4 MiB.
No atomic quota, environment-completeness, hostile filesystem/ABA or descendant-
absence claim follows. One owned termination only if needed; preserve errors.
Cleanup requires separately recorded ordinary ancestors/ownership/terminal/log
admission, with no peer/old target takeover. Independent evidence review and
current-version Required Gates remain required; no acceptance/ledger/ADR change.
