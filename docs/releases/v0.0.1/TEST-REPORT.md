# TEST-REPORT — v0.0.1 Repository Governance

```yaml
test_date: 2026-08-26
version: v0.0.1
commit: 11baf8be6df42006d0499e13778553991980efcc
environment: Windows / Python 3.12.10 / Git
scope: repository documentation, identity, license, links, templates, workflow, and forbidden-content checks
remote_ci: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32954727518
```

## Commands

```text
python scripts/validate_repository.py --package-root ../AdvancedRocketryCommunity/AdvancedRocketry-1.20.1-Community-Porting-Docs
python -m unittest discover -s tests -v
python scripts/validate_repository.py --require-approved-identity
git diff --check
$textFiles = Get-ChildItem -Recurse -File -Include *.md,*.py,*.yml,*.yaml; $textFiles += Get-Item .gitattributes,.gitignore; $whitespaceHits = $textFiles | Select-String -Pattern '[\t ]+$'; if ($whitespaceHits) { $whitespaceHits; exit 1 }
```

## Results

| Check | Result | Detail |
|---|---|---|
| Supplied package SHA-256 | PASS | 71 listed files matched |
| Required governance files | PASS | Root files, GitHub templates, status, evidence, and 12 version plans present |
| Public non-affiliation statements | PASS | README, NOTICE, branding, and issue intake checked |
| LICENSE | PASS | Original 2017 notice and community attribution present |
| Exact upstream commit | PASS | `c5cd5af62fc07cd4e0d24f06a16033f181c47c04` |
| Markdown relative links | PASS | 28 checked, 0 broken |
| Forbidden v0.0.1 content | PASS | No Java/Forge source, JAR, class, or game assets |
| Case-insensitive paths | PASS | No collisions |
| Issue templates | PASS | Required dependency-free structure present |
| GitHub Actions workflow | PASS | Strict repository validator invoked |
| Remote GitHub Actions | PASS | `validate-repository-docs` passed in 13 seconds with no annotations |
| Validator unit tests | PASS | 4 tests passed; identity current-value parsing and Markdown target normalization covered |
| Non-strict initialization validation | PASS | 11 passed, 0 warnings, 0 failed, including package checksums |
| Strict G0 validation | PASS | 10 passed, 0 warnings, 0 failed |
| Whitespace validation | PASS | `git diff --check` returned exit code 0 |
| Full text-worktree whitespace scan | PASS | No trailing whitespace in Markdown, Python, YAML, `.gitattributes`, or `.gitignore` |

## Interpretation

The repository structure and maintainer-approved identity pass locally and in GitHub Actions. G9 is complete; saved GitHub visual evidence and the final G8 human review remain outstanding.
