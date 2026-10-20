package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

/** W107B (Root decision R36): gated fixtures of the loader matrix. They write, observe or pause; they decide nothing. */
final class EscrowLoaderRecoveryProbe {
    private EscrowLoaderRecoveryProbe() { }

    /**
     * R36 item 1: an autosave arriving at C1's stop point. B's terrain chunk (which also holds A) and the rocket's entity
     * chunk are written through vanilla's own serializer, Save event and workers, then joined, before the forced stop.
     */
    static void prefixSave(MinecraftServer server, String cut, List<FluidFuelLoaderBlockEntity> loaders, RocketEntity rocket) {
        EscrowTestEndpoints.requireEnabled(server);
        var level = server.overworld(); var terrain = new ChunkPos(loaders.get(1).getBlockPos()); var entities = rocket.chunkPosition();
        EscrowReleaseProbe.newerSave(level, loaders.get(1).getBlockPos());
        EscrowReleaseProbe.newerEntitySave(level, entities);
        EscrowReleaseProbe.flushFixtureSaves(level);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FIXTURE_SAVE cut={} terrain={},{} entities={},{} tanks={} epochs={} source_markers={} bank={} rocket_epoch={} rocket_markers={}",
                cut, terrain.x, terrain.z, entities.x, entities.z, loaders.stream().map(FluidFuelLoaderEscrowFixtures::amount).toList(),
                loaders.stream().map(l -> section(l).epoch()).toList(), loaders.stream().map(l -> section(l).markers().size()).toList(),
                bank(rocket, false), rocket.escrowSection().epoch(), rocket.escrowSection().markers().size());
    }
}
