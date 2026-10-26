package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedAutomationGameTests {
    @GameTest(template = "rocket_test", batch = "w152_automation", timeoutTicks = 400)
    public static void tickingHoppersDropperAndCapabilityConsumerCannotAccessRetainedContainers(GameTestHelper helper) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(2, 3, 2));
        var targets = List.of(origin, origin.east(4)); var fence = origin.south(3);
        var blocks = new ArrayList<RocketBlock>(); var adapter = new VanillaContainerRocketAdapter();
        for (int index = 0; index < targets.size(); index++) {
            var source = new ChestBlockEntity(targets.get(index), Blocks.CHEST.defaultBlockState());
            source.setItem(0, item(Items.DIAMOND, 7, "original"));
            blocks.add(new RocketBlock(new RocketPosition(index * 4, 0, 0),
                    RocketBlockStateAdapter.capture((index == 0 ? Blocks.CHEST : Blocks.BARREL).defaultBlockState()), adapter.capture(source)));
        }
        blocks.add(new RocketBlock(new RocketPosition(0, 0, 3), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState())));
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(origin), blocks,
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(3, 300, 1000, 1000, 1, 1, 1, 2), level.getGameTime());
        var id = UUID.randomUUID(); var entity = UUID.randomUUID();
        var data = RocketTransactionSavedData.get(level.getServer());
        data.journalFor(snapshot, UUID.randomUUID()).write(new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY,
                RocketTransactionPhase.RESTORED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 2, entity));
        data.checkpoint(level.getServer());
        // A foreign third member holds native capacity proof without disabling saving or ticking unrelated blocks.
        level.setBlock(fence, Blocks.BEDROCK.defaultBlockState(), 2);
        var carriers = new ArrayList<SealedRocketBlockEntity>(); var roots = new ArrayList<CompoundTag>();
        var containers = new ArrayList<BlockPos>(); var recipient = new SimpleContainer(2);
        for (int index = 0; index < targets.size(); index++) {
            var at = targets.get(index);
            var block = snapshot.blocks().stream().filter(value -> value.position().equals(new RocketPosition(at.getX() - origin.getX(),
                    at.getY() - origin.getY(), at.getZ() - origin.getZ()))).findFirst().orElseThrow();
            int memberIndex = snapshot.blocks().indexOf(block);
            var state = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState(); level.setBlock(at, state, 2);
            var carrier = new SealedRocketBlockEntity(at, state);
            carrier.initialize(SealedRocketRoot.stage(new SealedRocketMember(id, snapshot.snapshotId(), entity, snapshot.contentHash(),
                    snapshot.sourceDimension(), position(at), memberIndex, block.state(), block.blockEntityPayload().orElseThrow())));
            level.setBlockEntity(carrier); carrier.setChanged(); carriers.add(carrier); roots.add(carrier.saveWithoutMetadata());
            level.setBlock(at.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST), 3);
            level.setBlock(at.west(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST), 3);
            ((Container) level.getBlockEntity(at.west())).setItem(0, item(Items.GOLD_INGOT, 3, "push"));
            level.setBlock(at.north(), Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH), 3);
            ((Container) level.getBlockEntity(at.north())).setItem(0, item(Items.EMERALD, 3, "dropper"));
            level.setBlock(at.north(2), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            containers.addAll(List.of(at, at.below(), at.west(), at.north()));
        }
        var began = level.getGameTime(); var released = new long[] {-1}; var done = new boolean[1];
        var pipeForeign = new ItemStack[] {item(Items.LAPIS_LAZULI, 3, "pipe"), item(Items.LAPIS_LAZULI, 3, "pipe")};
        var pipeUsed = new boolean[2]; var bounds = new AABB(origin.offset(-2, -3, -3), origin.offset(7, 3, 5));
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            long age = level.getGameTime() - began;
            for (int index = 0; index < targets.size(); index++) {
                var at = targets.get(index); var live = level.getBlockEntity(at);
                if (age < 60) {
                    helper.assertTrue(live == carriers.get(index) && roots.get(index).equals(live.saveWithoutMetadata()), "Automation changed retained C");
                    for (int face = -1; face < Direction.values().length; face++) {
                        helper.assertTrue(!live.getCapability(ForgeCapabilities.ITEM_HANDLER, face < 0 ? null : Direction.values()[face]).isPresent(),
                                "Retained C exposed a side/null item handler");
                    }
                    helper.assertTrue(((Container) level.getBlockEntity(at.west())).getItem(0).getCount() == 3, "Hopper inserted into C");
                    helper.assertTrue(count(level, containers, bounds, recipient, "original") == 0, "Hopper/dropper/pipe extracted from C");
                    if (age == 59) {
                        helper.assertTrue(((Container) level.getBlockEntity(at.north())).getItem(0).getCount() == 2, "Powered dropper did not tick");
                        helper.assertTrue(((net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(at.west()))
                                .getLastUpdateTime() >= level.getGameTime() - 1, "Push hopper did not tick");
                    }
                } else if (live instanceof Container && !pipeUsed[index]) {
                    var handler = live.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
                    var preview = handler.insertItem(2, pipeForeign[index], true);
                    var remainder = handler.insertItem(2, pipeForeign[index], false);
                    helper.assertTrue(preview.getCount() == remainder.getCount(), "Simulated insertion changed resources");
                    helper.assertTrue(remainder.isEmpty(), "Published target refused pipe input into its empty slot");
                    pipeForeign[index] = remainder;
                    var simulated = handler.extractItem(0, 64, true); var extracted = handler.extractItem(0, 64, false);
                    helper.assertTrue(ItemStack.matches(simulated, extracted), "Simulated extraction changed resources");
                    recipient.setItem(index, extracted); pipeUsed[index] = true;
                }
            }
            if (age == 60) { level.setBlock(fence, Blocks.AIR.defaultBlockState(), 3); }
            int foreign = count(level, containers, bounds, recipient, "push") + count(level, containers, bounds, recipient, "dropper")
                    + count(level, containers, bounds, recipient, "pipe");
            for (var supply : pipeForeign) { foreign += supply.getCount(); }
            helper.assertTrue(foreign == 18, "Original/foreign automation conservation failed");
            if (pipeUsed[0] && pipeUsed[1]) {
                if (released[0] < 0) { released[0] = level.getGameTime(); }
                helper.assertTrue(data.acknowledgedAbsent(id, snapshot.snapshotId(), entity), "Native inventory preceded checked U absence");
                helper.assertTrue(count(level, containers, bounds, recipient, "original") == 14, "Published original inventory was lost or replayed");
                helper.assertTrue(carriers.stream().allMatch(carrier -> carrier.isRemoved()
                        && !carrier.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()), "Cached C reference gained access");
                if (level.getGameTime() - released[0] >= 20) { done[0] = true; helper.succeed(); }
            }
        });
    }
    private static int count(net.minecraft.server.level.ServerLevel level, List<BlockPos> positions, AABB bounds, Container recipient, String label) {
        int amount = 0;
        for (var position : positions) { if (level.getBlockEntity(position) instanceof Container container) { amount += count(container, label); } }
        amount += count(recipient, label);
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) { amount += count(entity.getItem(), label); }
        return amount;
    }
    private static int count(Container container, String label) {
        int amount = 0; for (int slot = 0; slot < container.getContainerSize(); slot++) { amount += count(container.getItem(slot), label); } return amount;
    }
    private static int count(ItemStack item, String label) { return item.hasTag() && label.equals(item.getTag().getString("W152")) ? item.getCount() : 0; }
    private static ItemStack item(net.minecraft.world.item.Item item, int count, String label) {
        var stack = new ItemStack(item, count); stack.getOrCreateTag().putString("W152", label); return stack;
    }
    private static RocketPosition position(BlockPos at) { return new RocketPosition(at.getX(), at.getY(), at.getZ()); }
    private SealedAutomationGameTests() { }
}
