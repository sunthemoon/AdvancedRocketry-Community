# V130-ROCKET-03A independent consumer build

- Role: delegated implementation, separate worktree.
- Branch: `codex/v1.3.0-consumer-build`.
- Worktree: `D:/GitHub/arce-v130-consumer-build`.
- Base: `e0ef86d2b9983321d04bfb50285535d4acf856eb`.
- Contracts: accepted ADR-020/021/022; API 1.1.
- Write scope: `compat-test-mod/**`, `docs/work/v1.3.0-consumer-build/**` only.
- Root owns the production build, `src/adapterTest`, central documentation and
  runtime/lifecycle verification.

## Outcome and non-goals

Build a separate ForgeGradle project from the shared adapter fixture sources,
using only published/remapped API classifier and platform dependencies. Emit an
independently reobfuscated fixture JAR, classpath/provenance records and a
negative internal-import check. Do not bundle host/API classes.

No Minecraft process, network installation, root Gradle execution, production
change, new public contract or release approval is included. Runtime persistence,
uninstall/reinstall and cross-dimension evidence remains separately scoped.

## Validation

After the integrator confirms the local Maven publication is available, run the
consumer's offline `clean build`, inspect actual audit reports and artifact
hashes, then record results in HANDOFF. Static checks are not a build PASS.

Only the existing fixture sources are reused; no upstream implementation or
assets are copied. The new standalone build/check code is original. Shared
LICENSE and NOTICE files remain included in the fixture JAR.
