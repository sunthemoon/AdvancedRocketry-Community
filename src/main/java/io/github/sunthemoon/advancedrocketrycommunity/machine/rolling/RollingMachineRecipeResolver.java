package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Bounded server recipe lookup with explicit ambiguity and reload-safe active identity. */
final class RollingMachineRecipeResolver {
    static final int MAX_RECIPES = 1_024;

    private boolean ambiguityLogged;

    Resolution resolve(
            ServerLevel level,
            BlockPos controllerPosition,
            ItemStack input,
            @Nullable ProcessProgress progress,
            @Nullable String recipeSignature
    ) {
        if (progress != null) {
            ResourceLocation activeId = ResourceLocation.tryParse(progress.definitionId());
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded = activeId == null
                    ? Optional.empty()
                    : level.getRecipeManager().byKey(activeId);
            if (loaded.isEmpty()
                    || !(loaded.orElseThrow() instanceof RollingMachineRecipe recipe)
                    || !recipe.signature().equals(recipeSignature)) {
                return Resolution.missing(
                        ProcessMachineState.INVALID_RECIPE,
                        failure(ProcessFailureCode.INVALID_RECIPE, progress.definitionId())
                );
            }
            return Resolution.found(recipe);
        }
        if (input.isEmpty()) {
            return Resolution.missing(ProcessMachineState.IDLE, ProcessFailure.NONE);
        }
        List<RollingMachineRecipe> candidates = level.getRecipeManager().getAllRecipesFor(
                ModRecipes.ROLLING_TYPE.get()
        );
        if (candidates.size() > MAX_RECIPES) {
            logProblem(controllerPosition, "recipe limit exceeded: " + candidates.size());
            return Resolution.missing(
                    ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, "recipe_limit")
            );
        }
        SimpleContainer container = new SimpleContainer(input.copy());
        List<RollingMachineRecipe> matches = new ArrayList<>(2);
        for (RollingMachineRecipe candidate : candidates) {
            if (candidate.matches(container, level)) {
                matches.add(candidate);
                if (matches.size() == 2) {
                    break;
                }
            }
        }
        if (matches.size() == 1) {
            ambiguityLogged = false;
            return Resolution.found(matches.get(0));
        }
        if (matches.size() > 1) {
            logProblem(controllerPosition, "at least two recipes match");
            return Resolution.missing(
                    ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, "ambiguous")
            );
        }
        ambiguityLogged = false;
        return Resolution.missing(ProcessMachineState.IDLE, ProcessFailure.NONE);
    }

    void resourceChanged() {
        ambiguityLogged = false;
    }

    void reset() {
        ambiguityLogged = false;
    }

    private void logProblem(BlockPos controllerPosition, String detail) {
        if (!ambiguityLogged) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "Refusing Rolling Machine recipe lookup at {}: {}",
                    controllerPosition,
                    detail
            );
            ambiguityLogged = true;
        }
    }

    private static ProcessFailure failure(ProcessFailureCode code, String subject) {
        return new ProcessFailure(code, subject);
    }

    record Resolution(
            Optional<RollingMachineRecipe> recipe,
            ProcessMachineState missingState,
            ProcessFailure failure
    ) {
        Resolution {
            recipe = java.util.Objects.requireNonNull(recipe, "recipe");
            java.util.Objects.requireNonNull(missingState, "missingState");
            java.util.Objects.requireNonNull(failure, "failure");
        }

        static Resolution found(RollingMachineRecipe recipe) {
            return new Resolution(Optional.of(recipe), ProcessMachineState.IDLE, ProcessFailure.NONE);
        }

        static Resolution missing(ProcessMachineState state, ProcessFailure failure) {
            return new Resolution(Optional.empty(), state, failure);
        }
    }
}
