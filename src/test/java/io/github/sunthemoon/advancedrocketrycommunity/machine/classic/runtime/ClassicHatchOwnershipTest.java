package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicHatchOwnershipTest {
    // Level keys initialize the registries; without this the class only passed after another class bootstrapped.
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void onlyAnOlderExactOwnerBindingIsReconciled() {
        var id = UUID.randomUUID(); var position = new BlockPos(14, 70, 8);
        var current = new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 5);
        assertTrue(ClassicHatchBlockEntity.olderOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 1), current));
        assertFalse(ClassicHatchBlockEntity.olderOwnedBinding(current, current));
        assertFalse(ClassicHatchBlockEntity.olderOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 6), current));
        assertFalse(ClassicHatchBlockEntity.olderOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, UUID.randomUUID(), 1), current));
        assertFalse(ClassicHatchBlockEntity.olderOwnedBinding(new MultiblockPartBinding(1, Level.NETHER, position, id, 1), current));
        assertFalse(ClassicHatchBlockEntity.olderOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position.above(), id, 1), current));
    }
    @Test void ownerIdentityRequiresLevelPositionAndUuidButNotGeneration() {
        var id = UUID.randomUUID(); var position = new BlockPos(14, 70, 8);
        var first = new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 1);
        assertTrue(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 42)));
        assertFalse(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.OVERWORLD, position, UUID.randomUUID(), 1)));
        assertFalse(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.OVERWORLD, position.above(), id, 1)));
        assertFalse(ClassicHatchBlockEntity.sameOwner(first, new MultiblockPartBinding(1, Level.NETHER, position, id, 1)));
    }
}
