package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W128R-R1: the bay's release-test entries pass the complete {@link EscrowTestEndpoints} gate before they reach anything.
 * The first-tick hook's operator decommission removes any idle satellite without a receipt, so with RCON enabled or a
 * connected player the facility did not create, a real registry holding one idle satellite stays byte-equal and is never
 * fetched. The open gate's control shows the same fixture would lose its satellite. Every release-test command is
 * refused by the gate itself before its first read or write.
 */
final class BayReleaseTestGateTest {
    private static final String HOOKS = EscrowTestEndpoints.HOOKS;
    @TempDir Path directory;
    private final ReleaseTestServers servers = new ReleaseTestServers();
    private final Map<String, String> saved = new HashMap<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restore() {
        servers.close();
        saved.forEach((key, value) -> { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } });
    }

    private void property(String key, String value) {
        if (!saved.containsKey(key)) { saved.put(key, System.getProperty(key)); }
        System.setProperty(key, value);
    }

    @Test void rconEnabledLeavesAnIdleSatelliteWithoutAReceiptUntouched() throws Exception {
        assertFirstTickRefused(servers.server(directory, true));
    }

    @Test void aConnectedPlayerTheFacilityDidNotCreateLeavesTheSatelliteUntouched() throws Exception {
        MinecraftServer server = servers.server(directory, false);
        servers.join(server, UUID.randomUUID());
        assertFirstTickRefused(server);
    }

    @Test void theOpenGateReachesTheRegistryAndWouldRemoveTheIdleSatellite() throws Exception {
        MinecraftServer server = servers.server(directory, false);
        UUID satellite = UUID.randomUUID();
        var registry = registryWith(satellite);
        property(HOOKS, "true"); property(BayDeploymentCuts.FIRST_TICK, satellite.toString());
        assertTrue(EscrowTestEndpoints.enabled(server), "the control's gate is open");
        var fetched = new AtomicInteger();
        BayDeploymentCuts.firstTick(server, () -> { fetched.incrementAndGet(); return registry; });
        assertEquals(1, fetched.get(), "the open gate reaches the registry");
        assertTrue(registry.satellite(satellite).isEmpty(), "with no receipt, the operator decommission removes the satellite");
    }

    @Test void theBlockedRegistrySeamNeedsTheFullGate() throws Exception {
        property(HOOKS, "true"); property(BayDeploymentCuts.REGISTRY_BLOCKED, "true");
        MinecraftServer rcon = servers.server(directory, true);
        MinecraftServer foreign = servers.server(directory, false);
        servers.join(foreign, UUID.randomUUID());
        assertFalse(BayDeploymentCuts.registryBlocked(rcon), "RCON enabled the seam");
        assertFalse(BayDeploymentCuts.registryBlocked(foreign), "a foreign connection enabled the seam");
        assertTrue(BayDeploymentCuts.registryBlocked(servers.server(directory, false)), "the open gate's control");
        property(BayDeploymentCuts.REGISTRY_BLOCKED, "false");
        assertFalse(BayDeploymentCuts.registryBlocked(servers.server(directory, false)), "the seam is off without its property");
    }

    @Test void everyReleaseTestCommandIsRefusedByTheGateBeforeItsFirstEffect() throws Exception {
        property(HOOKS, "true");
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        new BayReleaseTestCommands().register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        List<String> commands = List.of("arce bay release-test fixture 8 2 8",
                "arce bay release-test status " + UUID.randomUUID(), "arce bay release-test fill 8", "arce bay release-test pending");
        MinecraftServer rcon = servers.server(directory, true);
        MinecraftServer foreign = servers.server(directory, false);
        servers.join(foreign, UUID.randomUUID());
        for (MinecraftServer closed : List.of(rcon, foreign)) {
            assertFalse(EscrowTestEndpoints.enabled(closed));
            for (String command : commands) {
                var refused = assertThrows(IllegalArgumentException.class, () -> dispatcher.execute(command, source(closed)), command);
                assertTrue(refused.getMessage().startsWith("Escrow fixture requires"), command + ": " + refused.getMessage());
            }
        }
        // Control: with the gate open the same dispatch passes it and fails at its first world or runtime access instead,
        // because this server has none. So the refusal above is the gate's, ahead of every effect.
        MinecraftServer open = servers.server(directory, false);
        assertTrue(EscrowTestEndpoints.enabled(open));
        for (String command : commands) {
            var past = assertThrows(Throwable.class, () -> dispatcher.execute(command, source(open)), command);
            assertFalse(past instanceof IllegalArgumentException && String.valueOf(past.getMessage()).startsWith("Escrow fixture requires"),
                    command + " was refused by an open gate");
        }
    }

    private void assertFirstTickRefused(MinecraftServer server) {
        UUID satellite = UUID.randomUUID();
        var registry = registryWith(satellite);
        CompoundTag before = registry.save(new CompoundTag());
        long sequence = registry.mutationSequence();
        property(HOOKS, "true"); property(BayDeploymentCuts.FIRST_TICK, satellite.toString());
        assertTrue(server.isDedicatedServer() && Boolean.getBoolean(HOOKS), "only the full gate differs from the open control");
        assertFalse(EscrowTestEndpoints.enabled(server), "the gate is closed");
        var fetched = new AtomicInteger();
        BayDeploymentCuts.firstTick(server, () -> { fetched.incrementAndGet(); return registry; });
        assertEquals(0, fetched.get(), "the closed gate never reached the registry");
        assertTrue(registry.satellite(satellite).isPresent(), "the idle satellite is still registered");
        assertEquals(sequence, registry.mutationSequence(), "no registry mutation");
        assertEquals(before, registry.save(new CompoundTag()), "the registry is byte-equal");
        // A malformed UUID is never parsed behind the closed gate either.
        property(BayDeploymentCuts.FIRST_TICK, "not-a-uuid");
        assertDoesNotThrow(() -> BayDeploymentCuts.firstTick(server, () -> { fetched.incrementAndGet(); return registry; }));
        assertEquals(0, fetched.get());
    }

    /** A real registry holding one idle gas harvester of another owner, with no receipt (the default fence). */
    private static SatelliteMissionSavedData registryWith(UUID satellite) {
        var registry = SatelliteMissionSavedData.create(0);
        UUID owner = UUID.randomUUID();
        var launched = registry.launchIdle(time -> SatelliteState.launchIdle(satellite, ModIdentity.id("gas_harvester"), owner, time,
                ModIdentity.id("earth"), new SatelliteBlueprint(List.of(ModIdentity.id("primary")), false,
                        new SatelliteStats(4, 10720, 1000, 1, 10)), new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER)), 0);
        assertEquals(SatelliteOperationCode.SUCCESS, launched.code());
        assertTrue(registry.satellite(satellite).isPresent());
        return registry;
    }

    private static CommandSourceStack source(MinecraftServer server) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4, "gate-test", Component.literal("gate-test"),
                server, null);
    }
}
