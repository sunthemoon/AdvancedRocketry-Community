package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicHatchKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.MultiblockPartBindingTarget;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockPartBindingNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** Item/Fluid hatches persist only owner claims; power is the sole physical resource. */
public final class ClassicHatchBlockEntity extends BlockEntity implements MultiblockPartBindingTarget {
    public static final String ROOT = "arce_classic_hatch";
    public static final int MAX_BYTES = 4_096;
    private final ClassicHatchKind kind;
    private MultiblockPartBinding binding;
    private MultiblockPartBinding claim;
    private Tag rejected;
    private int energy;
    private long epoch;
    private LazyOptional<IItemHandler> items = LazyOptional.empty();
    private LazyOptional<IFluidHandler> fluid = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> power = LazyOptional.empty();

    public ClassicHatchBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.CLASSIC_HATCH.get(), position, state);
        kind = ClassicHatchKind.fromBlockId(BuiltInRegistries.BLOCK.getKey(state.getBlock())).orElseThrow();
        refreshFacades();
    }
    public ClassicHatchKind kind() { return kind; }
    public boolean repairRequired() { return rejected != null; }
    ClassicBankKey bankKey() {
        return new ClassicBankKey(kind.bankKind().orElseThrow(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }
    Optional<MultiblockPartBinding> ownerClaim() { return Optional.ofNullable(claim); }
    @Override public Optional<MultiblockPartBinding> multiblockBinding() { return Optional.ofNullable(binding); }
    @Override public void setMultiblockBinding(Optional<MultiblockPartBinding> replacement) {
        if (repairRequired()) { throw new IllegalStateException("Unsupported hatch data"); }
        var next = replacement.orElse(null);
        if (next != null && claim != null && !sameOwner(claim, next)) { throw new IllegalArgumentException("Hatch already owned"); }
        binding = next; if (next != null) { claim = next; } refreshFacades(); setChanged();
    }
    static boolean sameOwner(MultiblockPartBinding first, MultiblockPartBinding second) {
        return first.controllerLevel().equals(second.controllerLevel()) && first.controllerPosition().equals(second.controllerPosition())
                && first.machineInstanceId().equals(second.machineInstanceId());
    }
    void clearClaim() { claim = null; binding = null; refreshFacades(); setChanged(); }
    boolean physical() {
        if (isRemoved() || repairRequired() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }
    ClassicControllerBlockEntity loadedOwner() {
        if (!physical() || claim == null || !(level instanceof ServerLevel server)
                || !claim.controllerLevel().equals(server.dimension())) { return null; }
        var chunk = server.getChunkSource().getChunkNow(claim.controllerPosition().getX() >> 4, claim.controllerPosition().getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(claim.controllerPosition()) instanceof ClassicControllerBlockEntity owner)
                || !owner.ownsClaim(this) || !owner.physical()) { return null; }
        return owner;
    }
    ClassicControllerBlockEntity owner(long expectedEpoch) {
        var owner = expectedEpoch == epoch ? loadedOwner() : null;
        return owner != null && owner.acceptsFacade(this) ? owner : null;
    }
    boolean current(long expectedEpoch, ClassicControllerBlockEntity owner) {
        return expectedEpoch == epoch && loadedOwner() == owner && owner.acceptsBinding(this);
    }
    boolean blocksRemoval() {
        return repairRequired() || claim != null && (loadedOwner() == null || loadedOwner().ownerUnavailable() || loadedOwner().transaction());
    }
    void removedByBlock() {
        var owner = loadedOwner();
        if (owner != null && !owner.ownerUnavailable() && !owner.transaction()) { owner.removeHatch(this); }
        refreshFacades();
    }
    public int energyStored() { return energy; }
    int debit(int requested) { int taken = Math.min(requested, energy); energy -= taken; if (taken > 0) { setChanged(); } return taken; }
    public void refreshFacades() {
        epoch++; items.invalidate(); fluid.invalidate(); power.invalidate(); long current = epoch;
        items = LazyOptional.of(() -> new ClassicItemFacade(this, current));
        fluid = LazyOptional.of(() -> new ClassicFluidFacade(this, current));
        power = LazyOptional.of(() -> new IEnergyStorage() {
            private boolean available() { return current == epoch && physical(); }
            @Override public int receiveEnergy(int offered, boolean simulate) {
                if (!available() || offered <= 0) { return 0; }
                int accepted = Math.min(offered, 10_000 - energy); if (!simulate) { energy += accepted; if (accepted > 0) { setChanged(); } }
                return accepted;
            }
            @Override public int extractEnergy(int requested, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return available() ? energy : 0; }
            @Override public int getMaxEnergyStored() { return 10_000; }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return available(); }
        });
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) {
        if (!physical()) { return LazyOptional.empty(); }
        if (kind == ClassicHatchKind.POWER_INPUT) {
            return requested == ForgeCapabilities.ENERGY ? power.cast() : super.getCapability(requested, side);
        }
        if (owner(epoch) == null) { return LazyOptional.empty(); }
        if (kind.bankKind().orElseThrow().isItem() && requested == ForgeCapabilities.ITEM_HANDLER) { return items.cast(); }
        if (!kind.bankKind().orElseThrow().isItem() && requested == ForgeCapabilities.FLUID_HANDLER) { return fluid.cast(); }
        return super.getCapability(requested, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); epoch++; items.invalidate(); fluid.invalidate(); power.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); refreshFacades(); }
    @Override public void onLoad() {
        super.onLoad(); refreshFacades();
        if (level instanceof ServerLevel server) { observeUnsafeRoot(server); ClassicRuntime.manager(server).changed(worldPosition); }
    }
    @Override public void onChunkUnloaded() { refreshFacades(); super.onChunkUnloaded(); }

    static boolean bounded(Tag root) { return BoundedNbt.fits(root, MAX_BYTES, 12, 256); }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (rejected != null) { outer.put(ROOT, bounded(rejected) ? rejected.copy() : rejected); return; }
        var root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("kind", kind.blockId().toString());
        root.putInt("energy", energy);
        var encodedClaim = new CompoundTag(); if (claim != null) { encodedClaim = MultiblockPartBindingNbtCodec.encode(claim); }
        root.put("claim", encodedClaim);
        var encodedBinding = new CompoundTag(); if (binding != null) { encodedBinding = MultiblockPartBindingNbtCodec.encode(binding); }
        root.put("binding", encodedBinding);
        root.putString("bank_key", kind.bankKind().isPresent() ? bankKey().channel() : "");
        outer.put(ROOT, root);
    }
    @Override public void load(CompoundTag outer) {
        super.load(outer); refreshFacades(); binding = null; claim = null; energy = 0; rejected = null;
        if (!outer.contains(ROOT)) { setChanged(); return; }
        Tag raw = outer.get(ROOT);
        try {
            if (!bounded(raw) || !(raw instanceof CompoundTag root)
                    || !root.getAllKeys().equals(Set.of("schema_version", "kind", "energy", "claim", "binding", "bank_key"))
                    || !root.contains("schema_version", Tag.TAG_INT) || root.getInt("schema_version") != 1
                    || !root.contains("kind", Tag.TAG_STRING) || !root.getString("kind").equals(kind.blockId().toString())
                    || !root.contains("energy", Tag.TAG_INT) || root.getInt("energy") < 0 || root.getInt("energy") > 10_000
                    || kind != ClassicHatchKind.POWER_INPUT && root.getInt("energy") != 0
                    || !root.contains("claim", Tag.TAG_COMPOUND) || !root.contains("binding", Tag.TAG_COMPOUND)
                    || !root.contains("bank_key", Tag.TAG_STRING)
                    || !root.getString("bank_key").equals(kind.bankKind().isPresent() ? bankKey().channel() : "")) {
                throw new IllegalArgumentException("Hatch schema/fields");
            }
            claim = decodeBinding(root.getCompound("claim")); binding = decodeBinding(root.getCompound("binding"));
            if (binding != null && (claim == null || !sameOwner(claim, binding))) { throw new IllegalArgumentException("Hatch claim mismatch"); }
            energy = root.getInt("energy");
        } catch (RuntimeException refused) {
            rejected = bounded(raw) ? raw.copy() : raw; binding = null; claim = null; energy = 0;
        }
        if (level instanceof ServerLevel server) { observeUnsafeRoot(server); }
    }
    private void observeUnsafeRoot(ServerLevel server) {
        if (rejected != null && !bounded(rejected)) { ClassicRuntime.observedUnsafeRoot(server, worldPosition); }
    }
    private static MultiblockPartBinding decodeBinding(CompoundTag raw) {
        if (raw.isEmpty()) { return null; }
        var outer = new CompoundTag(); outer.put(MultiblockPartBindingNbtCodec.ROOT, raw);
        return MultiblockPartBindingNbtCodec.decode(outer).value().orElseThrow();
    }
}
