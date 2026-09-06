# v1.2.0 pre-Gate audit verification

## Scope

This bundle verifies only the v1.2.0 machine audit, contract/test design and
non-runtime data samples based on commit
`660cfc95c1dff7901a25ad937cc3c0e3242e51ef`. It does not verify a v1.2.0
runtime implementation and does not approve any G0-G9 release Gate.

```yaml
branch: docs/v1.2.0-machine-audit
base_commit: 660cfc95c1dff7901a25ad937cc3c0e3242e51ef
upstream_commit: c5cd5af62fc07cd4e0d24f06a16033f181c47c04
runtime_files_changed: 0
production_implementation_status: BLOCKED_BY_PREREQUISITE
verified_at: 2026-09-06
```

## Results

| Check | Result | Evidence |
|---|---|---|
| 10 upstream process-machine Java files downloaded from the fixed commit and compared to the local manifest | PASS; 10/10 hashes | `machine-source-audit.json` |
| Seven process recipe types downloaded, hashed and parsed | PASS; 37/37 JSON files | `machine-source-audit.json` |
| Five contract samples plus audit JSON parsed; pattern cell/coordinate/anchor invariants checked | PASS | `validation.txt` |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and 33-input source inventory | `final-repository-validation.txt` |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 passed, 0 pending/warnings/failed; 802 links | `final-repository-validation.txt` |
| `git diff --check` | PASS | `validation.txt` |
| task write-scope audit | PASS; no runtime or unrelated tracked path | `validation.txt` |
| `gradlew test --rerun-tasks --no-daemon` with Java 17.0.7 | PASS in 41s | `unit-tests.txt`, `unit-test-summary.txt` |
| JUnit XML aggregation | PASS; 89 suites, 453 tests, 0 failures/errors/skips | `unit-test-summary.txt` |

## Not run by this planning scope

- `gradlew clean build`, `runData` and `runGameTestServer` were not rerun because no
  Java, resource, Gradle, registry, network or persisted runtime data changed.
- Dedicated restart, forced-stop recovery, 100-machine performance, long soak, real
  GPU and two-client tests require the future implementation/candidate and remain open.
- No JAR or v1.2.0 release evidence directory was created.

These omissions are not Gate passes. Once the prerequisite and contract freeze are
satisfied, the implementation must execute every command and environment in the v1.2.0
version plan against the candidate commit.

## Provenance boundary

The fetched upstream files remained in a temporary audit directory outside the
repository. No upstream source bytes, LibVulpes code, recipe payload or binary asset
was added to runtime or documentation. Only deterministic hashes, counts, arity ranges
and newly authored contract examples are committed.

## Files

- `machine-source-audit.json`: deterministic source/recipe summary and verified hashes.
- `validation.txt`: contract sample, planning, repository and write-scope checks.
- `final-repository-validation.txt`: final post-document link and repository validation.
- `unit-tests.txt`: Gradle/JUnit command output.
- `unit-test-summary.txt`: XML-derived suite and test totals.
- `SHA256SUMS.txt`: byte hashes for this bundle, excluding the manifest itself.
