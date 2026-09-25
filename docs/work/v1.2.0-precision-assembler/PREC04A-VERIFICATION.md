# V120-PREC-04A — saved-port recovery

```yaml
status: verified_scoped_recovery
date: 2026-09-25
tested_implementation_commit: 26be0299508b629c8d7dd49bb76927d7a1e2c6c8
artifact_sha256: 4a7e11c8b2bb55cc3a0c754fe8c65b579cf747edd33e1d1c959bea758b2b91c0
build: 1.20.1-1.1.1-dev
environment: Windows 11, Java 17.0.7, Forge 47.4.10
```

## Scope and behavior

Precision Assembler no longer retries final settlement from saved full progress
without a journal. It enters `RECOVERY_REQUIRED` before checking physical inputs;
this also catches an input port saved after consumption while another port was
saved before output production.

When a valid journal survives, recovery accepts each Item port only if its
physical stack matches that journal's complete before or after stack. Resource
keys and capacities, controller revision, journal phase and the applied marker
are checked before mutation. A mixed set is rolled forward to the single after
snapshot, then the existing transaction executor finalizes the journal. A
foreign stack or stale `PREPARED` revision retains the journal and leaves all
resources untouched. No persisted root, schema, public ID or network packet
changed.

## Bounded verification

| Check | Result |
|---|---|
| Pre-fix `runGameTestServer` | Reproduced 2 failures among 93: no-journal full progress and mixed `APPLIED` journal |
| First recovery revision | Stale `PREPARED` revision regression failed; phase/revision checks were tightened, not waived |
| `./gradlew clean build --offline --no-daemon` | PASS; 587 JUnit tests in 120 suites, 0 failures/errors/skips |
| `./gradlew runData --offline --no-daemon` | PASS; 61 generated files, written 0 |
| Final `./gradlew runGameTestServer --offline --no-daemon` | PASS; 94/94 Required, including four new recovery cases and the stale-revision regression |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine blocks and associated resources |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans and 33-input inventory |
| `python scripts/validate_repository.py --require-approved-identity` | PASS; 45 checks, 889 links, 0 warnings/failures |
| `git diff --check`; `git diff --exit-code -- src/generated` | PASS |

The final ignored GameTest console log is
`build/prec04a-final-gametest.log` (SHA-256
`0b21af42c01b7b4e235db31d3ffe267800e587e40d78551864f8091b9943e73b`).
The two pre-fix failures remain in ignored
`build/prec04a-before.log` (SHA-256
`14aeb32bfb60f0e852654e2dadfeea0c3a6d9fd765d57dc98976a1a77a25e2a8`).

The packaged Forge server passed first start and same-world restart. On the
same world, a cross-chunk Precision structure retained paused progress `2/20`,
`1520 FE` and revision `4` after `save-all flush`, forced termination and
restart. Resuming produced one advanced circuit and two redstone torches,
`800 FE`, revision `5` and one applied transaction UUID; these values remained
unchanged after a final restart. The
[baseline summary](packaged-restart/prec04a/baseline/summary.json),
[Precision summary](packaged-restart/prec04a/precision/summary.json) and
[evidence checksums](packaged-restart/prec04a/SHA256SUMS) bind the short run to
the tested commit and artifact. Full server logs remain in the ignored local
session recorded by the summaries.

## Remaining boundary

This run does not show that a controller journal is durable before a different
chunk saves its changed ports. `BlockEntity.setChanged()` does not establish
that order. Therefore `V120-PREC-04B` and the parent `V120-PREC-04` remain open;
the scoped recovery result is not arbitrary-crash exactly-once proof. Rolling
Machine and Electrolyzer migration aggregation, JEI-present visual evidence,
full G0-G9 and the ADR-018-deferred long-duration/full-content matrix are also
not discharged by this check.
