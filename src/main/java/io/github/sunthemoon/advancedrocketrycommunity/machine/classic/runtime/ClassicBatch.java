package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Detached native allocation and kernel transaction preflight; it never mutates an owner. */
final class ClassicBatch {
    record Plan(ClassicResources before, ClassicResources after, ProcessPlan kernel) { }
    record Result(Optional<Plan> plan, ClassicWait reason) { }
    private record Slot(ClassicBankKey key, int index) { }

    static Result prepare(ClassicResources resources, Set<ClassicBankKey> active,
                          ClassicMachineRecipe recipe, ProcessDefinition definition) {
        return prepare(resources, active, recipe, definition, List.of());
    }
    static Result prepare(ClassicResources resources, Set<ClassicBankKey> active,
                          ClassicMachineRecipe recipe, ProcessDefinition definition,
                          List<io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput> chosen) {
        var banks = new LinkedHashMap<ClassicBankKey, ClassicResourceBank>();
        resources.banks().stream().filter(bank -> active.contains(bank.key()))
                .sorted(java.util.Comparator.comparing(bank -> bank.key().channel())).forEach(bank -> banks.put(bank.key(), bank));
        var slots = new ArrayList<Slot>(); var supplies = new ArrayList<ClassicItemMatcher.Supply>();
        banks.values().stream().filter(bank -> bank.key().kind() == ClassicBankKind.ITEM_INPUT).forEach(bank -> {
            for (int i = 0; i < 4; i++) {
                ItemStack stack = bank.item(i); slots.add(new Slot(bank.key(), i));
                supplies.add(new ClassicItemMatcher.Supply(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
            }
        });
        var demands = new ArrayList<ClassicItemMatcher.Demand>();
        definition.inputs().stream().filter(input -> input.kind() == ProcessResourceKind.ITEM).forEach(input ->
                demands.add(new ClassicItemMatcher.Demand(Set.copyOf(input.alternatives()), Math.toIntExact(input.amount()))));
        var consumed = ClassicItemMatcher.consumed(supplies, demands);
        if (consumed.isEmpty()) { return refused(ClassicWait.INPUT); }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i); var bank = banks.get(slot.key()); ItemStack stack = bank.item(slot.index());
            ItemStack original = stack.copy(); int debit = consumed.orElseThrow()[i];
            stack.shrink(debit); banks.put(slot.key(), bank.withItem(slot.index(), stack));
            // Debit and restore the exact payload in the same channel/slot, never in the output hatch.
            if (recipe.retainsLens() && BuiltInRegistries.ITEM.getKey(original.getItem()).toString()
                    .equals("advancedrocketrycommunity:laser_lens") && debit > 0) {
                banks.put(slot.key(), banks.get(slot.key()).withItem(slot.index(), original));
            }
        }
        for (var row : recipe.rows().fluidInputs()) {
            int remaining = row.amount();
            for (var key : List.copyOf(banks.keySet())) {
                if (key.kind() != ClassicBankKind.FLUID_INPUT) { continue; }
                var bank = banks.get(key); FluidStack stack = bank.fluid();
                if (!stack.isEmpty() && BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(row.fluidId())) {
                    int amount = Math.min(remaining, stack.getAmount()); stack.shrink(amount); remaining -= amount;
                    banks.put(key, bank.withFluid(stack));
                }
            }
            if (remaining > 0) { return refused(ClassicWait.INPUT); }
        }
        var itemOutputs = new ArrayList<ItemStack>();
        for (int row = 0; row < recipe.rows().itemOutputs().size(); row++) { itemOutputs.add(recipe.output(row)); }
        for (var row : chosen) {
            ClassicMachineRecipe.stackLimit(row.itemId(), row.count());
            itemOutputs.add(new ItemStack(BuiltInRegistries.ITEM.get(row.itemId()), row.count()));
        }
        for (ItemStack output : itemOutputs) {
            int remaining = output.getCount();
            for (var key : List.copyOf(banks.keySet())) {
                if (key.kind() != ClassicBankKind.ITEM_OUTPUT) { continue; }
                for (int slot = 0; slot < 4 && remaining > 0; slot++) {
                    var bank = banks.get(key); ItemStack stack = bank.item(slot);
                    if (!stack.isEmpty() && !ItemStack.isSameItemSameTags(stack, output)) { continue; }
                    int count = Math.min(remaining, Math.min(64, output.getMaxStackSize()) - stack.getCount());
                    if (count <= 0) { continue; }
                    ItemStack replacement = output.copy(); replacement.setCount(stack.getCount() + count);
                    banks.put(key, bank.withItem(slot, replacement)); remaining -= count;
                }
            }
            if (remaining > 0) { return refused(ClassicWait.OUTPUT); }
        }
        for (var row : recipe.rows().fluidOutputs()) {
            int remaining = row.amount(); FluidStack output = new FluidStack(BuiltInRegistries.FLUID.get(row.fluidId()), row.amount());
            for (var key : List.copyOf(banks.keySet())) {
                if (key.kind() != ClassicBankKind.FLUID_OUTPUT) { continue; }
                var bank = banks.get(key); FluidStack stack = bank.fluid();
                if (!stack.isEmpty() && !stack.isFluidEqual(output)) { continue; }
                int amount = Math.min(remaining, 16_000 - stack.getAmount());
                FluidStack replacement = output.copy(); replacement.setAmount(stack.getAmount() + amount);
                banks.put(key, bank.withFluid(replacement)); remaining -= amount;
            }
            if (remaining > 0) { return refused(ClassicWait.OUTPUT); }
        }
        ClassicResources after = resources.replace(List.copyOf(banks.values()), false);
        // The native allocation already proved slot/tag semantics. Each normalized row
        // represents exactly the quantity allocated to that input/output, not a second bank.
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new LinkedHashMap<>();
        for (ProcessInput input : definition.inputs()) {
            balances.put(input.key(input.alternatives().get(0)), new ProcessResourceBalance(input.amount(), input.amount()));
        }
        var outputs = new ArrayList<>(definition.outputs());
        for (int i = 0; i < chosen.size(); i++) {
            var row = chosen.get(i);
            outputs.add(new ProcessOutput(new ProcessResourceKey(ProcessResourceKind.ITEM, "chance_output." + i,
                    row.itemId().toString()), row.count()));
        }
        for (ProcessOutput output : outputs) { balances.putIfAbsent(output.key(), new ProcessResourceBalance(0, output.amount())); }
        var selectedDefinition = new ProcessDefinition(definition.id(), definition.schemaVersion(), definition.encodedSizeBytes(),
                definition.durationTicks(), definition.energyPerTick(), definition.inputs(), outputs);
        var simulated = ProcessMachineLogic.simulate(selectedDefinition, new ProcessResourceSnapshot(resources.revision(), balances));
        ProcessPlan plan = simulated.plan().orElseThrow();
        ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(UUID.randomUUID(), resources.machineId(), plan));
        return new Result(Optional.of(new Plan(resources, after, plan)), ClassicWait.RUNNING);
    }

    /** Kernel journal phases remain detached until the controller publishes the entire checkpoint. */
    static boolean verifyCommit(Plan plan, UUID machine, UUID transaction) {
        var scratch = new Scratch(plan.kernel().before());
        return ProcessTransactionExecutor.commit(transaction, machine, plan.kernel(), scratch, scratch).failure().equals(ProcessFailure.NONE);
    }
    private static final class Scratch implements ProcessResourceStore, ProcessJournalStore {
        ProcessResourceSnapshot snapshot; UUID applied; ProcessTransactionJournal journal;
        Scratch(ProcessResourceSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public ProcessResourceSnapshot snapshot() { return snapshot; }
        @Override public boolean replaceIfMatches(ProcessResourceSnapshot before, ProcessResourceSnapshot after) {
            if (!snapshot.equals(before)) { return false; } snapshot = after; return true;
        }
        @Override public Optional<UUID> lastAppliedTransactionId() { return Optional.ofNullable(applied); }
        @Override public void markApplied(UUID id) { applied = id; }
        @Override public Optional<ProcessTransactionJournal> load() { return Optional.ofNullable(journal); }
        @Override public void save(ProcessTransactionJournal value) { journal = value; }
        @Override public void clear(UUID id) { if (journal != null && journal.transactionId().equals(id)) { journal = null; } }
    }
    private static Result refused(ClassicWait reason) { return new Result(Optional.empty(), reason); }
    private ClassicBatch() { }
}
