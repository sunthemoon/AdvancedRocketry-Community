package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Native registry and access-surface regression; no journal or publication permit is inferred. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedCarrierGameTests {
    @GameTest(template = "empty", batch = "w152_carrier", timeoutTicks = 100)
    public static void registeredCarrierDeniesNativeSurfaces(GameTestHelper helper) {
        var block = ForgeRegistries.BLOCKS.getValue(SealedRocketMember.TYPE);
        helper.assertTrue(block instanceof EntityBlock, "Sealed carrier block/BE must be registered");
        var state = block.defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var handler = LazyOptional.of(() -> new ItemStackHandler(1));
        java.util.function.Consumer<AttachCapabilitiesEvent<BlockEntity>> attach = event -> {
            if (event.getObject().getClass().getSimpleName().equals("SealedRocketBlockEntity")) {
                event.addCapability(ModIdentity.id("w152_probe_attachment"), new ICapabilityProvider() {
                    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                        return capability == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : LazyOptional.empty();
                    }
                });
            }
        };
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, EventPriority.NORMAL, false, attach);
        try {
            var carrier = ((EntityBlock) block).newBlockEntity(pos, state);
            helper.assertTrue(carrier != null && carrier.onlyOpCanSetNbt(), "Carrier requires operator NBT authority");
            helper.assertTrue(!(carrier instanceof Container) && !(carrier instanceof MenuProvider), "Carrier exposes inventory/menu");
            for (int face = -1; face < Direction.values().length; face++) {
                Direction side = face < 0 ? null : Direction.values()[face];
                helper.assertTrue(carrier.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent() == false,
                        "Carrier exposes attached items at " + side);
                helper.assertTrue(!carrier.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent(), "Carrier exposes fluid");
                helper.assertTrue(!carrier.getCapability(ForgeCapabilities.ENERGY, side).isPresent(), "Carrier exposes energy");
            }
            helper.assertTrue(((EntityBlock) block).getTicker(helper.getLevel(), state, carrier.getType()) == null, "Carrier ticks");
            helper.assertTrue(carrier.getUpdateTag().isEmpty() && carrier.getUpdatePacket() == null, "Carrier leaks client NBT");
            helper.assertTrue(block.asItem() == Items.AIR, "Carrier must not have a BlockItem");
            helper.assertTrue(block.getCloneItemStack(helper.getLevel(), pos, state).isEmpty(), "Pick exports the carrier");
            helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "Carrier moves with a piston");
            helper.assertTrue(state.getDestroySpeed(helper.getLevel(), pos) < 0 && !state.hasAnalogOutputSignal(), "Carrier is mineable or a comparator source");
            helper.assertTrue(!block.canEntityDestroy(state, helper.getLevel(), pos, null), "Entity destruction admitted");
            var player = FakePlayerFactory.getMinecraft(helper.getLevel());
            helper.assertTrue(!block.onDestroyedByPlayer(state, helper.getLevel(), pos, player, false, state.getFluidState()), "Player destruction admitted");
            var event = new BlockEvent.BreakEvent(helper.getLevel(), pos, state, player);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled(), "Creative break event was not refused");
            carrier.setRemoved();
            carrier.reviveCaps();
            helper.assertTrue(!carrier.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Revival exposes an attached capability");
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(attach);
            handler.invalidate();
        }
    }
    private SealedCarrierGameTests() { }
}
