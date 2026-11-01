package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The formed projection's bounded wire form and the render volume of a catalogued controller (ADR-066 section 7.1). */
class ClassicModelProjectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyK1K2AndK3ProjectAModelFlag() {
        for (String kind : new String[] {"centrifuge", "lathe", "crystallizer", "chemical_reactor", "cutting_machine",
                "observatory", "astrobody_data_processor", "planetary_beacon", "solar_array"}) {
            assertTrue(ClassicModelProjection.projects(ModIdentity.id(kind)), kind);
            assertTrue(ClassicModelProjection.footprint(ModIdentity.id(kind)).isPresent(), kind);
        }
        for (String kind : new String[] {"precision_laser_etcher", "rolling_machine", "arc_furnace"}) {
            assertFalse(ClassicModelProjection.projects(ModIdentity.id(kind)), kind);
            assertTrue(ClassicModelProjection.footprint(ModIdentity.id(kind)).isEmpty(), kind);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chemical_reactor", "crystallizer", "cutting_machine", "lathe", "observatory", "astrobody_data_processor"})
    void clientProjectionIsStrictTransientAndNotAuthorityForEveryK2Kind(String kind) {
        var definition = switch (kind) {
            case "chemical_reactor" -> ClassicMachineDefinition.CHEMICAL_REACTOR;
            case "crystallizer" -> ClassicMachineDefinition.CRYSTALLIZER;
            case "cutting_machine" -> ClassicMachineDefinition.CUTTING;
            case "observatory" -> ClassicMachineDefinition.OBSERVATORY;
            case "astrobody_data_processor" -> ClassicMachineDefinition.ASTROBODY_DATA_PROCESSOR;
            default -> ClassicMachineDefinition.LATHE;
        };
        var state = new ClassicControllerBlock(BlockBehaviour.Properties.of()).defaultBlockState();
        var replica = new ClassicControllerBlockEntity(null, BlockPos.ZERO, state, definition);
        var saved = replica.saveWithoutMetadata();
        var tag = new CompoundTag();
        tag.putBoolean("formed", true); tag.putBoolean("active", true);
        replica.handleUpdateTag(tag);
        assertTrue(replica.modelFormed());
        assertFalse(replica.formed(), "client projection must not form a machine");
        assertEquals(saved, replica.saveWithoutMetadata(), "projection must never enter the save schema");
        replica.handleUpdateTag(new CompoundTag());
        assertFalse(replica.modelFormed());
        tag.putInt("formed", 1); replica.handleUpdateTag(tag);
        assertFalse(replica.modelFormed());
        replica.handleUpdateTag(null);
        assertFalse(replica.modelFormed());
        assertEquals(saved, replica.saveWithoutMetadata());
    }

    @Test void encodesOneCanonicalByteAndDecodesNothingElse() {
        var tag = new CompoundTag();
        ClassicModelProjection.encode(tag, false);
        assertTrue(tag.isEmpty());
        ClassicModelProjection.encode(tag, true);
        assertEquals(Set.of(ClassicModelProjection.FORMED), tag.getAllKeys());
        assertTrue(ClassicModelProjection.decode(tag));
        assertFalse(ClassicModelProjection.decode(null));
        assertFalse(ClassicModelProjection.decode(new CompoundTag()));
        for (var wrong : new net.minecraft.nbt.Tag[] {ByteTag.valueOf((byte) 0), ByteTag.valueOf((byte) 2), ByteTag.valueOf((byte) -1),
                IntTag.valueOf(1), StringTag.valueOf("true")}) {
            var malformed = new CompoundTag();
            malformed.put(ClassicModelProjection.FORMED, wrong);
            assertFalse(ClassicModelProjection.decode(malformed), wrong.toString());
        }
    }

    @Test void theControllerRotationIsTheOneTheModelFixtureFreezes() {
        var block = new ClassicControllerBlock(BlockBehaviour.Properties.of());
        var expected = Map.of(Direction.NORTH, PatternRotation.ZERO, Direction.EAST, PatternRotation.CLOCKWISE_90,
                Direction.SOUTH, PatternRotation.CLOCKWISE_180, Direction.WEST, PatternRotation.CLOCKWISE_270);
        for (var entry : expected.entrySet()) {
            var state = block.defaultBlockState().setValue(ClassicControllerBlock.FACING, entry.getKey());
            assertEquals(new PatternTransform(entry.getValue(), false), ClassicControllerBlockEntity.transform(state));
        }
    }

    @Test void theRenderVolumeIsTheFootprintAroundTheController() {
        var footprint = ClassicModelProjection.footprint(ModIdentity.id("centrifuge")).orElseThrow();
        var controller = new BlockPos(10, 64, -20);
        assertEquals(new AABB(10, 63, -20, 13, 67, -17),
                ClassicModelProjection.bounds(footprint, new PatternTransform(PatternRotation.ZERO, false), controller));
        assertEquals(new AABB(8, 63, -20, 11, 67, -17),
                ClassicModelProjection.bounds(footprint, new PatternTransform(PatternRotation.CLOCKWISE_90, false), controller));
        assertEquals(new AABB(8, 63, -22, 11, 67, -19),
                ClassicModelProjection.bounds(footprint, new PatternTransform(PatternRotation.CLOCKWISE_180, false), controller));
        assertEquals(new AABB(10, 63, -22, 13, 67, -19),
                ClassicModelProjection.bounds(footprint, new PatternTransform(PatternRotation.CLOCKWISE_270, false), controller));
    }
}
