package io.github.sunthemoon.arceadaptertest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** W152B platform probe only. Neither these blocks nor its publication algorithm ship in the host. */
final class SealedStagingProbeFixture {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AdapterTestMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AdapterTestMod.MOD_ID);
    static final RegistryObject<Block> CARRIER = BLOCKS.register("sealed_probe", CarrierBlock::new);
    static final RegistryObject<Block> OBSERVER = BLOCKS.register("sealed_probe_observer", ObserverBlock::new);
    static final RegistryObject<BlockEntityType<Carrier>> TYPE = TYPES.register("sealed_probe",
            () -> BlockEntityType.Builder.of(Carrier::new, CARRIER.get()).build(null));
    private static final ThreadLocal<Trace> ACTIVE = new ThreadLocal<>();

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TYPES.register(bus);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, SealedStagingProbeFixture::attach);
    }

    static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        Trace trace = ACTIVE.get();
        if (trace != null && event.getObject().getBlockPos().equals(trace.at)) {
            trace.retained = event.getObject();
            trace.events.add("attach:" + event.getObject().getClass().getSimpleName());
            // The constructor has not initialized subclass fields. Retain only; do not call its handlers here.
        }
    }

    static Trace begin(ServerLevel level, BlockPos at) {
        if (ACTIVE.get() != null) { throw new IllegalStateException("Nested staging probe"); }
        Trace trace = new Trace(level, at);
        ACTIVE.set(trace);
        return trace;
    }

    static void end() { ACTIVE.remove(); }

    static void observe(String phase) {
        Trace trace = ACTIVE.get();
        if (trace != null) { trace.observe(phase); }
    }

    static final class Trace {
        final ServerLevel level;
        final BlockPos at;
        final List<String> events = new ArrayList<>();
        BlockEntity retained;
        BlockEntity prepared;
        Carrier carrier;
        boolean publish;
        boolean armed;
        boolean consumed;
        boolean prematureWorldAccess;
        int extracted;
        int foreignInserted;
        final SimpleContainer recipient = new SimpleContainer(1);
        ItemStack foreignSupply = new ItemStack(Items.GOLD_INGOT, 3);
        FluidStack receivedFluid = FluidStack.EMPTY;
        FluidStack foreignFluid = new FluidStack(Fluids.LAVA, 500);
        int firstCapability = -1;
        IItemHandler cached;
        IFluidHandler cachedFluid;

        Trace(ServerLevel level, BlockPos at) { this.level = level; this.at = at.immutable(); }

        void observe(String phase) {
            BlockEntity world = level.getBlockEntity(at);
            boolean capability = world != null && (world.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                    || world.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent());
            events.add(phase + ":world=" + (world == null ? "none" : world.getClass().getSimpleName())
                    + ",cap=" + capability + ",rawRemoved=" + (retained == null || retained.isRemoved()));
            if (capability && firstCapability < 0) { firstCapability = events.size() - 1; }
            if (!publish && world != null && world != carrier
                    && (world instanceof Container || capability)) { prematureWorldAccess = true; }
            if (!armed || consumed || retained == null || retained.isRemoved()) { return; }
            // A neighbour consumer honours R96's removal boundary, but retains the AttachCapabilities target.
            // Native Container and cached InvWrapper access do not require installation in the world.
            consumed = true;
            if (cached != null) {
                recipient.setItem(0, cached.extractItem(0, 64, false));
                foreignSupply = cached.insertItem(Math.min(1, cached.getSlots() - 1), foreignSupply, false);
                extracted = recipient.getItem(0).getCount();
                foreignInserted = 3 - foreignSupply.getCount();
            } else if (retained instanceof Container container) {
                recipient.setItem(0, container.removeItemNoUpdate(0));
                extracted = recipient.getItem(0).getCount();
                container.setItem(1, foreignSupply.copy());
                foreignInserted = container.getItem(1).getCount();
                foreignSupply.shrink(foreignInserted);
            } else if (cachedFluid != null) {
                receivedFluid = cachedFluid.drain(1_000, IFluidHandler.FluidAction.EXECUTE);
                extracted = receivedFluid.getAmount();
                foreignInserted = cachedFluid.fill(foreignFluid, IFluidHandler.FluidAction.EXECUTE);
                foreignFluid.shrink(foreignInserted);
            }
            events.add("retained-consumer:removed=" + retained.isRemoved() + ",original=" + extracted
                    + ",foreign=" + foreignInserted);
        }
    }

    static final class Carrier extends BlockEntity {
        // Diagnostic opaque copy, not the proposed production schema or a serializable game feature.
        CompoundTag sealed = new CompoundTag();

        Carrier(BlockPos at, BlockState state) { super(TYPE.get(), at, state); }

        @Override public void setRemoved() {
            observe("carrier.setRemoved.before");
            super.setRemoved();
            observe("carrier.setRemoved.after");
        }

        @Override public void onLoad() { super.onLoad(); observe("carrier.onLoad"); }
        @Override public boolean onlyOpCanSetNbt() { return true; }
    }

    static final class CarrierBlock extends BaseEntityBlock {
        CarrierBlock() { super(Properties.of().strength(-1.0F, 3_600_000.0F).noLootTable()); }
        @Override public BlockEntity newBlockEntity(BlockPos at, BlockState state) { return new Carrier(at, state); }
        @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

        @Override public void onRemove(BlockState old, Level level, BlockPos at, BlockState next, boolean moving) {
            Trace trace = ACTIVE.get();
            if (trace != null && trace.publish && trace.at.equals(at)) {
                trace.observe("carrier.onRemove.enter");
                if (trace.prepared == null) {
                    trace.events.add("level.removeBlockEntity.before");
                    level.removeBlockEntity(at);
                    trace.events.add("level.removeBlockEntity.after");
                } else {
                    // LevelChunk has already changed the state, but has not called next.onPlace yet.
                    // Its map replacement publishes the restored instance before native lifecycle callbacks.
                    level.setBlockEntity(trace.prepared);
                }
                trace.observe("carrier.onRemove.exit");
                return;
            }
            super.onRemove(old, level, at, next, moving);
        }
    }

    static final class ObserverBlock extends Block {
        ObserverBlock() { super(Properties.of()); }
        @Override public void onNeighborChange(BlockState state, LevelReader level, BlockPos at, BlockPos neighbor) {
            observe("observer.onNeighborChange");
        }
        @Override public void neighborChanged(BlockState state, Level level, BlockPos at,
                Block block, BlockPos from, boolean moving) {
            observe("observer.neighborChanged");
        }
    }

    private SealedStagingProbeFixture() { }
}
