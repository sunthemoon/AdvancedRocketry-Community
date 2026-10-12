package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads of a record's own position that never load a chunk: whether a loaded beacon there is the record's live beacon,
 * and whether the record is provably stale. An unloaded position or Level proves nothing.
 */
public final class BeaconWorld {
    private BeaconWorld() {
    }

    /** The loaded beacon block entity at the record's position, if its chunk is loaded. */
    public static Optional<PlanetaryBeaconBlockEntity> loaded(MinecraftServer server, BeaconRecord record) {
        Optional<ServerLevel> level = level(server, record);
        BlockPos position = new BlockPos(record.x(), record.y(), record.z());
        if (level.isEmpty() || !level.get().hasChunkAt(position)) {
            return Optional.empty();
        }
        return level.get().getBlockEntity(position) instanceof PlanetaryBeaconBlockEntity beacon
                ? Optional.of(beacon) : Optional.empty();
    }

    /** Live only while the loaded beacon is this record's, formed, enabled, registered and of the same generation. */
    public static boolean live(MinecraftServer server, BeaconRecord record) {
        return loaded(server, record).filter(beacon -> beacon.beaconId().filter(record.beaconId()::equals).isPresent()
                && beacon.live() && beacon.generation() == record.generation()).isPresent();
    }

    /**
     * Stale only when the record's position is loaded and holds no beacon, or holds another readable beacon. A
     * beacon whose data is kept unreadable proves nothing.
     */
    public static boolean stale(MinecraftServer server, BeaconRecord record) {
        Optional<ServerLevel> level = level(server, record);
        BlockPos position = new BlockPos(record.x(), record.y(), record.z());
        if (level.isEmpty() || level.get().isOutsideBuildHeight(position) || !level.get().hasChunkAt(position)) {
            return false;
        }
        BlockState state = level.get().getBlockState(position);
        if (!(state.getBlock() instanceof PlanetaryBeaconBlock)) {
            return true;
        }
        return level.get().getBlockEntity(position) instanceof PlanetaryBeaconBlockEntity beacon
                && beacon.beaconId().filter(id -> !id.equals(record.beaconId())).isPresent();
    }

    private static Optional<ServerLevel> level(MinecraftServer server, BeaconRecord record) {
        ResourceLocation id = ResourceLocation.tryParse(record.level());
        return id == null ? Optional.empty()
                : Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
    }
}
