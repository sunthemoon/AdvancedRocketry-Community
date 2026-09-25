package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalPhase;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceStore;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessSimulationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Bounded server-thread transaction over five inputs and two independent outputs. */
final class PrecisionAssemblerResourceStore implements ProcessResourceStore {
    private static final List<String> ITEM_CHANNELS = List.of(
            PrecisionAssemblerChannels.input(0), PrecisionAssemblerChannels.input(1),
            PrecisionAssemblerChannels.input(2), PrecisionAssemblerChannels.input(3),
            PrecisionAssemblerChannels.input(4), PrecisionAssemblerChannels.output(0),
            PrecisionAssemblerChannels.output(1)
    );
    private final ServerLevel level;
    private final PrecisionAssemblerBlockEntity controller;
    private final PrecisionAssemblerProcessController process;
    private final PrecisionAssemblerRecipe recipe;
    private final PrecisionAssemblerPortSet ports;

    PrecisionAssemblerResourceStore(
            ServerLevel level,
            PrecisionAssemblerBlockEntity controller,
            PrecisionAssemblerProcessController process,
            PrecisionAssemblerRecipe recipe,
            PrecisionAssemblerPortSet ports
    ) {
        this.level = java.util.Objects.requireNonNull(level, "level");
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
        this.process = java.util.Objects.requireNonNull(process, "process");
        this.recipe = java.util.Objects.requireNonNull(recipe, "recipe");
        this.ports = java.util.Objects.requireNonNull(ports, "ports");
    }

    boolean inputsMatchRecipe() {
        requireUsablePorts();
        return recipe.matches(new SimpleContainer(inputStacks().toArray(ItemStack[]::new)), level);
    }

    @Override
    public ProcessResourceSnapshot snapshot() {
        requireUsablePorts();
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new TreeMap<>();
        for (int index = 0; index < ports.inputs().size(); index++) {
            ItemStack stack = ports.inputs().get(index).storedItemCopy();
            String channel = PrecisionAssemblerChannels.input(index);
            if (index < recipe.ingredientAlternatives().size()) {
                for (String alternative : recipe.ingredientAlternatives().get(index)) {
                    ResourceLocation id = ResourceLocation.tryParse(alternative);
                    var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                    if (item == null || item.getDefaultInstance().isEmpty()) {
                        throw new IllegalStateException("Precision Assembler ingredient is no longer registered");
                    }
                    balances.put(key(channel, alternative), new ProcessResourceBalance(
                            !stack.isEmpty() && alternative.equals(itemId(stack)) ? stack.getCount() : 0,
                            item.getMaxStackSize()
                    ));
                }
            }
            if (!stack.isEmpty() && !balances.containsKey(key(channel, itemId(stack)))) {
                balances.put(key(channel, itemId(stack)),
                        new ProcessResourceBalance(stack.getCount(), stack.getMaxStackSize()));
            }
        }
        List<ItemStack> results = recipe.outputs();
        for (int index = 0; index < ports.outputs().size(); index++) {
            String channel = PrecisionAssemblerChannels.output(index);
            ItemStack stored = ports.outputs().get(index).storedItemCopy();
            if (index >= results.size()) {
                if (!stored.isEmpty()) {
                    balances.put(key(channel, itemId(stored)),
                            new ProcessResourceBalance(stored.getCount(), stored.getMaxStackSize()));
                }
                continue;
            }
            ItemStack result = results.get(index);
            String resultId = itemId(result);
            if (!stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, result)) {
                balances.put(key(channel, itemId(stored)),
                        new ProcessResourceBalance(stored.getCount(), stored.getMaxStackSize()));
                balances.put(key(channel, resultId), new ProcessResourceBalance(0, 0));
            } else {
                balances.put(key(channel, resultId), new ProcessResourceBalance(
                        stored.getCount(), result.getMaxStackSize()
                ));
            }
        }
        return new ProcessResourceSnapshot(process.resourceRevision(), balances);
    }

    @Override
    public boolean replaceIfMatches(
            ProcessResourceSnapshot expected,
            ProcessResourceSnapshot replacement
    ) {
        if (!snapshot().equals(expected) || !inputsMatchRecipe()) {
            return false;
        }
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(), expected
        );
        if (simulation.plan().isEmpty()
                || !simulation.plan().orElseThrow().after().equals(replacement)) {
            return false;
        }

        List<ItemStack> beforeInputs = inputStacks();
        List<ItemStack> beforeOutputs = outputStacks();
        List<ItemStack> afterInputs = copyStacks(beforeInputs);
        List<ItemStack> afterOutputs = copyStacks(beforeOutputs);
        for (int index = 0; index < recipe.inputs().size(); index++) {
            afterInputs.get(index).shrink(recipe.inputs().get(index).count());
        }
        List<ItemStack> results = recipe.outputs();
        for (int index = 0; index < results.size(); index++) {
            afterOutputs.set(index, mergeOutput(afterOutputs.get(index), results.get(index)));
        }

        long beforeRevision = process.resourceRevision();
        try {
            for (int index = 0; index < afterInputs.size(); index++) {
                ports.inputs().get(index).replaceStoredItemInternal(afterInputs.get(index));
            }
            for (int index = 0; index < afterOutputs.size(); index++) {
                ports.outputs().get(index).replaceStoredItemInternal(afterOutputs.get(index));
            }
            process.replaceResourceRevision(expected.revision(), replacement.revision());
            if (!snapshot().equals(replacement)) {
                throw new IllegalStateException("Precision Assembler final resources differ from the process plan");
            }
            return true;
        } catch (RuntimeException failure) {
            rollback(beforeInputs, beforeOutputs, beforeRevision, failure);
            throw failure;
        }
    }

    /** Replays a persisted journal only when every physical slot is before or after that batch. */
    boolean reconcileJournal(ProcessTransactionJournal journal) {
        requireUsablePorts();
        ProcessResourceSnapshot actual = snapshot();
        ProcessResourceSnapshot before = journal.before();
        ProcessResourceSnapshot after = journal.after();
        boolean appliedMarker = process.lastAppliedTransactionId()
                .filter(journal.transactionId()::equals).isPresent();
        boolean invalidPrepared = journal.phase() == ProcessJournalPhase.PREPARED
                && (actual.revision() != before.revision() || appliedMarker);
        boolean invalidApplied = journal.phase() == ProcessJournalPhase.APPLIED
                && (actual.revision() != after.revision() || !appliedMarker);
        if ((actual.revision() != before.revision() && actual.revision() != after.revision())
                || invalidPrepared || invalidApplied
                || (appliedMarker && actual.revision() != after.revision())
                || !sameShape(actual, before) || !sameShape(before, after)) {
            return false;
        }
        List<ItemStack> beforeStacks;
        List<ItemStack> afterStacks;
        try {
            beforeStacks = physicalStacks(before);
            afterStacks = physicalStacks(after);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        List<ItemStack> current = new ArrayList<>(inputStacks());
        current.addAll(outputStacks());
        for (int index = 0; index < current.size(); index++) {
            if (!sameStack(current.get(index), beforeStacks.get(index))
                    && !sameStack(current.get(index), afterStacks.get(index))) {
                return false;
            }
        }

        List<ItemStack> oldInputs = inputStacks();
        List<ItemStack> oldOutputs = outputStacks();
        long oldRevision = process.resourceRevision();
        try {
            for (int index = 0; index < oldInputs.size(); index++) {
                ports.inputs().get(index).replaceStoredItemInternal(afterStacks.get(index));
            }
            for (int index = 0; index < oldOutputs.size(); index++) {
                ports.outputs().get(index).replaceStoredItemInternal(
                        afterStacks.get(oldInputs.size() + index));
            }
            process.restoreResourceRevision(after.revision());
            if (!snapshot().equals(after)) {
                throw new IllegalStateException("Precision Assembler replay differs from its journal");
            }
        } catch (RuntimeException failure) {
            rollback(oldInputs, oldOutputs, oldRevision, failure);
            throw failure;
        }
        process.markApplied(journal.transactionId());
        return true;
    }

    private static boolean sameShape(ProcessResourceSnapshot left, ProcessResourceSnapshot right) {
        if (!left.balances().keySet().equals(right.balances().keySet())) {
            return false;
        }
        return left.balances().keySet().stream().allMatch(key ->
                left.balance(key).capacity() == right.balance(key).capacity());
    }

    private static List<ItemStack> physicalStacks(ProcessResourceSnapshot snapshot) {
        if (snapshot.balances().keySet().stream().anyMatch(key ->
                key.kind() != ProcessResourceKind.ITEM || !ITEM_CHANNELS.contains(key.channel()))) {
            throw new IllegalArgumentException("Journal contains an unrelated Precision resource");
        }
        List<ItemStack> stacks = new ArrayList<>(ITEM_CHANNELS.size());
        for (String channel : ITEM_CHANNELS) {
            ItemStack selected = ItemStack.EMPTY;
            for (var entry : snapshot.balances().entrySet()) {
                if (!entry.getKey().channel().equals(channel)) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey().resourceId());
                var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null || item.getDefaultInstance().isEmpty()
                        || entry.getValue().capacity() != item.getMaxStackSize()) {
                    throw new IllegalArgumentException("Journal contains an invalid Item capacity");
                }
                long amount = entry.getValue().amount();
                if (amount > 0) {
                    if (!selected.isEmpty() || amount > item.getMaxStackSize()) {
                        throw new IllegalArgumentException("Journal contains multiple or oversized Item stacks");
                    }
                    selected = new ItemStack(item, Math.toIntExact(amount));
                }
            }
            stacks.add(selected);
        }
        return stacks;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) {
            return left.isEmpty() && right.isEmpty();
        }
        return left.getCount() == right.getCount() && ItemStack.isSameItemSameTags(left, right);
    }

    @Override
    public Optional<UUID> lastAppliedTransactionId() {
        return process.lastAppliedTransactionId();
    }

    @Override
    public void markApplied(UUID transactionId) {
        process.markApplied(transactionId);
    }

    private void rollback(
            List<ItemStack> inputs,
            List<ItemStack> outputs,
            long revision,
            RuntimeException original
    ) {
        try {
            for (int index = 0; index < inputs.size(); index++) {
                ports.inputs().get(index).replaceStoredItemInternal(inputs.get(index));
            }
            for (int index = 0; index < outputs.size(); index++) {
                ports.outputs().get(index).replaceStoredItemInternal(outputs.get(index));
            }
            process.restoreResourceRevision(revision);
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private List<ItemStack> inputStacks() {
        return ports.inputs().stream().map(PrecisionAssemblerPortBlockEntity::storedItemCopy).toList();
    }

    private List<ItemStack> outputStacks() {
        return ports.outputs().stream().map(PrecisionAssemblerPortBlockEntity::storedItemCopy).toList();
    }

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copies = new ArrayList<>(source.size());
        source.forEach(stack -> copies.add(stack.copy()));
        return copies;
    }

    private void requireUsablePorts() {
        if (!ports.remainsUsable(level, controller)) {
            throw new IllegalStateException("Precision Assembler ports changed during a transaction");
        }
    }

    private static ItemStack mergeOutput(ItemStack existing, ItemStack addition) {
        if (existing.isEmpty()) {
            return addition.copy();
        }
        if (!ItemStack.isSameItemSameTags(existing, addition)) {
            throw new IllegalStateException("Precision Assembler output changed during commit");
        }
        ItemStack merged = existing.copy();
        merged.grow(addition.getCount());
        if (merged.getCount() > merged.getMaxStackSize()) {
            throw new IllegalStateException("Precision Assembler output exceeded its slot capacity");
        }
        return merged;
    }

    private static ProcessResourceKey key(String channel, String itemId) {
        return new ProcessResourceKey(ProcessResourceKind.ITEM, channel, itemId);
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || stack.isEmpty()) {
            throw new IllegalStateException("Precision Assembler resource has no registered Item identity");
        }
        return id.toString();
    }
}
