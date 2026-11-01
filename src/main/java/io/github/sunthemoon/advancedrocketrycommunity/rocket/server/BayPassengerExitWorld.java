package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** All world observations are confined to one fully loaded, precharged immutable window. */
final class BayPassengerExitWorld implements BayExitSafety.LoadedState {
    private final ServerLevel level;
    private final ServerPlayer player;
    int reads, columnChecks, entityVisits;
    boolean contact, inspected;
    String firstContact;

    BayPassengerExitWorld(ServerPlayer player) { this.player = player; level = player.serverLevel(); }
    @Override public String level() { return level.dimension().location().toString(); }
    @Override public Bounds bounds() {
        var border = level.getWorldBorder();
        return new Bounds(level.getMinBuildHeight(), level.getMaxBuildHeight(), border.getMinX(), border.getMaxX(),
                border.getMinZ(), border.getMaxZ());
    }
    @Override public boolean loadedColumn(int x, int z) {
        columnChecks++;
        return level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null
                && level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x, z))
                && level.isPositionEntityTicking(new BlockPos(x * 16, level.getMinBuildHeight(), z * 16));
    }
    static AABB aabb(Box box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
    @Override public Snapshot readWindow(Box box, Window window) {
        contact = false; inspected = false; firstContact = null;
        var view = new View(window, level.getMinBuildHeight(), level.getHeight());
        for (int x = window.minX(); x < window.maxX(); x++) {
            for (int z = window.minZ(); z < window.maxZ(); z++) {
                var chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                if (chunk == null) { return null; }
                for (int y = window.minY(); y < window.maxY(); y++) {
                    reads++;
                    view.states[view.index(x, y, z)] = chunk.getBlockState(new BlockPos(x, y, z));
                }
            }
        }
        var cells = new Cell[view.states.length];
        var body = Shapes.create(aabb(box).deflate(1e-7));
        try {
            for (int x = window.minX(); x < window.maxX(); x++) {
                for (int y = window.minY(); y < window.maxY(); y++) {
                    for (int z = window.minZ(); z < window.maxZ(); z++) {
                        var pos = new BlockPos(x, y, z);
                        var state = view.getBlockState(pos);
                        if (hazard(state) == Hazard.UNKNOWN) {
                            contact = true;
                            if (firstContact == null) { firstContact = pos + " " + state + " unknown"; }
                            cells[view.index(x, y, z)] = new Cell(true, Support.UNKNOWN, Hazard.UNKNOWN, false);
                            continue;
                        }
                        var shape = state.getCollisionShape(view, pos, CollisionContext.of(player));
                        boolean collision = Shapes.joinIsNotEmpty(body, shape.move(x, y, z), BooleanOp.AND);
                        boolean footprint = x < box.maxX() && x + 1 > box.minX() && z < box.maxZ() && z + 1 > box.minZ();
                        boolean touching = collision || footprint && !shape.isEmpty() && Math.abs(box.minY() - y - shape.max(Direction.Axis.Y)) <= .01
                                || hazard(state) != Hazard.NONE || !state.getFluidState().isEmpty();
                        contact |= touching;
                        if (touching && firstContact == null) { firstContact = pos + " " + state + " collision=" + collision + " fluid=" + !state.getFluidState().isEmpty(); }
                        Support support = support(state, shape);
                        if (Math.abs(box.minY() - (y + 1.0)) > .01) { support = Support.NONE; }
                        cells[view.index(x, y, z)] = new Cell(collision, support, hazard(state), !state.getFluidState().isEmpty());
                    }
                }
            }
        } catch (Unavailable | RuntimeException uncertain) {
            // A shape needing a Level, a block entity or an out-of-window neighbor is unproved, not air.
            return null;
        }
        inspected = true;
        return (x, y, z) -> window.contains(x, y, z) ? cells[view.index(x, y, z)] : null;
    }
    static Support support(BlockState state, VoxelShape shape) {
        if (state.getBlock() instanceof FallingBlock) { return Support.FALLING; }
        if (state.is(Blocks.MOVING_PISTON)) { return Support.MOVING_PISTON; }
        if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) { return Support.OPEN_TRAPDOOR; }
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) == 1 && Block.isFaceFull(shape, Direction.UP)
                ? Support.FULL_STABLE : Support.PARTIAL;
    }
    static Hazard hazard(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")
                || !state.getBlock().getClass().getPackageName().equals("net.minecraft.world.level.block")) { return Hazard.UNKNOWN; }
        if (state.is(net.minecraft.tags.BlockTags.CLIMBABLE) || state.is(Blocks.LADDER) || state.is(Blocks.VINE)
                || state.is(Blocks.SCAFFOLDING) || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT)) { return Hazard.UNKNOWN; }
        if (state.is(Blocks.FIRE)) { return Hazard.FIRE; }
        if (state.is(Blocks.SOUL_FIRE)) { return Hazard.SOUL_FIRE; }
        if (state.getBlock() instanceof CampfireBlock) { return Hazard.CAMPFIRE; }
        if (state.is(Blocks.LAVA)) { return Hazard.LAVA; }
        if (state.is(Blocks.CACTUS)) { return Hazard.CACTUS; }
        if (state.is(Blocks.MAGMA_BLOCK)) { return Hazard.MAGMA; }
        if (state.is(Blocks.SWEET_BERRY_BUSH)) { return Hazard.BERRY_BUSH; }
        if (state.is(Blocks.POWDER_SNOW)) { return Hazard.POWDER_SNOW; }
        if (state.is(Blocks.WITHER_ROSE)) { return Hazard.WITHER_ROSE; }
        if (state.is(Blocks.POINTED_DRIPSTONE)) { return Hazard.POINTED_DRIPSTONE; }
        if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) { return Hazard.PORTAL; }
        if (state.is(Blocks.END_GATEWAY)) { return Hazard.GATEWAY; }
        return Hazard.NONE;
    }
    @Override public boolean entitiesClear(Box box, BayExitBudget.Probe probe) {
        boolean[] clear = {true};
        level.getEntities().get(EntityTypeTest.forClass(Entity.class), aabb(box), entity -> {
            if (!probe.visit()) { clear[0] = false; return Continuation.ABORT; }
            entityVisits++;
            if (entity != player && !entity.isRemoved() && (entity instanceof net.minecraft.world.entity.LivingEntity
                    || entity instanceof io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity
                    || entity.canBeCollidedWith()) && entity.getBoundingBox().intersects(aabb(box))) {
                clear[0] = false; return Continuation.ABORT;
            }
            return Continuation.CONTINUE;
        });
        return clear[0] && !probe.incomplete();
    }
    private static final class Unavailable extends Error { }
    private static final class View implements BlockGetter {
        final Window window;
        final BlockState[] states;
        final int min, height;
        View(Window window, int min, int height) {
            this.window = window; this.min = min; this.height = height;
            states = new BlockState[(window.maxX() - window.minX()) * (window.maxY() - window.minY())
                    * (window.maxZ() - window.minZ())];
        }
        int index(int x, int y, int z) {
            return ((x - window.minX()) * (window.maxY() - window.minY()) + y - window.minY())
                    * (window.maxZ() - window.minZ()) + z - window.minZ();
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            if (!window.contains(pos.getX(), pos.getY(), pos.getZ())) { throw new Unavailable(); }
            return states[index(pos.getX(), pos.getY(), pos.getZ())];
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { throw new Unavailable(); }
        @Override public int getHeight() { return height; }
        @Override public int getMinBuildHeight() { return min; }
    }
}
