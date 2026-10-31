package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData.Gas;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** O1 container only. No player scan, automatic credit, receipt, proof or prompt save. */
public final class GasChargePadBlockEntity extends BlockEntity {
    public static final String KEY = "arce_gas_charge_pad";
    private GasPadState state = GasPadState.EMPTY;
    private Tag refused;
    private boolean operating;
    private boolean inspecting;
    private int generation;
    private LazyOptional<IFluidHandler> fluids;
    private LazyOptional<IItemHandler> items;
    public GasChargePadBlockEntity(BlockPos pos, BlockState block) {
        super(ModBlockEntities.GAS_CHARGE_PAD.get(), pos, block); createCaps();
    }
    public GasPadState state() { return state; }
    public boolean protectedData() { return refused != null; }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    private boolean available() { return !operating && !inspecting && !protectedData() && DeviceAccess.loaded(this); }
    private boolean enabled() { return available() && workEnabled(); }
    private boolean workEnabled() { return CommonConfig.classicDevicesEnabled() && !level.hasNeighborSignal(worldPosition); }
    public static void tick(Level level, BlockPos pos, BlockState block, GasChargePadBlockEntity pad) {
        if (level.getGameTime() % 20 == 0) { pad.process(); }
    }
    public boolean process() {
        if (!enabled()) { return false; }
        GasPadState after = state.process();
        if (after.equals(state)) { return false; }
        state = after; setChanged(); return true;
    }
    /** Moves to/from a container, not a cross-store atomicity claim. Fill happens separately in process(). */
    public boolean interact(Player player, InteractionHand hand) {
        if (!available() || !DeviceAccess.player(this, player)) { return false; }
        operating = true;
        try { return exchange(player, hand); }
        finally { operating = false; }
    }
    private boolean exchange(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            int slot = player.isShiftKeyDown() ? 0 : 1;
            ItemStack result = stack(slot);
            if (result.isEmpty()) { return false; }
            GasPadState next = state.remove(slot);
            if (!DeviceAccess.player(this, player) || player.getItemInHand(hand) != held) { return false; }
            state = next; player.setItemInHand(hand, result); setChanged(); player.getInventory().setChanged(); return true;
        }
        if (!workEnabled()) { return false; }
        int count = held.getCount();
        GasPadState.Canister kind = kind(held);
        if (kind == null) { return false; }
        int slot = kind == GasPadState.Canister.EMPTY ? 1 : 0;
        GasPadState next = state.insert(slot, kind);
        if (next == state) { return false; }
        ItemStack remainder = held.getCount() == 1 ? ItemStack.EMPTY : held.copyWithCount(held.getCount() - 1);
        if (!DeviceAccess.player(this, player) || !workEnabled() || kind(held) != kind
                || player.getItemInHand(hand) != held || held.getCount() != count) { return false; }
        state = next; player.setItemInHand(hand, remainder); setChanged(); player.getInventory().setChanged(); return true;
    }
    public ItemStack stack(int slot) { return stack(slot == 0 ? state.input() : state.output()); }
    private static ItemStack stack(GasPadState.Canister kind) {
        return switch (kind) {
            case NONE -> ItemStack.EMPTY; case EMPTY -> new ItemStack(ModItems.EMPTY_CANISTER.get());
            case OXYGEN -> new ItemStack(ModItems.OXYGEN_CANISTER.get()); case HYDROGEN -> new ItemStack(ModItems.HYDROGEN_CANISTER.get());
        };
    }
    private GasPadState.Canister kind(ItemStack stack) {
        // This compact root owns plain canisters only. Never erase an item's metadata/capability payload.
        if (inspecting || stack.isEmpty() || stack.getCount() > stack.getMaxStackSize() || stack.hasTag()) { return null; }
        GasPadState.Canister kind = stack.is(ModItems.EMPTY_CANISTER.get()) ? GasPadState.Canister.EMPTY
                : stack.is(ModItems.OXYGEN_CANISTER.get()) ? GasPadState.Canister.OXYGEN
                : stack.is(ModItems.HYDROGEN_CANISTER.get()) ? GasPadState.Canister.HYDROGEN : null;
        if (kind == null) { return null; }
        int count = stack.getCount(); inspecting = true;
        try { return !stack.serializeNBT().contains("ForgeCaps") && !stack.hasTag() && stack.getCount() == count ? kind : null; }
        finally { inspecting = false; }
    }
    public void dropContents() {
        if (!available()) { return; }
        ItemStack input = stack(0); ItemStack output = stack(1); state = GasPadState.EMPTY;
        net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), input);
        net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output);
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        outer.put(KEY, refused == null ? state.encode() : DeviceProtection.bounded(refused) ? refused.copy() : refused);
    }
    @Override public void load(CompoundTag outer) {
        super.load(outer); state = GasPadState.EMPTY; refused = null;
        Tag raw = outer.get(KEY); if (raw == null) { return; }
        GasPadState decoded = GasPadState.decode(raw);
        if (decoded != null) { state = decoded; }
        else { refused = DeviceProtection.bounded(raw) ? raw.copy() : raw; }
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) { return fluids.cast(); }
        if (cap == ForgeCapabilities.ITEM_HANDLER) { return items.cast(); }
        return super.getCapability(cap, side);
    }
    private void createCaps() {
        int version = ++generation;
        fluids = LazyOptional.of(() -> new FluidPort(version)); items = LazyOptional.of(() -> new ItemPort(version));
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); generation++; fluids.invalidate(); items.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); createCaps(); }
    private static Gas gas(FluidStack fluid) {
        return fluid.getFluid() == ClassicFluids.OXYGEN.get() ? Gas.OXYGEN : fluid.getFluid() == ClassicFluids.HYDROGEN.get() ? Gas.HYDROGEN : Gas.EMPTY;
    }
    private final class FluidPort implements IFluidHandler {
        private final int version;
        FluidPort(int version) { this.version = version; }
        private boolean valid() { return version == generation && available(); }
        private void index(int tank) { if (tank != 0) { throw new IndexOutOfBoundsException(tank); } }
        @Override public int getTanks() { return 1; }
        @Override public int getTankCapacity(int tank) { index(tank); return 16000; }
        @Override public FluidStack getFluidInTank(int tank) {
            index(tank); return !valid() || state.amount() == 0 ? FluidStack.EMPTY
                    : new FluidStack(state.gas() == Gas.OXYGEN ? ClassicFluids.OXYGEN.get() : ClassicFluids.HYDROGEN.get(), state.amount());
        }
        @Override public boolean isFluidValid(int tank, FluidStack fluid) {
            index(tank); return valid() && !fluid.isEmpty() && !fluid.hasTag() && gas(fluid) != Gas.EMPTY
                    && (state.amount() == 0 || state.gas() == gas(fluid));
        }
        @Override public int fill(FluidStack fluid, FluidAction action) {
            if (!valid() || !enabled() || !isFluidValid(0, fluid)) { return 0; }
            GasPadState next = state.fill(gas(fluid), fluid.getAmount()); int accepted = next.amount() - state.amount();
            if (action.execute() && accepted > 0) { state = next; setChanged(); }
            return accepted;
        }
        @Override public FluidStack drain(int maximum, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return FluidStack.EMPTY; }
    }
    private final class ItemPort implements IItemHandler {
        private final int version;
        ItemPort(int version) { this.version = version; }
        private boolean valid() { return version == generation && available(); }
        private void index(int slot) { if (slot < 0 || slot > 1) { throw new IndexOutOfBoundsException(slot); } }
        @Override public int getSlots() { return 2; }
        @Override public int getSlotLimit(int slot) { index(slot); return 1; }
        @Override public ItemStack getStackInSlot(int slot) { index(slot); return valid() ? stack(slot) : ItemStack.EMPTY; }
        @Override public boolean isItemValid(int slot, ItemStack value) {
            index(slot); GasPadState.Canister kind = kind(value);
            return valid() && kind != null && (slot == 1 ? kind == GasPadState.Canister.EMPTY
                    : kind == GasPadState.Canister.OXYGEN || kind == GasPadState.Canister.HYDROGEN);
        }
        @Override public ItemStack insertItem(int slot, ItemStack value, boolean simulate) {
            index(slot);
            if (!valid() || !enabled()) { return value; }
            operating = true;
            try {
                var kind = kind(value); if (kind == null) { return value; }
                int count = value.getCount(); GasPadState next = state.insert(slot, kind); if (next == state) { return value; }
                ItemStack result = count == 1 ? ItemStack.EMPTY : value.copyWithCount(count - 1);
                if (kind(value) != kind || value.getCount() != count || !DeviceAccess.loaded(GasChargePadBlockEntity.this)
                        || version != generation || protectedData() || !workEnabled()) { return value; }
                if (!simulate) { state = next; setChanged(); } return result;
            } finally { operating = false; }
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            index(slot); if (!valid() || amount < 1) { return ItemStack.EMPTY; }
            operating = true;
            try {
                GasPadState before = state; ItemStack result = stack(slot);
                if (state != before || version != generation || protectedData()
                        || !DeviceAccess.loaded(GasChargePadBlockEntity.this)) { return ItemStack.EMPTY; }
                if (!simulate && !result.isEmpty()) { state = state.remove(slot); setChanged(); }
                return result;
            } finally { operating = false; }
        }
    }
}
