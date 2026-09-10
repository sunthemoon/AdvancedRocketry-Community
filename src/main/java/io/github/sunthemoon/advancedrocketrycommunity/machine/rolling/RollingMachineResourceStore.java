package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceStore;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessSimulationResult;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Server-thread transactional adapter over the four loaded typed port BlockEntities. */
final class RollingMachineResourceStore implements ProcessResourceStore {
    private final ServerLevel level;
    private final RollingMachineBlockEntity controller;
    private final RollingMachineProcessController process;
    private final RollingMachineRecipe recipe;
    private final RollingMachinePortSet ports;

    RollingMachineResourceStore(
            ServerLevel level,
            RollingMachineBlockEntity controller,
            RollingMachineProcessController process,
            RollingMachineRecipe recipe,
            RollingMachinePortSet ports
    ) {
        this.level = java.util.Objects.requireNonNull(level, "level");
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
        this.process = java.util.Objects.requireNonNull(process, "process");
        this.recipe = java.util.Objects.requireNonNull(recipe, "recipe");
        this.ports = java.util.Objects.requireNonNull(ports, "ports");
    }

    @Override
    public ProcessResourceSnapshot snapshot() {
        requireUsablePorts();
        ItemStack input = ports.itemInput().storedItemCopy();
        ItemStack output = ports.itemOutput().storedItemCopy();
        FluidStack fluid = ports.fluidInput().storedFluidCopy();
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new TreeMap<>();

        String inputId = itemId(input).orElse("");
        for (String alternative : recipe.ingredientAlternatives()) {
            ResourceLocation alternativeId = ResourceLocation.tryParse(alternative);
            Item item = alternativeId == null
                    ? null
                    : BuiltInRegistries.ITEM.getOptional(alternativeId).orElse(null);
            if (item == null || item.getDefaultInstance().isEmpty()) {
                throw new IllegalStateException("Rolling Machine recipe alternative is no longer registered");
            }
            balances.put(
                    key(ProcessResourceKind.ITEM, RollingMachinePortType.ITEM_INPUT, alternative),
                    new ProcessResourceBalance(
                            alternative.equals(inputId) ? input.getCount() : 0,
                            item.getMaxStackSize()
                    )
            );
        }
        ProcessResourceKey fluidKey = key(
                ProcessResourceKind.FLUID,
                RollingMachinePortType.FLUID_INPUT,
                "minecraft:water"
        );
        balances.put(fluidKey, new ProcessResourceBalance(
                fluid.isEmpty() ? 0 : fluid.getAmount(),
                RollingMachinePortBlockEntity.FLUID_CAPACITY
        ));

        String resultId = itemId(recipe.result()).orElseThrow();
        boolean outputCompatible = output.isEmpty() || resultId.equals(itemId(output).orElse(""));
        balances.put(
                key(ProcessResourceKind.ITEM, RollingMachinePortType.ITEM_OUTPUT, resultId),
                new ProcessResourceBalance(
                        outputCompatible && !output.isEmpty() ? output.getCount() : 0,
                        outputCompatible ? recipe.result().getMaxStackSize() : 0
                )
        );
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

        ItemStack beforeInput = ports.itemInput().storedItemCopy();
        FluidStack beforeFluid = ports.fluidInput().storedFluidCopy();
        ItemStack beforeOutput = ports.itemOutput().storedItemCopy();
        long beforeRevision = process.resourceRevision();
        ItemStack afterInput = beforeInput.copy();
        afterInput.shrink(recipe.inputCount());
        FluidStack afterFluid = beforeFluid.copy();
        afterFluid.shrink(recipe.fluidAmount());
        ItemStack afterOutput = mergeOutput(beforeOutput, recipe.result());

        try {
            ports.itemInput().replaceStoredItemInternal(afterInput);
            ports.fluidInput().replaceStoredFluidInternal(afterFluid);
            ports.itemOutput().replaceStoredItemInternal(afterOutput);
            process.replaceResourceRevision(expected.revision(), replacement.revision());
            return true;
        } catch (RuntimeException mutationFailure) {
            rollback(beforeInput, beforeFluid, beforeOutput, beforeRevision, mutationFailure);
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

    private void rollback(
            ItemStack item,
            FluidStack fluid,
            ItemStack output,
            long revision,
            RuntimeException original
    ) {
        try {
            ports.itemInput().replaceStoredItemInternal(item);
            ports.fluidInput().replaceStoredFluidInternal(fluid);
            ports.itemOutput().replaceStoredItemInternal(output);
            process.restoreResourceRevision(revision);
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void requireUsablePorts() {
        if (!ports.remainsUsable(level, controller)) {
            throw new IllegalStateException("Rolling Machine ports changed during a process transaction");
        }
    }

    private static ItemStack mergeOutput(ItemStack existing, ItemStack addition) {
        if (existing.isEmpty()) {
            return addition.copy();
        }
        if (!ItemStack.isSameItemSameTags(existing, addition)) {
            throw new IllegalStateException("Rolling Machine output changed during commit");
        }
        ItemStack merged = existing.copy();
        merged.grow(addition.getCount());
        if (merged.getCount() > merged.getMaxStackSize()) {
            throw new IllegalStateException("Rolling Machine output exceeded its slot capacity");
        }
        return merged;
    }

    private static ProcessResourceKey key(
            ProcessResourceKind kind,
            RollingMachinePortType port,
            String resourceId
    ) {
        return new ProcessResourceKey(kind, port.channel(), resourceId);
    }

    private static Optional<String> itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .map(ResourceLocation::toString);
    }
}
