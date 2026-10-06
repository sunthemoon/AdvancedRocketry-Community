package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryCatalog;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Two selected-cell preflights, one boundary observation and existing indexed supply queries. */
final class SealDetectorService implements AutoCloseable {
    private volatile Binding binding;

    SealDetectorService(MinecraftServer server, AtmosphereManager manager, AtmosphereBoundaryCatalog boundaries) {
        binding = new Binding(Objects.requireNonNull(server, "server"), Objects.requireNonNull(manager, "manager"),
                Objects.requireNonNull(boundaries, "boundaries"));
    }

    SealDetectorReading read(UseOnContext context) {
        Binding active = binding;
        // Host/thread refusal precedes all actor, Level, chunk, catalog and manager reads.
        if (active == null || !active.server().isSameThread() || !current(active, context)) {
            return SealDetectorReading.unavailable();
        }
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        ServerLevel level = (ServerLevel) context.getLevel();
        BlockPos target = context.getClickedPos().immutable();
        Direction face = context.getClickedFace();
        Vec3 eye = player.getEyePosition();
        Vec3 hit = context.getClickLocation();
        if (face == null || !validEye(eye, level.getMinBuildHeight(), level.getMaxBuildHeight())
                || !inReach(eye, hit, target) || !validCell(level, target) || !level.mayInteract(player, target)) {
            return SealDetectorReading.unavailable();
        }
        BlockPos adjacent = target.relative(face).immutable();
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return SealDetectorReading.unavailable();
        }
        boolean adjacentAvailable = validCell(level, adjacent);
        if (adjacentAvailable && !sameChunk(target, adjacent)) {
            adjacentAvailable = level.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4) != null;
        }
        CellObservation observed = new ServerLevelVolumeWorldView(level, false, active.boundaries())
                .observe(new VolumePosition(target.getX(), target.getY(), target.getZ()));
        if (observed == CellObservation.UNLOADED) {
            return SealDetectorReading.unavailable();
        }
        SealDetectorReading.Boundary boundary = boundary(observed);
        SealDetectorReading.Supply supply = SealDetectorReading.Supply.UNAVAILABLE;
        if (adjacentAvailable) {
            BreathabilityState state = active.manager().breathabilityAt(level, adjacent);
            boolean supplied = state == BreathabilityState.BREATHABLE && active.manager().controlledAt(level, adjacent);
            supply = supply(state, supplied);
        }
        // A callback cannot produce a positive result through a closed/replaced actor/host binding.
        if (binding != active || !current(active, context)) {
            return SealDetectorReading.unavailable();
        }
        return SealDetectorReading.measured(boundary, supply);
    }

    private static boolean current(Binding active, UseOnContext context) {
        return context != null && context.getPlayer() instanceof ServerPlayer player
                && context.getLevel() instanceof ServerLevel level && level.getServer() == active.server()
                && player.serverLevel() == level && AtmosphereAnalyzerService.admitted(active.server(), player)
                && SealDetectorItem.heldMatches(context, null);
    }

    private static boolean validCell(ServerLevel level, BlockPos cell) {
        return Level.isInSpawnableBounds(cell) && !level.isOutsideBuildHeight(cell)
                && level.getWorldBorder().isWithinBounds(cell);
    }

    static boolean validEye(Vec3 eye, int minHeight, int maxHeight) {
        return finite(eye) && eye.x >= -30_000_000D && eye.x < 30_000_000D
                && eye.z >= -30_000_000D && eye.z < 30_000_000D
                && eye.y >= minHeight && eye.y < maxHeight;
    }

    static boolean inReach(Vec3 eye, Vec3 hit, BlockPos target) {
        return finite(eye) && finite(hit) && target != null && eye.distanceToSqr(hit) <= 36D
                && distanceToCellSquared(eye, target) <= 36D;
    }

    static double distanceToCellSquared(Vec3 eye, BlockPos target) {
        double x = gap(eye.x, target.getX()), y = gap(eye.y, target.getY()), z = gap(eye.z, target.getZ());
        return x * x + y * y + z * z;
    }

    private static double gap(double coordinate, int lower) {
        return Math.max(Math.max(lower - coordinate, coordinate - (lower + 1D)), 0D);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static boolean sameChunk(BlockPos first, BlockPos second) {
        return (first.getX() >> 4) == (second.getX() >> 4) && (first.getZ() >> 4) == (second.getZ() >> 4);
    }

    static SealDetectorReading.Boundary boundary(CellObservation observation) {
        return switch (Objects.requireNonNull(observation, "observation")) {
            case SEALED -> SealDetectorReading.Boundary.SEALED;
            case OPEN, TRAVERSABLE -> SealDetectorReading.Boundary.OPEN;
            case UNLOADED -> SealDetectorReading.Boundary.UNAVAILABLE;
        };
    }

    static SealDetectorReading.Supply supply(BreathabilityState state, boolean supplied) {
        return switch (Objects.requireNonNull(state, "state")) {
            case BREATHABLE -> supplied ? SealDetectorReading.Supply.SUPPLIED : SealDetectorReading.Supply.NOT_KNOWN_SUPPLIED;
            case VACUUM -> SealDetectorReading.Supply.NOT_KNOWN_SUPPLIED;
            case PENDING -> SealDetectorReading.Supply.PENDING;
        };
    }

    boolean owns(MinecraftServer server) { Binding active = binding; return active != null && active.server() == server; }
    @Override public void close() { binding = null; }

    private record Binding(MinecraftServer server, AtmosphereManager manager, AtmosphereBoundaryCatalog boundaries) { }
}
