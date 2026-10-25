package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Read-only bounded world adapter. Only the two recorded ticket columns may request loading. */
final class RocketRejoinWorld implements AutoCloseable {
    private static final TicketType<UUID> TICKET = TicketType.create("arce_passenger_rejoin", Comparator.<UUID>naturalOrder(), 200);
    private record Held(ServerLevel level, ChunkPos chunk, UUID token) { }
    private final MinecraftServer server;
    private final RocketRejoinTicketBudget budget = new RocketRejoinTicketBudget();
    private final Map<UUID, List<Held>> tickets = new HashMap<>();
    RocketRejoinWorld(MinecraftServer server) { this.server = server; }
    ServerLevel level(Batch batch, Target target) {
        return target == Target.OVERWORLD_SPAWN ? server.overworld()
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(batch.dimension())));
    }
    boolean ticket(RocketRejoinGuards.Session session, Batch batch) {
        for (Target target : Target.values()) {
            var level = level(batch, target); if (level == null) { continue; }
            var pos = target == Target.PAD ? new BlockPos(batch.pad().x(), batch.pad().y(), batch.pad().z()) : level.getSharedSpawnPos();
            var chunk = new ChunkPos(pos); var own = tickets.computeIfAbsent(session.player.getUUID(), ignored -> new ArrayList<>());
            if (own.stream().anyMatch(h -> h.level == level && h.chunk.equals(chunk))) { continue; }
            if (!budget.acquire(session.player.getUUID(), level.dimension().location() + ":" + chunk.toLong())) { return false; }
            // Region level 2 matches the existing flight adapter. Never refresh an acquired ticket.
            level.getChunkSource().addRegionTicket(TICKET, chunk, 2, session.token);
            own.add(new Held(level, chunk, session.token));
        }
        return true;
    }
    Attempt choose(ServerPlayer player, Batch batch) {
        var spawn = server.overworld().getSharedSpawnPos();
        for (var column : RocketRejoinCandidates.columns(batch.minimum(), batch.maximum(), spawn.getX(), spawn.getZ())) {
            var level = level(batch, column.target()); if (level == null) { continue; }
            var chunk = level.getChunkSource().getChunkNow(column.x() >> 4, column.z() >> 4);
            if (chunk == null) { continue; }
            int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.x() & 15, column.z() & 15) + 1;
            var pos = new Vec3(column.x() + 0.5, y, column.z() + 0.5);
            if (safe(player, level, pos)) { return new Attempt(UUID.randomUUID(), column.target(), pos.x, pos.y, pos.z); }
        }
        return null;
    }
    boolean safe(ServerPlayer player, Batch batch, Attempt attempt) {
        return safe(player, level(batch, attempt.target()), new Vec3(attempt.x(), attempt.y(), attempt.z()));
    }
    static boolean safe(ServerPlayer player, ServerLevel level, Vec3 pos) {
        if (level == null || !player.isAlive() || !Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) { return false; }
        // Our candidates are centered, integral-height columns. This also keeps vanilla's expanded
        // collision iterator inside the same 3-by-3-by-4 envelope for a restored attempt.
        if (pos.y != Math.floor(pos.y) || pos.x - Math.floor(pos.x) != 0.5 || pos.z - Math.floor(pos.z) != 0.5) { return false; }
        var feet = BlockPos.containing(pos); var box = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3);
        return RocketRejoinSafety.check(new RocketRejoinSafety.Probe() {
            public boolean bounds(int x, int y, int z) {
                return level.getWorldBorder().isWithinBounds(box) && y - 1 >= level.getMinBuildHeight() && y + 2 < level.getMaxBuildHeight();
            }
            public boolean loaded(int x, int z) { return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null; }
            public boolean harmless(int x, int y, int z) {
                var state = level.getBlockState(new BlockPos(x, y, z)); return state.getFluidState().isEmpty() && knownHarmless(state.getBlock());
            }
            public boolean supported(int x, int y, int z) {
                var at = new BlockPos(x, y, z); return level.getBlockState(at).isFaceSturdy(level, at, Direction.UP);
            }
            public boolean clear() { return level.noCollision(player, box); }
            public boolean survives(int x, int y, int z) { return AtmosphereRuntime.rejoinSurvivable(level, new BlockPos(x, y, z), player); }
        }, feet.getX(), feet.getY(), feet.getZ());
    }
    /** Unknown/modded collision callbacks are not assumed harmless. Exact classes exclude damaging subclasses. */
    private static boolean knownHarmless(Block block) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (!id.getNamespace().equals("minecraft")) { return false; }
        var type = block.getClass();
        return type == Block.class || type == AirBlock.class || type == GrassBlock.class || type == RotatedPillarBlock.class
                || type == StairBlock.class || type == SlabBlock.class || type == GlassBlock.class || type == TintedGlassBlock.class
                || type == TallGrassBlock.class || type == FlowerBlock.class && block != Blocks.WITHER_ROSE;
    }
    void release(UUID player) {
        var own = tickets.remove(player); if (own != null) { own.forEach(h -> h.level.getChunkSource().removeRegionTicket(TICKET, h.chunk, 2, h.token)); }
        budget.release(player);
    }
    @Override public void close() { List.copyOf(tickets.keySet()).forEach(this::release); }
}
