package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.command.ResourceRestartReleaseFixture.require;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lifecycle-bounded observer activated only by two explicit release-test properties and a disposable world name. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class FuelLoaderS1ReleaseProbe {
    private static FuelLoaderS1ReleaseProbe active;
    private final MinecraftServer server;
    private final String stage;
    private int ticks;
    private boolean done;

    private FuelLoaderS1ReleaseProbe(MinecraftServer server, String stage) {
        this.server = server; this.stage = stage;
        require(server.isDedicatedServer() && server.getPlayerList().getPlayerCount() == 0, "S1 requires an empty dedicated server");
        require(List.of("seed", "check", "old-check").contains(stage), "Unknown loader S1 stage");
        require(server.getWorldData().getLevelName().equals("world-W176-" + (stage.startsWith("old-") ? "old" : "new")),
                "Not a disposable loader S1 world");
        if (stage.startsWith("old-")) { server.overworld().setChunkForced(16, 16, true); }
        else { for (int x = 4; x <= 6; x++) { server.overworld().setChunkForced(x, 4, true); } }
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks")
                || System.getProperty("advancedrocketrycommunity.fuelLoaderS1Stage") == null) { return; }
        active = new FuelLoaderS1ReleaseProbe(event.getServer(), System.getProperty("advancedrocketrycommunity.fuelLoaderS1Stage"));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (active != null && active.server == event.getServer()) { active = null; }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (active == null || active.done || active.server != event.getServer() || event.phase != TickEvent.Phase.END) { return; }
        try { active.advance(); }
        catch (RuntimeException failure) {
            active.done = true;
            AdvancedRocketryCommunity.LOGGER.error("ARCE_W176_FAILURE stage={} reason={}", active.stage, failure.toString(), failure);
        }
    }
    private void advance() {
        require(++ticks < 600, "Loader S1 timed out");
        var level = server.overworld(); boolean old = stage.startsWith("old-");
        if (old) { if (!level.areEntitiesLoaded(new ChunkPos(16, 16).toLong())) { return; } }
        else { for (int x = 4; x <= 6; x++) { if (!level.areEntitiesLoaded(new ChunkPos(x, 4).toLong())) { return; } } }
        if (!RocketTransferSavedData.get(server).retryChecked(server) || !RocketTransferSavedData.get(server).clean()) { return; }
        if (stage.equals("seed")) { require(rockets().isEmpty(), "S1 seed is not empty"); FuelLoaderS1Fixture.seed(level); }
        var rockets = rockets(); require(rockets.size() == 1, "S1 rocket missing or duplicated");
        var rocket = rockets.get(0);
        require(rocket.operational() && rocket.saveWithoutId(new CompoundTag()).getCompound("RocketEntityData").getInt("schema_version") == 3,
                "S1 rocket not operational schema 3");
        if (old) { FuelLoaderS1Fixture.checkOld(level); }
        else {
            FuelLoaderS1Fixture.checkDevices(level, rocket); FuelLoaderS1Interlocks.check(level, rocket);
            FuelLoaderS1Fixture.checkDevices(level, rocket);
        }
        require(ResourceEscrowSavedData.existing(server) == null && RocketTransferSavedData.get(server).entries().isEmpty(),
                "S1 admitted escrow or travel");
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W176_RESULT stage={} snapshot={} schema=3 protected={} g2={} old_loader={}",
                stage, rocket.snapshot().orElseThrow().contentHash(), old ? 0 : 5, !old, old);
    }
    private List<RocketEntity> rockets() {
        var result = new ArrayList<RocketEntity>();
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) { if (entity instanceof RocketEntity rocket && !rocket.isRemoved()) { result.add(rocket); } }
        }
        return result;
    }
}
