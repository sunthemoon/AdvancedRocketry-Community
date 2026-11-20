package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.PipeSealBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressBlock;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** F3: native chunk save/unload/read, not a blockstate-only or BE-only codec round trip. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SimpleDeviceRestartGameTests {
    private SimpleDeviceRestartGameTests() { }

    // F3: persisted POWERED is not another rising edge; native item entities survive the same reload.
    @GameTest(template = "empty", batch = "w416_f3_press_reload", timeoutTicks = 600)
    public static void f3PressChunkReloadDoesNotInventAPoweredEdgeOrOutput(GameTestHelper helper) {
        UnlitTorchFixture f = fixture(helper, 0);
        BlockPos press = f.origin, target = press.below(), power = press.east();
        int[] phase = {0}, ticks = {0};
        Reload reload = new Reload(f);
        f.run(() -> {
            f.set(press.below(2), Blocks.OBSIDIAN.defaultBlockState());
            f.set(target, Blocks.IRON_BLOCK.defaultBlockState());
            f.set(press, MaterialContent.SMALL_PLATE_PRESS.get().defaultBlockState());
            f.set(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
        }, () -> {
            switch (phase[0]) {
                case 0 -> {
                    plates(helper, f, 4);
                    helper.assertTrue(f.level.getBlockState(press).getValue(SmallPlatePressBlock.POWERED),
                            "Press did not retain the first powered edge");
                    f.set(target, Blocks.IRON_BLOCK.defaultBlockState());
                    reload.release(); phase[0]++;
                }
                case 1 -> {
                    if (!reload.ready()) { return false; }
                    helper.assertTrue(f.level.getBlockState(press).getValue(SmallPlatePressBlock.POWERED),
                            "Chunk reload lost POWERED");
                    phase[0]++;
                }
                case 2 -> {
                    helper.assertTrue(f.level.getBlockState(target).is(Blocks.IRON_BLOCK),
                            "Reload or a held signal invented a rising edge");
                    plates(helper, f, 4);
                    if (++ticks[0] < 10) { return false; }
                    f.set(power, Blocks.AIR.defaultBlockState());
                    f.set(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    phase[0]++;
                }
                case 3 -> { plates(helper, f, 8); return true; }
                default -> throw new IllegalStateException("Unknown press phase");
            }
            return false;
        }, () -> helper.assertTrue(f.level.isEmptyBlock(target), "A fresh rising edge did not consume one input"));
    }

    // F3: both saved door halves must agree before and after the first post-reload power change.
    @GameTest(template = "empty", batch = "w416_f3_door_reload", timeoutTicks = 600)
    public static void f3DoorChunkReloadKeepsBothHalvesOpenAndPoweredTogether(GameTestHelper helper) {
        UnlitTorchFixture f = fixture(helper, 1);
        BlockPos lower = f.origin;
        BlockState bottom = ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState top = bottom.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        int[] phase = {0}; Reload reload = new Reload(f);
        f.run(() -> {
            f.set(lower.below(), Blocks.IRON_BLOCK.defaultBlockState());
            f.set(lower, bottom); f.set(lower.above(), top);
            f.set(lower.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            door(helper, f, true);
        }, () -> {
            if (phase[0] == 0) { reload.release(); phase[0]++; return false; }
            if (!reload.ready()) { return false; }
            door(helper, f, true);
            f.set(lower.east(), Blocks.AIR.defaultBlockState());
            door(helper, f, false);
            return true;
        }, () -> helper.assertTrue(f.level.getBlockState(lower).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && f.level.getBlockState(lower.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER,
                "Reload changed a door half identity"));
    }

    // F3: unloading revokes a retained forwarding object, while the deserialized seal obtains a new lease.
    @GameTest(template = "empty", batch = "w416_f3_seal_reload", timeoutTicks = 600)
    public static void f3SealChunkReloadRevokesOldHandleAndRebuildsForwardingLease(GameTestHelper helper) {
        UnlitTorchFixture f = fixture(helper, 2);
        BlockPos sealPos = f.origin, chestPos = sealPos.west();
        PipeSealBlockEntity[] old = {null}; IItemHandler[] stale = {null};
        int[] phase = {0}; Reload reload = new Reload(f);
        f.run(() -> {
            f.set(chestPos, Blocks.CHEST.defaultBlockState());
            f.set(sealPos, ModBlocks.PIPE_SEAL.get().defaultBlockState());
            old[0] = (PipeSealBlockEntity) f.level.getBlockEntity(sealPos);
            stale[0] = port(old[0]);
            helper.assertTrue(stale[0].insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(),
                    "Initial forwarding lease did not insert");
        }, () -> {
            if (phase[0] == 0) { reload.release(); phase[0]++; return false; }
            if (!reload.ready()) { return false; }
            PipeSealBlockEntity fresh = (PipeSealBlockEntity) f.level.getBlockEntity(sealPos);
            helper.assertTrue(old[0].isRemoved() && fresh != old[0], "Seal was not deserialized anew");
            ItemStack offer = new ItemStack(Items.IRON_INGOT, 2);
            helper.assertTrue(stale[0].getSlots() == 0 && stale[0].insertItem(0, offer, false) == offer
                    && stale[0].extractItem(0, 1, false).isEmpty(), "Stale seal handle still forwards");
            var chest = (ChestBlockEntity) f.level.getBlockEntity(chestPos);
            helper.assertTrue(chest.getItem(0).is(Items.IRON_INGOT) && chest.getItem(0).getCount() == 3,
                    "Reload or the stale lease changed saved inventory");
            IItemHandler live = port(fresh);
            helper.assertTrue(live.insertItem(0, offer, false).isEmpty()
                    && live.extractItem(0, 1, false).getCount() == 1 && chest.getItem(0).getCount() == 4,
                    "Fresh seal lease failed exact insertion/extraction");
            return true;
        }, () -> { });
    }

    private static IItemHandler port(PipeSealBlockEntity seal) {
        return seal.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(IllegalStateException::new);
    }

    private static void plates(GameTestHelper helper, UnlitTorchFixture f, int count) {
        List<ItemEntity> drops = f.newDrops();
        helper.assertTrue(drops.stream().allMatch(item -> item.getItem().is(MaterialContent.item("iron_plate")))
                && drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == count,
                "Expected exactly " + count + " plates, got " + drops.stream().map(ItemEntity::getItem).toList());
    }

    private static void door(GameTestHelper helper, UnlitTorchFixture f, boolean powered) {
        BlockState lower = f.level.getBlockState(f.origin), upper = f.level.getBlockState(f.origin.above());
        for (BlockState state : List.of(lower, upper)) {
            helper.assertTrue(state.is(ModBlocks.AIRLOCK_DOOR.get()) && state.getValue(DoorBlock.POWERED) == powered
                    && state.getValue(DoorBlock.OPEN) == powered, "Door halves disagree on open/powered state");
        }
        helper.assertTrue(lower.getValue(DoorBlock.FACING) == upper.getValue(DoorBlock.FACING)
                && lower.getValue(DoorBlock.HINGE) == upper.getValue(DoorBlock.HINGE), "Door geometry changed");
    }

    private static UnlitTorchFixture fixture(GameTestHelper helper, int index) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(12_000 + index * 16, 12_000);
        return new UnlitTorchFixture(helper, level, new BlockPos(chunk.getMinBlockX() + 7, 180, chunk.getMinBlockZ() + 7),
                new AABB(chunk.getMinBlockX(), 176, chunk.getMinBlockZ(), chunk.getMaxBlockX() + 1, 186,
                        chunk.getMaxBlockZ() + 1), true);
    }

    /** Releases only the fixture force, waits for vanilla save/removal, then reads that chunk back from disk. */
    private static final class Reload {
        private final UnlitTorchFixture f;
        private final ChunkPos chunk;
        private final GameTestTickPacer pacer;
        private Object before;
        private boolean unloaded;
        private boolean read;
        Reload(UnlitTorchFixture f) {
            this.f = f; chunk = new ChunkPos(f.origin); pacer = new GameTestTickPacer(f.level.getServer());
            Consumer<ChunkEvent.Unload> listener = event -> {
                if (event.getLevel() == f.level && event.getChunk() == before) { unloaded = true; }
            };
            MinecraftForge.EVENT_BUS.addListener(listener);
            f.onClose(() -> MinecraftForge.EVENT_BUS.unregister(listener));
        }
        void release() {
            before = f.level.getChunkAt(f.origin);
            f.releaseForce();
        }
        boolean ready() {
            if (!read) {
                GameTestChunkUnloadPump.drainReady(f.level);
                // A chunk stops being FULL before its pending unload callback has removed/saved the old instance.
                if (!unloaded || f.level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
                    pacer.pace(); return false;
                }
                f.level.setChunkForced(chunk.x, chunk.z, true);
                if (f.level.getChunkAt(f.origin) == before) { throw new GameTestAssertException("Chunk instance did not change"); }
                read = true;
            }
            if (!f.level.areEntitiesLoaded(chunk.toLong())) { pacer.pace(); return false; }
            return true;
        }
    }
}
