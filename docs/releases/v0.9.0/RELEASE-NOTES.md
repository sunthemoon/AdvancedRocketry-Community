# Advanced Rocketry: Community Edition v0.9.0 Beta 1

> Unofficial community rewrite for Minecraft 1.20.1 Forge. This project is not
> supported by the original Advanced Rocketry maintainers and is not an
> official Minecraft product.

This first Beta freezes the accepted Earth–Moon, life-support, rocket, station,
research, and logical-satellite loop while strengthening save upgrades,
dedicated-server recovery, diagnostics, security, and compatibility.

## Highlights

- Transactional v0.8-to-v0.9 managed-data upgrade with a byte-exact pre-change
  backup, manifest, staged validation, and rollback after partial failure.
- Recovery from a forced server stop at a durable cross-dimension flight
  checkpoint without duplicate or missing rocket authority, blocks, inventory,
  or fuel.
- Bounded operator diagnostics and stable `ARCE-BETA-*` diagnostic IDs.
- Forge 47.4.10 baseline plus a recorded Forge 47.4.23 advisory lane.
- Optional JEI 15.56.0.205 Electrolyzer recipe display; JEI is not required by
  clients or dedicated servers.
- Stricter malformed/replayed flight-intent handling and a complete Beta
  authority/security review with zero known Critical or High findings.
- A two-hour packaged-server run combining a 2,048-block rocket, 16 vents, 10
  stations, 100 missions, periodic saves, and four simulated status clients.

## Install and upgrade

Use Minecraft 1.20.1, Java 17, Forge 47.4.10, and the same ARCE JAR on the
server and every client. Verify the downloaded asset before use:

```text
advancedrocketry-community-1.20.1-0.9.0-beta.1.jar
SHA-256 fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad
```

Back up the complete world before upgrading. The supported valued-world path
starts at accepted v0.8.0; downgrades and direct 1.12.2 world loading are not
supported. Follow [`INSTALLATION.md`](INSTALLATION.md) before first startup.

## Beta limits

This is a pre-release, not a stable build or a universal modpack compatibility
claim. Forge 47.4.23 and JEI support are limited to the exact recorded lanes.
The server soak is headless and does not measure render performance. See
[`KNOWN-ISSUES.md`](KNOWN-ISSUES.md) and the
[`Beta support policy`](../../BETA-SUPPORT-POLICY.md) before deployment.
