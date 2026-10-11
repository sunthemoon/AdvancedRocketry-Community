package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual production room manager, not a replacement service or a delayed rescan assertion. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldRoomGameTests {
    @GameTest(template = "empty", batch = "force_field_room", timeoutTicks = 40)
    public static void suppliedRoomIsRevokedAtCommitButNotAtCancelledBreakPreflight(GameTestHelper helper) {
        var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST);
        BlockPos interior = f.identity.target(2);
        var held = new java.util.HashSet<ChunkPos>();
        held.add(new ChunkPos(f.identity.source()));
        for (BlockPos pos : BlockPos.betweenClosed(interior.offset(-1, -1, -1), interior.offset(1, 1, 1))) {
            held.add(new ChunkPos(pos));
        }
        // The remote fixture has no player. Keep it ticking for the native producer, not in production code.
        held.forEach(chunk -> f.level.getChunkSource().addRegionTicket(TicketType.PORTAL, chunk, 1, chunk.getWorldPosition()));
        for (BlockPos mutable : BlockPos.betweenClosed(interior.offset(-1, -1, -1), interior.offset(1, 1, 1))) {
            BlockPos pos = mutable.immutable();
            f.level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            f.extras.add(pos);
        }
        f.level.setBlock(interior, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        var field = f.field(1, f.identity);
        f.power(true);
        f.source.claims(1); f.data.reserve(f.identity, 1);
        BlockPos ventPos = interior.below();
        f.level.setBlock(ventPos, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
        var vent = (OxygenVentBlockEntity) f.level.getBlockEntity(ventPos);
        vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new)
                .insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false);
        vent.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(IllegalStateException::new).receiveEnergy(40000, false);
        OxygenVentBlockEntity.serverTick(f.level, ventPos, vent.getBlockState(), vent);
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(f.level, interior).orElseThrow() == BreathabilityState.BREATHABLE,
                        "Native supplied room never established authority");
                int oxygen = vent.oxygenUnits();
                f.power(false);
                Consumer<BlockEvent.BreakEvent> cancel = event -> {
                    if (event.getLevel() == f.level && event.getPos().equals(field.getBlockPos())) { event.setCanceled(true); }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, cancel);
                try { f.work(); } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(f.level, interior).orElseThrow() == BreathabilityState.BREATHABLE
                        && vent.oxygenUnits() == oxygen, "Refused break revoked unchanged boundary authority or debited oxygen");
                f.work();
                helper.assertTrue(f.level.getBlockState(field.getBlockPos()).is(Blocks.AIR), "Field was not physically removed");
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(f.level, interior).orElseThrow() != BreathabilityState.BREATHABLE
                        && vent.oxygenUnits() == oxygen, "Removal exposed stale room authority before a subsequent oxygen debit");
                helper.succeed();
            } finally {
                f.close();
                held.forEach(chunk -> f.level.getChunkSource().removeRegionTicket(TicketType.PORTAL, chunk, 1, chunk.getWorldPosition()));
            }
        });
    }
    private ForceFieldRoomGameTests() { }
}
