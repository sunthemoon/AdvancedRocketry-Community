# MANUAL-TEST — v0.0.1 GitHub Identity and Governance

```yaml
status: IN_PROGRESS
test_date: 2026-08-26
tester: Codex local initialization
build_hash: NOT_APPLICABLE
commit: 11baf8be6df42006d0499e13778553991980efcc
```

## MANUAL-V001-001 — Local repository first-screen identity

**Preconditions**

- Governance package copied into the repository root.
- No Forge source or playable binary present.

**Steps**

1. Read the first screen of `README.md`.
2. Confirm it labels the project unofficial and unsupported by the original maintainers.
3. Confirm the Minecraft non-affiliation statement is visible.
4. Confirm the status says pre-alpha/planning and no playable release.

**Expected**

All four statements are explicit and mutually consistent.

**Actual**

PASS. The automated validator also enforces the required phrases.

## MANUAL-V001-002 — GitHub authenticated repository and settings review

**Steps completed**

1. Open the private repository in the signed-in Chrome session.
2. Confirm repository administration access and default branch.
3. Inspect the repository homepage, General settings, Rulesets, Branches, and Advanced Security pages.
4. Confirm current license detection and feature state.

**Actual**

PASS WITH FOLLOW-UP EVIDENCE REQUIRED:

- The repository is private and the current signed-in account has owner/admin access.
- `main` is the default branch and still contains only the initial `LICENSE` commit; the governance baseline is isolated on `docs/v0.0.1-governance` in pull request #1.
- GitHub recognizes the license as MIT.
- Issues and Pull Requests are enabled; Discussions are disabled as planned for the early milestone.
- The About text is `Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers.`
- Nine topics are present: `advanced-rocketry`, `community-edition`, `forge`, `forge-mod`, `java`, `minecraft`, `minecraft-1-20-1`, `minecraft-mod`, and `space`.
- Dependency Graph, Dependabot alerts, Dependabot security updates, and grouped security updates are enabled.
- A classic protection rule exists for `main`; it requires pull requests and conversation resolution, disallows administrator bypass, and leaves force pushes and deletion disabled.
- `validate-repository-docs` is a required status check, and branches must be up to date before merging.
- GitHub marks the classic rule `Not enforced` while the repository is private under a personal account. This is an account-plan limitation, not proof of active protection.
- Pull request [#1](https://github.com/sunthemoon/AdvancedRocketry-Community/pull/1) is open with the baseline and CI-runtime commits and no merge to `main`.
- GitHub Actions run [32954727518](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32954727518) passed `validate-repository-docs` in 13 seconds with no annotations.

An unauthenticated GitHub REST request still returns `404 Not Found`, which is consistent with the repository remaining private but does not replace the required final signed-out visual check.

## MANUAL-V001-003 — Remaining public-facing review

**Steps still required**

1. Capture the README first screen, About panel, license detection, PR check, and branch-protection settings.
2. Confirm Issues, Security, Contributing, and Code of Conduct links from the repository UI.
3. Before making the repository public, repeat the anonymous/signed-out review and confirm that `main` protection is enforceable.
4. Complete the human review of pull request #1 before merging.

**Required evidence**

- GitHub homepage and About screenshot
- GitHub license-detection screenshot
- Ruleset/settings screenshots or exported configuration
- Tester name and completion date
