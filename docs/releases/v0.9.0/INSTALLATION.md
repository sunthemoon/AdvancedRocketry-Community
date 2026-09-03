# Installation — v0.9.0 Beta 1

## Supported runtime

- Minecraft `1.20.1` only
- Java `17` only
- Forge `47.4.10` release baseline
- Forge `47.4.23` recorded advisory compatibility lane
- JEI `15.56.0.205` optional on clients
- Matching ARCE JAR on the server and every connecting client

Candidate file:

```text
advancedrocketry-community-1.20.1-0.9.0-beta.1.jar
SHA-256 fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad
```

Place the JAR in the `mods` directory of the Forge dedicated server and every
client. Do not mix separately built files merely because their displayed
version matches; compare SHA-256 first. JEI is not bundled and is not required
by the dedicated server or by clients that do not need its recipe view.

## Save upgrade

The supported valued-world upgrade is one-way from an accepted v0.8.0 world to
v0.9.x. Before installing:

1. stop the server cleanly;
2. make and retain an operator-controlled full-world backup;
3. install the exact matching JAR on server and clients;
4. start once without players and inspect `ARCE-BETA-1001` or
   `ARCE-BETA-1002` plus the bounded operator report;
5. retain the managed-data backup created under the world directory.

The startup guard validates all managed roots before replacement, creates a
byte-exact manifest, stages and rereads schema 2, and restores every original
after a partial commit failure. Its backup is not a substitute for the full
world backup. Downgrade, direct 1.12.2 loading, and direct valued-world support
for pre-v0.8 formats are unsupported.

## Release meaning

This file describes a Beta pre-release, not a stable build or a v1.0 save/API
guarantee. The complete compatibility and reporting boundary is in
[`BETA-SUPPORT-POLICY.md`](../../BETA-SUPPORT-POLICY.md).
