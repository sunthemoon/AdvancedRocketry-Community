# MANUAL-TEST — v0.0.2 Forge Bootstrap

```yaml
status: NOT_RUN
test_date: ""
tester: ""
build: 1.20.1-0.0.2-dev
commit: PENDING_BRANCH_COMMIT
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

NOT_RUN.

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

NOT_RUN. The Forge GameTest physical server passed, but it does not replace the packaged player-connection/restart case.

## Evidence required

- Client Mods page screenshot.
- Client world-start log excerpt.
- Packaged server first-start and restart log excerpts.
- Player connection evidence tied to the final JAR SHA-256.
