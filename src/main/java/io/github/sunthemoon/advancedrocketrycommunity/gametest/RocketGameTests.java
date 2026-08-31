package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketGameTests {
    private RocketGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blockStateAdapterRoundTripsAllProperties(GameTestHelper helper) {
        BlockState source = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.WATERLOGGED, true);
        RocketBlockState captured = RocketBlockStateAdapter.capture(source);

        helper.assertTrue("west".equals(captured.properties().get("facing")), "Facing was not captured");
        helper.assertTrue("top".equals(captured.properties().get("half")), "Half was not captured");
        helper.assertTrue("true".equals(captured.properties().get("waterlogged")), "Waterlogged was not captured");
        helper.assertTrue(
                RocketBlockStateAdapter.restore(captured).orElseThrow().equals(source),
                "Captured BlockState did not round-trip exactly"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vanillaContainerAdapterConservesItemsAndRejectsMalformedSlots(GameTestHelper helper) {
        BlockPos sourcePosition = BlockPos.ZERO;
        BlockPos targetPosition = new BlockPos(2, 0, 0);
        helper.setBlock(sourcePosition, Blocks.CHEST);
        helper.setBlock(targetPosition, Blocks.CHEST);
        ChestBlockEntity source = (ChestBlockEntity) helper.getBlockEntity(sourcePosition);
        ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(targetPosition);
        source.setItem(0, new ItemStack(Items.DIAMOND, 17));
        source.setItem(26, new ItemStack(Items.IRON_INGOT, 64));
        VanillaContainerRocketAdapter adapter = new VanillaContainerRocketAdapter();

        RocketBlockEntityPayload payload = adapter.capture(source);
        helper.assertTrue(adapter.restore(target, payload), "Approved chest payload was rejected");
        helper.assertTrue(
                target.getItem(0).is(Items.DIAMOND) && target.getItem(0).getCount() == 17,
                "Diamond stack changed during adapter round-trip"
        );
        helper.assertTrue(
                target.getItem(26).is(Items.IRON_INGOT) && target.getItem(26).getCount() == 64,
                "Iron stack changed during adapter round-trip"
        );
        helper.assertTrue(!payload.data().contains("id"), "BlockEntity identity leaked into payload");

        CompoundTag malformed = new CompoundTag();
        ListTag items = new ListTag();
        items.add(stackTag(0, new ItemStack(Items.DIAMOND)));
        items.add(stackTag(0, new ItemStack(Items.IRON_INGOT)));
        malformed.put("Items", items);
        helper.assertTrue(
                !adapter.restore(target, new RocketBlockEntityPayload(adapter.id(), malformed)),
                "Duplicate container slots were accepted"
        );
        helper.succeed();
    }

    private static CompoundTag stackTag(int slot, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        tag.putByte("Slot", (byte) slot);
        return tag;
    }
}
