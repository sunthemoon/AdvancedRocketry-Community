# Public API: version metadata

Use the API classifier when compiling an integration against supported ARCE
types. The initial API version is **1.0**. It exposes only
`io.github.sunthemoon.advancedrocketrycommunity.api.version`:

| Type | Purpose |
|---|---|
| `ApiVersion` | Immutable positive major and non-negative minor |
| `ApiCompatibility` | Compatible, major mismatch, or minor too old |
| `ApiVersions` | Host version and pure compatibility checks |

Other project packages are implementation details, even when their Java types
are `public`. Container/provider registration and gameplay extension APIs are
not available through this metadata package.

## Compile and run

Build with Java 17:

```text
./gradlew apiJar
```

The resulting `build/libs/*-api.jar` is a **compile-only** dependency. Add that
file to the consumer's Gradle `compileOnly` configuration. The Maven publication
also exposes classifier `api` under the main artifact's group/name/version;
the repository does not imply an already published remote package.

Install the normal ARCE mod JAR in the game at runtime. Do not install the API
classifier as a mod, or shade/bundle its classes into an integration. The normal
mod JAR supplies exactly the same API classes. Integrations still declare their
loader dependency on ARCE; a Java metadata check does not replace Forge's
dependency or Minecraft compatibility checks.

## Check compatibility

```java
ApiVersion required = new ApiVersion(1, 0);
ApiCompatibility compatibility = ApiVersions.check(ApiVersions.current(), required);
if (compatibility != ApiCompatibility.COMPATIBLE) {
    // Disable the integration and report the incompatibility to its user.
}
```

Import these types from the package above. A requirement specifies an **exact
major** and **minimum minor**. A different major returns `MAJOR_MISMATCH` first;
an insufficient minor within the same major returns `MINOR_TOO_OLD`. Invalid
version components throw `IllegalArgumentException`; null check arguments throw
`NullPointerException`.

These methods are JDK-only, thread-safe, side-independent and do not access a
world, network, file or mod loader. They do not validate third-party behavior.
API versions are independent of mod versions, packet protocols and save schemas.
Call `current()` instead of embedding a host-version constant.

## Compatibility policy

Supported API additions increment the minor version. Existing signatures and
documented behavior remain compatible within a major. Removal or incompatible
changes require a new major and a prior publicly released minor with deprecation,
a replacement and migration notes. See [the version policy](decisions/ADR-021-PUBLIC-API-VERSION-POLICY.md).

## Verify an integration boundary

`./gradlew test` checks the API archive allowlist, final runtime/classifier class
identity, version semantics, and isolated consumer compilation. The positive
fixture also runs with only JDK and classifier classes; the negative fixture
must fail specifically because an internal package is unavailable. These are
metadata checks, not a complete third-party Forge compatibility test mod.
