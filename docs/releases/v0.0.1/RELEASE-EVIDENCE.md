# RELEASE-EVIDENCE — v0.0.1

## Identity

```yaml
version: v0.0.1
build: NOT_APPLICABLE
commit: ca4d2a89219cc09e8ac4f4146f875ce2a3fbf505
tag: ""
pull_request: https://github.com/sunthemoon/AdvancedRocketry-Community/pull/1
workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32955717987
evidence_pull_request: https://github.com/sunthemoon/AdvancedRocketry-Community/pull/2
evidence_workflow_run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32958108278
minecraft: 1.20.1
forge_baseline: 47.4.10
forge_compat_lane: 47.4.23
java: 17
built_at: NOT_APPLICABLE
built_by: NOT_APPLICABLE
jar_sha256: NOT_APPLICABLE
```

## Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 License/Provenance | PASS | Maintainer-approved identity, license, and exact upstream commit checks pass |
| G1 Build | NOT_APPLICABLE | No Forge code in v0.0.1 |
| G2 Data/Assets | NOT_APPLICABLE | No game data or assets in v0.0.1 |
| G3 Automated Behavior | NOT_APPLICABLE | No game behavior in v0.0.1 |
| G4 Dedicated/Sides | NOT_APPLICABLE | No executable mod in v0.0.1 |
| G5 Persistence/Recovery | NOT_APPLICABLE | No persistent data in v0.0.1 |
| G6 Security/Authority | NOT_APPLICABLE | No network or game authority code in v0.0.1 |
| G7 Performance | NOT_APPLICABLE | No runtime code in v0.0.1 |
| G8 Manual Flow | PASS | Maintainer accepted seven indexed authenticated screenshots and anonymous `404` privacy checks under ADR-004 |
| G9 Docs/Release | PASS | Local strict validation and remote governance workflow pass; PR and run evidence are recorded |

## Commands actually run

```text
git clone https://github.com/sunthemoon/AdvancedRocketry-Community.git AdvancedRocketry-Community
git switch -c docs/v0.0.1-governance
robocopy AdvancedRocketryCommunity\AdvancedRocketry-1.20.1-Community-Porting-Docs AdvancedRocketry-Community /E /COPY:DAT /DCOPY:DAT /R:1 /W:1
git ls-remote https://github.com/Advanced-Rocketry/AdvancedRocketry.git refs/heads/1.12
git remote add upstream-ar https://github.com/Advanced-Rocketry/AdvancedRocketry.git
git fetch --depth=1 upstream-ar 1.12
git remote set-url --push upstream-ar DISABLED
git rev-parse upstream-ar/1.12
git show upstream-ar/1.12:LICENSE
git fetch --prune origin
git push --dry-run origin HEAD:refs/heads/docs/v0.0.1-governance
git commit -m "docs(repo): initialize v0.0.1 governance baseline"
git push --set-upstream origin docs/v0.0.1-governance
git commit -m "ci(repo): update governance actions runtime"
git push
python scripts/validate_repository.py --package-root ../AdvancedRocketryCommunity/AdvancedRocketry-1.20.1-Community-Porting-Docs
python -m unittest discover -s tests -v
python scripts/validate_repository.py --require-approved-identity
curl.exe -sS https://api.github.com/repos/sunthemoon/AdvancedRocketry-Community
git diff --check
$textFiles = Get-ChildItem -Recurse -File -Include *.md,*.py,*.yml,*.yaml; $textFiles += Get-Item .gitattributes,.gitignore; $whitespaceHits = $textFiles | Select-String -Pattern '[\t ]+$'; if ($whitespaceHits) { $whitespaceHits; exit 1 }
```

## Automated validation

See `TEST-REPORT.md` for the focused report.

Validator unit tests: 4 run, 4 passed, 0 failed.

Non-strict initialization check:

```text
[PASS] Required governance files and 12 version plans exist
[PASS] Project identity is APPROVED and expected values are present
[PASS] README, NOTICE, branding, and issue intake state unofficial status
[PASS] LICENSE preserves the original notice and community attribution
[PASS] Exact upstream commit is recorded: c5cd5af62fc07cd4e0d24f06a16033f181c47c04
[PASS] Markdown relative links resolve (46 checked)
[PASS] No forbidden source tree, binary, or unaudited v0.0.1 assets found (7 evidence screenshots verified)
[PASS] No case-insensitive path collisions found
[PASS] Issue template files have the required dependency-free structure
[PASS] Repository governance workflow invokes the strict validator
[PASS] Planning package checksums match (71 files checked)
Summary: 11 passed, 0 warnings, 0 failed
```

Strict Gate check:

```text
[PASS] Required governance files and 12 version plans exist
[PASS] Project identity is APPROVED and expected values are present
[PASS] README, NOTICE, branding, and issue intake state unofficial status
[PASS] LICENSE preserves the original notice and community attribution
[PASS] Exact upstream commit is recorded: c5cd5af62fc07cd4e0d24f06a16033f181c47c04
[PASS] Markdown relative links resolve (46 checked)
[PASS] No forbidden source tree, binary, or unaudited v0.0.1 assets found (7 evidence screenshots verified)
[PASS] No case-insensitive path collisions found
[PASS] Issue template files have the required dependency-free structure
[PASS] Repository governance workflow invokes the strict validator
Summary: 10 passed, 0 warnings, 0 failed
```

The strict identity Gate now passes. `git diff --check` completed successfully, and the full text-worktree scan found no trailing whitespace in the initialization files.

Remote GitHub Actions validation:

```yaml
workflow: Repository governance
run: https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32955717987
validated_commit: ca4d2a89219cc09e8ac4f4146f875ce2a3fbf505
job: validate-repository-docs
result: PASS
duration_seconds: 10
annotations: 0
```

Authenticated GitHub inspection confirmed that the current account can administer the private repository. The About description and nine topics were updated; Dependency Graph, Dependabot alerts, security updates, and grouped security updates were enabled. A classic `main` protection rule was created with pull-request, conversation-resolution, no-bypass, no-force-push, and no-deletion settings. The successful `validate-repository-docs` check is required, and branches must be up to date before merging. GitHub still marks the rule `Not enforced` on this private personal-account repository.

## Provenance

```yaml
new_imported_code_or_assets: 0
upstream_repository: https://github.com/Advanced-Rocketry/AdvancedRocketry
upstream_branch: 1.12
upstream_commit: c5cd5af62fc07cd4e0d24f06a16033f181c47c04
upstream_license: MIT
upstream_license_notice: Copyright (c) 2017
unresolved: []
```

## Save, network, security, and performance

Not applicable. This version contains governance and documentation only.

## Known issues

See `KNOWN-ISSUES.md`.

## Manual tests

See `MANUAL-TEST.md`, [`evidence/README.md`](evidence/README.md), and [ADR-004](../../decisions/ADR-004-PRIVATE-REPOSITORY-G8-ACCEPTANCE.md). Authenticated settings, homepage, license, PR, Issues, Security, and workflow evidence are archived. The maintainer accepted this evidence plus the anonymous `404` privacy checks for v0.0.1 while keeping the repository private. A new signed-out review is mandatory before any future public-visibility change or public release.

## Final recommendation

```yaml
recommended_status: PASSED
remaining_items: []
accepted_exception: docs/decisions/ADR-004-PRIVATE-REPOSITORY-G8-ACCEPTANCE.md
reviewed_by: "sunthemoon"
reviewed_at: "2026-08-26"
```
