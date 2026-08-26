# MANUAL-TEST — v0.0.1 GitHub Identity and Governance

```yaml
status: IN_PROGRESS
test_date: 2026-08-26
tester: Codex local initialization
build_hash: NOT_APPLICABLE
commit: WORKTREE
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
- `main` is the default branch; the repository still contains only the initial `LICENSE` commit remotely.
- GitHub recognizes the license as MIT.
- Issues and Pull Requests are enabled; Discussions are disabled as planned for the early milestone.
- The About text is `Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers.`
- Nine topics are present: `advanced-rocketry`, `community-edition`, `forge`, `forge-mod`, `java`, `minecraft`, `minecraft-1-20-1`, `minecraft-mod`, and `space`.
- Dependency Graph, Dependabot alerts, Dependabot security updates, and grouped security updates are enabled.
- A classic protection rule exists for `main`; it requires pull requests and conversation resolution, disallows administrator bypass, and leaves force pushes and deletion disabled.
- GitHub marks the classic rule `Not enforced` while the repository is private under a personal account. This is an account-plan limitation, not proof of active protection.
- `git push --dry-run origin HEAD:refs/heads/docs/v0.0.1-governance` succeeded without creating the remote branch.

An unauthenticated GitHub REST request still returns `404 Not Found`, which is consistent with the repository remaining private but does not replace the required final signed-out visual check.

## MANUAL-V001-003 — Remaining public-facing review

**Steps still required**

1. Commit and push the governance baseline through a pull request.
2. Run the repository workflow remotely, then add its check to branch protection when GitHub exposes the check name.
3. After the governance baseline is visible remotely, capture the README first screen and About panel.
4. Confirm Issues, Security, Contributing, and Code of Conduct links from the repository UI.
5. Before making the repository public, repeat the anonymous/signed-out review and confirm that `main` protection is enforceable.

**Required evidence**

- GitHub homepage and About screenshot
- GitHub license-detection screenshot
- Ruleset/settings screenshots or exported configuration
- Tester name and completion date
