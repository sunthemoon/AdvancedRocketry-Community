package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * W68 (W62 S5) test-only automation rig: vanilla hoppers and droppers placed beside a machine, a bounded sub-tick
 * loop for them, item counting, and cleanup of every block, container and loose item it placed. A sub-tick calls the
 * same static hopper ticker and dropper block tick that the server calls, so one game tick can run several transfers.
 */
final class AutomationRig implements AutoCloseable {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("arce_gametest_w68_automation",
            Comparator.comparingLong(ChunkPos::toLong), 60);
    final GameTestHelper helper;
    final ServerLevel level;
    private final Map<BlockPos, Boolean> placed = new LinkedHashMap<>();
    private final Set<ChunkPos> tickets = new LinkedHashSet<>();
    private final List<HopperBlockEntity> hoppers = new ArrayList<>();
    private final List<Runnable> beforeClose = new ArrayList<>();
    private boolean closed;

    AutomationRig(GameTestHelper helper) { this.helper = helper; level = helper.getLevel(); }

    /** Keeps the chunk of {@code position} loaded and ticking until close; it is already loaded around a test. */
    void hold(BlockPos position) {
        ChunkPos chunk = new ChunkPos(position);
        if (tickets.add(chunk)) { level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk); level.getChunk(chunk.x, chunk.z); }
    }
    /**
     * GameTests run deep underground; vanilla clears only a small box around a template. This opens an air room over
     * [min, max] inside a one-block stone shell, so no cave fluid or terrain touches the fixture. The room stays air.
     */
    void room(BlockPos min, BlockPos max) {
        for (BlockPos position : BlockPos.betweenClosed(min.offset(-1, -1, -1), max.offset(1, 1, 1))) {
            hold(position);
            boolean shell = position.getX() < min.getX() || position.getX() > max.getX() || position.getY() < min.getY()
                    || position.getY() > max.getY() || position.getZ() < min.getZ() || position.getZ() > max.getZ();
            level.setBlock(position, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
    void place(BlockPos position, BlockState state) {
        hold(position); placed.putIfAbsent(position.immutable(), true); level.setBlock(position, state, Block.UPDATE_ALL);
    }
    /** Runs before any rig block is removed, in registration order (for example to release a controller first). */
    void beforeClose(Runnable action) { beforeClose.add(action); }
    boolean free(BlockPos position) { return !placed.containsKey(position) && level.getBlockState(position).isAir(); }
    /** Neighbours of {@code target} that this rig has not used and that hold air, in Direction order. */
    List<Direction> freeSides(BlockPos target, Set<BlockPos> reserved) {
        var sides = new ArrayList<Direction>();
        for (Direction side : Direction.values()) {
            BlockPos next = target.relative(side);
            if (!reserved.contains(next) && free(next)) { sides.add(side); }
        }
        return sides;
    }

    /** A hopper at {@code position} that pushes into the block at {@code position.relative(facing)}. */
    HopperBlockEntity hopper(BlockPos position, Direction facing, List<ItemStack> contents) {
        if (facing == Direction.UP) { throw new IllegalArgumentException("A hopper cannot face up"); }
        place(position, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
        var hopper = (HopperBlockEntity) level.getBlockEntity(position);
        fill(hopper, contents); hoppers.add(hopper); return hopper;
    }
    DispenserBlockEntity dropper(BlockPos position, Direction facing, List<ItemStack> contents) {
        place(position, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, facing));
        var dropper = (DispenserBlockEntity) level.getBlockEntity(position);
        fill(dropper, contents); return dropper;
    }
    private static void fill(Container container, List<ItemStack> contents) {
        if (contents.size() > container.getContainerSize()) { throw new IllegalArgumentException("Too many stacks"); }
        for (int slot = 0; slot < contents.size(); slot++) { container.setItem(slot, contents.get(slot).copy()); }
    }
    /** One hopper cooldown step for every rig hopper, exactly as the server's block entity ticker runs it. */
    void tickHoppers() {
        for (var hopper : hoppers) {
            if (!hopper.isRemoved()) { HopperBlockEntity.pushItemsTick(level, hopper.getBlockPos(), hopper.getBlockState(), hopper); }
        }
    }
    /** One dropper activation, as its scheduled block tick after a redstone pulse runs it. */
    void pulse(DispenserBlockEntity dropper) {
        BlockPos position = dropper.getBlockPos(); level.getBlockState(position).tick(level, position, level.random);
    }

    static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) { total += container.getItem(slot).getCount(); }
        }
        return total;
    }
    static boolean empty(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) { if (!container.getItem(slot).isEmpty()) { return false; } }
        return true;
    }
    /** Loose items of {@code item} within {@code radius} blocks of {@code center}. */
    int loose(BlockPos center, int radius, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius)).stream()
                .filter(entity -> entity.isAlive() && entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    int looseStacks(BlockPos center, int radius) {
        return (int) level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius)).stream().filter(ItemEntity::isAlive).count();
    }

    /**
     * Registers the scenario's single per-tick callback at test start (WORKER-RULES: no scheduling from a callback).
     * {@code step} runs one game tick of work and returns true when the scenario is finished; failures and timeouts
     * close the rig first, so no fixture block outlives its test.
     */
    void drive(int timeoutTicks, BooleanSupplier step, Runnable verify) { drive(timeoutTicks, step, verify, () -> ""); }
    /** As above; {@code state} describes the scenario in the failure if it runs out of ticks. */
    void drive(int timeoutTicks, BooleanSupplier step, Runnable verify, Supplier<String> state) {
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) { return; }
            try {
                if (step.getAsBoolean()) { finished[0] = true; verify.run(); close(); helper.succeed(); }
                else if (helper.getTick() >= timeoutTicks - 3) {
                    finished[0] = true; throw new IllegalStateException("W68 scenario did not finish: " + state.get());
                }
            } catch (RuntimeException | Error failure) {
                finished[0] = true;
                try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        });
    }

    @Override public void close() {
        if (closed) { return; }
        closed = true;
        RuntimeException failed = null;
        for (Runnable action : beforeClose) {
            try { action.run(); } catch (RuntimeException error) { if (failed == null) { failed = error; } else { failed.addSuppressed(error); } }
        }
        var positions = new ArrayList<>(placed.keySet());
        for (int index = positions.size() - 1; index >= 0; index--) {
            BlockPos position = positions.get(index);
            if (level.getBlockEntity(position) instanceof Container container) { container.clearContent(); }
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos position : positions) {
            level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(2)).forEach(ItemEntity::discard);
        }
        tickets.forEach(chunk -> level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk));
        if (failed != null) { throw failed; }
    }
}
