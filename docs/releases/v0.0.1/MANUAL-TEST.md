# MANUAL-TEST — v0.0.1 GitHub Identity and Governance

```yaml
status: PASS
test_date: 2026-08-26
tester: sunthemoon with Codex-assisted evidence capture
build_hash: NOT_APPLICABLE
commit: ca4d2a89219cc09e8ac4f4146f875ce2a3fbf505
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

PASS:

- The repository is private and the current signed-in account has owner/admin access.
- `main` is the default branch and contains the merged governance baseline at `ca4d2a89219cc09e8ac4f4146f875ce2a3fbf505`.
- GitHub recognizes the license as MIT.
- Issues and Pull Requests are enabled; Discussions are disabled as planned for the early milestone.
- The About text is `Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers.`
- Nine topics are present: `advanced-rocketry`, `community-edition`, `forge`, `forge-mod`, `java`, `minecraft`, `minecraft-1-20-1`, `minecraft-mod`, and `space`.
- Dependency Graph, Dependabot alerts, Dependabot security updates, and grouped security updates are enabled.
- A classic protection rule exists for `main`; it requires pull requests and conversation resolution, disallows administrator bypass, and leaves force pushes and deletion disabled.
- `validate-repository-docs` is a required status check, and branches must be up to date before merging.
- GitHub marks the classic rule `Not enforced` while the repository is private under a personal account. This is an account-plan limitation, not proof of active protection.
- Pull request [#1](https://github.com/sunthemoon/AdvancedRocketry-Community/pull/1) is merged with all checks passing.
- Post-merge GitHub Actions run [32955717987](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/32955717987) passed the repository-governance workflow in 10 seconds.
- Authenticated screenshots were captured, visually reviewed, and indexed in [`evidence/README.md`](evidence/README.md).

Unauthenticated GitHub page and REST requests both return `404 Not Found`, which is consistent with the repository remaining private. This does not demonstrate public rendering; the maintainer accepted that limitation for v0.0.1 under ADR-004.

## MANUAL-V001-003 — Private-repository evidence acceptance

**Steps completed**

1. Captured the README first screen, About panel, license detection, PR check, Issues, Security, and branch-protection settings.
2. Confirmed the repository UI exposes MIT License, Security, Contributing, and Code of Conduct links.
3. Confirmed unauthenticated page and API requests return `404` while visibility is private.

**Maintainer decision**

The maintainer chose to keep the repository private and accepted the seven indexed authenticated screenshots plus the anonymous `404` privacy checks as sufficient v0.0.1 G8 evidence. See [ADR-004](../../decisions/ADR-004-PRIVATE-REPOSITORY-G8-ACCEPTANCE.md).

**Actual**

PASS. The signed-out content review is not claimed as executed and is not a v0.0.1 blocker under the accepted decision.

**Future trigger**

Before any public-visibility change or public release:

1. Capture a genuinely signed-out review of the homepage, license, Issues, Security, Contributing, and Code of Conduct pages.
2. Confirm the `main` protection rule becomes enforceable.
3. Record the result in the version active at that time.

**Required evidence**

- Authenticated evidence index: [`evidence/README.md`](evidence/README.md)
- Privacy evidence: anonymous page and REST requests returned `404`
- Acceptance decision: [ADR-004](../../decisions/ADR-004-PRIVATE-REPOSITORY-G8-ACCEPTANCE.md)
- Final human reviewer: `sunthemoon`, 2026-08-26
