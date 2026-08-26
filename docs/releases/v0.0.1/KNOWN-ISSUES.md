# KNOWN-ISSUES — v0.0.1

## Remaining gate work

- Pull request [#1](https://github.com/sunthemoon/AdvancedRocketry-Community/pull/1) is merged as commit `ca4d2a89219cc09e8ac4f4146f875ce2a3fbf505`; its post-merge `main` workflow passed.
- The classic protection rule for `main` is configured but marked `Not enforced` while the repository remains private under a personal account.
- Authenticated GitHub screenshots are archived under [`evidence/`](evidence/README.md).
- Anonymous requests correctly return `404` while the repository is private, so the signed-out content review cannot be completed until the maintainer explicitly authorizes public visibility.

## Expected limitations

- There is no Forge/Gradle project, Java source, game asset, or playable JAR in `v0.0.1`.
- Dependabot version updates remain disabled until real dependency manifests exist and a reviewed `.github/dependabot.yml` can be added.
- The upstream outreach document is a draft and has not been sent.
