package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessOutput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Immutable client display snapshot of the same definition used by server processing. */
final class MachineRecipeView {
    private final List<ItemInput> itemInputs;
    private final List<FluidInput> fluidInputs;
    private final List<ItemStack> itemOutputs;
    private final int durationTicks;
    private final int energyPerTick;

    private MachineRecipeView(
            List<ItemInput> itemInputs,
            List<FluidInput> fluidInputs,
            List<ItemStack> itemOutputs,
            int durationTicks,
            int energyPerTick
    ) {
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidInputs = List.copyOf(fluidInputs);
        this.itemOutputs = itemOutputs.stream().map(ItemStack::copy).toList();
        this.durationTicks = durationTicks;
        this.energyPerTick = energyPerTick;
    }

    static MachineRecipeView from(ProcessDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        List<ItemInput> itemInputs = new ArrayList<>();
        List<FluidInput> fluidInputs = new ArrayList<>();
        List<ItemStack> itemOutputs = new ArrayList<>();
        for (ProcessInput input : definition.inputs()) {
            if (input.kind() == ProcessResourceKind.ITEM) {
                List<ItemStack> choices = input.alternatives().stream()
                        .map(id -> itemStack(id, input.amount()))
                        .toList();
                itemInputs.add(new ItemInput(input.channel(), choices));
            } else if (input.kind() == ProcessResourceKind.FLUID) {
                if (input.alternatives().size() != 1) {
                    throw new IllegalArgumentException("machine display requires one fluid alternative");
                }
                ResourceLocation id = ResourceLocation.tryParse(input.alternatives().get(0));
                Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
                if (fluid == null || fluid == Fluids.EMPTY) {
                    throw new IllegalArgumentException("machine display has an unknown fluid");
                }
                fluidInputs.add(new FluidInput(input.channel(), fluid, Math.toIntExact(input.amount())));
            } else {
                throw new IllegalArgumentException("machine display has an unsupported input kind");
            }
        }
        for (ProcessOutput output : definition.outputs()) {
            if (output.key().kind() != ProcessResourceKind.ITEM) {
                throw new IllegalArgumentException("machine display has an unsupported output kind");
            }
            itemOutputs.add(itemStack(output.key().resourceId(), output.amount()));
        }
        return new MachineRecipeView(
                itemInputs, fluidInputs, itemOutputs,
                definition.durationTicks(), definition.energyPerTick()
        );
    }

    List<ItemInput> itemInputs() {
        return itemInputs;
    }

    List<FluidInput> fluidInputs() {
        return fluidInputs;
    }

    List<ItemStack> itemOutputs() {
        return itemOutputs.stream().map(ItemStack::copy).toList();
    }

    int durationTicks() {
        return durationTicks;
    }

    int energyPerTick() {
        return energyPerTick;
    }

    private static ItemStack itemStack(String rawId, long count) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item.getDefaultInstance().isEmpty()
                || count < 1 || count > item.getMaxStackSize()) {
            throw new IllegalArgumentException("machine display has an invalid item stack");
        }
        return new ItemStack(item, Math.toIntExact(count));
    }

    record ItemInput(String channel, List<ItemStack> alternatives) {
        ItemInput {
            Objects.requireNonNull(channel, "channel");
            alternatives = alternatives.stream().map(ItemStack::copy).toList();
            if (alternatives.isEmpty()) {
                throw new IllegalArgumentException("machine display has no item alternative");
            }
        }

        @Override
        public List<ItemStack> alternatives() {
            return alternatives.stream().map(ItemStack::copy).toList();
        }
    }

    record FluidInput(String channel, Fluid fluid, int amount) {
        FluidInput {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(fluid, "fluid");
            if (amount < 1) {
                throw new IllegalArgumentException("machine display has an invalid fluid amount");
            }
        }
    }
}
