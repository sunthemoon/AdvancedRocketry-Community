package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.ResourceRestartReleaseFixture.require;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded, disposable-world-only S1 observer. Ordinary server/GameTest launches never activate it. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class CargoS1ReleaseProbe {
    private static CargoS1ReleaseProbe active;
    private final MinecraftServer server;
    private final String leaf;
    private final String stage;
    private int ticks;
    private boolean done;

    private CargoS1ReleaseProbe(MinecraftServer server, String leaf, String stage) {
        this.server = server; this.leaf = leaf; this.stage = stage;
        require(server.isDedicatedServer() && server.getPlayerList().getPlayerCount() == 0, "S1 requires an empty dedicated server");
        require(List.of("fluid", "item").contains(leaf) && List.of("seed", "check", "old-check", "old-restore").contains(stage), "Unknown S1 stage");
        require(server.getWorldData().getLevelName().equals("world-W136-" + leaf + (stage.startsWith("old-") ? "-old" : "-new")), "Not a disposable S1 world");
        for (int x = 4; x <= 6; x++) { server.overworld().setChunkForced(x, 4, true); }
        if (stage.startsWith("old-")) { server.overworld().setChunkForced(16, 16, true); }
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks")
                || System.getProperty("advancedrocketrycommunity.cargoS1Stage") == null) { return; }
        active = new CargoS1ReleaseProbe(event.getServer(), System.getProperty("advancedrocketrycommunity.cargoS1Leaf"),
                System.getProperty("advancedrocketrycommunity.cargoS1Stage"));
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (active != null && active.server == event.getServer()) { active = null; }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (active == null || active.done || active.server != event.getServer() || event.phase != TickEvent.Phase.END) { return; }
        try { active.advance(); }
        catch (RuntimeException failure) {
            active.done = true;
            AdvancedRocketryCommunity.LOGGER.error("ARCE_W136_FAILURE leaf={} stage={} reason={}", active.leaf, active.stage, failure.toString(), failure);
        }
    }

    private void advance() {
        require(++ticks < 600, "S1 timed out");
        var level = server.overworld();
        for (int x = 4; x <= 6; x++) { if (!level.areEntitiesLoaded(new ChunkPos(x, 4).toLong())) { return; } }
        if (stage.startsWith("old-") && !level.areEntitiesLoaded(new ChunkPos(16, 16).toLong())) { return; }
        if (!RocketTransferSavedData.get(server).retryChecked(server) || !RocketTransferSavedData.get(server).clean()) { return; }
        CargoS1Fixture.tags();
        boolean items = leaf.equals("item");
        if (stage.equals("seed")) { require(rockets().isEmpty(), "S1 seed is not empty"); CargoS1Fixture.seed(level, items); }
        var rockets = rockets(); require(rockets.size() == 1, "S1 rocket missing or duplicated: " + rockets.size());
        var rocket = rockets.get(0); require(rocket.operational(), "S1 rocket not operational");
        CargoS1Fixture.restorable(level, rocket);
        if (!stage.startsWith("old-")) {
            CargoS1Fixture.checkDevices(level, items, rocket);
            CargoS1Interlocks.check(level, items, rocket);
            CargoS1Fixture.checkDevices(level, items, rocket);
        }
        require(ResourceEscrowSavedData.existing(server) == null && RocketTransferSavedData.get(server).entries().isEmpty(),
                "S1 fixture admitted escrow or travel");
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W136_RESULT leaf={} stage={} snapshot={} blocks={} protected={} g2={} tags=true restore=true",
                leaf, stage, rocket.snapshot().orElseThrow().contentHash(), rocket.snapshot().orElseThrow().blocks().size(),
                stage.startsWith("old-") ? 0 : 10, !stage.startsWith("old-"));
    }

    private List<RocketEntity> rockets() {
        var result = new ArrayList<RocketEntity>();
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) { if (entity instanceof RocketEntity rocket && !rocket.isRemoved()) { result.add(rocket); } }
        }
        return result;
    }
}
