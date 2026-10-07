package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import com.google.gson.JsonElement;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicGuardedResourceAccess;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Whole native/quantitative/cut proof; no replay tag resolution or resource publication. */
final class ClassicCheckpointChecks {
    /** Necessary data exclusions only; this predicate cannot issue installed authority. */
    static boolean ordinaryCapabilityAllowed(ClassicMachineState machine,
            java.util.Optional<ProcessTransactionJournal> journal) {
        return machine.nativePlan().isEmpty() && journal.isEmpty() && machine.dropQuarantine().isEmpty();
    }

    /** Exact bounded set comparison; the caller separately proves every live identity/binding. */
    static boolean completeAssignments(List<ClassicAssignment> expected, List<ClassicAssignment> observed) {
        return expected.size() <= 64 && observed.size() == expected.size()
                && new HashSet<>(expected).size() == expected.size()
                && new HashSet<>(observed).size() == observed.size()
                && new HashSet<>(expected).equals(new HashSet<>(observed));
    }

    static void validate(ClassicControllerFrame frame, GuardTicket ticket) {
        ticket.requireValid();
        CompoundTag resources = ClassicGuardedResourceAccess.encode(frame.resources(), ticket::requireValid);
        ClassicValidatedRecipe recipe = null;
        if (frame.machine().process() instanceof ClassicProcessFrame.Work work) {
            recipe = ticket.controller().matchingRecipe(work.recipeId(), work.jsonSignature(), ticket);
            validateWork(work, recipe);
        }
        if (frame.machine().dropQuarantine().isPresent()) {
            for (ClassicUncertainDrop entry : frame.machine().dropQuarantine().orElseThrow().uncertain()) {
                ClassicValueChecks.require(!payload(resources, entry.bankKey().channel(), entry.slot()).isEmpty(),
                        "Quarantined native slot is no longer retained");
            }
        }
        if (frame.machine().nativePlan().isEmpty()) { ticket.requireValid(); return; }
        ClassicNativePlan plan = frame.machine().nativePlan().orElseThrow();
        ProcessTransactionJournal journal = frame.journal().orElseThrow();
        ClassicValueChecks.require(recipe != null, "Native plan has no matching work recipe");
        validateRows(plan, recipe);
        ProcessResourceSnapshot before = quantitative(plan, true);
        ProcessResourceSnapshot after = quantitative(plan, false);
        ClassicValueChecks.require(journal.before().equals(before) && journal.after().equals(after)
                && journal.portRevision() == plan.beforeRevision()
                && journal.beforeFingerprint().equals(before.fingerprint()), "Selected journal quantities/fingerprint");
        for (ClassicNativeEntry entry : plan.entries()) {
            if (entry.before().amount() > 0) { ClassicValueChecks.require(entry.before().capacity(ticket) == entry.capacity(), "Before native capacity"); }
            if (entry.after().amount() > 0) { ClassicValueChecks.require(entry.after().capacity(ticket) == entry.capacity(), "After native capacity"); }
        }
        CompoundTag beforeRoot = replaced(resources, plan, true, ticket);
        CompoundTag afterRoot = replaced(resources, plan, false, ticket);
        ClassicValueChecks.require(ClassicNbtCanonicalHash.sha256(beforeRoot, ClassicNbtLimits.RESOURCES).equals(plan.beforeNativeHash())
                && ClassicNbtCanonicalHash.sha256(afterRoot, ClassicNbtLimits.RESOURCES).equals(plan.afterNativeHash()), "Whole native hashes");
        boolean nativeBefore = resources.equals(beforeRoot);
        boolean nativeAfter = resources.equals(afterRoot);
        boolean marked = frame.machine().lastAppliedTransaction().filter(plan.transactionId()::equals).isPresent();
        ClassicValueChecks.require(nativeBefore || nativeAfter, "Third native resource state");
        ClassicValueChecks.require(!marked || nativeAfter, "Applied marker with non-after resources");
        switch (journal.phase()) {
            case PREPARED -> ClassicValueChecks.require(nativeBefore && !marked, "Prepared native cut");
            case APPLYING -> ClassicValueChecks.require(nativeAfter || !marked, "Applying native cut");
            case APPLIED -> ClassicValueChecks.require(nativeAfter && marked, "Applied native cut");
        }
        ticket.requireValid();
    }

    static ProcessResourceSnapshot quantitative(ClassicNativePlan plan, boolean before) {
        var balances = new TreeMap<ProcessResourceKey, ProcessResourceBalance>();
        for (ClassicNativeEntry entry : plan.entries()) {
            balances.put(entry.quantitativeKey(), new ProcessResourceBalance(
                    (before ? entry.before() : entry.after()).amount(), entry.capacity()));
        }
        return new ProcessResourceSnapshot(before ? plan.beforeRevision() : plan.afterRevision(), balances);
    }

    private static CompoundTag replaced(CompoundTag current, ClassicNativePlan plan, boolean before, GuardTicket ticket) {
        CompoundTag result = current.copy();
        result.putLong("revision", before ? plan.beforeRevision() : plan.afterRevision());
        for (ClassicNativeEntry entry : plan.entries()) {
            CompoundTag bank = bank(result, entry.bankKey().channel());
            CompoundTag replacement = (before ? entry.before() : entry.after()).detached(ticket);
            if (entry.slot().isPresent()) { ((ListTag) bank.get("items")).set(entry.slot().getAsInt(), replacement); }
            else { bank.put("fluid", replacement); }
        }
        ClassicFieldCodec.bounded(result, ClassicNbtLimits.RESOURCES); return result;
    }

    private static CompoundTag bank(CompoundTag resources, String channel) {
        for (var value : ClassicFieldCodec.list(resources, "banks", 64)) {
            CompoundTag bank = (CompoundTag) value;
            if (bank.getString("channel").equals(channel)) { return bank; }
        }
        throw new IllegalArgumentException("Recorded plan bank is not retained");
    }

    private static CompoundTag payload(CompoundTag resources, String channel, int slot) {
        CompoundTag bank = bank(resources, channel);
        return ((ListTag) bank.get("items")).getCompound(slot);
    }

    static void validateRows(ClassicNativePlan plan, ClassicValidatedRecipe recipe) {
        ClassicValueChecks.require(recipe.id().equals(plan.recipeId()) && recipe.jsonSignature().equals(plan.jsonSignature()),
                "Recipe identity/signature changed");
        var seen = new HashSet<String>();
        for (ClassicAllocation row : plan.allocations()) {
            String identity = row.input() + ":" + row.kind() + ":" + row.recipeIndex();
            ClassicValueChecks.require(seen.add(identity), "Duplicate actual recipe row");
            if (row.kind() == ProcessResourceKind.ITEM && row.input()) {
                ClassicValueChecks.require(row.recipeIndex() < recipe.itemInputs().size(), "Unknown Item input row");
                ClassicItemInput input = recipe.itemInputs().get(row.recipeIndex());
                ClassicValueChecks.require(row.amount() == input.count(), "Item input allocation amount");
                JsonElement ingredient = ClassicRecipeCodec.parse(input.canonicalIngredientJson());
                List<JsonElement> options = ingredient.isJsonArray() ? ingredient.getAsJsonArray().asList() : List.of(ingredient);
                boolean tag = options.stream().map(JsonElement::getAsJsonObject).anyMatch(option -> option.has("tag"));
                if (!tag) {
                    boolean direct = options.stream().map(JsonElement::getAsJsonObject)
                            .map(option -> net.minecraft.resources.ResourceLocation.tryParse(option.get("item").getAsString()).toString())
                            .anyMatch(row.resourceId()::equals);
                    ClassicValueChecks.require(direct, "Recorded direct Item alternative");
                }
                // A recorded tag selection is not authenticated by today's tag membership.
                // First planning resolves tags; replay checks concrete conserved native entries.
            } else if (row.kind() == ProcessResourceKind.ITEM) {
                ClassicValueChecks.require(row.recipeIndex() < recipe.itemOutputs().size(), "Unknown Item output row");
                ClassicItemOutput output = recipe.itemOutputs().get(row.recipeIndex());
                ClassicValueChecks.require(row.amount() == output.count() && row.resourceId().equals(output.itemId().toString()), "Item output row");
            } else {
                List<ClassicFluidRow> rows = row.input() ? recipe.fluidInputs() : recipe.fluidOutputs();
                ClassicValueChecks.require(row.recipeIndex() < rows.size(), "Unknown Fluid row");
                ClassicFluidRow fluid = rows.get(row.recipeIndex());
                ClassicValueChecks.require(row.amount() == fluid.amount() && row.resourceId().equals(fluid.fluidId().toString()), "Fluid row");
            }
        }
        int expected = recipe.itemInputs().size() + recipe.itemOutputs().size()
                + recipe.fluidInputs().size() + recipe.fluidOutputs().size();
        ClassicValueChecks.require(seen.size() == expected, "Missing actual recipe allocation row");
    }

    static void validateWork(ClassicProcessFrame.Work work, ClassicValidatedRecipe recipe) {
        ClassicValueChecks.require(work.recipeId().equals(recipe.id()) && work.jsonSignature().equals(recipe.jsonSignature())
                && work.durationTicks() == recipe.processingTicks() && work.energyPerTick() == recipe.energyPerTick(),
                "Work differs from exact current recipe ID/JSON signature");
    }

    static void transition(ClassicControllerFrame before, ClassicControllerFrame after,
                           ClassicNativePlan sealed, GuardTicket ticket) {
        ticket.requireValid();
        ClassicMachineState old = before.machine(), next = after.machine();
        ClassicValueChecks.require(old.machineKind().equals(next.machineKind()) && old.machineId().equals(next.machineId())
                && old.ownerLevel().equals(next.ownerLevel()) && old.ownerPosition().equals(next.ownerPosition())
                && old.generation() == next.generation() && old.rotation() == next.rotation()
                && old.formationState() == next.formationState() && old.assignments().equals(next.assignments())
                && old.batchOrdinal() == next.batchOrdinal() && old.refusal().equals(next.refusal())
                && old.dropQuarantine().equals(next.dropQuarantine()), "Transaction changed fixed owner fields");
        ClassicNativePlan plan = old.nativePlan().orElseGet(() -> next.nativePlan().orElseThrow());
        if (sealed != null) { ClassicValueChecks.require(plan == sealed, "Different sealed native plan"); }
        if (next.nativePlan().isPresent()) {
            ClassicValueChecks.require(next.nativePlan().orElseThrow() == plan && old.process().equals(next.process())
                    && before.signatureMarker().equals(after.signatureMarker()), "Transaction changed plan/work/marker");
        }
        int from = cut(before, plan), to = cut(after, plan);
        ClassicValueChecks.require(to == from + 1, "Invalid transaction cut sequence");
        if (from == 0) {
            ClassicValueChecks.require(sealed == null && ticket.purpose() == ClassicTicketPurpose.COMPLETION
                    && old.process() instanceof ClassicProcessFrame.Work work && work.progressTicks() == work.durationTicks()
                    && before.resources().revision() == plan.beforeRevision()
                    && before.machine().lastAppliedTransaction().equals(after.machine().lastAppliedTransaction()), "First prepared cut");
            ClassicValueChecks.require(ClassicNativeHash.resources(before.resources(), ticket).equals(plan.beforeNativeHash()),
                    "First plan differs from whole current resources");
            ClassicValidatedRecipe recipe = ticket.controller().matchingRecipe(plan.recipeId(), plan.jsonSignature(), ticket);
            validateRows(plan, recipe);
            for (ClassicAllocation row : plan.allocations()) {
                if (row.input() && row.kind() == ProcessResourceKind.ITEM) {
                    ClassicValueChecks.require(recipe.resolvedItemAlternatives(row.recipeIndex(), ticket).contains(row.resourceId()),
                            "First plan current Item/tag eligibility");
                }
            }
        }
        if (from < 3) {
            ClassicValueChecks.require(before.machine().lastAppliedTransaction().equals(after.machine().lastAppliedTransaction()),
                    "Early applied marker change");
        } else {
            ClassicValueChecks.require(next.lastAppliedTransaction().filter(plan.transactionId()::equals).isPresent(), "Applied transaction marker");
        }
        if (to == 6) {
            ClassicValueChecks.require(next.process() instanceof ClassicProcessFrame.Idle && next.nativePlan().isEmpty()
                    && after.journal().isEmpty() && after.signatureMarker().recipeId().isEmpty(), "Finalization is not whole");
        }
        String hash = ClassicNativeHash.resources(after.resources(), ticket);
        ClassicValueChecks.require(hash.equals(to < 3 ? plan.beforeNativeHash() : plan.afterNativeHash()), "Cut changed unselected native data");
        ticket.requireValid();
    }

    private static int cut(ClassicControllerFrame frame, ClassicNativePlan plan) {
        if (frame.machine().nativePlan().isEmpty()) {
            return frame.machine().process() instanceof ClassicProcessFrame.Idle ? 6 : 0;
        }
        ClassicValueChecks.require(frame.machine().nativePlan().orElseThrow() == plan, "Cut plan identity");
        var phase = frame.journal().orElseThrow().phase();
        boolean after = frame.resources().revision() == plan.afterRevision();
        boolean marked = frame.machine().lastAppliedTransaction().filter(plan.transactionId()::equals).isPresent();
        return switch (phase) {
            case PREPARED -> 1;
            case APPLYING -> !after ? 2 : marked ? 4 : 3;
            case APPLIED -> 5;
        };
    }

    private ClassicCheckpointChecks() { }
}
