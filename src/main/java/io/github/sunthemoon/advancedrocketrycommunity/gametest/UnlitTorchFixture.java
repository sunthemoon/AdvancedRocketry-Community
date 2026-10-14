package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.DeferredRoomBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchRoot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Owned torch fixture cells, an optionally forced Moon chunk and the drops they make; everything is restored on
 * success, failure or timeout. Waits run in one onEachTick and never schedule another GameTest callback.
 */
final class UnlitTorchFixture implements AutoCloseable, GameTestListener {
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    final ServerLevel level;
    final BlockPos origin;
    private final GameTestHelper helper;
    private final AABB bounds;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    private Set<UUID> oldDrops;
    private boolean closed;
    private boolean ownsForce;

    UnlitTorchFixture(GameTestHelper helper, ServerLevel level, BlockPos origin, AABB bounds, boolean pin) {
        this.helper = helper;
        this.level = level;
        this.origin = origin;
        this.bounds = bounds;
        try {
            if (pin) {
                ChunkPos chunk = new ChunkPos(origin);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    ownsForce = true;
                    level.setChunkForced(chunk.x, chunk.z, true);
                }
                level.getChunk(chunk.x, chunk.z); // One explicitly forced fixture chunk, loaded before any placement.
                // Torch changes wait until the 3x3 chunks around them are FULL (W33G). The force already gives these
                // eight a FULL level; load them now rather than leave every conversion to asynchronous loading.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.getChunk(chunk.x + dx, chunk.z + dz);
                    }
                }
            }
            oldDrops = currentDrops();
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(this);
        } catch (ReflectiveOperationException failure) {
            IllegalStateException primary = new IllegalStateException("Cannot install fixture timeout cleanup", failure);
            close(primary);
            throw primary;
        } catch (RuntimeException | Error failure) {
            close(failure);
            throw failure;
        }
    }

    /** Open Moon vacuum at y 180, in the one forced chunk below the test; every fixture cell stays in it. */
    static UnlitTorchFixture moon(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Configured Moon Level is absent");
        ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(8, 8, 8)));
        return new UnlitTorchFixture(helper, moon, new BlockPos(chunk.getMinBlockX() + 2, 180, chunk.getMinBlockZ() + 7),
                new AABB(chunk.getMinBlockX(), 176, chunk.getMinBlockZ(), chunk.getMaxBlockX() + 1, 186,
                        chunk.getMaxBlockZ() + 1), true);
    }

    /** Breathable Earth air inside the 16-block test structure. */
    static UnlitTorchFixture overworld(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 4, 8));
        return new UnlitTorchFixture(helper, helper.getLevel(), origin, new AABB(origin).inflate(7, 3, 7), false);
    }

    FakePlayer player(GameType mode) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "UnlitTorchFixture"));
        player.setGameMode(mode);
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        return player;
    }

    void set(BlockPos position, BlockState state) {
        BlockPos key = position.immutable();
        helper.assertTrue(bounds.contains(Vec3.atCenterOf(key)) && level.isInWorldBounds(key) && level.hasChunkAt(key),
                "Fixture cell is unloaded or outside the fixture");
        if (!before.containsKey(key)) {
            helper.assertTrue(level.getBlockEntity(key) == null, "Fixture cell already holds a block entity");
            helper.assertTrue(before.size() < 96, "Fixture exceeded its 96-cell budget");
            before.put(key, level.getBlockState(key));
        }
        level.setBlock(key, state, Block.UPDATE_ALL);
    }

    /** An unlit torch with the given root, or none, loaded like a saved block entity. */
    UnlitTorchBlockEntity unlit(BlockPos position, Direction facing, UnlitTorchRoot.Identity identity) {
        BlockState state = facing == null ? ModBlocks.UNLIT_TORCH.get().defaultBlockState()
                : ModBlocks.UNLIT_WALL_TORCH.get().defaultBlockState().setValue(WallTorchBlock.FACING, facing);
        set(position, state);
        UnlitTorchBlockEntity torch = (UnlitTorchBlockEntity) level.getBlockEntity(position);
        CompoundTag outer = new CompoundTag();
        if (identity != null) {
            outer.put(UnlitTorchRoot.KEY, UnlitTorchRoot.encode(identity));
        }
        torch.load(outer);
        return torch;
    }

    List<ItemEntity> newDrops() {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> !oldDrops.contains(entity.getUUID()));
        helper.assertTrue(drops.size() <= 32, "Fixture exceeded its 32-drop budget");
        return drops;
    }

    /** Vetoes only environmental changes at these fixture cells, with cleanup even on timeout. */
    Runnable vetoChanges(Set<BlockPos> cells, int[] cancellations) {
        Consumer<BlockEvent.EntityPlaceEvent> veto = event -> {
            if (event instanceof DeferredRoomBoundary && event.getLevel() == level && cells.contains(event.getPos())) {
                cancellations[0]++;
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, veto);
        Runnable release = () -> MinecraftForge.EVENT_BUS.unregister(veto);
        cleanup.add(release);
        return release;
    }

    /** Waits for the condition, then verifies and succeeds. */
    void await(BooleanSupplier ready, Runnable verify) {
        run(() -> { }, ready, verify);
    }

    /**
     * Item drops are entities, and a fixture chunk's entity section loads asynchronously after its blocks. Each tick
     * waits, paced to the server's tick length, until that section has loaded; then {@code start} runs once, and later
     * ticks wait for {@code ready} before {@code verify} runs and the test succeeds.
     */
    void run(Runnable start, BooleanSupplier ready, Runnable verify) {
        GameTestTickPacer pacer = new GameTestTickPacer(level.getServer());
        boolean[] started = {false};
        helper.onEachTick(() -> {
            if (closed) {
                return;
            }
            try {
                if (!started[0]) {
                    if (!level.areEntitiesLoaded(ChunkPos.asLong(origin.getX() >> 4, origin.getZ() >> 4))) {
                        pacer.pace();
                        return;
                    }
                    oldDrops = currentDrops();
                    started[0] = true;
                    start.run();
                    return;
                }
                if (!ready.getAsBoolean()) {
                    return;
                }
                verify.run();
                close();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close(failure);
                throw failure;
            }
        });
    }

    @Override
    public void close() {
        close(null);
    }

    private void close(Throwable primary) {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = primary;
        for (Runnable action : cleanup) {
            try { action.run(); }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
        }
        cleanup.clear();
        List<BlockPos> cells = new ArrayList<>(before.keySet());
        Collections.reverse(cells);
        for (BlockPos cell : cells) {
            try { level.setBlock(cell, before.get(cell), QUIET); }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
        }
        try {
            if (oldDrops != null) {
                level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> !oldDrops.contains(entity.getUUID())).forEach(ItemEntity::discard);
            }
        }
        catch (RuntimeException | Error next) { failure = combine(failure, next); }
        try {
            if (ownsForce) {
                ChunkPos chunk = new ChunkPos(origin);
                level.setChunkForced(chunk.x, chunk.z, false);
                ownsForce = false;
            }
        } catch (RuntimeException | Error next) { failure = combine(failure, next); }
        if (primary == null && failure instanceof RuntimeException next) { throw next; }
        if (primary == null && failure instanceof Error next) { throw next; }
    }

    private Set<UUID> currentDrops() {
        return level.getEntitiesOfClass(ItemEntity.class, bounds).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) { return next; }
        if (first != next) { first.addSuppressed(next); }
        return first;
    }

    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { close(); }
    @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
}
