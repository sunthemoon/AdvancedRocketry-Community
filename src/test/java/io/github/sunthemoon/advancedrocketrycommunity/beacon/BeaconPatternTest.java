package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180BeaconData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternValidator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternObservation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The generated beacon pattern through the real kernel decoder and validator: the legacy layout (a 3 x 3 casing base
 * with the controller at the front, a casing column of three and a redstone cap) forms in all four facings, behind the
 * controller; a missing or wrong cell is a mismatch, and an unloaded cell waits without reading anything else.
 */
class BeaconPatternTest {
    private static final String CASING = "advancedrocketrycommunity:machine_casing";
    private static final String REDSTONE = "minecraft:redstone_block";
    private static final String CLEARANCE = "advancedrocketrycommunity:machine_clearance";
    private static final PatternPosition CONTROLLER = new PatternPosition(100, 70, -40);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static MultiblockPatternDefinition definition() {
        return MultiblockPatternJsonCodec.decode(V180BeaconData.pattern().toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The world built for one facing: each pattern cell gets a block that matches it. */
    private static Map<PatternPosition, PatternBlock> build(MultiblockPatternDefinition definition, Direction facing) {
        PatternTransform transform = EndgameStructure.forFacing(facing);
        Map<PatternPosition, PatternBlock> world = new HashMap<>();
        definition.cells().forEach((local, matcher) -> {
            PatternPosition at = transform.localToWorld(local, definition.controllerAnchor(), CONTROLLER);
            PatternBlock block;
            if (matcher instanceof PatternMatcher.Controller) {
                block = new PatternBlock("advancedrocketrycommunity:planetary_beacon", Set.of(), false, true,
                        Optional.empty());
            } else if (matcher instanceof PatternMatcher.ExactBlock exact) {
                block = new PatternBlock(exact.blockId(), Set.of(), false, false, Optional.empty());
            } else {
                block = new PatternBlock("minecraft:air", Set.of(CLEARANCE), true, false, Optional.empty());
            }
            world.put(at, block);
        });
        return world;
    }

    private static PatternValidationStatus validate(MultiblockPatternDefinition definition, Direction facing,
                                                    Map<PatternPosition, PatternBlock> world) {
        return MultiblockPatternValidator.validate(definition, EndgameStructure.forFacing(facing), CONTROLLER,
                position -> world.containsKey(position) ? PatternObservation.loaded(world.get(position))
                        : PatternObservation.unloaded()).status();
    }

    @Test
    void theLayoutIsFiveHighThreeByThreeWithTwelveRequiredBlocks() {
        MultiblockPatternDefinition definition = definition();
        assertEquals(45, definition.cells().size());
        assertEquals(new PatternPosition(1, 0, 0), definition.controllerAnchor());
        long casing = definition.cells().values().stream().filter(matcher -> matcher instanceof PatternMatcher.ExactBlock
                exact && exact.blockId().equals(CASING)).count();
        long caps = definition.cells().values().stream().filter(matcher -> matcher instanceof PatternMatcher.ExactBlock
                exact && exact.blockId().equals(REDSTONE)).count();
        assertEquals(11, casing);
        assertEquals(1, caps);
        assertEquals(new PatternMatcher.ExactBlock(REDSTONE), definition.cells().get(new PatternPosition(1, 4, 1)));
        for (int y = 1; y <= 3; y++) {
            assertEquals(new PatternMatcher.ExactBlock(CASING), definition.cells().get(new PatternPosition(1, y, 1)));
        }
        assertEquals(new PatternMatcher.Controller(), definition.cells().get(new PatternPosition(1, 0, 0)));
    }

    @Test
    void itFormsInAllFourFacingsBehindTheController() {
        MultiblockPatternDefinition definition = definition();
        Map<Direction, PatternPosition> capOffsets = Map.of(
                Direction.NORTH, new PatternPosition(0, 4, 1),
                Direction.EAST, new PatternPosition(-1, 4, 0),
                Direction.SOUTH, new PatternPosition(0, 4, -1),
                Direction.WEST, new PatternPosition(1, 4, 0));
        for (Direction facing : capOffsets.keySet()) {
            Map<PatternPosition, PatternBlock> world = build(definition, facing);
            assertEquals(PatternValidationStatus.FORMED, validate(definition, facing, world), facing.toString());
            assertEquals(REDSTONE, world.get(CONTROLLER.add(capOffsets.get(facing))).blockId(), facing.toString());
            // The column stands right behind the controller, opposite its facing.
            PatternPosition behind = CONTROLLER.add(new PatternPosition(-facing.getStepX(), 1, -facing.getStepZ()));
            assertEquals(CASING, world.get(behind).blockId(), facing.toString());
        }
    }

    @Test
    void aMissingWrongOrUnloadedCellIsNotFormed() {
        MultiblockPatternDefinition definition = definition();
        Map<PatternPosition, PatternBlock> world = build(definition, Direction.NORTH);
        PatternPosition cap = CONTROLLER.add(new PatternPosition(0, 4, 1));
        world.put(cap, new PatternBlock("minecraft:air", Set.of(CLEARANCE), true, false, Optional.empty()));
        assertEquals(PatternValidationStatus.MISMATCH, validate(definition, Direction.NORTH, world));
        world = build(definition, Direction.NORTH);
        world.put(CONTROLLER.add(new PatternPosition(1, 2, 0)),
                new PatternBlock("minecraft:stone", Set.of(), false, false, Optional.empty()));
        assertEquals(PatternValidationStatus.MISMATCH, validate(definition, Direction.NORTH, world));
        world = build(definition, Direction.NORTH);
        world.remove(CONTROLLER.add(new PatternPosition(-1, 0, 2)));
        assertEquals(PatternValidationStatus.WAITING_UNLOADED, validate(definition, Direction.NORTH, world));
        assertTrue(definition.allows(EndgameStructure.forFacing(Direction.WEST)));
    }
}
