package io.github.sunthemoon.advancedrocketrycommunity.endgame.protection;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.common.MinecraftForge;

/** The ADR-054 section 5 chain's view of a live {@link ServerLevel}; it reads chunks only with getChunkNow. */
public final class ForgeProtectionView implements EndgameProtection.View {
    private static final StationAccessService ACCESS = new StationAccessService();

    private final ServerLevel level;
    private final Collection<ProtectedZone> zones;

    public ForgeProtectionView(ServerLevel level, Collection<ProtectedZone> zones) {
        this.level = Objects.requireNonNull(level, "level");
        this.zones = List.copyOf(zones);
    }

    @Override
    public boolean chunksFull(BlockPos min, BlockPos max) {
        for (int x = SectionPos.blockToSectionCoord(min.getX()); x <= SectionPos.blockToSectionCoord(max.getX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(min.getZ()); z <= SectionPos.blockToSectionCoord(max.getZ());
                 z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean insideWorld(BlockPos min, BlockPos max) {
        WorldBorder border = level.getWorldBorder();
        return border.isWithinBounds(min) && border.isWithinBounds(max)
                && min.getY() >= level.getMinBuildHeight() && max.getY() < level.getMaxBuildHeight();
    }

    @Override
    public Collection<ProtectedZone> zones() {
        return zones;
    }

    @Override
    public boolean spaceLevel() {
        return level.dimension().equals(CelestialIds.SPACE_LEVEL);
    }

    @Override
    public boolean stationsAllow(UUID owner, BlockPos min, BlockPos max) {
        StationRegistrySavedData stations = StationRegistrySavedData.get(level.getServer());
        if (!stations.updatesAvailable()) {
            return false;
        }
        Optional<StationState> station = stations.findAt(min.getX(), min.getZ());
        return station.filter(state -> state.region().contains(max.getX(), max.getZ())
                && ACCESS.allowed(state, owner, false, StationAccessAction.BUILD)).isPresent();
    }

    @Override
    public Optional<EndgameProtection.SpawnSquare> spawnSquare() {
        MinecraftServer server = level.getServer();
        int radius = server.getSpawnProtectionRadius();
        if (!server.isDedicatedServer() || !level.dimension().equals(Level.OVERWORLD) || radius <= 0) {
            return Optional.empty();
        }
        BlockPos spawn = level.getSharedSpawnPos();
        return Optional.of(new EndgameProtection.SpawnSquare(spawn.getX(), spawn.getZ(), radius));
    }

    @Override
    public boolean cancelled(EndgameEffectEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }
}
