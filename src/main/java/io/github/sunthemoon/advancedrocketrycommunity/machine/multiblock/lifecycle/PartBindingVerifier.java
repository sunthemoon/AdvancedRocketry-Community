package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Validates identity before resolving an already-loaded controller. */
public final class PartBindingVerifier {
    private PartBindingVerifier() {
    }

    public static PartBindingValidationStatus verify(
            MultiblockPartBinding binding,
            ResourceKey<Level> partLevel,
            Predicate<BlockPos> isControllerChunkLoaded,
            Function<BlockPos, Optional<ControllerBindingView>> loadedController
    ) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(partLevel, "partLevel");
        Objects.requireNonNull(isControllerChunkLoaded, "isControllerChunkLoaded");
        Objects.requireNonNull(loadedController, "loadedController");
        if (!binding.controllerLevel().equals(partLevel)) {
            return PartBindingValidationStatus.WRONG_LEVEL;
        }
        if (!isControllerChunkLoaded.test(binding.controllerPosition())) {
            return PartBindingValidationStatus.CONTROLLER_UNLOADED;
        }
        Optional<ControllerBindingView> resolved = loadedController.apply(binding.controllerPosition());
        if (resolved.isEmpty()) {
            return PartBindingValidationStatus.CONTROLLER_MISSING;
        }
        ControllerBindingView controller = resolved.get();
        if (!controller.machineInstanceId().equals(binding.machineInstanceId())) {
            return PartBindingValidationStatus.WRONG_INSTANCE;
        }
        if (controller.generation() != binding.generation()) {
            return PartBindingValidationStatus.STALE_GENERATION;
        }
        return controller.acceptsPartAccess()
                ? PartBindingValidationStatus.VALID
                : PartBindingValidationStatus.CONTROLLER_INACTIVE;
    }
}
