package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelService;
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
    private static final TicketType<ChunkPos> FIXTURE_TICKET = TicketType.create(
            "arce_force_field_room_fixture", java.util.Comparator.comparingLong(ChunkPos::toLong));
    @GameTest(template = "empty", batch = "force_field_room", timeoutTicks = 40)
    public static void suppliedRoomIsRevokedAtCommitButNotAtCancelledBreakPreflight(GameTestHelper helper) {
        var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST);
        boolean previous = ForceFieldConfig.enabled();
        ForceFieldConfig.ENABLED.set(false);
        BlockPos interior = f.identity.target(2);
        var held = new java.util.HashSet<ChunkPos>();
        held.add(new ChunkPos(f.identity.source()));
        for (BlockPos pos : BlockPos.betweenClosed(interior.offset(-1, -1, -1), interior.offset(1, 1, 1))) {
            held.add(new ChunkPos(pos));
        }
        // The remote fixture has no player. Keep it ticking for the native producer, not in production code.
        held.forEach(chunk -> {
            f.level.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 1, chunk);
            f.level.getChunk(chunk.x, chunk.z);
        });
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
                var manager = (AtmosphereManager) readField(null, AtmosphereRuntime.class, "manager");
                OxygenVentBlockEntity.serverTick(f.level, ventPos, vent.getBlockState(), vent);
                var service = (AtmosphereLevelService) ((java.util.Map<?, ?>) readField(manager, AtmosphereManager.class, "levels"))
                        .get(f.level.dimension());
                // Drive the installed native producer with its unchanged per-service inspection budget.
                for (int i = 0; i < 32 && !manager.controlledAt(f.level, interior); i++) {
                    OxygenVentBlockEntity.serverTick(f.level, ventPos, vent.getBlockState(), vent);
                    service.tick();
                }
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(f.level, interior).orElseThrow() == BreathabilityState.BREATHABLE,
                        "Native supplied room never established authority: status=" + vent.status() + " metrics=" + service.metrics());
                int oxygen = vent.oxygenUnits();
                int energy = vent.energyStored();
                long ticks = service.metrics().completedServiceTicks();
                ForceFieldConfig.ENABLED.set(true);
                f.power(false);
                Consumer<BlockEvent.BreakEvent> cancel = event -> {
                    if (event.getLevel() == f.level && event.getPos().equals(field.getBlockPos())) { event.setCanceled(true); }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, cancel);
                try { f.work(); } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(f.level, interior).orElseThrow() == BreathabilityState.BREATHABLE
                        && vent.oxygenUnits() == oxygen && vent.energyStored() == energy
                        && service.metrics().completedServiceTicks() == ticks,
                        "Refused break revoked unchanged boundary authority or debited oxygen");
                f.work();
                helper.assertTrue(f.level.getBlockState(field.getBlockPos()).is(Blocks.AIR), "Field was not physically removed");
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(f.level, interior).orElseThrow() != BreathabilityState.BREATHABLE
                        && vent.oxygenUnits() == oxygen && vent.energyStored() == energy
                        && service.metrics().completedServiceTicks() == ticks,
                        "Removal exposed stale room authority before a subsequent oxygen debit");
                helper.succeed();
            } finally {
                f.close();
                held.forEach(chunk -> f.level.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 1, chunk));
                ForceFieldConfig.ENABLED.set(previous);
            }
        });
    }
    private static Object readField(Object owner, Class<?> type, String name) {
        try {
            var field = type.getDeclaredField(name); field.setAccessible(true);
            return java.util.Objects.requireNonNull(field.get(owner));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Installed room witness unavailable", failure); }
    }
    private ForceFieldRoomGameTests() { }
}
