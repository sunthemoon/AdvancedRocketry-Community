package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicBankKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Private typed selected plan codec; caller holds the actual operation ticket. */
final class ClassicPlanCodec {
    static ClassicNativePlan decode(CompoundTag root, GuardTicket ticket) {
        ticket.requireValid();
        ClassicNativePlan structural = preflight(root);
        var entries = new ArrayList<ClassicNativeEntry>();
        for (Tag value : (ListTag) root.get("entries")) {
            CompoundTag entry = (CompoundTag) value;
            ClassicBankKey key = ClassicBankKey.parse(entry.getString("bank_channel"));
            entries.add(new ClassicNativeEntry(key, key.kind().isItem()
                    ? OptionalInt.of(ClassicFieldCodec.integer(entry, "slot")) : OptionalInt.empty(),
                    ClassicFieldCodec.id(entry, "resource_id").toString(), ClassicFieldCodec.number(entry, "capacity"),
                    OwnedNativeTag.captureBounded(entry.getCompound("before"), key.kind(), ticket),
                    OwnedNativeTag.captureBounded(entry.getCompound("after"), key.kind(), ticket)));
        }
        ticket.requireValid();
        // The preflight value is data only. Build a new plan from every guarded
        // native payload, then full frame/cut validation precedes owner publication.
        return new ClassicNativePlan(structural.transactionId(), structural.ownerGeneration(), structural.ordinal(),
                structural.recipeId(), structural.jsonSignature(), structural.beforeRevision(), structural.afterRevision(),
                structural.beforeNativeHash(), structural.afterNativeHash(), entries, structural.allocations());
    }

    static ClassicNativePlan preflight(CompoundTag root) {
        ClassicFieldCodec.bounded(root, ClassicNbtLimits.MACHINE);
        ClassicFieldCodec.fields(root, new String[] {"transaction_id", "owner_generation", "ordinal", "recipe_id",
                "json_signature", "before_revision", "after_revision", "before_native_hash", "after_native_hash",
                "entries", "allocations"});
        var entries = new ArrayList<ClassicNativeEntry>();
        // Complete envelopes/collections are inspected before the first native payload callback.
        ListTag rawEntries = ClassicFieldCodec.list(root, "entries", 64);
        ListTag rawAllocations = ClassicFieldCodec.list(root, "allocations", 12);
        ClassicValueChecks.require(!rawEntries.isEmpty() && !rawAllocations.isEmpty(), "Empty plan");
        for (Tag value : rawEntries) {
            CompoundTag entry = (CompoundTag) value;
            ClassicFieldCodec.fields(entry, new String[] {"bank_channel", "kind", "resource_id", "capacity", "before", "after"}, "slot");
            ClassicBankKey key = ClassicBankKey.parse(ClassicFieldCodec.string(entry, "bank_channel", 64));
            ClassicValueChecks.require(key.kind().id().equals(ClassicFieldCodec.string(entry, "kind", 32)), "Plan bank kind");
            ClassicValueChecks.require(entry.contains("slot") == key.kind().isItem(), "Plan slot presence");
            entries.add(new ClassicNativeEntry(key, key.kind().isItem()
                    ? OptionalInt.of(ClassicFieldCodec.integer(entry, "slot")) : OptionalInt.empty(),
                    ClassicFieldCodec.id(entry, "resource_id").toString(), ClassicFieldCodec.number(entry, "capacity"),
                    OwnedNativeTag.captureData(ClassicFieldCodec.compound(entry, "before"), key.kind()),
                    OwnedNativeTag.captureData(ClassicFieldCodec.compound(entry, "after"), key.kind())));
        }
        List<ClassicAllocation> allocations = allocations(rawAllocations);
        return new ClassicNativePlan(ClassicFieldCodec.uuid(root, "transaction_id"),
                ClassicFieldCodec.number(root, "owner_generation"), ClassicFieldCodec.number(root, "ordinal"),
                ClassicFieldCodec.id(root, "recipe_id"), ClassicFieldCodec.string(root, "json_signature", 64),
                ClassicFieldCodec.number(root, "before_revision"), ClassicFieldCodec.number(root, "after_revision"),
                ClassicFieldCodec.string(root, "before_native_hash", 64), ClassicFieldCodec.string(root, "after_native_hash", 64),
                entries, allocations);
    }

    private static List<ClassicAllocation> allocations(ListTag raw) {
        var result = new ArrayList<ClassicAllocation>();
        int totalParts = 0;
        for (Tag value : raw) {
            CompoundTag row = (CompoundTag) value;
            ClassicFieldCodec.fields(row, new String[] {"direction", "kind", "recipe_index", "resource_id", "amount", "parts"});
            String direction = ClassicFieldCodec.string(row, "direction", 16);
            ClassicValueChecks.require(direction.equals("input") || direction.equals("output"), "Allocation direction");
            var parts = new ArrayList<ClassicAllocationPart>();
            ListTag rawParts = ClassicFieldCodec.list(row, "parts", 64);
            totalParts = Math.addExact(totalParts, rawParts.size());
            ClassicValueChecks.require(totalParts <= 256, "Plan total part count");
            for (Tag partValue : rawParts) {
                CompoundTag part = (CompoundTag) partValue;
                ClassicFieldCodec.fields(part, new String[] {"entry_index", "amount"});
                parts.add(new ClassicAllocationPart(ClassicFieldCodec.integer(part, "entry_index"),
                        ClassicFieldCodec.number(part, "amount")));
            }
            result.add(new ClassicAllocation(direction.equals("input"), ClassicFieldCodec.enumeration(ProcessResourceKind.class,
                    ClassicFieldCodec.string(row, "kind", 16), true), ClassicFieldCodec.integer(row, "recipe_index"),
                    ClassicFieldCodec.id(row, "resource_id").toString(), ClassicFieldCodec.number(row, "amount"), parts));
        }
        return List.copyOf(result);
    }

    static CompoundTag encode(ClassicNativePlan plan, GuardTicket ticket) {
        ticket.requireValid();
        CompoundTag root = new CompoundTag(); root.putUUID("transaction_id", plan.transactionId());
        root.putLong("owner_generation", plan.ownerGeneration()); root.putLong("ordinal", plan.ordinal());
        root.putString("recipe_id", plan.recipeId().toString()); root.putString("json_signature", plan.jsonSignature());
        root.putLong("before_revision", plan.beforeRevision()); root.putLong("after_revision", plan.afterRevision());
        root.putString("before_native_hash", plan.beforeNativeHash()); root.putString("after_native_hash", plan.afterNativeHash());
        ListTag entries = new ListTag();
        for (ClassicNativeEntry value : plan.entries()) {
            CompoundTag entry = new CompoundTag(); entry.putString("bank_channel", value.bankKey().channel());
            entry.putString("kind", value.bankKey().kind().id()); entry.putString("resource_id", value.resourceId());
            entry.putLong("capacity", value.capacity()); value.slot().ifPresent(slot -> entry.putInt("slot", slot));
            entry.put("before", value.before().detached(ticket)); entry.put("after", value.after().detached(ticket));
            entries.add(entry);
        }
        root.put("entries", entries);
        ListTag allocations = new ListTag();
        for (ClassicAllocation value : plan.allocations()) {
            CompoundTag row = new CompoundTag(); row.putString("direction", value.input() ? "input" : "output");
            row.putString("kind", value.kind() == ProcessResourceKind.ITEM ? "item" : "fluid");
            row.putInt("recipe_index", value.recipeIndex()); row.putString("resource_id", value.resourceId());
            row.putLong("amount", value.amount()); ListTag parts = new ListTag();
            for (ClassicAllocationPart valuePart : value.parts()) {
                CompoundTag part = new CompoundTag(); part.putInt("entry_index", valuePart.entryIndex());
                part.putLong("amount", valuePart.amount()); parts.add(part);
            }
            row.put("parts", parts); allocations.add(row);
        }
        root.put("allocations", allocations); ticket.requireValid(); return root;
    }

    private ClassicPlanCodec() { }
}
