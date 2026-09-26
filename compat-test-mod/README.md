# Independent rocket adapter compatibility fixture

This standalone ForgeGradle project builds the existing
[`src/adapterTest`](../src/adapterTest/) fixture into a separate, reobfuscated
mod JAR. It consumes the published ARCE **API classifier**, not the host project,
main JAR or compiled output. Its platform is Java 17, Minecraft 1.20.1 and
Forge 47.4.10; rocket registration requires API 1.1.

## Build

First publish the host artifacts to a local Maven repository from the repository
root:

```powershell
.\gradlew.bat publishMavenJavaPublicationToLocalProjectRepositoryRepository --offline --no-daemon --no-build-cache
```

Then build this independent project, still from the repository root:

```powershell
.\gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache
```

Both commands require an installed Java 17 JDK and previously cached Gradle,
ForgeGradle 6.0.54, Forge and platform dependencies. They do not install missing
dependencies in offline mode. The shared wrapper launches this project's own
settings/build; it does not include or build the host project.

To consume a publication from another checkout, pass an absolute local Maven
directory:

```powershell
.\gradlew.bat -p compat-test-mod build "-ParceRepository=D:/GitHub/AdvancedRocketry-Community/build/repo" --offline --no-daemon --no-build-cache
```

The default dependency is
`io.github.sunthemoon.advancedrocketrycommunity:advancedrocketry-community:1.20.1-1.3.0-dev:api`.
`-ParceVersion=<published-version>` selects another publication while retaining
the API classifier and platform boundary. The repository property is a local
directory, not a remote repository URL.

## Inspect the boundary

`build` runs two finite checks, also available individually:

- `verifyApiClasspath` records requested/resolved coordinates, raw publication
  and remapped API hashes, the complete compiler classpath, and fixture source
  hashes. It requires the six exported API 1.1 types and rejects host internals,
  project output directories or additional host artifacts.
- `verifyConsumerBoundary` compiles an intentional internal import against that
  same classpath, requires the missing internal-package diagnostic, and checks
  the final reobfuscated fixture JAR for bundled host/API/platform classes. It
  reads the published normal host JAR to confirm the internal class exists,
  without ever adding that JAR to the compiler classpath.

Reports are written to `compat-test-mod/build/reports/consumer/`. The resulting
mod is `compat-test-mod/build/libs/arce-adapter-compat-test-1.0.0.jar`.

## Runtime limits

Use the fixture JAR only in disposable development/test installations with the
matching normal host mod and Forge. Never install the API classifier as a mod,
and do not ship this fixture in a gameplay modpack. It has no ordinary gameplay
UI or client assets. The build and boundary checks above do not launch Minecraft.

Successful compilation/reobfuscation is not startup, gameplay, restart,
uninstall/reinstall or cross-dimension evidence. Those require separately
recorded runtime tests with the exact fixture and host artifact hashes.
