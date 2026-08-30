# Third-Party Notices

This document identifies third-party build-template and bootstrap components
present in the repository. These components remain under their respective
licenses; the repository's MIT license applies only where the third-party terms
do not apply.

The detailed import hashes and transformations are recorded in the repository
at `docs/provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md`. That provenance
record is not embedded in the distributable JAR.

## Minecraft Forge 1.20.1-47.4.10 MDK

Source:

- Project: MinecraftForge/MinecraftForge
- Repository: <https://github.com/MinecraftForge/MinecraftForge>
- Branch: `1.20.1`
- Source commit inferred for the artifact from the official universal-JAR
  manifest and matching source-tree files (see the provenance record):
  `132704e5f23dbee28d776738eb1c0c42fefc0bf6`
- Artifact: `net.minecraftforge:forge:1.20.1-47.4.10:mdk@zip`
- Artifact URL:
  <https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-mdk.zip>
- Artifact SHA-256:
  `73e0122becd05e39b47eced54e030380d66411850ed86786a2d58ecd886b0451`

License evidence: LGPL 2.1 (`LGPL-2.1-only` in current Forge source
headers). The official artifact POM identifies LGPL 2.1, and the MDK member
`LICENSE.txt` states the Forge repository's license terms and included notices.
Project attribution: Minecraft Forge / Forge Mod Loader, Forge Development LLC
and contributors. The exact copied license controls the detailed authorship and
included third-party notices.

Affected repository paths:

- `.gitattributes` (conservatively retained by the reviewer)
- `.gitignore` (conservatively retained by the reviewer)
- `build.gradle`
- `gradle.properties`
- `settings.gradle`
- `gradle/wrapper/gradle-wrapper.properties`
- `src/main/resources/pack.mcmeta`
- `src/main/resources/META-INF/mods.toml`

Each retained target carries an in-file notice identifying the Forge MDK
license, the community project as modifier, and its modification dates. The
comment-capable formats use leading comments. `pack.mcmeta` instead uses the
namespaced top-level `advancedrocketrycommunity:provenance` metadata field so
the JSON remains valid.

Recorded modification dates:

| Target | Modification dates |
|---|---|
| `.gitattributes` | 2026-08-26, 2026-08-27, 2026-08-30 |
| `.gitignore` | 2026-08-26, 2026-08-30 |
| `build.gradle` | 2026-08-26, 2026-08-27, 2026-08-28, 2026-08-30 |
| `gradle.properties` | 2026-08-26, 2026-08-30 |
| `settings.gradle` | 2026-08-26, 2026-08-30 |
| `gradle/wrapper/gradle-wrapper.properties` | 2026-08-26, 2026-08-30 |
| `src/main/resources/pack.mcmeta` | 2026-08-26, 2026-08-30 |
| `src/main/resources/META-INF/mods.toml` | 2026-08-26, 2026-08-30 |

Exact license/notice copy:

```yaml
target_path: docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt
source_artifact_member: LICENSE.txt
source_repository_path: LICENSE.txt
source_commit: 132704e5f23dbee28d776738eb1c0c42fefc0bf6
source_sha256: 481c96d94d182382c4225d5b210f8c658c85350cf548f25c9f56c058804f1e57
target_sha256: 481c96d94d182382c4225d5b210f8c658c85350cf548f25c9f56c058804f1e57
copy_transformation: none; byte-for-byte copy
```

The exact repository copy is
`docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt`; distributable JARs
carry it as `META-INF/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt`.

The binary JAR contains only two adapted MDK targets: `pack.mcmeta` and
`META-INF/mods.toml`. The companion sources JAR contains their exact source
forms and carries the same notice and license copies. The remaining adapted
build/bootstrap targets are available in the corresponding repository source
revision and source archive. A v0.0.2 distribution must offer the sources JAR
and repository source revision alongside the binary JAR; omitting that source
access falls outside this recorded treatment and requires a new review.

## Gradle Wrapper

The official Forge MDK supplied the checked-in Wrapper launchers and JAR. The
launcher scripts retain their Apache License 2.0 headers. The Wrapper JAR hash
matches the checksum published by Gradle for Gradle 8.1 and 8.1.1; Gradle 8.1.1
is the selected, byte-identical upstream source used for this record, not a
claim that the MDK uniquely identifies that release as the Wrapper's origin.
Project attribution: Gradle and the original Wrapper authors. The copyright
notices already present in `gradlew` and `gradlew.bat` remain unchanged.

Affected repository paths:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`

Component source:

```yaml
source_repository: https://github.com/gradle/gradle
source_tag: v8.1.1
source_commit: 1cf537a851c635c364a4214885f8b9798051175b
wrapper_jar_source_path: gradle/wrapper/gradle-wrapper.jar
wrapper_jar_sha256: ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58
license: Apache-2.0
```

Exact Gradle license and bundled-notice copy:

```yaml
target_path: docs/licenses/GRADLE-8.1.1-LICENSE.txt
source_repository_path: LICENSE
source_commit: 1cf537a851c635c364a4214885f8b9798051175b
source_url: https://raw.githubusercontent.com/gradle/gradle/1cf537a851c635c364a4214885f8b9798051175b/LICENSE
source_sha256: e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437
target_sha256: e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437
copy_transformation: none; byte-for-byte copy
```

The exact repository copy is `docs/licenses/GRADLE-8.1.1-LICENSE.txt`;
distributable JARs carry it as
`META-INF/licenses/GRADLE-8.1.1-LICENSE.txt`.

`gradle/wrapper/gradle-wrapper.properties` selects the Gradle 8.8 binary
distribution. The distribution is downloaded from Gradle and is not checked
into this repository. Its pinned SHA-256 is
`a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612`.

## Scope boundary

This notice concerns the Forge MDK and Gradle Wrapper bootstrap inputs only. It
does not classify or license material from original Advanced Rocketry,
LibVulpes, Minecraft, or community forks. Original Advanced Rocketry attribution
is maintained separately in the repository's `NOTICE.md` and `UPSTREAM.md`.
The distributable JAR carries `NOTICE.md` as `META-INF/NOTICE.md`.

## Review status

```yaml
status: THIRD_PARTY_APPROVED
reviewer: sunthemoon
reviewed_at: 2026-08-30
```

The exact source copies and mappings above address the mechanical source-tree
license-copy and attribution gap. The build packages this notice and both exact
license copies under `META-INF` in the main and sources JARs. Reviewer
`sunthemoon` determined on 2026-08-30 that this treatment is sufficient for the
Forge MDK and Gradle Wrapper bootstrap batch, provided each distribution offers
the companion sources JAR and exact repository source revision as required
above. This scoped determination does not establish full-repository originality
or complete the broader G0 release review.
