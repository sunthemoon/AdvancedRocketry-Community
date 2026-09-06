# Advanced Rocketry: Community Edition

> **Unofficial community rewrite for Minecraft 1.20.1 Forge.**
>
> This project is not an official continuation and is not maintained or supported by the original Advanced Rocketry maintainers.
>
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

## Status

**v1.0.0 Stable Core is in development (`1.0.0-dev`), not yet a stable release.
The latest published build remains v0.9.0 Beta 1. Development and acceptance
status are recorded in [`docs/status/GATE_STATUS.md`](docs/status/GATE_STATUS.md).**

Current target:

- Minecraft `1.20.1`
- Forge baseline `47.4.10`
- Forge compatibility lane `47.4.23`
- Java `17`
- License `MIT`

The latest `PASSED` milestone is `v0.9.0`. Its feature-frozen Beta 1 is bound to
one 1,225,536-byte, 758-entry JAR, packaged migration, recovery, Forge/JEI
compatibility, and maximum-soak evidence. PR #13 passed 4/4 checks, merged, and
reproduced the exact JAR and manifest from `main`. Download the correctly marked
[GitHub pre-release](https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1).
See
[`docs/04-VERSION-ROADMAP.md`](docs/04-VERSION-ROADMAP.md),
[`docs/releases/v0.9.0/RELEASE-EVIDENCE.md`](docs/releases/v0.9.0/RELEASE-EVIDENCE.md),
and [`docs/status/GATE_STATUS.md`](docs/status/GATE_STATUS.md) for the exact
candidate and acceptance record.

v1.0 freezes the existing gameplay and focuses on world upgrades, interrupted
transactions, compatibility and performance. Test development builds only on
copies of backed-up worlds. The planned `1.0.x` maintenance line is limited to
compatible defect, security and migration fixes; expansion work starts in
v1.1. Current checks and remaining acceptance work are listed in the
[v1.0 implementation log](docs/work/v1.0.0-implementation-log.md).
The [v1.0 development evidence handoff](docs/releases/v1.0.0/RELEASE-EVIDENCE.md)
maps tested artifacts to their results and lists outstanding release criteria;
it is not a release approval or a stable download.

## What this project is

Advanced Rocketry: Community Edition aims to rebuild the core Advanced Rocketry experience on a maintainable Forge 1.20.1 foundation:

- rockets constructed from real blocks;
- Earth, Moon, and space travel;
- vacuum and life support;
- basic space stations;
- basic research and satellites;
- server-authoritative multiplayer behavior;
- versioned save data and automated tests.

The original 1.12.2 project is treated as a behavior and asset reference. This repository is not a line-by-line compilation port.

## MVP definition

The first stable release is complete only when a player can:

1. build and fuel a block-built rocket;
2. survive vacuum with life support;
3. launch from Earth;
4. land on the Moon;
5. return safely;
6. recover correctly after disconnects and server restarts;
7. do so without known inventory, block, passenger, or rocket duplication.

## Roadmap

| Version | Goal |
|---|---|
| `v0.0.1` | Repository, attribution, governance |
| `v0.0.2` | Forge 1.20.1 build foundation |
| `v0.1.0` | Asset and registry baseline |
| `v0.2.0` | One complete machine vertical slice |
| `v0.3.0` | Celestial data and fixed dimensions |
| `v0.4.0` | Vacuum, suits, oxygen, sealed rooms |
| `v0.5.0` | Transactional rocket assembly |
| `v0.6.0` | Reliable Earth–Moon round trip |
| `v0.7.0` | Basic space station |
| `v0.8.0` | Progression and satellites |
| `v0.9.0` | Beta hardening |
| `v1.0.0` | Stable community MVP |
| `v1.1.0–v1.3.0` | Travel, machine/multiblock, and public API expansion kernels |
| `v1.4.0–v1.8.0` | Planetary, orbital, mission, endgame, and classic content expansion |
| `v1.9.0` | Feature-parity Beta hardening |
| `v2.0.0` | Classic feature parity stable |

These are plans, not shipped features. v1.0 stabilizes the existing core;
classic feature parity is the v2.0 target, not a promise of 1.12.2 save or
binary compatibility. See the
[post-1.0 roadmap](docs/16-POST-1.0-VERSION-ROADMAP.md) and
[version plans](DOCUMENT-INDEX.md).

## Attribution

This project may include audited portions derived from the MIT-licensed original Advanced Rocketry repository. The original license notice is preserved in [`LICENSE`](LICENSE), with additional details in [`NOTICE.md`](NOTICE.md), [`UPSTREAM.md`](UPSTREAM.md), and the provenance ledger.

Do not report Community Edition bugs to the original Advanced Rocketry maintainers.

## Contributing

Read:

1. [`CONTRIBUTING.md`](CONTRIBUTING.md)
2. [`AGENTS.md`](AGENTS.md)
3. [`docs/04-VERSION-ROADMAP.md`](docs/04-VERSION-ROADMAP.md)
4. the document for the current target version.

A feature is not complete until its required automated, dedicated-server, persistence, performance, and manual acceptance gates pass.

## Support policy

The published Beta's runtime, world-upgrade, optional-mod, server-scale and
report scope is defined in [the Beta support policy](docs/BETA-SUPPORT-POLICY.md).
Development builds are not stable releases:

- use copies of backed-up worlds for `1.0.0-dev` testing;
- use matching development builds on clients and servers: the flight-console
  synchronization uses flight protocol `4`, which rejects earlier flight protocols;
- the intended v1.0 upgrade source is the accepted `v0.9.0-beta.1` world format;
  representative-world acceptance is still in progress;
- direct 1.12.2 world loading and downgrading an upgraded world are unsupported;
- provide a minimal reproduction for combinations outside the tested Forge/JEI matrix.

Security-sensitive duplication, arbitrary chunk loading, packet abuse, or save corruption reports should follow [`SECURITY.md`](SECURITY.md).

## Install the published Beta

1. Use Java 17 and a Minecraft 1.20.1 instance with Forge `47.4.10`.
   Forge `47.4.23` is the separately tested compatibility lane.
2. Download the main mod JAR from the [Beta release](https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1).
   Put it in the instance's `mods` directory; do not install the `-sources.jar`.
3. For multiplayer, use the same Community Edition version on the server and
   every client. JEI is optional on clients; the tested version is `15.56.0.205`.
   It is not needed on the dedicated server.
4. Back up the complete world before upgrading. Follow the supported source
   versions and recovery instructions in the Beta support policy. Keep the
   backup separate; restoring it is the supported rollback, not opening an
   upgraded world with an older mod.
5. Start the instance and check that **Advanced Rocketry: Community Edition**
   appears in the mod list. Report problems with the exact mod/Forge/Java
   versions, relevant logs and reproduction steps using the repository issues.

The unpublished `1.0.0-dev` build is not the download offered by these steps.

## License

MIT. See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md).
