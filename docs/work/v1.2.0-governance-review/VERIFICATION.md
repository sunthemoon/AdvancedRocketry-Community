# v1.2.0 development-order proposal verification

## Scope

This bundle verifies that proposed ADR-017 is internally consistent, points to an
existing immutable ancestor and preserves the distinction between implementation order
and release approval. It does not accept the ADR or authorize production work.

```yaml
branch: docs/v1.2.0-machine-audit
proposal_commit_base: 112591404917df5e8c02e7ea59af5ce95c8fec05
decision: ADR-017
decision_status: PROPOSED
production_implementation_allowed: false
verified_at: 2026-09-06
```

## Results

| Check | Result | Evidence |
|---|---|---|
| Proposed baseline object exists and is an ancestor of the working HEAD | PASS | `governance-validation.txt` |
| v1.1.0 implementation, evidence-fix and v1.1.1 maintenance commits exist and are ancestors | PASS; 4 referenced commits total | `governance-validation.txt` |
| Proposal status, requested decider, expiry and exact baseline are present | PASS | `governance-validation.txt` |
| Non-waiver, no-v1.3 and explicit acceptance-record clauses are present | PASS; 9 required governance tokens | `governance-validation.txt` |
| ADR-016, version plan, implementation log and document index cross-reference ADR-017 | PASS; 4 documents | `governance-validation.txt` |
| Runtime write scope and credential scan | PASS; 0 runtime paths, 0 findings | `governance-validation.txt` |
| `python scripts/validate_v1plus_planning.py` | PASS | `v1plus-validation.txt` |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; final output after this page was added | `repository-validation.txt` |
| `git diff --check` and staged-scope review | PASS | `final-static.txt` |

## Decision still required

The repository maintainer must explicitly accept or reject ADR-017. If accepted, the
record must include maintainer identity, date and the exact accepted baseline. ADR-016
must independently be accepted before any of its stable IDs or schemas are implemented.

Until then, the v1.2.0 version plan continues to allow audit, test design and data
samples only. No passing check in this bundle changes that rule.

## Evidence files

- `governance-validation.txt`
- `v1plus-validation.txt`
- `repository-validation.txt`
- `final-static.txt`
- `SHA256SUMS.txt` (covers every file above plus this page)
