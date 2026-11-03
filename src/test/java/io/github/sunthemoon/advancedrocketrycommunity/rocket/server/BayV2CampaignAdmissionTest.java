package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayV2Campaign;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaimBook;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The campaign connection exception reaches D0 for an ordinary named owner's transfer, not just fixture rockets. */
final class BayV2CampaignAdmissionTest {
    private static final UUID A = new UUID(269, 1), B = new UUID(269, 2);
    @TempDir Path directory;
    private final ReleaseTestServers servers = new ReleaseTestServers();
    private final List<MinecraftServer> started = new ArrayList<>();
    private final List<LevelStorageSource.LevelStorageAccess> worlds = new ArrayList<>();
    private final Map<String, String> saved = new HashMap<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @AfterEach void restore() throws Exception {
        started.forEach(server -> BayV2Campaign.stopped(new ServerStoppedEvent(server)));
        for (var world : worlds) { world.close(); }
        servers.close();
        saved.forEach((key, value) -> { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } });
    }

    @Test void aNamedClientsBayTransferReachesD0OnlyWithSelectionAndMarker() throws Exception {
        property(EscrowTestEndpoints.HOOKS, "true");
        assertD0(true, true, true);
        assertD0(false, true, false);
        assertD0(true, false, false);
    }

    private void assertD0(boolean selected, boolean marked, boolean admitted) throws Exception {
        property(BayV2Campaign.PROPERTY, selected ? A + "," + B : null);
        var server = servers.server(directory, false);
        var world = LevelStorageSource.createDefault(directory).createAccess("world-" + worlds.size());
        worlds.add(world); started.add(server);
        UnstartedServer.set(MinecraftServer.class, server, "storageSource", world);
        UnstartedServer.set(MinecraftServer.class, server, "running", true);
        if (marked) { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(BayV2Campaign.MARKER), BayV2Campaign.MARKER_TEXT); }
        BayV2Campaign.starting(new ServerAboutToStartEvent(server));
        servers.join(server, A); servers.join(server, B); // Neither connection is facility-owned.

        var session = new EscrowRuntime().bayReceipts(server).session(server);
        var constructor = BayTransferRecovery.class.getDeclaredConstructor(MinecraftServer.class, BayDeploymentSession.class);
        constructor.setAccessible(true);
        var coordinator = constructor.newInstance(server, session);
        var record = W231BayCapacityFixture.record(0, A);
        var source = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(Entity.class, source, "uuid", record.sourceEntityId());
        try {
            assertEquals(A, record.ownerId());
            assertEquals(admitted, session.admissionEnabled(), "D0's session admission");
            coordinator.ascentComplete(server, new RocketTransferSavedData(), record, source);
            BayClaimBook claims = UnstartedServer.get(BayTransferRecovery.class, coordinator, "claims");
            BayStepQueue queue = UnstartedServer.get(BayTransferRecovery.class, coordinator, "queue");
            assertEquals(admitted ? 1 : 0, claims.size(), "D0's immediate HELD claim");
            assertEquals(admitted ? 1 : 0, queue.size(), "D0's queued deployment");
            if (admitted) {
                var claim = claims.get(record.logicalRocketId()).orElseThrow();
                assertEquals(record.transferId(), claim.transfer());
                assertEquals(BayClaim.Origin.CHECKPOINT, claim.origin());
                assertEquals(BayClaim.Hold.OWNED_WAIT, claim.hold());
                assertEquals(8, claim.members().size());
            }
        } finally { coordinator.stop(); session.close(); }
    }

    private void property(String key, String value) {
        if (!saved.containsKey(key)) { saved.put(key, System.getProperty(key)); }
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }
}
