package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

/** Pure pause/resume/completion transition logic with no Minecraft lifecycle dependency. */
public final class ElectrolyzerProcessEngine {
    private ElectrolyzerProcessEngine() {
    }

    public static ElectrolyzerTickResult tick(
            int progress,
            int processingTicks,
            ElectrolyzerTickInput input
    ) {
        if (processingTicks < 1 || processingTicks > ElectrolyzerRecipeSpec.MAX_PROCESSING_TICKS) {
            throw new IllegalArgumentException("processingTicks is outside the bounded recipe range");
        }
        if (progress < 0 || progress >= processingTicks) {
            throw new IllegalArgumentException("progress must be in [0, processingTicks)");
        }
        if (!input.recipeAvailable()) {
            return new ElectrolyzerTickResult(
                    0,
                    0,
                    false,
                    progress == 0 ? ElectrolyzerStatus.NO_RECIPE : ElectrolyzerStatus.INVALID_RECIPE
            );
        }
        if (!input.enabled()) {
            return paused(progress, ElectrolyzerStatus.REDSTONE_DISABLED);
        }
        if (!input.waterAvailable()) {
            return paused(progress, ElectrolyzerStatus.NEEDS_WATER);
        }
        if (!input.outputSpaceAvailable()) {
            return paused(progress, ElectrolyzerStatus.OUTPUT_BLOCKED);
        }
        if (input.storedEnergy() < input.energyPerTick()) {
            return paused(progress, ElectrolyzerStatus.NEEDS_ENERGY);
        }

        int nextProgress = progress + 1;
        boolean completed = nextProgress == processingTicks;
        return new ElectrolyzerTickResult(
                completed ? 0 : nextProgress,
                input.energyPerTick(),
                completed,
                ElectrolyzerStatus.RUNNING
        );
    }

    private static ElectrolyzerTickResult paused(int progress, ElectrolyzerStatus status) {
        return new ElectrolyzerTickResult(progress, 0, false, status);
    }
}
