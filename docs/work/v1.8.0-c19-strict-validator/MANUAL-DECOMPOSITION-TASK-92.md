# C19 manual scenario decomposition92

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Status: frozen for implementation.

## Ownership and source scope

Root is sole source implementer and Main integrator. Base is committed
f2587b54b753d77bb985e652612003eee54cc769. Create fresh branch
fix/v1.8.0-manual-scenario-decomposition in
D:/GitHub/arce-v180-manual-decomposition-20261010-92. Source write scope:
tests/test_collect_v002_manual_evidence.py, new
tests/test_manual_evidence_organization.py, new
tests/fixtures/manual_evidence_case_ids.txt and these new files under
tests/manual_evidence_cases/: __init__.py, png.py, session_fixture.py,
fixture_operations.py, session.py, profiles.py, source_logs.py, payload_bounds.py,
server_audits.py, mismatch_receipts.py, readiness.py, identity_privacy.py,
mismatch_runtime.py, publication.py and archive_identity.py. No other source write.
Root separately owns task/review/checkpoint/status/log and ADR071 annotations.
No ADR acceptance, approved size waiver or Gate change is assigned.

## Observable behavior and decomposition

Preserve all original 137 manual scenarios and the CLI method, exact method and
helper bodies/assertions/decorators, complete unittest selections/IDs/order and
per-case physical repository/artifact isolation. Keep ManualEvidenceTests and
CollectorCliTests as the only original runnable classes in the original module.
Keep CLI source-location semantics there. Scenario mixins must not inherit
TestCase, define lifecycle overrides or expose duplicate independently runnable
cases. Re-export existing png_chunk/make_png names from the original module.
Method defining-module/traceback source locations may change by this relocation;
case class/module identity, named selections and behavior must not change.

Move source-contained related scenarios into the named modules above. Keep
original setUp and artifact_name on the small runnable facade. Move ready_session
alone to session_fixture, remaining non-setUp fixture helpers to fixture_operations,
and unchanged PNG functions to png. Group session/bundle outcomes, profile inventory,
source-log identity, payload bounds, server audits, mismatch receipts, readiness,
privacy/source identity, mismatch runtime, publication and archive identity.
Split distinct concerns into separate mixins where appropriate. Every declared
class must stay below 500 AST lines. Use explicit dependency imports, no wildcard
imports, reverse import from the facade, load_tests hook or altered collector/
validator policy. Keep the existing repository-fixture module byte-exact.

Add three focused organization tests covering frozen original case selections,
non-runnable scenario mixins and fixture-lifecycle composition. Freeze the 138
original class/method IDs as a small text fixture; it is not approval/result cache.
No original test/helper/body omission or asserted outcome weakening. Production,
bootstrap/reader/protocol tests, original fixture tests, budgets and assets stay
unchanged. Adjacent bootstrap organization remains separate under proposed ADR070.

## Actual verification and limits

Root own fresh leaf: D:/GitHub/ARCE-Task-Evidence/v1.8.0/
c19-manual-decomposition-root-20261010-92. Fresh absolute sibling TEMP/TMP prefix:
c19-manual-decomposition-runtime-20261010-92-, one suffix per command. Use pinned
Python 3.13.15 -X utf8 -B; own analyses -I -X utf8 -B. Read/file 1 MiB, combined
streams 256 KiB, leaf 4 MiB; C:/D: >=10 GiB before authored execution. Original
180 seconds per command, full raw streams and original Popen argv/PID/UTC/wait.
Bind HEAD/tree/full status/index/all Python inputs/new ID fixture/task/runner/
executable before and after. Hold source/runner/task identities during execution.

Static comparison proves complete original method/helper source and AST coverage,
IDs/default discovery selections and class sizes. Root runs full original manual138,
unchanged fixture3 and new organization3 modules once each. Fresh independent
actual-diff review runs organization3 and full original manual138 once each,
scheduled without competing authored workloads. Wait for peer final bindings/read
cutoff, then explicit owned staging/stat/check, commit and non-force push. Record
actual committed organization3 separately. Run literal complete discovery once at
that committed candidate under unchanged180 seconds; failure stays failure.

Only authored cleanup/nonrecursive removal of fresh empty own runtimes. Retain
nonempty runtime. Distinct maximum ten-second original-owned wait/drain may observe
deadline termination, not qualify it. No descendants/unowned termination, target
retry, old helper execution or sealed evidence modification. No Main integration,
Gradle/native/client/resource execution, unknown acquisition, inherited cleanup,
upstream import, sleep/writer activation, delivery ledger or release approval.
