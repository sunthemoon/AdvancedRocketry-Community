# MANUAL-TEST — v0.0.2 Forge Bootstrap

```yaml
status: PARTIAL
test_date: "2026-08-27"
tester: "Packaged-server automation completed; visible client deferred to external test machine"
build: 1.20.1-0.0.2-dev
commit: 41374d828e9200dc3efc8d2435e8857adb11335b
```

## MANUAL-V002-001 — Client metadata and world start

**Preconditions**

- Build the distributable JAR with Java 17 and Forge 47.4.10.
- Use a clean Minecraft test profile containing only Forge and this mod.

**Steps**

1. Start the client and open the Mods list.
2. Inspect the project name, version, description, credits, license, and logo.
3. Create a disposable single-player world.
4. Save and return to the title screen.

**Expected**

- Metadata matches the approved project identity and says the build has no playable rocket systems.
- The client reaches the title screen and world without a project-source ERROR.

**Actual**

DEFERRED_TO_TEST_MACHINE. A development client was started only far enough to
confirm Forge 47.4.10, Java 17, the `Dev` identity, and the project initialization
line. No valid Mods-screen or world-start screenshot was retained, so this case
remains unpassed.

## MANUAL-V002-002 — Packaged dedicated server and client connection

**Steps**

1. Install Forge 47.4.10 server files in a disposable directory and add the built JAR.
2. Accept the Minecraft EULA for that disposable test instance.
3. Start the server, wait for readiness, and connect with a matching client.
4. Disconnect, stop cleanly, restart, reconnect, and stop again.

**Expected**

- The server starts without loading `net.minecraft.client` classes.
- The matching client connects, and both shutdowns complete without a project-source ERROR.

**Actual**

PARTIAL PASS. `scripts/run_dedicated_server_smoke.py` installed the pinned Forge
47.4.10 server, copied the final JAR, verified its optimized status marker,
created and saved a world, stopped cleanly, restarted the same world, saved it,
and stopped cleanly again. Both cycles exited 0 with no ERROR or client-class
linkage finding. A matching visible client did not join; join/disconnect/reconnect
remains deferred to the external test machine.

## Evidence required

- [ ] Client Mods page screenshot.
- [ ] Client world-start log excerpt.
- [x] Packaged server first-start and restart log excerpts.
- [ ] Player connection evidence tied to the final JAR SHA-256.

See [`evidence/dedicated-server/`](evidence/dedicated-server/) for the completed
server evidence and
[`../../work/v0.0.2-test-machine-handoff.md`](../../work/v0.0.2-test-machine-handoff.md)
for the remaining-machine procedure.
