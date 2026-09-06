# v1.0.0 development release handoff verification

2026-09-06. Documentation/evidence slice on `codex/v1.0.0-stable-core`.
The prior goal turn made implementation progress with verified logout cleanup.
This slice creates the missing release-review entry points without changing
runtime code, resources, save/network contracts or accepted v0.9 evidence.

## Delivered scope

- Eleven populated documents in `docs/releases/v1.0.0/`: release identity,
  Gate review, automation, migration, recovery, multiplayer, visuals, manual
  installation/player flow, performance, security and known issues.
- `evidence-index.json` binds ten existing reports to their report hashes and
  exact development artifact identities. Older reports are supporting evidence,
  not tests of the latest JAR. Candidate commit/JAR/tag remain null.
- `checksums.txt` covers all twelve packet payload files using repository-relative
  paths. It is explicitly a draft handoff checksum list, not a distributable.
- README and document index link the packet. Canonical G9 moves from
  NOT_STARTED to IN_PROGRESS, with no Gate approved and overall IN_PROGRESS.
- A scoped verifier explicitly checks untracked packet Markdown, report and
  source hashes, JAR identity, required files and checksum completeness. Seven
  small tests exercise valid non-approval, false candidate claims, changed
  report/source, wrong artifact binding, a removed required file and an
  uncovered extra file.

The packet does not recursively revalidate every historical binary/log archive
or perform an independent review. It preserves links to their canonical
reports and checksum records. Human approval, candidate-specific revalidation
and the requested long-load deferral remain separate from packet integrity.

## Actual verification

| Command/check | Result |
|---|---|
| `python docs/work/v1.0.0-release-packet/verify.py` | Exit 0: ten report bindings, 93 Markdown links, twelve payload checksums, 747 source hashes and unchanged JAR; release_approved=false |
| `python -m unittest discover -s docs/work/v1.0.0-release-packet -p test_verify.py -v` | Exit 0; seven tests in 0.344 s |
| `gradlew.bat clean build --no-daemon` | Exit 0, 17 s; compilation and 406-test results restored from cache |
| `gradlew.bat test runData runGameTestServer --no-daemon` | Exit 0, 1m 51s; Java tests up-to-date, all 51 required GameTests run and pass |
| `python scripts/validate_build_artifact.py build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar --expected-version 1.20.1-1.0.0-dev --content-manifest docs/work/v1.0.0-release-packet/jar-content-manifest.json` | Exit 0; 782 entries, metadata/notices/path/credential scan passes |
| `git diff --check` | Exit 0 |
| `git diff --exit-code -- src/generated` | Exit 0 |
| `git diff --exit-code` | Exit 1: inherited and new uncommitted work; not a clean-candidate pass |
| `python scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 passed, zero pending/warnings/failures |

The rebuild reproduces SHA-256
`569f41ab59d953e3b4fad3c1b00588705b260fc0b73f1deaf829024bc603e381`,
1,278,474 bytes. All 406 cached Java test results in 79 suites have zero
failures/errors/skips; their XML is retained in `java-results/`. Cache reuse
and the modified worktree mean this is not clean-environment candidate R1
evidence. Raw command output and native GameTest bytes are preserved.

## Remaining work and risks

No remote runtime, native client, packaged restart, authentication, compatibility
matrix or soak is launched here. No source fix is inferred from missing evidence.
Native asynchronous passenger recovery, connection-timeout triage, final-candidate
coverage, performance/visual acceptance and independent/human approval remain
open. There is no claim of zero unreviewed defects or all Required Gates passing.
The next implementation/verification work stays within v1.0; release preparation
must not introduce v1.1/v1.2 functionality or extend expired ADR exceptions.
