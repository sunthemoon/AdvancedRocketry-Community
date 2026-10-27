package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Independent provider with real native automation; only fixture setup crosses the test reflection bridge. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedProviderAutomationGameTests {
    private static final String ACCESS = "io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedProductionTestAccess";

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152_provider_automation", timeoutTicks = 400)
    public static void retainedProviderDeniesTickingAutomationAndOnlyPublishesAfterCheckedAbsence(GameTestHelper helper) {
        var level = helper.getLevel(); var at = helper.absolutePos(new BlockPos(4, 3, 4));
        var source = new FixtureContainerBlockEntity(at, AdapterTestMod.SEALED_CONTAINER.get().defaultBlockState());
        source.setItem(0, item(Items.DIAMOND, 7, "original"));
        var sourcePort = source.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        var carrier = stage(level, source); var held = carrier.saveWithoutMetadata();
        helper.assertTrue(source.isRemoved() && sourcePort.extractItem(0, 64, false).isEmpty(), "Removed capture source retained accessible items");
        level.setBlock(at.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST), 3);
        level.setBlock(at.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST), 3);
        ((Container) level.getBlockEntity(at.west())).setItem(0, item(Items.GOLD_INGOT, 3, "push"));
        level.setBlock(at.north(), Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH), 3);
        ((Container) level.getBlockEntity(at.north())).setItem(0, item(Items.EMERALD, 3, "dropper"));
        level.setBlock(at.north(2), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        var positions = List.of(at, at.below(), at.west(), at.north()); var recipient = new SimpleContainer(2);
        var bounds = new AABB(at.offset(-2, -2, -3), at.offset(3, 3, 5)); var began = level.getGameTime();
        var consumed = new boolean[1]; var done = new boolean[1]; var release = new long[] {-1};
        // W166 (R123): the fence vetoes the first proof, so the second runs after the 200-tick retry. Its store, force and
        // readback, and the journal writes, take wall-clock time, so each tick lasts a 20 TPS server tick.
        var pacer = new FixtureTickPacer(level.getServer());
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            pacer.pace();
            long age = level.getGameTime() - began; var live = level.getBlockEntity(at);
            if (age == 399) {
                com.mojang.logging.LogUtils.getLogger().info("W152_PROVIDER_AUTOMATION_PENDING {} live={} consumed={} releaseAge={}",
                        call("diagnostic", carrier), live == null ? "none" : live.getClass().getSimpleName(),
                        consumed[0], release[0] < 0 ? -1 : level.getGameTime() - release[0]);
            }
            if (age < 60) {
                helper.assertTrue(live == carrier && held.equals(carrier.saveWithoutMetadata()), "Native automation changed retained provider C");
                for (int face = -1; face < Direction.values().length; face++) {
                    helper.assertTrue(!carrier.getCapability(ForgeCapabilities.ITEM_HANDLER, face < 0 ? null : Direction.values()[face]).isPresent(),
                            "Retained provider exposed an item capability");
                }
                if (age == 20) { carrier.invalidateCaps(); carrier.reviveCaps(); }
                helper.assertTrue(((Container) level.getBlockEntity(at.west())).getItem(0).getCount() == 3, "Hopper inserted into provider C");
                helper.assertTrue(count(level, positions, bounds, recipient, "original") == 0, "Provider C exposed original inventory");
                if (age == 59) {
                    helper.assertTrue(((Container) level.getBlockEntity(at.north())).getItem(0).getCount() == 2, "Powered dropper did not tick");
                    helper.assertTrue(((HopperBlockEntity) level.getBlockEntity(at.below())).getLastUpdateTime() >= level.getGameTime() - 1
                            && ((HopperBlockEntity) level.getBlockEntity(at.west())).getLastUpdateTime() >= level.getGameTime() - 1, "Pull/push hopper did not tick");
                }
            } else if (live instanceof FixtureContainerBlockEntity && !consumed[0]) {
                helper.assertTrue((boolean) call("checkedAbsent", carrier), "Provider publication preceded checked U absence");
                var port = live.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
                for (int slot = 0; slot < 2; slot++) {
                    var preview = port.extractItem(slot, 64, true); var actual = port.extractItem(slot, 64, false);
                    helper.assertTrue(ItemStack.matches(preview, actual), "Provider pipe extraction simulation changed resources"); recipient.setItem(slot, actual);
                }
                var foreign = item(Items.LAPIS_LAZULI, 3, "pipe");
                helper.assertTrue(port.insertItem(0, foreign.copy(), true).isEmpty() && port.insertItem(0, foreign.copy(), false).isEmpty(),
                        "Provider pipe insertion failed after publication"); consumed[0] = true; release[0] = level.getGameTime();
            }
            if (age == 60) { call("releaseHeld", carrier); }
            helper.assertTrue(count(level, positions, bounds, recipient, "push") == 3
                    && count(level, positions, bounds, recipient, "dropper") == 3, "Provider automation lost foreign input or fallback drops");
            if (consumed[0]) {
                helper.assertTrue(count(level, positions, bounds, recipient, "original") == 7
                        && count(level, positions, bounds, recipient, "pipe") == 3, "Provider resource conservation failed");
                helper.assertTrue(carrier.isRemoved() && !(boolean) call("unseal", carrier)
                        && !carrier.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Stale provider C replayed/exposed contents");
                if (level.getGameTime() - release[0] >= 20) { done[0] = true; helper.succeed(); }
            }
        });
    }
    private static BlockEntity stage(ServerLevel level, BlockEntity source) {
        try { return (BlockEntity) Class.forName(ACCESS).getMethod("stageHeld", ServerLevel.class, BlockEntity.class,
                ResourceLocation.class, RocketBlockEntityAdapter.class).invoke(null, level, source, AdapterTestMod.SEALED_ADAPTER_ID, new FixtureInventoryAdapter()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static Object call(String method, BlockEntity carrier) {
        try { return Class.forName(ACCESS).getMethod(method, BlockEntity.class).invoke(null, carrier); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static int count(ServerLevel level, List<BlockPos> positions, AABB bounds, Container recipient, String label) {
        int total = count(recipient, label);
        for (var position : positions) { if (level.getBlockEntity(position) instanceof Container container) { total += count(container, label); } }
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) { total += count(entity.getItem(), label); } return total;
    }
    private static int count(Container container, String label) {
        int total = 0; for (int slot = 0; slot < container.getContainerSize(); slot++) { total += count(container.getItem(slot), label); } return total;
    }
    private static int count(ItemStack stack, String label) { return stack.hasTag() && label.equals(stack.getTag().getString("W152")) ? stack.getCount() : 0; }
    private static ItemStack item(net.minecraft.world.item.Item type, int count, String label) {
        var stack = new ItemStack(type, count); stack.getOrCreateTag().putString("W152", label); return stack;
    }
    private SealedProviderAutomationGameTests() { }
}
