package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalInventory;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The package identity decoder (W27A-DESIGN section 6). The raw tag is decoded only to show a live stack and read the
 * identity: {@code SatelliteItemData.readTag} on its {@code tag}, then {@code SatelliteTerminalInventory.validPackage}.
 * Anything that would not come back byte for byte from {@code ItemStack.of} stays raw.
 */
public final class SatelliteBayPackage {
    private SatelliteBayPackage() {
    }

    public enum Availability {
        /** A valid identity in the matching package item; the live stack round-trips exactly. */
        AVAILABLE,
        /** A FUTURE identity (UNSUPPORTED_DATA): kept raw, no deployment or extraction. */
        UNSUPPORTED_DATA,
        /** An unknown item ID (PACKAGE_UNAVAILABLE): {@code ItemStack.of} would return EMPTY, so it is kept raw. */
        PACKAGE_UNAVAILABLE,
        /** Any other raw tag: not a satellite package, an invalid identity, or a stack that would be normalized. */
        INVALID_PACKAGE
    }

    public record Decoded(Availability availability, Optional<SatelliteIdentity> identity, ItemStack stack) {
        public Decoded {
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(stack, "stack");
            if ((availability == Availability.AVAILABLE) == stack.isEmpty()) {
                throw new IllegalArgumentException("Only an available package has a live stack");
            }
        }

        public boolean available() {
            return availability == Availability.AVAILABLE;
        }

        /** A detached copy of the live stack, or EMPTY. */
        public ItemStack liveCopy() {
            return stack.copy();
        }
    }

    /** {@code pkg} must already satisfy {@link SatelliteBayRoot#validPackage}; it is never mutated. */
    public static Decoded decode(CompoundTag pkg) {
        if (!SatelliteBayRoot.validPackage(pkg)) {
            throw new IllegalArgumentException("Package decoding requires a bounded single-item tag");
        }
        SatelliteItemData.DecodeResult identity = SatelliteItemData.readTag(
                pkg.getTagType("tag") == Tag.TAG_COMPOUND ? pkg.getCompound("tag") : null);
        Optional<SatelliteIdentity> known = identity.identity();
        String rawId = pkg.getString("id");
        ResourceLocation id = rawId.length() > 255 ? null : ResourceLocation.tryParse(rawId);
        if (id == null) {
            return raw(Availability.INVALID_PACKAGE, known);
        }
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (item == null || item == Items.AIR) {
            return raw(Availability.PACKAGE_UNAVAILABLE, known);
        }
        if (item != ModItems.SATELLITE_PACKAGE.get() && item != ModItems.DATA_SATELLITE_PACKAGE.get()) {
            return raw(Availability.INVALID_PACKAGE, known);
        }
        if (identity.status() == SatelliteItemData.DecodeStatus.FUTURE) {
            return raw(Availability.UNSUPPORTED_DATA, known);
        }
        ItemStack stack = ItemStack.of(pkg.copy());
        if (stack.isEmpty() || stack.getCount() != 1 || !pkg.equals(stack.serializeNBT())
                || !SatelliteTerminalInventory.validPackage(stack)) {
            return raw(Availability.INVALID_PACKAGE, known);
        }
        return new Decoded(Availability.AVAILABLE, known, stack);
    }

    /**
     * The native one-item tag of a stack offered for insertion, or empty when it could not be kept byte for byte:
     * outside the native item bound, not exactly one item, or not an available package.
     */
    public static Optional<CompoundTag> capture(ItemStack offered) {
        if (offered.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag pkg = offered.copyWithCount(1).serializeNBT();
        if (!SatelliteBayRoot.validPackage(pkg) || !decode(pkg).available()) {
            return Optional.empty();
        }
        return Optional.of(pkg);
    }

    private static Decoded raw(Availability availability, Optional<SatelliteIdentity> identity) {
        return new Decoded(availability, identity, ItemStack.EMPTY);
    }
}
