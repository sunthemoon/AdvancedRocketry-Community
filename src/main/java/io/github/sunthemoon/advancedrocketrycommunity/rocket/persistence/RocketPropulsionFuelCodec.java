package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict independent schema for moving native content and captured plan debits. */
public final class RocketPropulsionFuelCodec {
    private RocketPropulsionFuelCodec() { }
    public static CompoundTag encode(RocketPropulsionFuel fuel) {
        CompoundTag root = new CompoundTag(); root.putInt("schema", 1);
        root.put("descriptor", RocketPropulsionCodec.encode(fuel.descriptor()));
        root.put("primary", bank(fuel.primary())); root.put("oxidizer", bank(fuel.oxidizer()));
        root.putLong("revision", fuel.revision());
        return root;
    }
    public static RocketPropulsionFuel decode(CompoundTag root) {
        require(root, Set.of("schema", "descriptor", "primary", "oxidizer", "revision"), 32_768);
        schema(root); long revision = number(root, "revision");
        return new RocketPropulsionFuel(RocketPropulsionCodec.decode(compound(root, "descriptor")),
                bank(compound(root, "primary")), bank(compound(root, "oxidizer")), revision);
    }
    private static CompoundTag bank(RocketFuelBank bank) {
        CompoundTag root = new CompoundTag(); root.putString("branch", bank.branch().name()); root.putLong("amount", bank.amount());
        if (bank.fluidId() != null) { root.putString("fluid", bank.fluidId().toString()); }
        if (bank.fluidTag() != null) { root.put("tag", bank.fluidTag()); }
        return root;
    }
    private static RocketFuelBank bank(CompoundTag root) {
        if (!root.contains("branch", Tag.TAG_STRING)) { throw new IllegalArgumentException("Invalid bank branch"); }
        var branch = RocketFuelBank.Branch.valueOf(root.getString("branch"));
        long amount = number(root, "amount");
        if (branch != RocketFuelBank.Branch.FLUID) {
            require(root, Set.of("branch", "amount"), 128);
            if (branch == RocketFuelBank.Branch.EMPTY && amount != 0 || branch == RocketFuelBank.Branch.ABSTRACT && amount == 0) {
                throw new IllegalArgumentException("Invalid empty bank");
            }
            return RocketFuelBank.abstractUnits(amount);
        }
        require(root, root.contains("tag") ? Set.of("branch", "amount", "fluid", "tag")
                : Set.of("branch", "amount", "fluid"), 9_216);
        if (!root.contains("fluid", Tag.TAG_STRING)) { throw new IllegalArgumentException("Missing Fluid ID"); }
        String name = root.getString("fluid"); var id = name.length() <= 128 && name.contains(":") ? ResourceLocation.tryParse(name) : null;
        if (id == null || !id.toString().equals(name) || amount == 0) { throw new IllegalArgumentException("Invalid Fluid ID"); }
        return RocketFuelBank.fluid(id, root.contains("tag") ? compound(root, "tag") : null, amount);
    }
    public static CompoundTag encodeDebit(RocketPropulsionDebit debit) {
        CompoundTag root = new CompoundTag(); root.putInt("schema", 1);
        root.putLong("primary", debit.primary()); root.putLong("oxidizer", debit.oxidizer()); root.putLong("revision", debit.revision());
        root.putString("resources", debit.resourceSignature()); root.putString("catalog", debit.catalogSignature()); return root;
    }
    public static RocketPropulsionDebit decodeDebit(CompoundTag root) {
        require(root, Set.of("schema", "primary", "oxidizer", "revision", "resources", "catalog"), 512); schema(root);
        if (!root.contains("resources", Tag.TAG_STRING) || !root.contains("catalog", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing debit signatures");
        }
        return new RocketPropulsionDebit(number(root, "primary"), number(root, "oxidizer"), number(root, "revision"),
                root.getString("resources"), root.getString("catalog"));
    }
    private static void require(CompoundTag root, Set<String> keys, int bytes) {
        if (!RocketNativeResources.bounded(root, bytes) || !root.getAllKeys().equals(keys)) { throw new IllegalArgumentException("Unknown propulsion fields"); }
    }
    private static void schema(CompoundTag root) {
        if (!root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != 1) { throw new IllegalArgumentException("Unknown propulsion schema"); }
    }
    private static long number(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_LONG) || root.getLong(key) < 0) { throw new IllegalArgumentException("Invalid propulsion amount"); }
        return root.getLong(key);
    }
    private static CompoundTag compound(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) { throw new IllegalArgumentException("Missing propulsion compound"); }
        return root.getCompound(key);
    }
}
