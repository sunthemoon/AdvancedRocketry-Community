package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class K4ModelProjectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void transientFlagUsesOnlyCanonicalByteAndNeverWritesSaveData() {
        var entity = new BlockEntity(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState()) { };
        var projection = new K4ModelProjection(); var saved = entity.saveWithoutMetadata();
        var tag = new CompoundTag(); tag.putBoolean("formed", true);
        projection.read(tag); assertTrue(projection.formed());
        for (Tag wrong : new Tag[] {IntTag.valueOf(1), ByteTag.valueOf((byte)2), ByteTag.valueOf((byte)-1), StringTag.valueOf("true"), new CompoundTag()}) {
            var invalid = new CompoundTag(); invalid.put("formed", wrong); projection.read(invalid); assertFalse(projection.formed());
        }
        projection.read(tag); projection.read(null); assertFalse(projection.formed());
        projection.read(tag); projection.read(new CompoundTag()); assertFalse(projection.formed());
        projection.publish(entity, true); projection.publish(entity, true); assertTrue(projection.formed());
        projection.publish(entity, false); assertFalse(projection.formed()); assertEquals(saved, entity.saveWithoutMetadata());
    }
    @Test void fixedBoxesTranslateAndUnsupportedKindsOrFacingsFailClosed() {
        var position = new BlockPos(40,80,-20);
        for (String kind : new String[] {"black_hole_generator","orbital_laser_drill","precision_assembler","railgun","rolling_machine","elevator_anchor"}) {
            assertTrue(K4ModelProjection.projects(ModIdentity.id(kind)));
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var zero = K4ModelProjection.bounds(kind,facing,BlockPos.ZERO);
                assertEquals(zero.move(position),K4ModelProjection.bounds(kind,facing,position));
                assertTrue(zero.getSize() < 32);
            }
            assertThrows(IllegalArgumentException.class, () -> K4ModelProjection.bounds(kind,Direction.UP,position));
        }
        assertFalse(K4ModelProjection.projects(ModIdentity.id("elevator_terminal")));
        assertThrows(IllegalArgumentException.class, () -> K4ModelProjection.bounds("unknown",Direction.NORTH,position));
    }
}
