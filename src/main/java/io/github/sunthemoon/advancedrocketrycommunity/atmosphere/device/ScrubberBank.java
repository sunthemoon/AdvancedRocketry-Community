package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.AtmosphereVolume;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

/** Vent-owned inventory and phase. A facade never persists a cartridge or savings credit. */
public final class ScrubberBank {
    private UUID generation = UUID.randomUUID();
    private int phase;
    private final UUID[] identities = new UUID[6];
    private final ItemStack[] cartridges = new ItemStack[6];
    private final boolean[] supplied = new boolean[6];
    private boolean operating;
    public ScrubberBank() { java.util.Arrays.fill(cartridges, ItemStack.EMPTY); }
    public UUID generation() { return generation; }
    public void observe(OxygenVentBlockEntity vent, AtmosphereVolume volume) {
        java.util.Arrays.fill(supplied, false);
        if (operating || !(vent.getLevel() instanceof ServerLevel level) || !CommonConfig.classicDevicesEnabled()) { return; }
        for (Direction direction : Direction.values()) {
            var position = vent.getBlockPos().relative(direction);
            if (!level.hasChunkAt(position) || !(level.getBlockEntity(position) instanceof ScrubberBlockEntity scrubber)
                    || !scrubber.available()) { continue; }
            // Bounded six-cell measurement, never discovery in another room or a scan.
            boolean sameRoom = false;
            for (Direction face : Direction.values()) {
                var cell = position.relative(face);
                if (volume.cells().contains(new VolumePosition(cell.getX(), cell.getY(), cell.getZ()))) {
                    sameRoom = true; break;
                }
            }
            int slot = direction.ordinal();
            if (!sameRoom || identities[slot] != null && !identities[slot].equals(scrubber.identity())
                    || !scrubber.bind(vent, generation)) { continue; }
            if (identities[slot] == null) { identities[slot] = scrubber.identity(); vent.setChanged(); }
            supplied[slot] = true;
        }
    }
    public boolean skipDebit(OxygenVentBlockEntity vent) {
        if (operating || !vent.deviceAvailable()) { return false; }
        int count = 0;
        boolean required = CommonConfig.scrubberRequiresCartridge();
        for (int i = 0; i < 6; i++) {
            if (supplied[i] && (!required || CarbonCartridge.uses(cartridges[i]) > 0)) { count++; }
        }
        var decision = ScrubberSavings.debit(phase, CommonConfig.classicDevicesEnabled() ? count : 0);
        int oldPhase = phase; phase = decision.nextPhase();
        if (decision.skip() && required) {
            for (int offset = 0; offset < 6; offset++) {
                int i = (oldPhase + offset) % 6;
                if (supplied[i] && CarbonCartridge.uses(cartridges[i]) > 0) {
                    operating = true;
                    try {
                        ItemStack prepared = CarbonCartridge.spend(cartridges[i]);
                        if (prepared == null) { return false; }
                        cartridges[i] = prepared;
                    } finally { operating = false; }
                    break;
                }
            }
        }
        vent.setChanged();
        return decision.skip();
    }
    public void clearSupply() { java.util.Arrays.fill(supplied, false); }
    private int slot(OxygenVentBlockEntity vent, ScrubberBlockEntity facade) {
        if (operating || !vent.deviceAvailable() || !facade.available() || !facade.boundTo(vent, generation)) { return -1; }
        for (Direction direction : Direction.values()) {
            int i = direction.ordinal();
            if (vent.getBlockPos().relative(direction).equals(facade.getBlockPos()) && facade.identity().equals(identities[i])) { return i; }
        }
        return -1;
    }
    public boolean admits(OxygenVentBlockEntity vent, ScrubberBlockEntity facade) {
        return slot(vent, facade) >= 0;
    }
    /** Explicit withdrawal only: an unloaded or unreadable neighbour is not proof of an orphan. */
    public ItemStack recoverOrphan(OxygenVentBlockEntity vent) {
        ItemStack result = releaseOrphan(vent, true); return result == null ? ItemStack.EMPTY : result;
    }
    /** The same proof releases an orphaned association that holds no cartridge; no item moves. */
    public boolean releaseEmptyOrphan(OxygenVentBlockEntity vent) { return releaseOrphan(vent, false) != null; }
    /** Releases the first provable orphan whose slot is charged (or empty); null when there is none. */
    private ItemStack releaseOrphan(OxygenVentBlockEntity vent, boolean charged) {
        if (operating || !vent.deviceAvailable() || vent.scrubbers() != this) { return null; }
        var level = (ServerLevel) vent.getLevel();
        for (Direction direction : Direction.values()) {
            int i = direction.ordinal();
            if (identities[i] == null || cartridges[i].isEmpty() == charged) { continue; }
            var position = vent.getBlockPos().relative(direction);
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) { continue; }
            var target = chunk.getBlockEntities().get(position);
            // A physical facade with a missing/pending entity has no readable identity yet.
            if (chunk.getBlockState(position).getBlock() instanceof ScrubberBlock
                    && !(target instanceof ScrubberBlockEntity)) { continue; }
            if (target instanceof ScrubberBlockEntity facade
                    && (!facade.available() || identities[i].equals(facade.identity()))) { continue; }
            operating = true;
            try {
                ItemStack result = cartridges[i];
                cartridges[i] = ItemStack.EMPTY; identities[i] = null; supplied[i] = false;
                vent.setChanged();
                if (target instanceof ScrubberBlockEntity facade) { facade.unbind(vent, generation); }
                return result;
            } finally { operating = false; }
        }
        return null;
    }
    public ItemStack stack(OxygenVentBlockEntity vent, ScrubberBlockEntity facade) {
        int i = slot(vent, facade); if (i < 0) { return ItemStack.EMPTY; }
        operating = true; try { return cartridges[i].copy(); } finally { operating = false; }
    }
    public ItemStack insert(OxygenVentBlockEntity vent, ScrubberBlockEntity facade, ItemStack stack, boolean simulate) {
        int i = slot(vent, facade);
        if (i < 0 || !CommonConfig.classicDevicesEnabled() || !cartridges[i].isEmpty()) { return stack; }
        operating = true;
        try {
            ItemStack prepared = CarbonCartridge.prepare(stack);
            if (prepared == null || !BoundedNbt.fits(encodeReplacing(i, prepared), 65_000, 14, 1_000)
                    || !live(vent, facade, i)) { return stack; }
            if (!simulate) { cartridges[i] = prepared; vent.setChanged(); }
            return ItemStack.EMPTY;
        } finally { operating = false; }
    }
    public ItemStack extract(OxygenVentBlockEntity vent, ScrubberBlockEntity facade, boolean simulate) {
        int i = slot(vent, facade); if (i < 0) { return ItemStack.EMPTY; }
        operating = true;
        try {
            ItemStack result = cartridges[i].copy(); if (!live(vent, facade, i)) { return ItemStack.EMPTY; }
            if (!simulate) { cartridges[i] = ItemStack.EMPTY; vent.setChanged(); }
            return result;
        } finally { operating = false; }
    }
    private boolean live(OxygenVentBlockEntity vent, ScrubberBlockEntity facade, int i) {
        return vent.deviceAvailable() && facade.available() && facade.boundTo(vent, generation)
                && facade.identity().equals(identities[i]);
    }
    public boolean detach(OxygenVentBlockEntity vent, ScrubberBlockEntity facade) {
        int i = slot(vent, facade); if (i < 0) { return false; }
        ItemStack drop = cartridges[i]; cartridges[i] = ItemStack.EMPTY; identities[i] = null; supplied[i] = false;
        vent.setChanged();
        if (!drop.isEmpty()) { Containers.dropItemStack(vent.getLevel(), facade.getBlockPos().getX() + .5,
                facade.getBlockPos().getY() + .5, facade.getBlockPos().getZ() + .5, drop); }
        return true;
    }
    public void dropAll(OxygenVentBlockEntity vent) {
        for (int i = 0; i < 6; i++) {
            ItemStack drop = cartridges[i]; cartridges[i] = ItemStack.EMPTY; identities[i] = null; supplied[i] = false;
            var position = vent.getBlockPos().relative(Direction.values()[i]);
            if (vent.getLevel().hasChunkAt(position) && vent.getLevel().getBlockEntity(position) instanceof ScrubberBlockEntity facade
                    && facade.boundTo(vent, generation)) { facade.unbind(vent, generation); }
            if (!drop.isEmpty()) { Containers.dropItemStack(vent.getLevel(), vent.getBlockPos().getX() + .5,
                    vent.getBlockPos().getY() + .5, vent.getBlockPos().getZ() + .5, drop); }
        }
        vent.setChanged();
    }
    public CompoundTag encode() {
        return encodeReplacing(-1, ItemStack.EMPTY);
    }
    private CompoundTag encodeReplacing(int replace, ItemStack prepared) {
        CompoundTag root = new CompoundTag(); root.putString("generation", generation.toString()); root.putInt("phase", phase);
        ListTag banks = new ListTag();
        for (int i = 0; i < 6; i++) {
            if (identities[i] == null) { continue; }
            CompoundTag entry = new CompoundTag(); entry.putInt("face", i); entry.putString("identity", identities[i].toString());
            ItemStack stack = i == replace ? prepared : cartridges[i];
            entry.put("cartridge", stack.isEmpty() ? new CompoundTag() : stack.serializeNBT()); banks.add(entry);
        }
        root.put("banks", banks); return root;
    }
    public static UUID uuid(Tag value) {
        if (value == null || value.getId() != Tag.TAG_STRING || value.getAsString().length() != 36) { return null; }
        try { UUID id = UUID.fromString(value.getAsString()); return id.toString().equals(value.getAsString()) ? id : null; }
        catch (IllegalArgumentException invalid) { return null; }
    }
    public static ScrubberBank decode(Tag raw) {
        if (!(raw instanceof CompoundTag root) || !BoundedNbt.fits(root, 65_536, 16, 1_024)
                || !root.getAllKeys().equals(Set.of("generation", "phase", "banks")) || uuid(root.get("generation")) == null
                || !root.contains("phase", Tag.TAG_INT) || root.getInt("phase") < 0 || root.getInt("phase") > 3
                || !root.contains("banks", Tag.TAG_LIST)) { return null; }
        ListTag banks = (ListTag) root.get("banks");
        if (banks.size() > 6 || !banks.isEmpty() && banks.getElementType() != Tag.TAG_COMPOUND) { return null; }
        ScrubberBank result = new ScrubberBank(); result.generation = uuid(root.get("generation")); result.phase = root.getInt("phase");
        Set<UUID> unique = new java.util.HashSet<>();
        for (Tag rawEntry : banks) {
            CompoundTag entry = (CompoundTag) rawEntry;
            if (!entry.getAllKeys().equals(Set.of("face", "identity", "cartridge")) || !entry.contains("face", Tag.TAG_INT)
                    || entry.getInt("face") < 0 || entry.getInt("face") > 5 || uuid(entry.get("identity")) == null
                    || !entry.contains("cartridge", Tag.TAG_COMPOUND)) { return null; }
            int i = entry.getInt("face"); UUID id = uuid(entry.get("identity"));
            if (result.identities[i] != null || !unique.add(id)) { return null; }
            ItemStack stack = CarbonCartridge.decode(entry.getCompound("cartridge")); if (stack == null) { return null; }
            result.identities[i] = id; result.cartridges[i] = stack;
        }
        return result;
    }
}
