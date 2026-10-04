package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import java.util.Objects;
import java.util.function.Supplier;

/** Recipe-local bounded resolution cache, invalidated by tag rebinding rather than parsing. */
public final class DeferredProcessDefinition {
    private final String id;
    private final Supplier<ProcessDefinition> factory;
    private long generation = Long.MIN_VALUE;
    private ProcessDefinition definition;
    private IllegalArgumentException failure;

    public DeferredProcessDefinition(String id, Supplier<ProcessDefinition> factory) {
        this.id = Objects.requireNonNull(id);
        this.factory = Objects.requireNonNull(factory);
    }

    public boolean available() {
        refresh();
        return failure == null;
    }

    public ProcessDefinition get() {
        refresh();
        if (failure != null) {
            throw failure;
        }
        return definition;
    }

    private void refresh() {
        long current = RecipeTagGeneration.current();
        if (generation == current) {
            return;
        }
        generation = current;
        definition = null;
        failure = null;
        try {
            definition = Objects.requireNonNull(factory.get());
        } catch (IllegalArgumentException exception) {
            failure = exception;
            AdvancedRocketryCommunity.LOGGER.error("Disabling machine recipe {} until tag reload: {}",
                    id, exception.getMessage());
        }
    }
}
