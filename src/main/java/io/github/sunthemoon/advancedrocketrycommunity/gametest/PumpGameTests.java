package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpProtection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpSave;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpSources;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual registry, source mutation, protection events and capability/NBT tests; run only after root integration. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PumpGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 2, 1);
    private static final UUID OWNER = new UUID(0x50554D50, 6); // Intentionally offline; never a nearby player lookup.
    private static PumpBlockEntity place(GameTestHelper helper, BlockState source, UUID owner, int energy, FluidStack fluid) {
        var level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(POSITION);
        level.setBlock(absolute.below(), source, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(absolute, PumpContent.BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        var pump = (PumpBlockEntity) level.getBlockEntity(absolute);
        state(pump, owner, energy, 0, fluid);
        return pump;
    }
    private static void state(PumpBlockEntity pump, UUID owner, int energy, int cooldown, FluidStack fluid) {
        CompoundTag outer = new CompoundTag(); outer.put(PumpSave.ROOT, PumpSave.encode(owner, energy, cooldown, fluid)); pump.load(outer);
    }
    private static void tick(GameTestHelper helper, PumpBlockEntity pump) {
        PumpBlockEntity.serverTick(helper.getLevel(), pump.getBlockPos(), pump.getBlockState(), pump);
    }
    private static IFluidHandler fluids(PumpBlockEntity pump) {
        return pump.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
    }
    private static IEnergyStorage energy(PumpBlockEntity pump) {
        return pump.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    }
    private static void drained(GameTestHelper helper, BlockState source, Fluid fluid) {
        var pump = place(helper, source, OWNER, 10_000, FluidStack.EMPTY);
        tick(helper, pump);
        helper.assertTrue(helper.getLevel().getBlockState(pump.getBlockPos().below()).isAir(), "Source was not removed");
        helper.assertTrue(pump.status() == PumpCode.DRAINED && pump.energy() == 9_900 && pump.cooldown() == 5
                && pump.fluid().isFluidStackIdentical(new FluidStack(fluid, 1_000)), "Source resource accounting differs");
        helper.assertTrue(pump.getBlockState().getPistonPushReaction() == PushReaction.BLOCK, "Pump can be pushed");
        tick(helper, pump);
        helper.assertTrue(pump.energy() == 9_900 && pump.fluid().getAmount() == 1_000 && pump.cooldown() == 5, "Duplicate ticker call changed resources");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void vanillaWaterDrainsWithOfflineOwner(GameTestHelper helper) {
        drained(helper, Blocks.WATER.defaultBlockState(), Fluids.WATER);
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void vanillaLavaDrains(GameTestHelper helper) {
        drained(helper, Blocks.LAVA.defaultBlockState(), Fluids.LAVA);
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void rocketFuelDrains(GameTestHelper helper) {
        drained(helper, ClassicFluids.ROCKET_FUEL_BLOCK.get().defaultBlockState(), ClassicFluids.ROCKET_FUEL.get());
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void enrichedLavaDrains(GameTestHelper helper) {
        drained(helper, ClassicFluids.ENRICHED_LAVA_BLOCK.get().defaultBlockState(), ClassicFluids.ENRICHED_LAVA.get());
    }
    private static void unchanged(GameTestHelper helper, PumpBlockEntity pump, BlockState before, int energy, int amount, PumpCode code) {
        helper.assertTrue(helper.getLevel().getBlockState(pump.getBlockPos().below()).equals(before), "Refusal changed source");
        helper.assertTrue(pump.energy() == energy && pump.fluid().getAmount() == amount && pump.status() == code, "Refusal changed resources/code");
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void absentOwnerNeverDrains(GameTestHelper helper) {
        var source = Blocks.WATER.defaultBlockState(); var pump = place(helper, source, null, 10_000, FluidStack.EMPTY);
        tick(helper, pump); unchanged(helper, pump, source, 10_000, 0, PumpCode.NO_OWNER); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void insufficientEnergyNeverDrains(GameTestHelper helper) {
        var source = Blocks.WATER.defaultBlockState(); var pump = place(helper, source, OWNER, 99, FluidStack.EMPTY);
        tick(helper, pump); unchanged(helper, pump, source, 99, 0, PumpCode.NO_ENERGY); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void fullTankNeverDrains(GameTestHelper helper) {
        var source = Blocks.WATER.defaultBlockState(); var pump = place(helper, source, OWNER, 10_000, new FluidStack(Fluids.WATER, 15_001));
        tick(helper, pump); unchanged(helper, pump, source, 10_000, 15_001, PumpCode.TANK_FULL); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void incompatibleTankNeverDrains(GameTestHelper helper) {
        var source = Blocks.WATER.defaultBlockState(); var pump = place(helper, source, OWNER, 10_000, new FluidStack(Fluids.LAVA, 1));
        tick(helper, pump); unchanged(helper, pump, source, 10_000, 1, PumpCode.TANK_INCOMPATIBLE); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void publicCancellationStopsSearchAndReentrantCaps(GameTestHelper helper) {
        var source = Blocks.WATER.defaultBlockState(); var pump = place(helper, source, OWNER, 10_000, FluidStack.EMPTY);
        var retained = energy(pump); AtomicInteger events = new AtomicInteger();
        Consumer<EndgameEffectEvent> veto = event -> {
            if (event.systemId().equals(ModIdentity.id("pump")) && event.min().equals(pump.getBlockPos().below())) {
                helper.assertTrue(event.ownerId().equals(OWNER) && event.actorId().isEmpty(), "Wrong public event owner");
                helper.assertTrue(retained.receiveEnergy(100, false) == 0 && pump.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent() == false,
                        "Public listener reentered pump"); events.incrementAndGet(); event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EndgameEffectEvent.class, veto);
        try { tick(helper, pump); unchanged(helper, pump, source, 10_000, 0, PumpCode.TARGET_PROTECTED); }
        finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        helper.runAfterDelay(2, () -> {
            unchanged(helper, pump, source, 10_000, 0, PumpCode.TARGET_PROTECTED);
            helper.assertTrue(events.get() == 1, "Refused search automatically continued"); helper.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void standardBreakCancellationUsesOwnerFakePlayer(GameTestHelper helper) {
        var source = Blocks.LAVA.defaultBlockState(); var pump = place(helper, source, OWNER, 10_000, FluidStack.EMPTY);
        AtomicInteger events = new AtomicInteger();
        Consumer<BlockEvent.BreakEvent> veto = event -> {
            if (event.getPos().equals(pump.getBlockPos().below()) && event.getLevel() == helper.getLevel()) {
                helper.assertTrue(event.getPlayer() instanceof net.minecraftforge.common.util.FakePlayer
                        && event.getPlayer().getUUID().equals(OWNER), "Break event lost owner identity");
                events.incrementAndGet(); event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, veto);
        try { tick(helper, pump); unchanged(helper, pump, source, 10_000, 0, PumpCode.TARGET_PROTECTED); }
        finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        helper.assertTrue(events.get() == 1, "Missing/duplicate standard break event"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void protectedZoneStopsBeforePublicEvent(GameTestHelper helper) {
        var source = Blocks.WATER.defaultBlockState(); var pump = place(helper, source, OWNER, 10_000, FluidStack.EMPTY);
        BlockPos target = pump.getBlockPos().below(); String name = "pump_" + Integer.toUnsignedString(target.hashCode(), 36);
        var root = EndgameSavedData.get(helper.getLevel().getServer());
        helper.assertTrue(root.update(data -> data.addZone(ProtectedZone.of(name, helper.getLevel().dimension().location(),
                target.getX(), target.getZ(), target.getX(), target.getZ(), List.of()), 256)) == EndgameCode.OK, "Zone fixture failed");
        AtomicInteger events = new AtomicInteger();
        Consumer<EndgameEffectEvent> observe = event -> { if (event.systemId().equals(ModIdentity.id("pump")) && event.min().equals(target)) { events.incrementAndGet(); } };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EndgameEffectEvent.class, observe);
        try { tick(helper, pump); unchanged(helper, pump, source, 10_000, 0, PumpCode.TARGET_PROTECTED); }
        finally { MinecraftForge.EVENT_BUS.unregister(observe); root.update(data -> data.removeZone(name)); }
        helper.assertTrue(events.get() == 0, "Protected zone was checked after public event"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void listenerChangedSourceIsNotPaidOrBuffered(GameTestHelper helper) {
        var pump = place(helper, Blocks.WATER.defaultBlockState(), OWNER, 10_000, FluidStack.EMPTY);
        Consumer<EndgameEffectEvent> change = event -> {
            if (event.systemId().equals(ModIdentity.id("pump")) && event.min().equals(pump.getBlockPos().below())) {
                helper.getLevel().setBlock(event.min(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EndgameEffectEvent.class, change);
        try { tick(helper, pump); }
        finally { MinecraftForge.EVENT_BUS.unregister(change); }
        helper.assertTrue(pump.energy() == 10_000 && pump.fluid().isEmpty() && pump.status() == PumpCode.SOURCE_CHANGED
                && helper.getLevel().getBlockState(pump.getBlockPos().below()).is(Blocks.STONE), "Revalidation removed/rewarded listener replacement"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void genericPortsAreBoundedCopiedAndShareOutputBudget(GameTestHelper helper) {
        var pump = place(helper, Blocks.STONE.defaultBlockState(), OWNER, 0, FluidStack.EMPTY);
        var port = fluids(pump); var power = energy(pump);
        FluidStack gas = new FluidStack(ClassicFluids.NITROGEN.get(), 20_000);
        helper.assertTrue(port.fill(gas, IFluidHandler.FluidAction.SIMULATE) == 16_000 && pump.fluid().isEmpty(), "Fill simulation changed tank");
        helper.assertTrue(port.fill(gas, IFluidHandler.FluidAction.EXECUTE) == 16_000 && gas.getAmount() == 20_000, "Generic gas fill bounds/alias differ");
        FluidStack copy = port.getFluidInTank(0); copy.setAmount(1);
        helper.assertTrue(pump.fluid().getAmount() == 16_000, "Fluid view aliases tank");
        helper.assertTrue(port.drain(20_000, IFluidHandler.FluidAction.SIMULATE).getAmount() == 1_000 && pump.fluid().getAmount() == 16_000, "Drain simulation changed tank");
        helper.assertTrue(port.drain(600, IFluidHandler.FluidAction.EXECUTE).getAmount() == 600
                && port.drain(600, IFluidHandler.FluidAction.EXECUTE).getAmount() == 400
                && port.drain(1, IFluidHandler.FluidAction.EXECUTE).isEmpty() && pump.fluid().getAmount() == 15_000, "Per-tick output exceeded1000");
        helper.assertTrue(power.receiveEnergy(Integer.MAX_VALUE, true) == 10_000 && pump.energy() == 0
                && power.receiveEnergy(Integer.MAX_VALUE, false) == 10_000 && power.extractEnergy(1, false) == 0, "Energy bounds/direction differ");
        pump.invalidateCaps(); pump.reviveCaps();
        helper.assertTrue(port.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty() && power.receiveEnergy(1, false) == 0,
                "Retained capability survived invalidation"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void futureAndOversizedRootsRemainVerbatimInRepair(GameTestHelper helper) {
        var pump = place(helper, Blocks.WATER.defaultBlockState(), OWNER, 100, FluidStack.EMPTY);
        var root = PumpSave.encode(OWNER, 100, 0, FluidStack.EMPTY); root.putInt("schema", 2); root.putString("future", "kept");
        CompoundTag outer = new CompoundTag(); outer.put(PumpSave.ROOT, root); pump.load(outer);
        helper.assertTrue(pump.repairRequired() && pump.getCapability(ForgeCapabilities.ENERGY).isPresent() == false
                && root.equals(pump.saveWithoutMetadata().get(PumpSave.ROOT)), "Future root was exposed/defaulted");
        helper.assertTrue(!pump.getBlockState().onDestroyedByPlayer(helper.getLevel(), pump.getBlockPos(), helper.makeMockPlayer(), true,
                Fluids.EMPTY.defaultFluidState()), "Ordinary break destroyed future root");
        CompoundTag huge = new CompoundTag(); huge.putByteArray("future", new byte[8_192]); outer.put(PumpSave.ROOT, huge); pump.load(outer);
        helper.assertTrue(pump.repairRequired() && pump.saveWithoutMetadata().get(PumpSave.ROOT) == huge,
                "Oversized root was copied/discarded before chunk writer veto");
        var chunk = helper.getLevel().getChunkAt(pump.getBlockPos());
        CompoundTag outgoing = new CompoundTag(); var entries = new net.minecraft.nbt.ListTag();
        entries.add(pump.saveWithFullMetadata()); outgoing.put("block_entities", entries);
        chunk.setUnsaved(false);
        try {
            PumpProtection.chunkSave(new ChunkDataEvent.Save(chunk, helper.getLevel(), outgoing));
            helper.fail("Oversized native chunk write was admitted");
        } catch (IllegalStateException refused) {
            helper.assertTrue(refused.getMessage().startsWith("Refusing oversized pump chunk save")
                    && chunk.isUnsaved() && pump.saveWithoutMetadata().get(PumpSave.ROOT) == huge,
                    "Native veto did not preserve retry/raw root");
        }
        // Explicit test-only restoration: production still quarantines and vetoes the oversized root.
        state(pump, OWNER, 100, 0, FluidStack.EMPTY);
        io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void callerFluidCallbacksCannotMutateSimulationValidationOrExecution(GameTestHelper helper) {
        var pump = place(helper, Blocks.STONE.defaultBlockState(), OWNER, 0, new FluidStack(Fluids.WATER, 1_000));
        var port = fluids(pump); var power = energy(pump);
        AtomicInteger callbacks = new AtomicInteger();
        FluidStack offered = new FluidStack(Fluids.WATER, 1_000) {
            @Override public FluidStack copy() {
                callbacks.incrementAndGet();
                helper.assertTrue(port.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Copy callback drained pump");
                helper.assertTrue(power.receiveEnergy(200, false) == 0, "Copy callback inserted energy");
                helper.assertTrue(port.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.EXECUTE) == 0,
                        "Copy callback reentered fill");
                try { pump.load(new CompoundTag()); helper.fail("Copy callback replaced resource snapshot"); }
                catch (IllegalStateException expected) { }
                return super.copy();
            }
        };
        helper.assertTrue(port.fill(offered, IFluidHandler.FluidAction.SIMULATE) == 1_000
                && pump.fluid().getAmount() == 1_000 && pump.energy() == 0, "Fill simulation changed resources");
        helper.assertTrue(port.isFluidValid(0, offered) && pump.fluid().getAmount() == 1_000 && pump.energy() == 0,
                "Fluid validation changed resources");
        helper.assertTrue(port.drain(offered, IFluidHandler.FluidAction.SIMULATE).getAmount() == 1_000
                && pump.fluid().getAmount() == 1_000 && pump.energy() == 0, "Matching drain simulation changed resources");
        helper.assertTrue(port.fill(offered, IFluidHandler.FluidAction.EXECUTE) == 1_000
                && pump.fluid().getAmount() == 2_000 && pump.energy() == 0, "Fill execution was reentered");
        helper.assertTrue(callbacks.get() >= 4, "Caller copy callbacks were not exercised");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void knownRootReloadKeepsResourcesAndDropRebindsOwner(GameTestHelper helper) {
        var pump = place(helper, Blocks.STONE.defaultBlockState(), OWNER, 123, new FluidStack(ClassicFluids.OXYGEN.get(), 321));
        state(pump, OWNER, 123, 4, pump.fluid());
        CompoundTag saved = pump.saveWithoutMetadata(); pump.load(saved);
        helper.assertTrue(pump.energy() == 123 && pump.cooldown() == 4 && pump.fluid().getAmount() == 321 && OWNER.equals(pump.owner()), "Reload changed resource root");
        List<ItemStack> drops = Block.getDrops(pump.getBlockState(), helper.getLevel(), pump.getBlockPos(), pump, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(PumpContent.ITEM.get()) && drops.get(0).getTag().contains(PumpSave.ROOT), "Normal drop lost pump resource root");
        var carried = PumpSave.decode(drops.get(0).getTag().get(PumpSave.ROOT));
        helper.assertTrue(carried.energy() == 123 && carried.cooldown() == 4 && carried.fluid().getAmount() == 321, "Carried resource amount differs");
        UUID newOwner = new UUID(7, 8); pump.placed(newOwner, drops.get(0).getTag().get(PumpSave.ROOT));
        helper.assertTrue(newOwner.equals(pump.owner()) && pump.energy() == 123 && pump.fluid().getAmount() == 321, "Placement failed to rebind owner/preserve resources");
        pump.placed(null, drops.get(0).getTag().get(PumpSave.ROOT)); helper.assertTrue(pump.owner() == null, "Machine inherited item owner"); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void waterloggedAndNonfluidBlocksHaveNoSourceAdapter(GameTestHelper helper) {
        var waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        helper.assertTrue(PumpSources.fluid(waterlogged) == null && PumpSources.fluid(Blocks.STONE.defaultBlockState()) == null,
                "Pump accepted nonstandard waterlogged/nonfluid adapter");
        var pump = place(helper, waterlogged, OWNER, 100, FluidStack.EMPTY); tick(helper, pump);
        unchanged(helper, pump, waterlogged, 100, 0, PumpCode.SOURCE_UNSUPPORTED); helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20) public static void receiverCannotLoadReplacementResourcesDuringPush(GameTestHelper helper) {
        var pump = place(helper, Blocks.STONE.defaultBlockState(), OWNER, 23, new FluidStack(Fluids.WATER, 2_000));
        CompoundTag replacement = new CompoundTag();
        replacement.put(PumpSave.ROOT, PumpSave.encode(new UUID(99, 99), 10_000, 5, new FluidStack(Fluids.LAVA, 16_000)));
        AtomicInteger refused = new AtomicInteger();
        var receiverTank = new net.minecraftforge.fluids.capability.templates.FluidTank(16_000) {
            @Override public int fill(FluidStack stack, FluidAction action) {
                try { pump.load(replacement); }
                catch (IllegalStateException busy) { refused.incrementAndGet(); }
                return super.fill(stack, action);
            }
        };
        BlockPos target = pump.getBlockPos().east();
        helper.getLevel().setBlock(target, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        var receiver = new net.minecraft.world.level.block.entity.ChestBlockEntity(target, Blocks.CHEST.defaultBlockState()) {
            private final net.minecraftforge.common.util.LazyOptional<IFluidHandler> port =
                    net.minecraftforge.common.util.LazyOptional.of(() -> receiverTank);
            @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                    net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
                return capability == ForgeCapabilities.FLUID_HANDLER ? port.cast() : super.getCapability(capability, side);
            }
        };
        helper.getLevel().setBlockEntity(receiver);
        tick(helper, pump);
        helper.assertTrue(refused.get() == 1 && pump.energy() == 23 && pump.cooldown() == 0 && OWNER.equals(pump.owner())
                && pump.fluid().isFluidStackIdentical(new FluidStack(Fluids.WATER, 1_000))
                && receiverTank.getFluid().isFluidStackIdentical(new FluidStack(Fluids.WATER, 1_000)),
                "Reentrant load replaced resources before export debit");
        helper.succeed();
    }
    private PumpGameTests() { }
}
