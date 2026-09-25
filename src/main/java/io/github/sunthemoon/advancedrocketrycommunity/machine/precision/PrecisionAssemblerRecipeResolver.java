package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Server-only, order-independent selection with an explicit recipe-count budget. */
final class PrecisionAssemblerRecipeResolver {
    static final int MAX_RECIPES = 1_024;

    private boolean problemLogged;

    Resolution resolve(
            ServerLevel level,
            BlockPos controllerPosition,
            List<ItemStack> inputs,
            @Nullable ProcessProgress progress,
            @Nullable String signature
    ) {
        Objects.requireNonNull(level, "level");
        if (progress != null) {
            ResourceLocation activeId = ResourceLocation.tryParse(progress.definitionId());
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded = activeId == null
                    ? Optional.empty()
                    : level.getRecipeManager().byKey(activeId);
            if (loaded.isEmpty()
                    || !(loaded.orElseThrow() instanceof PrecisionAssemblerRecipe recipe)
                    || !recipe.signature().equals(signature)) {
                return Resolution.missing(
                        ProcessMachineState.INVALID_RECIPE,
                        new ProcessFailure(ProcessFailureCode.INVALID_RECIPE, progress.definitionId())
                );
            }
            return Resolution.found(recipe);
        }
        if (inputs.size() != PrecisionAssemblerChannels.INPUT_COUNT) {
            throw new IllegalArgumentException("precision input snapshot must contain five slots");
        }
        if (inputs.stream().allMatch(ItemStack::isEmpty)) {
            return Resolution.missing(ProcessMachineState.IDLE, ProcessFailure.NONE);
        }
        List<PrecisionAssemblerRecipe> candidates = level.getRecipeManager().getAllRecipesFor(
                ModRecipes.PRECISION_ASSEMBLING_TYPE.get()
        );
        Selection selected = select(candidates, new SimpleContainer(
                inputs.stream().map(ItemStack::copy).toArray(ItemStack[]::new)
        ));
        if (selected.problem() != SelectionProblem.NONE && !problemLogged) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "Refusing Precision Assembler recipe lookup at {}: {}",
                    controllerPosition,
                    selected.problem()
            );
            problemLogged = true;
        } else if (selected.problem() == SelectionProblem.NONE) {
            problemLogged = false;
        }
        return switch (selected.problem()) {
            case NONE -> selected.recipe()
                    .map(Resolution::found)
                    .orElseGet(() -> Resolution.missing(ProcessMachineState.IDLE, ProcessFailure.NONE));
            case TOO_MANY -> Resolution.missing(ProcessMachineState.INVALID_RECIPE,
                    new ProcessFailure(ProcessFailureCode.INVALID_RECIPE, "recipe_limit"));
            case AMBIGUOUS -> Resolution.missing(ProcessMachineState.INVALID_RECIPE,
                    new ProcessFailure(ProcessFailureCode.INVALID_RECIPE, "ambiguous"));
        };
    }

    static Selection select(List<PrecisionAssemblerRecipe> candidates, SimpleContainer inputs) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(inputs, "inputs");
        if (candidates.size() > MAX_RECIPES) {
            return new Selection(Optional.empty(), SelectionProblem.TOO_MANY);
        }
        PrecisionAssemblerRecipe matched = null;
        for (PrecisionAssemblerRecipe candidate : candidates) {
            if (candidate.matches(inputs, null)) {
                if (matched != null) {
                    return new Selection(Optional.empty(), SelectionProblem.AMBIGUOUS);
                }
                matched = candidate;
            }
        }
        return new Selection(Optional.ofNullable(matched), SelectionProblem.NONE);
    }

    void reset() {
        problemLogged = false;
    }

    enum SelectionProblem {
        NONE,
        TOO_MANY,
        AMBIGUOUS
    }

    record Selection(Optional<PrecisionAssemblerRecipe> recipe, SelectionProblem problem) {
        Selection {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(problem, "problem");
        }
    }

    record Resolution(
            Optional<PrecisionAssemblerRecipe> recipe,
            ProcessMachineState missingState,
            ProcessFailure failure
    ) {
        Resolution {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(missingState, "missingState");
            Objects.requireNonNull(failure, "failure");
        }

        static Resolution found(PrecisionAssemblerRecipe recipe) {
            return new Resolution(Optional.of(recipe), ProcessMachineState.IDLE, ProcessFailure.NONE);
        }

        static Resolution missing(ProcessMachineState state, ProcessFailure failure) {
            return new Resolution(Optional.empty(), state, failure);
        }
    }
}
