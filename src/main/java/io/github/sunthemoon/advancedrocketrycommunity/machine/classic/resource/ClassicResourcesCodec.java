package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Independent schema 1; strict preflight precedes copies, native decode and recursive equality. */
public final class ClassicResourcesCodec {
    public static final String ROOT = "arce_classic_resources";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 32_768;
    // Proposed structural caps, separate from the unchanged process journal and other roots.
    public static final int MAX_DEPTH = 16;
    public static final int MAX_NODES = 8_192;
    private static final Set<String> ROOT_FIELDS = Set.of("schema_version", "machine_uuid", "revision", "banks");
    private static final Set<String> ITEM_FIELDS = Set.of("channel", "kind", "x", "y", "z", "items");
    private static final Set<String> FLUID_FIELDS = Set.of("channel", "kind", "x", "y", "z", "fluid");

    public static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES); }

    public static CompoundTag encode(ClassicResources resources) {
        Objects.requireNonNull(resources, "resources");
        CompoundTag root = frame(resources);
        if (!bounded(root)) { throw new IllegalArgumentException("Classic resources exceed aggregate budget"); }
        return root.copy();
    }

    static void requireAggregate(ClassicResources resources) {
        if (!bounded(frame(resources))) { throw new IllegalArgumentException("Classic resources exceed aggregate budget"); }
    }

    private static CompoundTag frame(ClassicResources resources) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", SCHEMA_VERSION);
        root.putUUID("machine_uuid", resources.machineId());
        root.putLong("revision", resources.revision());
        ListTag banks = new ListTag();
        resources.banks().forEach(bank -> banks.add(bank.frame()));
        root.put("banks", banks);
        return root;
    }

    /** Pass the required root itself (null means missing), never a fresh fallback root. */
    public static ClassicResourcesDecode decode(Tag raw, UUID expectedMachineId) {
        Objects.requireNonNull(expectedMachineId, "expectedMachineId");
        if (raw == null) { return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.MISSING, null); }
        if (!bounded(raw)) { return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.UNBOUNDED, raw); }
        if (!(raw instanceof CompoundTag root) || !root.contains("schema_version", Tag.TAG_INT)) {
            return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.INVALID_DATA, raw);
        }
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.UNSUPPORTED_SCHEMA, raw);
        }
        try {
            if (!root.getAllKeys().equals(ROOT_FIELDS) || !root.hasUUID("machine_uuid")
                    || !root.contains("revision", Tag.TAG_LONG) || root.getLong("revision") < 0
                    || !root.contains("banks", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Invalid resource root fields/types");
            }
            UUID machineId = root.getUUID("machine_uuid");
            if (!expectedMachineId.equals(machineId)) {
                return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.OWNER_CONFLICT, raw);
            }
            ListTag list = (ListTag) root.get("banks");
            if (list.size() > ClassicResources.MAX_BANKS
                    || list.getElementType() != (list.isEmpty() ? Tag.TAG_END : Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Invalid retained bank list");
            }
            // Check every bank's structure/key/envelope before copying or decoding any native payload.
            var seen = new HashSet<ClassicBankKey>();
            for (Tag element : list) {
                if (!seen.add(preflightBank((CompoundTag) element))) {
                    throw new IllegalArgumentException("Duplicate retained bank key");
                }
            }
            List<ClassicResourceBank> banks = new ArrayList<>(list.size());
            for (Tag element : list) { banks.add(decodeBank((CompoundTag) element)); }
            ClassicResources resources = ClassicResources.restore(machineId, root.getLong("revision"), banks);
            if (!root.equals(frame(resources))) { throw new IllegalArgumentException("Lossy resource root decoding"); }
            return ClassicResourcesDecode.supported(resources);
        } catch (RuntimeException invalid) {
            return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.INVALID_DATA, raw);
        }
    }

    private static ClassicBankKey preflightBank(CompoundTag bank) {
        if (!bank.contains("channel", Tag.TAG_STRING) || !bank.contains("kind", Tag.TAG_STRING)
                || !bank.contains("x", Tag.TAG_INT) || !bank.contains("y", Tag.TAG_INT) || !bank.contains("z", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Invalid bank key field types");
        }
        ClassicBankKey key = ClassicBankKey.parse(bank.getString("channel"));
        if (!key.kind().id().equals(bank.getString("kind"))
                || key.x() != bank.getInt("x") || key.y() != bank.getInt("y") || key.z() != bank.getInt("z")
                || !bank.getAllKeys().equals(key.kind().isItem() ? ITEM_FIELDS : FLUID_FIELDS)) {
            throw new IllegalArgumentException("Conflicting bank identity or fields");
        }
        if (key.kind().isItem()) {
            if (!bank.contains("items", Tag.TAG_LIST)) { throw new IllegalArgumentException("Missing Item slots"); }
            ListTag slots = (ListTag) bank.get("items");
            if (slots.size() != ClassicResourceBank.ITEM_SLOTS || slots.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Item bank requires four compound slots");
            }
            for (Tag slot : slots) { ClassicNativePayload.preflightItem((CompoundTag) slot); }
        } else {
            if (!bank.contains("fluid", Tag.TAG_COMPOUND)) { throw new IllegalArgumentException("Missing Fluid payload"); }
            ClassicNativePayload.preflightFluid(bank.getCompound("fluid"));
        }
        return key;
    }

    private static ClassicResourceBank decodeBank(CompoundTag bank) {
        ClassicBankKey key = ClassicBankKey.parse(bank.getString("channel"));
        if (key.kind().isItem()) {
            List<ItemStack> items = new ArrayList<>(ClassicResourceBank.ITEM_SLOTS);
            for (Tag slot : (ListTag) bank.get("items")) {
                items.add(ClassicNativePayload.decodeItem((CompoundTag) slot));
            }
            return ClassicResourceBank.items(key, items);
        }
        return ClassicResourceBank.fluid(key, ClassicNativePayload.decodeFluid(bank.getCompound("fluid")));
    }

    private ClassicResourcesCodec() { }

    static ClassicResourcesDecode decode(Tag raw, UUID expectedMachineId, Runnable check) {
        Objects.requireNonNull(expectedMachineId, "expectedMachineId");
        ClassicGuardedResourceAccess.check(check);
        if (raw == null) { return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.MISSING, null); }
        if (!bounded(raw)) { return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.UNBOUNDED, raw); }
        if (!(raw instanceof CompoundTag root) || !root.contains("schema_version", Tag.TAG_INT)) {
            return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.INVALID_DATA, raw);
        }
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.UNSUPPORTED_SCHEMA, raw);
        }
        try {
            if (!root.getAllKeys().equals(ROOT_FIELDS) || !root.hasUUID("machine_uuid")
                    || !root.contains("revision", Tag.TAG_LONG) || root.getLong("revision") < 0
                    || !root.contains("banks", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Invalid resource root fields/types");
            }
            UUID machineId = root.getUUID("machine_uuid");
            if (!expectedMachineId.equals(machineId)) {
                return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.OWNER_CONFLICT, raw);
            }
            ListTag list = (ListTag) root.get("banks");
            if (list.size() > ClassicResources.MAX_BANKS
                    || list.getElementType() != (list.isEmpty() ? Tag.TAG_END : Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Invalid retained bank list");
            }
            var seen = new HashSet<ClassicBankKey>();
            for (Tag element : list) {
                if (!seen.add(preflightBank((CompoundTag) element, check))) {
                    throw new IllegalArgumentException("Duplicate retained bank key");
                }
            }
            List<ClassicResourceBank> banks = new ArrayList<>(list.size());
            for (Tag element : list) { banks.add(decodeBank((CompoundTag) element, check)); }
            ClassicGuardedResourceAccess.check(check);
            ClassicResources resources = ClassicResources.restore(machineId, root.getLong("revision"), banks);
            if (!root.equals(frame(resources))) { throw new IllegalArgumentException("Lossy resource root decoding"); }
            ClassicGuardedResourceAccess.check(check);
            return ClassicResourcesDecode.supported(resources);
        } catch (ClassicGuardedResourceAccess.WitnessFailure failed) {
            throw failed;
        } catch (RuntimeException invalid) {
            ClassicGuardedResourceAccess.check(check);
            return ClassicResourcesDecode.refused(ClassicResourcesDecode.Status.INVALID_DATA, raw);
        }
    }

    private static ClassicBankKey preflightBank(CompoundTag bank, Runnable check) {
        if (!bank.contains("channel", Tag.TAG_STRING) || !bank.contains("kind", Tag.TAG_STRING)
                || !bank.contains("x", Tag.TAG_INT) || !bank.contains("y", Tag.TAG_INT) || !bank.contains("z", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Invalid bank key field types");
        }
        ClassicBankKey key = ClassicBankKey.parse(bank.getString("channel"));
        if (!key.kind().id().equals(bank.getString("kind")) || key.x() != bank.getInt("x")
                || key.y() != bank.getInt("y") || key.z() != bank.getInt("z")
                || !bank.getAllKeys().equals(key.kind().isItem() ? ITEM_FIELDS : FLUID_FIELDS)) {
            throw new IllegalArgumentException("Conflicting bank identity or fields");
        }
        if (key.kind().isItem()) {
            if (!bank.contains("items", Tag.TAG_LIST)) { throw new IllegalArgumentException("Missing Item slots"); }
            ListTag slots = (ListTag) bank.get("items");
            if (slots.size() != ClassicResourceBank.ITEM_SLOTS || slots.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Item bank requires four compound slots");
            }
            for (Tag slot : slots) { ClassicNativePayload.preflightItem((CompoundTag) slot, check); }
        } else {
            if (!bank.contains("fluid", Tag.TAG_COMPOUND)) { throw new IllegalArgumentException("Missing Fluid payload"); }
            ClassicNativePayload.preflightFluid(bank.getCompound("fluid"), check);
        }
        return key;
    }

    private static ClassicResourceBank decodeBank(CompoundTag bank, Runnable check) {
        ClassicBankKey key = ClassicBankKey.parse(bank.getString("channel"));
        if (key.kind().isItem()) {
            List<ItemStack> items = new ArrayList<>(ClassicResourceBank.ITEM_SLOTS);
            for (Tag slot : (ListTag) bank.get("items")) {
                items.add(ClassicNativePayload.decodeItem((CompoundTag) slot, check));
            }
            return ClassicResourceBank.items(key, items, check);
        }
        return ClassicResourceBank.fluid(key, ClassicNativePayload.decodeFluid(bank.getCompound("fluid"), check), check);
    }
}
