# CONTRIBUTING.md

## Before contributing

Read, in order:

1. `PROJECT-CONFIG.md`
2. `AGENTS.md`
3. `docs/04-VERSION-ROADMAP.md`
4. the current version document
5. `docs/05-MASTER-TEST-PLAN.md`
6. `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`

## Scope rule

Contributions must fit the current milestone. A technically good implementation may still be rejected if it introduces future-version systems early or recreates unnecessary legacy abstractions.

## Issue first

Open or claim a porting task before substantial work. The issue must state:

- target version;
- player-visible behavior;
- upstream behavior/source references;
- explicit non-goals;
- test plan;
- save/network impact;
- asset/license impact.

## Pull request requirements

Every PR must include:

- a single focused purpose;
- tests appropriate to the change;
- actual commands run and results;
- screenshots/video for visual or player-flow changes;
- provenance records for imported assets/code;
- migration notes for persistent data;
- no unrelated generated or formatting churn.

## Required local checks

```bash
./gradlew clean build
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
```

Additional checks are defined by the target version.

For the documentation-only `v0.0.1` baseline, before the Gradle Wrapper exists, run:

```bash
python scripts/validate_repository.py --require-approved-identity
git diff --check
```

The Gradle checks become mandatory from `v0.0.2` onward.

## Source and asset policy

Do not copy code or assets from another community fork merely because it is available online. Provide source repository, commit, path, license, hash, and transformation record.

## Compatibility reports

A compatibility bug must include a minimal mod list. Reports from large modpacks without reduction may be closed as not actionable.

## Commit messages

Use conventional scopes where practical:

```text
feat(rocket):
fix(atmosphere):
test(celestial):
docs(release):
chore(build):
```

## AI-assisted contributions

AI-generated code is allowed, but the contributor is responsible for:

- validating sources and licenses;
- reviewing generated code;
- running tests;
- explaining architectural choices;
- ensuring no accidental code was copied from an incompatible source;
- not presenting unverified AI output as test evidence.

## Review expectations

Reviewers check behavior, architecture, server authority, save safety, test quality, scope, and provenance. Compilation alone is insufficient.
