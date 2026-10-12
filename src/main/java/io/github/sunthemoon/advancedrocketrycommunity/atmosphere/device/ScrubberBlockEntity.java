package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/** Generation-scoped one-slot facade; never owns the cartridge. */
public final class ScrubberBlockEntity extends BlockEntity {
    public static final String KEY = "arce_co2_scrubber";
    private UUID identity = UUID.randomUUID();
    private UUID ownerGeneration;
    private BlockPos ownerPosition;
    private Tag refused;
    private int capabilityGeneration;
    private LazyOptional<IItemHandler> items = createItems();
    public ScrubberBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.CO2_SCRUBBER.get(), pos, state); }
    public UUID identity() { return identity; }
    public boolean available() { return refused == null && DeviceAccess.loaded(this); }
    public void unbind(OxygenVentBlockEntity owner, UUID generation) {
        if (available() && boundTo(owner, generation)) { ownerPosition = null; ownerGeneration = null; capabilityGeneration++;
            items.invalidate(); items = createItems(); setChanged(); }
    }
    public boolean bind(OxygenVentBlockEntity owner, UUID generation) {
        if (!available() || !owner.deviceAvailable() || !owner.getBlockPos().closerThan(worldPosition, 1.1)) { return false; }
        if (ownerGeneration != null) { return boundTo(owner, generation); }
        ownerPosition = owner.getBlockPos().immutable(); ownerGeneration = generation; setChanged(); return true;
    }
    public boolean boundTo(OxygenVentBlockEntity owner, UUID generation) {
        return owner.getLevel() == level && generation.equals(ownerGeneration) && owner.getBlockPos().equals(ownerPosition);
    }
    private OxygenVentBlockEntity owner() {
        if (!available() || ownerPosition == null || !level.hasChunkAt(ownerPosition)
                || !(level.getBlockEntity(ownerPosition) instanceof OxygenVentBlockEntity owner)
                || !owner.deviceAvailable() || !boundTo(owner, owner.scrubbers().generation())) { return null; }
        return owner;
    }
    public boolean protectedData() { return refused != null || ownerGeneration != null && owner() == null; }
    public void detach() {
        var owner = owner(); if (owner != null) { owner.scrubbers().detach(owner, this); }
    }
    private LazyOptional<IItemHandler> createItems() {
        int generation = capabilityGeneration;
        return LazyOptional.of(() -> new IItemHandler() {
            private OxygenVentBlockEntity admitted() { return generation == capabilityGeneration ? owner() : null; }
            @Override public int getSlots() { return 1; }
            @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : 0; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && CarbonCartridge.uses(stack) >= 0; }
            @Override public ItemStack getStackInSlot(int slot) {
                var owner = admitted(); return slot != 0 || owner == null ? ItemStack.EMPTY : owner.scrubbers().stack(owner, ScrubberBlockEntity.this);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                var owner = admitted(); if (slot != 0 || owner == null) { return stack; }
                // Bank revalidation is performed before the non-reentrancy flag.
                return owner.scrubbers().insert(owner, ScrubberBlockEntity.this, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                var owner = admitted(); return slot != 0 || amount <= 0 || owner == null ? ItemStack.EMPTY
                        : owner.scrubbers().extract(owner, ScrubberBlockEntity.this, simulate);
            }
        });
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? items.cast() : super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); capabilityGeneration++; items.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); items = createItems(); }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (refused != null) { outer.put(KEY, DeviceProtection.bounded(refused) ? refused.copy() : refused); return; }
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("identity", identity.toString());
        if (ownerPosition != null) { root.putLong("owner_position", ownerPosition.asLong()); root.putString("owner_generation", ownerGeneration.toString()); }
        outer.put(KEY, root);
    }
    @Override public void load(CompoundTag outer) {
        super.load(outer); capabilityGeneration++; items.invalidate(); items = createItems();
        refused = null; ownerPosition = null; ownerGeneration = null;
        Tag raw = outer.get(KEY); if (raw == null) { identity = UUID.randomUUID(); return; }
        if (!(raw instanceof CompoundTag root) || !DeviceProtection.bounded(root)
                || !(root.getAllKeys().equals(Set.of("schema_version", "identity"))
                || root.getAllKeys().equals(Set.of("schema_version", "identity", "owner_position", "owner_generation")))
                || !root.contains("schema_version", Tag.TAG_INT) || root.getInt("schema_version") != 1
                || ScrubberBank.uuid(root.get("identity")) == null
                || root.contains("owner_position") && (!root.contains("owner_position", Tag.TAG_LONG)
                || ScrubberBank.uuid(root.get("owner_generation")) == null
                || !BlockPos.of(root.getLong("owner_position")).closerThan(worldPosition, 1.1))) {
            refused = DeviceProtection.bounded(raw) ? raw.copy() : raw; return;
        }
        identity = ScrubberBank.uuid(root.get("identity"));
        if (root.contains("owner_position")) { ownerPosition = BlockPos.of(root.getLong("owner_position")); ownerGeneration = ScrubberBank.uuid(root.get("owner_generation")); }
    }
}
