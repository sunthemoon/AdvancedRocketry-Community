package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

/** Lifecycle adapter for retained, unsupported or unprovable signature input. */
public interface RecipeSignatureProtected {
    boolean preservesRecipeInput();
}
