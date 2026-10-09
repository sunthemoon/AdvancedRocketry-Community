# C19 whole unittest lifecycle observation71

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Status: sealed partial observation; whole execution TIMEOUT.

Root owns this read-only observation. Fixed checkout is
D:/GitHub/arce-v180-bootstrap-fixture-20261010-49 at clean commit
8062781ca0cfc3157212f6f313c509cd0e522c7c. Discover the complete authored tests
directory with the default test*.py pattern and unchanged order. A fresh
unittest.TextTestResult subclass may record startTest and stopTest monotonic
timestamps, without replacing authored tests, assertions, fixtures, subprocesses,
Git settings, mocks or validation phases. Record the discovered IDs, discovery
interval, completed case intervals and gaps outside case intervals. Nested work
inside one case is inclusive, not a separate additive timing attribution.

Write scope is this task and Root's new external evidence leaf
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-whole-lifecycle-root-20261010-71.
TEMP and TMP must both be the fresh absolute D: sibling
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-whole-lifecycle-runtime-20261010-71.
No source writes, old evidence/helper execution, inherited runtime cleanup,
descendant termination, build/native/client runs, integration or Gate decision.

Run the observation once using Python3.13.15 with -X utf8 -B, retaining the
original 180-second execution ceiling. Record the original Popen handle and
wait result, PID, argv, UTC, both raw streams and before/after finite bindings
of source HEAD/tree/status/index, all scripts/tests Python inputs, own helpers,
task and Python bytes. Check C: and D: each have at least10 GiB free first.
Read/file cap is1 MiB; combined child streams256 KiB; timing rows4096 and1 MiB;
evidence leaf4 MiB. A timeout remains failure. A separately measured maximum
10-second post-timeout owned wait and pipe drain may observe termination, not
qualify execution. Terminate only the original child if needed. Retain nonempty
own runtime residues; remove only a fresh empty own runtime nonrecursively.

Static-check helper syntax and fixed output references before launching. Emit
bounded flushed timing rows so a timeout preserves completed cases and the
last started case. Report observed class/module costs and limitations without
attributing a historical timeout to this new observation. This instrumented
run is not the literal original CLI qualification, a lifetime/descendant proof,
resource acceptance or an approval. Seal the exact inventory without reruns.
