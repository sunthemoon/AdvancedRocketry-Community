package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Bounded, reload-aware recipe selection kept outside the process state owner. */
final class ElectrolyzerRecipeResolver {
    @Nullable
    private ElectrolyzerRecipe cachedRecipe;
    private boolean dirty = true;
    private boolean ambiguous;
    private boolean ambiguityLogged;
    private long lookupCount;

    @Nullable
    ElectrolyzerRecipe resolve(
            ServerLevel level,
            BlockPos machinePosition,
            ItemStack input,
            @Nullable io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress progress,
            @Nullable String recipeSignature
    ) {
        SimpleContainer container = new SimpleContainer(input.copy());
        if (progress != null) {
            ElectrolyzerRecipe active = byId(level, progress.definitionId());
            lookupCount++;
            if (active != null
                    && active.matches(container, level)
                    && (ElectrolyzerProcessController.LEGACY_UNVERIFIED_SIGNATURE.equals(recipeSignature)
                    || active.signature().equals(recipeSignature))) {
                cachedRecipe = active;
                dirty = false;
                ambiguous = false;
                return active;
            }
            return null;
        }
        if (!dirty && cachedRecipe != null && cachedRecipe.matches(container, level)) {
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> registered =
                    level.getRecipeManager().byKey(cachedRecipe.getId());
            lookupCount++;
            if (registered.isPresent() && registered.get() == cachedRecipe) {
                return cachedRecipe;
            }
            cachedRecipe = null;
            dirty = true;
        }
        if (!dirty) {
            return null;
        }
        List<ElectrolyzerRecipe> matches = level.getRecipeManager().getRecipesFor(
                ModRecipes.ELECTROLYZING_TYPE.get(),
                container,
                level
        );
        lookupCount++;
        dirty = false;
        ambiguous = matches.size() > 1;
        cachedRecipe = matches.size() == 1 ? matches.get(0) : null;
        if (ambiguous && !ambiguityLogged) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "Refusing ambiguous Electrolyzer input at {}: {} recipes match",
                    machinePosition,
                    matches.size()
            );
            ambiguityLogged = true;
        } else if (!ambiguous) {
            ambiguityLogged = false;
        }
        return cachedRecipe;
    }

    @Nullable
    ElectrolyzerRecipe byId(ServerLevel level, String definitionId) {
        ResourceLocation id = ResourceLocation.tryParse(definitionId);
        if (id == null) {
            return null;
        }
        Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded =
                level.getRecipeManager().byKey(id);
        return loaded.isPresent() && loaded.orElseThrow() instanceof ElectrolyzerRecipe recipe
                ? recipe
                : null;
    }

    void requestRefresh() {
        dirty = true;
    }

    void inputChanged() {
        cachedRecipe = null;
        dirty = true;
        ambiguityLogged = false;
    }

    void processReset() {
        cachedRecipe = null;
        dirty = true;
    }

    void reset() {
        cachedRecipe = null;
        dirty = true;
        ambiguous = false;
        ambiguityLogged = false;
        lookupCount = 0;
    }

    boolean ambiguous() {
        return ambiguous;
    }

    int totalProcessingTicks() {
        return cachedRecipe == null ? 0 : cachedRecipe.spec().processingTicks();
    }

    long lookupCount() {
        return lookupCount;
    }
}
