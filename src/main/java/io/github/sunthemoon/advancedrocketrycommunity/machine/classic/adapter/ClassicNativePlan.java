package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Read-only data plan. Construction does not validate a live recipe, owner or checkpoint. */
public final class ClassicNativePlan {
    private final UUID transactionId;
    private final long ownerGeneration;
    private final long ordinal;
    private final ResourceLocation recipeId;
    private final String jsonSignature;
    private final long beforeRevision;
    private final long afterRevision;
    private final String beforeNativeHash;
    private final String afterNativeHash;
    private final List<ClassicNativeEntry> entries;
    private final List<ClassicAllocation> allocations;

    ClassicNativePlan(UUID transactionId, long ownerGeneration, long ordinal, ResourceLocation recipeId,
                      String jsonSignature, long beforeRevision, long afterRevision, String beforeNativeHash,
                      String afterNativeHash, List<ClassicNativeEntry> entries, List<ClassicAllocation> allocations) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.recipeId = ClassicValueChecks.id(recipeId);
        this.jsonSignature = ClassicValueChecks.hash(jsonSignature);
        this.beforeNativeHash = ClassicValueChecks.hash(beforeNativeHash);
        this.afterNativeHash = ClassicValueChecks.hash(afterNativeHash);
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(allocations, "allocations");
        ClassicValueChecks.require(ownerGeneration >= 1 && ordinal >= 1
                && beforeRevision >= 0 && beforeRevision < Long.MAX_VALUE
                && afterRevision == beforeRevision + 1, "Plan revisions/counters");
        ClassicValueChecks.require(!entries.isEmpty() && entries.size() <= 64
                && !allocations.isEmpty() && allocations.size() <= 12, "Plan collections");
        ProcessResourceKey previous = null;
        var slots = new HashSet<String>();
        for (ClassicNativeEntry entry : entries) {
            ProcessResourceKey key = entry.quantitativeKey();
            ClassicValueChecks.require((previous == null || previous.compareTo(key) < 0)
                    && slots.add(key.channel()), "Plan entry order or physical duplicate");
            previous = key;
        }
        ClassicAllocation prior = null;
        long[] totals = new long[entries.size()];
        int partCount = 0;
        for (ClassicAllocation allocation : allocations) {
            ClassicValueChecks.require(prior == null || ClassicAllocation.ORDER.compare(prior, allocation) < 0,
                    "Allocation order or duplicate row");
            prior = allocation;
            partCount = Math.addExact(partCount, allocation.parts().size());
            ClassicValueChecks.require(partCount <= 256, "Total allocation part limit");
            for (ClassicAllocationPart part : allocation.parts()) {
                ClassicValueChecks.require(part.entryIndex() < entries.size(), "Allocation points outside entries");
                ClassicNativeEntry entry = entries.get(part.entryIndex());
                ClassicValueChecks.require(entry.bankKey().kind().isInput() == allocation.input()
                        && (entry.bankKey().kind().isItem() ? ProcessResourceKind.ITEM : ProcessResourceKind.FLUID)
                            == allocation.kind() && entry.resourceId().equals(allocation.resourceId()), "Allocation role/ID");
                totals[part.entryIndex()] = Math.addExact(totals[part.entryIndex()], part.amount());
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            ClassicNativeEntry entry = entries.get(i);
            long change = entry.bankKey().kind().isInput() ? entry.before().amount() - entry.after().amount()
                    : entry.after().amount() - entry.before().amount();
            ClassicValueChecks.require(totals[i] == change, "Allocation does not prove exact native change");
        }
        this.ownerGeneration = ownerGeneration;
        this.ordinal = ordinal;
        this.beforeRevision = beforeRevision;
        this.afterRevision = afterRevision;
        this.entries = List.copyOf(entries);
        this.allocations = List.copyOf(allocations);
    }

    public UUID transactionId() { return transactionId; }
    public ResourceLocation recipeId() { return recipeId; }
    public long ordinal() { return ordinal; }
    public int entryCount() { return entries.size(); }
    public String beforeNativeHash() { return beforeNativeHash; }
    public String afterNativeHash() { return afterNativeHash; }
    List<ClassicNativeEntry> entries() { return entries; }
    List<ClassicAllocation> allocations() { return allocations; }
    long ownerGeneration() { return ownerGeneration; }
    String jsonSignature() { return jsonSignature; }
    long beforeRevision() { return beforeRevision; }
    long afterRevision() { return afterRevision; }
}
