package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The array's visual tag is neither its FE save nor its formation authority. */
class K3SolarProjectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void receivedFlagIsTransientStrictAndCannotFormTheArray() {
        var state = new ClassicControllerBlock(BlockBehaviour.Properties.of()).defaultBlockState();
        var replica = new SolarArrayBlockEntity(null, BlockPos.ZERO, state, () -> null, () -> true, () -> 1, () -> null);
        var saved = replica.saveWithoutMetadata();
        var formed = new CompoundTag(); formed.putBoolean("formed", true); replica.handleUpdateTag(formed);
        assertTrue(replica.modelFormed()); assertEquals(SolarArrayRules.Structure.PENDING, replica.structure());
        assertTrue(replica.getUpdateTag().isEmpty()); assertEquals(saved, replica.saveWithoutMetadata());
        for (Tag wrong : new Tag[] {IntTag.valueOf(1), ByteTag.valueOf((byte)2), ByteTag.valueOf((byte)-1), StringTag.valueOf("true")}) {
            var invalid = new CompoundTag(); invalid.put("formed", wrong); replica.handleUpdateTag(invalid); assertFalse(replica.modelFormed());
        }
        replica.handleUpdateTag(formed); replica.handleUpdateTag(new CompoundTag()); assertFalse(replica.modelFormed());
        replica.handleUpdateTag(formed); replica.handleUpdateTag(null); assertFalse(replica.modelFormed());
        assertEquals(saved, replica.saveWithoutMetadata());
        assertTrue(replica.onlyOpCanSetNbt());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var rotated = state.setValue(ClassicControllerBlock.FACING, facing);
            var array = new SolarArrayBlockEntity(null, BlockPos.ZERO, rotated, () -> null, () -> true, () -> 1, () -> null);
            assertEquals(ClassicModelProjection.bounds(ModIdentity.id("solar_array"), facing, BlockPos.ZERO), array.getRenderBoundingBox());
        }
    }
}
