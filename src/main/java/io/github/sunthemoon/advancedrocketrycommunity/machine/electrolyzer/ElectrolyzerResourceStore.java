package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceStore;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessSimulationResult;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Atomic Item/Fluid resource adapter for one single-block Electrolyzer batch. */
final class ElectrolyzerResourceStore implements ProcessResourceStore {
    private final ElectrolyzerBlockEntity machine;
    private final ElectrolyzerProcessController process;
    private final ElectrolyzerRecipe recipe;

    ElectrolyzerResourceStore(
            ElectrolyzerBlockEntity machine,
            ElectrolyzerProcessController process,
            ElectrolyzerRecipe recipe
    ) {
        this.machine = java.util.Objects.requireNonNull(machine, "machine");
        this.process = java.util.Objects.requireNonNull(process, "process");
        this.recipe = java.util.Objects.requireNonNull(recipe, "recipe");
    }

    @Override
    public ProcessResourceSnapshot snapshot() {
        ItemStack input = machine.storedItemCopy(ElectrolyzerBlockEntity.SLOT_INPUT);
        ItemStack hydrogen = machine.storedItemCopy(ElectrolyzerBlockEntity.SLOT_HYDROGEN);
        ItemStack oxygen = machine.storedItemCopy(ElectrolyzerBlockEntity.SLOT_OXYGEN);
        FluidStack water = machine.storedWaterCopy();
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new TreeMap<>();

        String storedInputId = itemId(input).orElse("");
        for (String alternative : recipe.ingredientAlternatives()) {
            ResourceLocation alternativeId = ResourceLocation.tryParse(alternative);
            Item item = alternativeId == null
                    ? null
                    : BuiltInRegistries.ITEM.getOptional(alternativeId).orElse(null);
            if (item == null || item.getDefaultInstance().isEmpty()) {
                throw new IllegalStateException("Electrolyzer recipe alternative is no longer registered");
            }
            balances.put(
                    key(ProcessResourceKind.ITEM, ElectrolyzerPortPolicy.ITEM_INPUT_CHANNEL, alternative),
                    new ProcessResourceBalance(
                            alternative.equals(storedInputId) ? input.getCount() : 0,
                            item.getMaxStackSize()
                    )
            );
        }

        ProcessResourceKey waterKey = key(
                ProcessResourceKind.FLUID,
                ElectrolyzerPortPolicy.FLUID_INPUT_CHANNEL,
                "minecraft:water"
        );
        balances.put(waterKey, new ProcessResourceBalance(
                water.isEmpty() ? 0 : water.getAmount(),
                ElectrolyzerBlockEntity.WATER_CAPACITY
        ));
        putOutput(balances, recipe.hydrogenResult(), hydrogen, ElectrolyzerBlockEntity.SLOT_HYDROGEN);
        putOutput(balances, recipe.oxygenResult(), oxygen, ElectrolyzerBlockEntity.SLOT_OXYGEN);
        return new ProcessResourceSnapshot(process.resourceRevision(), balances);
    }

    @Override
    public boolean replaceIfMatches(
            ProcessResourceSnapshot expected,
            ProcessResourceSnapshot replacement
    ) {
        ProcessResourceSnapshot actual = snapshot();
        if (!actual.equals(expected)) {
            return false;
        }
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(),
                expected
        );
        if (simulation.plan().isEmpty()
                || !simulation.plan().orElseThrow().after().equals(replacement)) {
            return false;
        }
        requireAcceptedOutputs();

        ItemStack beforeInput = machine.storedItemCopy(ElectrolyzerBlockEntity.SLOT_INPUT);
        FluidStack beforeWater = machine.storedWaterCopy();
        ItemStack beforeHydrogen = machine.storedItemCopy(ElectrolyzerBlockEntity.SLOT_HYDROGEN);
        ItemStack beforeOxygen = machine.storedItemCopy(ElectrolyzerBlockEntity.SLOT_OXYGEN);
        long beforeRevision = process.resourceRevision();

        ItemStack afterInput = beforeInput.copy();
        afterInput.shrink(recipe.spec().inputCount());
        FluidStack afterWater = beforeWater.copy();
        afterWater.shrink(recipe.spec().waterAmount());
        ItemStack afterHydrogen = mergeOutput(beforeHydrogen, recipe.hydrogenResult());
        ItemStack afterOxygen = mergeOutput(beforeOxygen, recipe.oxygenResult());
        try {
            machine.replaceProcessResources(afterInput, afterWater, afterHydrogen, afterOxygen);
            process.replaceResourceRevision(expected.revision(), replacement.revision());
            return true;
        } catch (RuntimeException mutationFailure) {
            rollback(
                    beforeInput,
                    beforeWater,
                    beforeHydrogen,
                    beforeOxygen,
                    beforeRevision,
                    mutationFailure
            );
            throw mutationFailure;
        }
    }

    @Override
    public Optional<UUID> lastAppliedTransactionId() {
        return process.lastAppliedTransactionId();
    }

    @Override
    public void markApplied(UUID transactionId) {
        process.markApplied(transactionId);
    }

    private void putOutput(
            Map<ProcessResourceKey, ProcessResourceBalance> balances,
            ItemStack result,
            ItemStack stored,
            int slot
    ) {
        String resultId = itemId(result).orElseThrow();
        boolean compatible = stored.isEmpty() || ItemStack.isSameItemSameTags(stored, result);
        int capacity = compatible
                ? Math.min(result.getMaxStackSize(), machine.slotLimit(slot))
                : 0;
        balances.put(
                key(ProcessResourceKind.ITEM, ElectrolyzerPortPolicy.ITEM_OUTPUT_CHANNEL, resultId),
                new ProcessResourceBalance(
                        compatible && !stored.isEmpty() ? stored.getCount() : 0,
                        capacity
                )
        );
    }

    private void requireAcceptedOutputs() {
        if (!recipe.hydrogenResult().is(ModItems.HYDROGEN_CANISTER.get())
                || !recipe.oxygenResult().is(ModItems.OXYGEN_CANISTER.get())) {
            throw new IllegalStateException("Electrolyzer recipe outputs do not fit its stable output slots");
        }
    }

    private void rollback(
            ItemStack input,
            FluidStack water,
            ItemStack hydrogen,
            ItemStack oxygen,
            long revision,
            RuntimeException original
    ) {
        try {
            machine.replaceProcessResources(input, water, hydrogen, oxygen);
            process.restoreResourceRevision(revision);
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static ItemStack mergeOutput(ItemStack existing, ItemStack addition) {
        if (existing.isEmpty()) {
            return addition.copy();
        }
        if (!ItemStack.isSameItemSameTags(existing, addition)) {
            throw new IllegalStateException("Electrolyzer output changed during commit");
        }
        ItemStack merged = existing.copy();
        merged.grow(addition.getCount());
        if (merged.getCount() > merged.getMaxStackSize()) {
            throw new IllegalStateException("Electrolyzer output exceeded its slot capacity");
        }
        return merged;
    }

    private static ProcessResourceKey key(
            ProcessResourceKind kind,
            String channel,
            String resourceId
    ) {
        return new ProcessResourceKey(kind, channel, resourceId);
    }

    private static Optional<String> itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .map(ResourceLocation::toString);
    }
}
