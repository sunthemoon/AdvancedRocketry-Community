package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent black-box atmosphere fixture: API and platform only, no host classes. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereBoundaryGameTests {
    private static final String HOST = "advancedrocketrycommunity";
    private static final TicketType<BlockPos> ROOM_TICKET = TicketType.create("arce_boundary_fixture",
            java.util.Comparator.comparingLong(BlockPos::asLong), 140);

    private AtmosphereBoundaryGameTests() {
    }

    @GameTest(templateNamespace = HOST, template = "atmosphere_test", timeoutTicks = 20)
    public static void externalBoundaryReceivesOneClosedRegistrationEvent(GameTestHelper helper) {
        helper.assertTrue(AdapterTestMod.boundaryEvents == 1 && AdapterTestMod.boundaryEvent != null,
                "Expected one boundary registration event on the fixture MOD bus");
        boolean rejected = false;
        try {
            AdapterTestMod.boundaryEvent.register(AdapterTestMod.id("late_boundary"),
                    Set.of(ResourceLocation.tryParse("minecraft:stone")), state -> AtmosphereBoundary.SEALED);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Retained boundary event remained usable after startup freeze");
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "atmosphere_test", timeoutTicks = 120)
    public static void externalStateBoundaryControlsTheProductionVent(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.tryParse(HOST + ":moon")));
        helper.assertTrue(moon != null, "Moon is missing");
        // An unoccupied dimension pauses BlockEntity ticks after 300 ticks. This test
        // needs only its existing 120-tick window, not persistent forced-chunk data.
        moon.resetEmptyTime();
        BlockPos allocation = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos vent = new BlockPos(allocation.getX(), 240, allocation.getZ());
        Set<net.minecraft.world.level.ChunkPos> forced = new java.util.HashSet<>();
        for (BlockPos corner : new BlockPos[]{vent.offset(-1, 0, -1), vent.offset(1, 0, 1),
                vent.offset(-1, 0, 1), vent.offset(1, 0, -1)}) {
            forced.add(new net.minecraft.world.level.ChunkPos(corner));
        }
        // Per-test ownership; also expires after the test timeout if setup/assertions fail.
        forced.forEach(chunk -> moon.getChunkSource().addRegionTicket(ROOM_TICKET, chunk, 2, vent));
        for (BlockPos pos : BlockPos.betweenClosed(vent.offset(-1, 0, -1), vent.offset(1, 2, 1))) {
            moon.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
        moon.setBlock(vent.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        moon.setBlock(vent.above(2), AdapterTestMod.BOUNDARY.get().defaultBlockState(), Block.UPDATE_ALL);
        moon.setBlock(vent, ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(HOST + ":oxygen_vent"))
                .defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity entity = moon.getBlockEntity(vent);
        helper.assertTrue(entity != null, "Vent BlockEntity is missing");
        var items = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Missing vent items"));
        ItemStack remainder = items.insertItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(
                ResourceLocation.tryParse(HOST + ":oxygen_canister"))), false);
        helper.assertTrue(remainder.isEmpty(), "Vent refused the oxygen fixture");
        entity.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Missing vent energy")).receiveEnergy(40000, false);

        helper.startSequence()
                .thenWaitUntil(() -> assertLit(helper, moon, vent, true))
                .thenExecute(() -> moon.setBlock(vent.above(2), AdapterTestMod.BOUNDARY.get().defaultBlockState()
                        .setValue(FixtureBoundaryBlock.OPEN, true), Block.UPDATE_ALL))
                .thenWaitUntil(() -> assertLit(helper, moon, vent, false))
                .thenExecute(() -> moon.setBlock(vent.above(2), AdapterTestMod.BOUNDARY.get().defaultBlockState(),
                        Block.UPDATE_ALL))
                .thenWaitUntil(() -> assertLit(helper, moon, vent, true))
                .thenExecute(() -> moon.setBlock(vent.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
                .thenWaitUntil(() -> assertLit(helper, moon, vent, false))
                .thenExecute(() -> {
                    moon.removeBlock(vent, false);
                    forced.forEach(chunk -> moon.getChunkSource().removeRegionTicket(ROOM_TICKET, chunk, 2, vent));
                })
                .thenSucceed();
    }

    private static void assertLit(GameTestHelper helper, ServerLevel level, BlockPos vent, boolean lit) {
        helper.assertTrue(level.getBlockState(vent).getValue(BlockStateProperties.LIT) == lit,
                "Production vent lit state did not become " + lit + " at " + vent
                        + " roof=" + level.getBlockState(vent.above(2))
                        + " data=" + level.getBlockEntity(vent).saveWithoutMetadata());
    }
}
