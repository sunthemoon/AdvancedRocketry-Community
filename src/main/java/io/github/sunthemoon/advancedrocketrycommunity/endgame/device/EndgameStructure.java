package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternValidator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.PatternRoleResolver;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.ServerLevelPatternWorldView;
import java.util.Objects;
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
 * ADR-054 section 2.1 for one structure-only controller: the pattern is matched by the kernel's bounded validator
 * through a loaded-chunk view, when a block inside the pattern box changes and every 200 ticks, within the shared
 * budget of 8 validations per tick. An unformed structure is {@code UNFORMED}, one with an unloaded cell
 * {@code STRUCTURE_UNLOADED}; neither loses anything. The state is runtime only: a loaded controller validates
 * before its first operation.
 */
public final class EndgameStructure {
    private final String patternId;
    private final Block controllerBlock;
    private EndgameCode code = EndgameCode.UNFORMED;
    private boolean known;
    private boolean pending = true;
    private long lastValidation = Long.MIN_VALUE / 2;
    @Nullable
    private BoundingBox trackedBox;
    @Nullable
    private ServerLevel trackedLevel;
    @Nullable
    private BlockPos trackedController;

    public EndgameStructure(String patternId, Block controllerBlock) {
        this.patternId = Objects.requireNonNull(patternId, "patternId");
        this.controllerBlock = Objects.requireNonNull(controllerBlock, "controllerBlock");
    }

    public EndgameCode code() {
        return code;
    }

    /** False until the first validation since the controller loaded; nothing operates before it. */
    public boolean known() {
        return known;
    }

    /** Called from the controller's ticker on the server thread. */
    public void tick(ServerLevel level, BlockPos controller, Direction facing, UUID device, EndgameDevices devices,
                     long now) {
        Optional<MultiblockPatternDefinition> definition = devices.pattern(patternId);
        if (definition.isEmpty()) {
            untrack(devices);
            known = true;
            return;
        }
        PatternTransform transform = forFacing(facing);
        BoundingBox box = box(definition.get(), transform, controller);
        if (!box.equals(trackedBox) || trackedLevel != level) {
            untrack(devices);
            devices.structures().track(level.dimension(), controller, box);
            trackedBox = box;
            trackedLevel = level;
            trackedController = controller.immutable();
            pending = true;
        }
        if (devices.structures().consumeDirty(level.dimension(), controller)) {
            pending = true;
        }
        if (!pending && now - lastValidation < EndgameLimits.STRUCTURE_REVALIDATION_TICKS) {
            return;
        }
        if (!devices.structureBudget().take(device)) {
            devices.structureBudget().request(device);
            return;
        }
        PatternValidationResult result = MultiblockPatternValidator.validate(definition.get(), transform,
                new PatternPosition(controller.getX(), controller.getY(), controller.getZ()),
                new ServerLevelPatternWorldView(level, new Roles(controller, controllerBlock)));
        code = switch (result.status()) {
            case FORMED -> EndgameCode.OK;
            case WAITING_UNLOADED -> EndgameCode.STRUCTURE_UNLOADED;
            default -> EndgameCode.UNFORMED;
        };
        pending = false;
        known = true;
        lastValidation = now;
    }

    /** Called when the controller unloads or is removed. */
    public void untrack(EndgameDevices devices) {
        if (trackedLevel != null && trackedController != null) {
            devices.structures().untrack(trackedLevel.dimension(), trackedController);
        }
        trackedBox = null;
        trackedLevel = null;
        trackedController = null;
        code = EndgameCode.UNFORMED;
        known = false;
        pending = true;
    }

    /** North-facing controllers use the pattern as authored; the others rotate it clockwise. */
    public static PatternTransform forFacing(Direction facing) {
        PatternRotation rotation = switch (facing) {
            case NORTH -> PatternRotation.ZERO;
            case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180;
            case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("An endgame controller faces a horizontal direction");
        };
        return new PatternTransform(rotation, false);
    }

    /** The world box of every pattern cell. */
    public static BoundingBox box(MultiblockPatternDefinition definition, PatternTransform transform, BlockPos controller) {
        PatternPosition world = new PatternPosition(controller.getX(), controller.getY(), controller.getZ());
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PatternPosition local : definition.cells().keySet()) {
            PatternPosition cell = transform.localToWorld(local, definition.controllerAnchor(), world);
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
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
