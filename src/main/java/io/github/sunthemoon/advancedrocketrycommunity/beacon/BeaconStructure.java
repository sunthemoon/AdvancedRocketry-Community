package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternValidator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.PatternRoleResolver;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.ServerLevelPatternWorldView;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The beacon's v1.2 pattern lifecycle (ADR-065 section 7), as the endgame structures use it: the 5-by-3-by-3 pattern
 * is matched in the controller's facing through the kernel's bounded validator and a loaded-chunk view, when a block
 * in its box changes and every {@link BeaconServices#REVALIDATION_TICKS}, within the shared per-tick budget. A cell
 * in an unloaded chunk is {@link Status#UNLOADED}, never a chunk load. The state is runtime only.
 */
public final class BeaconStructure {
    /** The last validation's outcome; {@link #PENDING} until the first one since loading. */
    public enum Status {
        PENDING, FORMED, UNFORMED, UNLOADED, NO_PATTERN
    }

    private final Block controllerBlock;
    private Status status = Status.PENDING;
    private boolean pending = true;
    private boolean validated;
    private long lastValidation = Long.MIN_VALUE / 2;
    @Nullable
    private BoundingBox trackedBox;
    @Nullable
    private ServerLevel trackedLevel;
    @Nullable
    private BlockPos trackedController;
    @Nullable
    private Direction trackedFacing;
    @Nullable
    private MultiblockPatternDefinition trackedDefinition;

    public BeaconStructure(Block controllerBlock) {
        this.controllerBlock = controllerBlock;
    }

    public Status status() {
        return status;
    }

    /** True once, after each validation, so the beacon can reconcile its index record. */
    public boolean consumeValidated() {
        boolean result = validated;
        validated = false;
        return result;
    }

    /** Called from the controller's server ticker. */
    public void tick(ServerLevel level, BlockPos controller, Direction facing, UUID beaconId, BeaconServices services,
                     long now) {
        Optional<MultiblockPatternDefinition> definition = services.pattern();
        if (definition.isEmpty()) {
            untrack(services);
            if (status != Status.NO_PATTERN) {
                status = Status.NO_PATTERN;
                validated = true;
            }
            return;
        }
        PatternTransform transform = EndgameStructure.forFacing(facing);
        if (definition.get() != trackedDefinition || facing != trackedFacing || trackedLevel != level
                || !controller.equals(trackedController)) {
            BoundingBox box = EndgameStructure.box(definition.get(), transform, controller);
            if (!box.equals(trackedBox) || trackedLevel != level) {
                untrack(services);
                services.structures().track(level.dimension(), controller, box);
                trackedBox = box;
                trackedLevel = level;
                trackedController = controller.immutable();
            }
            trackedFacing = facing;
            trackedDefinition = definition.get();
            pending = true;
        }
        if (services.structures().consumeDirty(level.dimension(), controller)) {
            pending = true;
        }
        if (!pending && now - lastValidation < BeaconServices.REVALIDATION_TICKS) {
            return;
        }
        if (!services.validations().take(beaconId)) {
            services.validations().request(beaconId);
            return;
        }
        PatternValidationResult result = MultiblockPatternValidator.validate(definition.get(), transform,
                new PatternPosition(controller.getX(), controller.getY(), controller.getZ()),
                new ServerLevelPatternWorldView(level, new Roles(controller, controllerBlock), services.patternBlocks()));
        status = switch (result.status()) {
            case FORMED -> Status.FORMED;
            case WAITING_UNLOADED -> Status.UNLOADED;
            default -> Status.UNFORMED;
        };
        pending = false;
        validated = true;
        lastValidation = now;
    }

    /** Called when the controller unloads or is removed. */
    public void untrack(BeaconServices services) {
        if (trackedLevel != null && trackedController != null) {
            services.structures().untrack(trackedLevel.dimension(), trackedController);
        }
        trackedBox = null;
        trackedLevel = null;
        trackedController = null;
        trackedFacing = null;
        trackedDefinition = null;
        pending = true;
    }

    private record Roles(BlockPos controller, Block block) implements PatternRoleResolver {
        @Override
        public boolean isController(BlockPos position, BlockState state) {
            return position.equals(controller) && state.is(block);
        }

        @Override
        public Optional<String> portChannel(BlockPos position, BlockState state) {
            return Optional.empty();
        }
    }
}
