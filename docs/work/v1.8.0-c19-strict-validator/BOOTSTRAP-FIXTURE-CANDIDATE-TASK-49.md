# C19 bootstrap fixture candidate49

Date:2026-10-10. Version:v1.8, ADR-060. Status:implemented; committed module qualified, whole open.
Root implements only tests/test_validate_bootstrap_provenance.py in new worktree
D:/GitHub/arce-v180-bootstrap-fixture-20261010-49, branch
fix/v1.8.0-bootstrap-fixture-isolation, base e28fd8a658679482fb4471265d83a57f4d491c54.
Root exclusively owns central task/checkpoint/status/log records in Main.

Task47's single instrumented two-case observation returns0 with unchanged inputs:
setUp takes3.811/3.777 seconds and68 subprocess starts per case, body0.963/0.239
seconds. This is evidence to consider fixture preparation separately, not a
whole-suite measurement or retrospective proof of the older timeout's sole cause.

Build the existing pristine fixture once per class using its original construction
algorithm, then copy the complete directory including .git for each case.
Each case owns its TEMP, raw materialized bytes, ordinary independent files,
Git index/config/object database, and deep-copied manifest; preserve history,
metadata, modes and untracked review documents. Do not use Git checkout/clone to
rematerialize bytes, alternates/hardlinks, validator-result caches or shared
mutable per-case state. Class cleanup also applies if class construction fails.
Keep every original test method and helper construction body unchanged, apart
from relocating preparation into a named helper. Add direct isolation checks.
No production/budget/assertion/config/source import, build, ledger or Gate change.

Own evidence/runtime leaves under D:/GitHub/ARCE-Task-Evidence/v1.8.0:
c19-bootstrap-fixture-root-20261010-49 and c19-bootstrap-fixture-runtime-20261010-49.
Check source/index/HEAD and disks>=10 GiB. Use pinned Python3.13.15 -X utf8 -B;
run the unchanged full bootstrap module with the original180-second ceiling,
then the original broad Python command if feasible, retaining any actual failures.
No retry, timeout expansion, assertion reduction or old/peer cleanup. Finite capture
must retain original process wait exit, raw streams and source/helper/executable
bindings. Observation1 MiB, combined stream256 KiB, file1 MiB/leaf4 MiB.
Only authored cleanup in owned TEMP. Commit and normally push this source slice
after reviewable verification; independent diff/fixture review remains required
before Main integration. Record actual tested postimage versus committed SHA.

Results are in checkpoint55. Original adapter/preflight and failed d7ae source
are sealed unchanged. Explicit53 successor is committed/pushed8062781ca0cfc3157212f6f313c509cd0e522c7c;
actual committed56 full95 returns0 within180 seconds. Independent54's two added
cases and finite57 evidence audit complete; broad timeout/exit:null/custody,
proposed ADR-070 size disposition and whole qualification stay open. No Main
integration/delivery/ledger or Gate conclusion. Audit57's original six contracts
are retained at matching hashes in Root58 before these status annotations,
as late matching copies, not historical runtime raw capture/ABA proof.
