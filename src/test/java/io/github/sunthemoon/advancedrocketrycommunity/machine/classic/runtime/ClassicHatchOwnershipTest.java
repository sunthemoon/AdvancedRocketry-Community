package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ClassicHatchOwnershipTest {
    @Test void ownerIdentityRequiresLevelPositionAndUuidButNotGeneration() {
        var id = UUID.randomUUID(); var position = new BlockPos(14, 70, 8);
        var first = new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 1);
        assertTrue(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 42)));
        assertFalse(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.OVERWORLD, position, UUID.randomUUID(), 1)));
        assertFalse(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.OVERWORLD, position.above(), id, 1)));
        assertFalse(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.NETHER, position, id, 1)));
    }
}
