# Advanced Rocketry: Community Edition

> **Unofficial community rewrite for Minecraft 1.20.1 Forge.**
>
> This project is not an official continuation and is not maintained or supported by the original Advanced Rocketry maintainers.
>
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

## Status

**The v0.9.0 Beta 1 pre-release is available for Forge 1.20.1. The authoritative
build and acceptance state is recorded in
[`docs/status/GATE_STATUS.md`](docs/status/GATE_STATUS.md). This is not a stable
v1.0 release.**

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

## What this project is

Advanced Rocketry: Community Edition aims to rebuild the core Advanced Rocketry experience on a maintainable Forge 1.20.1 foundation:

- rockets constructed from real blocks;
- Earth, Moon, and space travel;
- vacuum and life support;
- basic space stations;
- later, research and satellites;
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

Before a public Beta is published:

- test worlds may be reset;
- APIs may change;
- binary releases may be withheld;
- unsupported mod combinations are not investigated unless a minimal reproduction is provided.

Security-sensitive duplication, arbitrary chunk loading, packet abuse, or save corruption reports should follow [`SECURITY.md`](SECURITY.md).

The v0.9.x runtime, save-upgrade, optional-mod, server-scale, and report scope
is defined in [`docs/BETA-SUPPORT-POLICY.md`](docs/BETA-SUPPORT-POLICY.md).

## License

MIT. See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md).
