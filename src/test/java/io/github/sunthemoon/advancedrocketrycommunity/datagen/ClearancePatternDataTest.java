package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Null cells accept air or the closed clearance tag, never arbitrary third-party machinery. */
class ClearancePatternDataTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @ParameterizedTest
    @CsvSource({"chemical_reactor, 2", "centrifuge, 1", "planetary_beacon, 32"})
    void everyNullCellAcceptsUntaggedAirAndDecorationButRefusesSolidBlockEntities(String id, int count) {
        var json = id.equals("planetary_beacon") ? V180BeaconData.pattern() : V180C16cMachineData.pattern(id);
        var pattern = MultiblockPatternJsonCodec.decode(json.toString().getBytes(StandardCharsets.UTF_8));
        var clearance = new PatternMatcher.OptionalCell(new PatternMatcher.BlockTag(V180ArcCuttingData.CLEARANCE_TAG));
        var cells = pattern.cells().entrySet().stream().filter(cell -> cell.getValue().equals(clearance)).toList();
        assertEquals(count, cells.size(), id);
        assertFalse(pattern.cells().containsValue(new PatternMatcher.BlockTag(V180ArcCuttingData.CLEARANCE_TAG)));

        var airState = new Block(BlockBehaviour.Properties.of().air()).defaultBlockState();
        assertTrue(airState.isAir());
        var air = new PatternBlock("examplemod:light_air", Set.of(), airState.isAir(), false, Optional.empty());
        var decoration = new PatternBlock("minecraft:snow", Set.of(V180ArcCuttingData.CLEARANCE_TAG),
                false, false, Optional.empty());
        // The world adapter observes isAir/tags/roles, not a BE's class. A third-party BE with these
        // non-air, untagged properties must not gain admission merely because the cell is optional.
        var entityState = Blocks.CHEST.defaultBlockState();
        assertTrue(entityState.hasBlockEntity());
        assertFalse(entityState.isAir());
        var cable = new PatternBlock("examplemod:cable", Set.of(), entityState.isAir(), false, Optional.empty());
        var controller = new PatternPosition(100, 70, -40);
        for (var rotation : pattern.allowedRotations()) {
            var transform = new PatternTransform(rotation, false);
            var world = new HashMap<PatternPosition, PatternObservation>();
            pattern.cells().forEach((position, matcher) -> world.put(
                    transform.localToWorld(position, pattern.controllerAnchor(), controller),
                    PatternObservation.loaded(matchingBlock(matcher))));
            for (var cell : cells) {
                var position = transform.localToWorld(cell.getKey(), pattern.controllerAnchor(), controller);
                for (var accepted : new PatternBlock[] {air, decoration}) {
                    world.put(position, PatternObservation.loaded(accepted));
                    assertEquals(PatternValidationStatus.FORMED,
                            MultiblockPatternValidator.validate(pattern, transform, controller, world::get).status(),
                            id + " " + rotation + " " + cell.getKey() + " " + accepted.blockId());
                }
                world.put(position, PatternObservation.loaded(cable));
                assertEquals(PatternValidationStatus.MISMATCH,
                        MultiblockPatternValidator.validate(pattern, transform, controller, world::get).status());
                world.put(position, PatternObservation.unloaded());
                assertEquals(PatternValidationStatus.WAITING_UNLOADED,
                        MultiblockPatternValidator.validate(pattern, transform, controller, world::get).status());
                world.put(position, PatternObservation.loaded(air));
            }
        }
    }

    private static PatternBlock matchingBlock(PatternMatcher matcher) {
        String id = matcher instanceof PatternMatcher.ExactBlock exact ? exact.blockId() : "minecraft:air";
        var tags = matcher instanceof PatternMatcher.BlockTag tag ? Set.of(tag.tagId()) : Set.<String>of();
        var port = matcher instanceof PatternMatcher.Port value ? Optional.of(value.channel()) : Optional.<String>empty();
        return new PatternBlock(id, tags, matcher instanceof PatternMatcher.Air || matcher instanceof PatternMatcher.OptionalCell,
                matcher instanceof PatternMatcher.Controller, port);
    }
}
