# v0.0.1 GitHub Visual Evidence

```yaml
captured_at: 2026-08-26
repository: https://github.com/sunthemoon/AdvancedRocketry-Community
source_branch: main
source_commit: ca4d2a89219cc09e8ac4f4146f875ce2a3fbf505
visibility_at_capture: private
session: authenticated repository owner
image_format: JPEG
```

These images record the GitHub state after pull request
[#1](https://github.com/sunthemoon/AdvancedRocketry-Community/pull/1) was
merged. The maintainer accepted them, together with the anonymous `404`
privacy checks, as sufficient Gate G8 evidence for private-repository v0.0.1
under [ADR-004](../../../decisions/ADR-004-PRIVATE-REPOSITORY-G8-ACCEPTANCE.md).

| Evidence | What it demonstrates | Resolution | SHA-256 |
|---|---|---:|---|
| [Repository homepage](github-home-authenticated.jpg) | Private status, `main`, About text/topics, README first screen, MIT/Code of Conduct/Contributing/Security links | 1889x4333 | `fbe8cc001f1c30ac2970b6fb5ed578250d8b99d85c4ded6e1cc98535956ac725` |
| [GitHub license detection](github-license-detection-authenticated.jpg) | GitHub identifies the root license as MIT and preserves the 2017 notice | 1889x962 | `5d2b26d98effce697bdf20b1b87977d7296368ebe049a2882cd84371233dc527` |
| [Pull request checks](github-pr-1-checks-authenticated.jpg) | Pull request #1 is merged and `validate-repository-docs` succeeded | 1889x1035 | `2d0228948354dde25a6f4ae25cdb2ed96ac38a30253eebb427c6f19c2781687f` |
| [Branch protection overview](github-branch-protection-authenticated.jpg) | A `main` rule exists and GitHub reports the private-account enforcement limitation | 1889x988 | `003247c85e1159aa196e92d4bdde11c5f905480e7d140da85f18f199955be735` |
| [Branch protection details](github-branch-protection-details-authenticated.jpg) | Pull requests, current-branch checks, required CI, conversation resolution, no bypass, no force push, and no deletion are configured | 1889x2167 | `48a1f2d7c9f6f956a2273d792ca50335f963127f77fb6e544501f65c6bde5ebc` |
| [Issues](github-issues-authenticated.jpg) | Issues are enabled and the new-issue entry point is available | 1904x956 | `aee8eab3ddf202743a0564d9289d2f4ff0f5938949aa5fd8e5f390c168767420` |
| [Security](github-security-authenticated.jpg) | The security policy and Dependabot alerts are enabled | 1904x862 | `74111e31656263ea8ef31c187d9bcaedeaf60bf7a5eb8c86c64db0edec62c9c2` |

## Anonymous access state

Unauthenticated requests made on 2026-08-26 returned `404` for both the
repository page and REST repository endpoint because the repository remained
private:

```text
github_page_status=404
github_api_status=404
```

This is a successful privacy check, but it does not prove that repository
content and community links render correctly for signed-out visitors. The
maintainer accepted that limitation for private v0.0.1. ADR-004 requires a new
signed-out review before any future public-visibility change or public release.
