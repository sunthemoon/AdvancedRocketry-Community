package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Bounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Cell;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Hazard;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Snapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Support;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Window;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.LogoutExitSafety;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * W312F (R375): the logout proof's loaded state. Bounds and column checks are the shared adapter's; block states come
 * from one region read. Only two rules differ from the bay exit selector's world, and only here: this mod's listed
 * structural blocks are ordinary blocks, and neither the rider's own rocket nor another player blocks the exit, because
 * neither is a collision surface. Mobs and other collidable entities still do.
 */
final class RocketLogoutExitWorld implements LogoutExitSafety.Region {
    /** This mod's full-cube, non-hazard structural blocks; their real collision shape still decides support. */
    static final List<Supplier<Block>> STRUCTURAL = List.of(ModBlocks.ROCKET_ASSEMBLER::get, ModBlocks.LANDING_PAD::get,
            ModBlocks.MACHINE_CASING::get);
    private final ServerPlayer player;
    private final RocketEntity rocket;
    private final ServerLevel level;
    private final BayPassengerExitWorld world;
    private final BayExitBudget.Probe probe;
    private Window region;
    private BlockState[] states;

    RocketLogoutExitWorld(ServerPlayer player, RocketEntity rocket, BayExitBudget.Probe probe) {
        this.player = player; this.rocket = rocket; this.probe = probe;
        level = player.serverLevel(); world = new BayPassengerExitWorld(player);
    }

    static boolean structural(BlockState state) {
        for (var block : STRUCTURAL) { if (state.is(block.get())) { return true; } }
        return false;
    }
    static Hazard hazard(BlockState state) { return structural(state) ? Hazard.NONE : BayPassengerExitWorld.hazard(state); }

    @Override public String level() { return world.level(); }
    @Override public Bounds bounds() { return world.bounds(); }
    @Override public boolean loadedColumn(int x, int z) { return world.loadedColumn(x, z); }

    /** One raw read per cell, through getChunkNow only, of the window that the shared probe has just paid for. */
    @Override public boolean read(Window window) {
        var read = new BlockState[volume(window)];
        for (int x = window.minX(); x < window.maxX(); x++) {
            for (int z = window.minZ(); z < window.maxZ(); z++) {
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) { return false; }
                for (int y = window.minY(); y < window.maxY(); y++) {
                    read[index(window, x, y, z)] = chunk.getBlockState(new BlockPos(x, y, z));
                }
            }
        }
        region = window; states = read;
        return true;
    }

    /** Cells for this box from the region alone; the same per-cell rules as the shared adapter, minus its diagnostics. */
    @Override public Snapshot readWindow(Box box, Window window) {
        if (region == null || window.minX() < region.minX() || window.minY() < region.minY() || window.minZ() < region.minZ()
                || window.maxX() > region.maxX() || window.maxY() > region.maxY() || window.maxZ() > region.maxZ()) { return null; }
        var view = new View();
        var cells = new Cell[volume(window)];
        var body = Shapes.create(BayPassengerExitWorld.aabb(box).deflate(1e-7));
        try {
            for (int x = window.minX(); x < window.maxX(); x++) {
                for (int y = window.minY(); y < window.maxY(); y++) {
                    for (int z = window.minZ(); z < window.maxZ(); z++) {
                        var pos = new BlockPos(x, y, z);
                        var state = view.getBlockState(pos);
                        Hazard hazard = hazard(state);
                        if (hazard == Hazard.UNKNOWN) {
                            cells[index(window, x, y, z)] = new Cell(true, Support.UNKNOWN, Hazard.UNKNOWN, false);
                            continue;
                        }
                        var shape = state.getCollisionShape(view, pos, CollisionContext.of(player));
                        boolean collision = Shapes.joinIsNotEmpty(body, shape.move(x, y, z), BooleanOp.AND);
                        Support support = BayPassengerExitWorld.support(state, shape);
                        if (Math.abs(box.minY() - (y + 1.0)) > .01) { support = Support.NONE; }
                        cells[index(window, x, y, z)] = new Cell(collision, support, hazard, !state.getFluidState().isEmpty());
                    }
                }
            }
        } catch (Unavailable | RuntimeException uncertain) {
            // A shape that needs a Level, a block entity or a block outside the region is unproved, not air.
            return null;
        }
        return (x, y, z) -> window.contains(x, y, z) ? cells[index(window, x, y, z)] : null;
    }

    /** The shared probe pays for every visited entity, whichever proof asks. */
    @Override public boolean entitiesClear(Box box, BayExitBudget.Probe ignored) {
        var aabb = BayPassengerExitWorld.aabb(box);
        boolean[] clear = {true};
        level.getEntities().get(EntityTypeTest.forClass(Entity.class), aabb, entity -> {
            if (!probe.visit()) { clear[0] = false; return Continuation.ABORT; }
            if (entity == player || entity == rocket || entity instanceof Player || entity.isRemoved()) { return Continuation.CONTINUE; }
            if ((entity instanceof LivingEntity || entity instanceof RocketEntity || entity.canBeCollidedWith())
                    && entity.getBoundingBox().intersects(aabb)) {
                clear[0] = false; return Continuation.ABORT;
            }
            return Continuation.CONTINUE;
        });
        return clear[0] && !probe.incomplete();
    }

    private static int volume(Window w) { return (w.maxX() - w.minX()) * (w.maxY() - w.minY()) * (w.maxZ() - w.minZ()); }
    private static int index(Window w, int x, int y, int z) {
        return ((x - w.minX()) * (w.maxY() - w.minY()) + y - w.minY()) * (w.maxZ() - w.minZ()) + z - w.minZ();
    }

    private static final class Unavailable extends Error {
        Unavailable() { super(null, null, false, false); }
    }

    private final class View implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            if (!region.contains(pos.getX(), pos.getY(), pos.getZ())) { throw new Unavailable(); }
            return states[index(region, pos.getX(), pos.getY(), pos.getZ())];
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { throw new Unavailable(); }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
