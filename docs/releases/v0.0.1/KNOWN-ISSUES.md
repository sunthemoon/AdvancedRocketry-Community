# KNOWN-ISSUES — v0.0.1

## Remaining gate work

- The governance baseline is not committed or pushed; the remote `main` branch still contains only the initial `LICENSE` commit.
- The classic protection rule for `main` is configured but marked `Not enforced` while the repository remains private under a personal account.
- A required repository-workflow status check cannot be selected until the workflow has run remotely and GitHub has registered its check name.
- Required GitHub screenshots and the final signed-out review have not been captured.

## Expected limitations

- There is no Forge/Gradle project, Java source, game asset, or playable JAR in `v0.0.1`.
- Dependabot version updates remain disabled until real dependency manifests exist and a reviewed `.github/dependabot.yml` can be added.
- The upstream outreach document is a draft and has not been sent.
