# Beta Support Policy

This policy applies to the `v0.9.x` Beta line of Advanced Rocketry: Community
Edition. The project is an unofficial community rewrite and is not supported
by the original Advanced Rocketry maintainers, Mojang, Microsoft, or Forge.

## Supported runtime

- Minecraft `1.20.1` only;
- Java `17` only;
- Forge `47.4.10` as the release baseline;
- Forge `47.4.23` as the recorded advisory compatibility lane;
- dedicated and integrated servers using matching ARCE JARs.

Other Forge 47.x builds may work but are not supported until represented by a
recorded compatibility result. Fabric, NeoForge, hybrid server software, and
offline/public authentication bypasses are outside this Beta support claim.

## Save upgrades and backups

The supported valued-world upgrade starts with an accepted v0.8.0 world and
moves one way to v0.9.x. Before opening a valued world:

1. stop the server cleanly;
2. retain an operator-controlled full-world backup;
3. keep enough free space for an additional ARCE managed-data backup;
4. install matching server and client JARs;
5. inspect the startup migration result before allowing players to join.

The mod's migration guard backs up managed ARCE SavedData before changing its
schema and restores originals after a partial write failure. It does not
replace a full-world backup. Downgrades and direct 1.12.2 world upgrades are
unsupported; a future or unknown schema is rejected rather than overwritten.

## Optional mods

JEI is optional. The compatibility report records one exact JEI build tested
both present and absent. Core startup, dedicated-server operation, recipes, and
save behavior must not require JEI. No other modpack compatibility is promised
without a minimal reproduction.

## Server scale represented by the Beta evidence

The acceptance target is a small dedicated server with four simulated or real
participants, the maximum supported test structures, 16 active vents, 10
stations, and 100 logical satellite missions during the recorded soak. Larger
servers should treat the result as a baseline, not a capacity guarantee.

## Reports and severity

Reports must include the ARCE JAR SHA-256, Java/Forge versions, relevant config,
the smallest reproducing steps, and sanitized logs. Use the compatibility issue
template for mod interactions and follow `SECURITY.md` for duplication, remote
crash, permission bypass, arbitrary chunk loading, or save corruption.

Critical and High defects block a Beta candidate. Medium and Low defects may be
published only when their impact and workaround are accurately recorded in the
version Known Issues.

## Release meaning

`PASSED` means the recorded Beta candidate met its G0–G9 evidence at one exact
commit. It does not mean stable API compatibility, universal modpack support,
or a v1.0 long-term save guarantee. Public downloads, when created, must remain
marked pre-release throughout v0.9.x.
