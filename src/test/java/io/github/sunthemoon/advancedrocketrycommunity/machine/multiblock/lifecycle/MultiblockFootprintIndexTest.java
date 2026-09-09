package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiblockFootprintIndexTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void replacementUpdatesCellAndChunkReverseIndexes() {
        MultiblockFootprintIndex index = new MultiblockFootprintIndex(4, 4_096);
        MultiblockControllerKey controller = key(new BlockPos(-1, 64, -1));

        assertTrue(index.replace(controller, Set.of(
                controller.position(),
                new BlockPos(-17, 64, -1)
        )));
        assertEquals(Set.of(controller), index.controllersAt(Level.OVERWORLD, new BlockPos(-17, 64, -1)));
        assertEquals(Set.of(controller), index.controllersInChunk(Level.OVERWORLD, -2, -1));

        assertTrue(index.replace(controller, Set.of(
                controller.position(),
                new BlockPos(16, 64, 16)
        )));
        assertTrue(index.controllersAt(Level.OVERWORLD, new BlockPos(-17, 64, -1)).isEmpty());
        assertTrue(index.controllersInChunk(Level.OVERWORLD, -2, -1).isEmpty());
        assertEquals(Set.of(controller), index.controllersInChunk(Level.OVERWORLD, 1, 1));
        assertEquals(2, index.cellReferenceCount());
    }

    @Test
    void rejectedReplacementLeavesPreviousFootprintUntouched() {
        MultiblockFootprintIndex index = new MultiblockFootprintIndex(1, 4_096);
        MultiblockControllerKey first = key(BlockPos.ZERO);
        MultiblockControllerKey second = key(new BlockPos(32, 0, 0));
        assertTrue(index.replace(first, Set.of(BlockPos.ZERO)));

        assertFalse(index.replace(second, Set.of(second.position())));

        assertEquals(Set.of(first), index.controllersAt(Level.OVERWORLD, BlockPos.ZERO));
        assertEquals(1, index.controllerCount());
        assertEquals(1, index.cellReferenceCount());
    }

    @Test
    void requiresControllerCellAndBoundedFootprint() {
        MultiblockFootprintIndex index = new MultiblockFootprintIndex(1, 4_096);
        MultiblockControllerKey controller = key(BlockPos.ZERO);

        assertThrows(
                IllegalArgumentException.class,
                () -> index.replace(controller, Set.of(BlockPos.ZERO.east()))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> index.replace(controller, Set.of())
        );
    }

    private static MultiblockControllerKey key(BlockPos position) {
        return new MultiblockControllerKey(Level.OVERWORLD, position);
    }
}
