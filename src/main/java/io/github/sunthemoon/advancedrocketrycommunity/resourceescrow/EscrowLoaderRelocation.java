package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderIndex;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Loaded holders diagnose relocation only. This index is neither an absence proof nor adoption authority. */
final class EscrowLoaderRelocation {
    record Slot(String level, BlockPos position) { }
    private EscrowLoaderRelocation() { }

    static EscrowReason missingReason(MinecraftServer server, EscrowEndpoint endpoint) {
        return missingReason(server.getAllLevels(), endpoint, level -> loadedHolder(level, endpoint.identity()));
    }

    static <L> EscrowReason missingReason(Iterable<L> levels, EscrowEndpoint endpoint, Function<L, Slot> lookup) {
        int visited = 0; boolean relocated = false;
        var original = new Slot(endpoint.level(), EscrowLoaderEndpoint.position(endpoint));
        for (var level : levels) {
            // Overflow cannot certify the unsearched Levels, even if an earlier lookup saw a holder.
            if (++visited > 64) { return EscrowReason.BARRIER_BOUNDS; }
            var holder = lookup.apply(level);
            relocated |= holder != null && !holder.equals(original);
        }
        return relocated ? EscrowReason.RELOCATED_ENDPOINT : EscrowReason.MISSING_ENDPOINT;
    }

    private static Slot loadedHolder(ServerLevel level, UUID device) {
        return loadedHolder(level, device, FluidFuelLoaderIndex.holder(level, device), position -> {
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            return chunk == null ? null : chunk.getBlockEntity(position);
        });
    }

    static Slot loadedHolder(ServerLevel level, UUID device, FluidFuelLoaderBlockEntity holder,
                             Function<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> loadedAt) {
        if (holder == null || holder.isRemoved() || holder.getLevel() != level
                || holder.dataStatus() != LoaderRoot.Status.VALID || holder.root().isEmpty()
                || !device.equals(holder.root().orElseThrow().device())) { return null; }
        var position = holder.getBlockPos();
        if (loadedAt.apply(position) != holder) { return null; }
        // A live duplicate still disproves absence; it never becomes a permitted source at this new place.
        return new Slot(level.dimension().location().toString(), position);
    }

    static void requireMissing(MinecraftServer server, EscrowEndpoint endpoint) {
        var reason = missingReason(server, endpoint);
        if (reason != EscrowReason.MISSING_ENDPOINT) { throw new EscrowReconciliationView.Refusal(reason); }
    }
}
