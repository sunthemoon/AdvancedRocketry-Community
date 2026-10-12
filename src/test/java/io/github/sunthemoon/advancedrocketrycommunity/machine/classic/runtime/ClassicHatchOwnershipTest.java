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
        assertTrue(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 1), current));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(current, current));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 6), current));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, UUID.randomUUID(), 1), current));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.NETHER, position, id, 1), current));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position.above(), id, 1), current));
    }
    @Test void anExactOwnerBindingTwoOrMoreGenerationsAheadIsReconciledButAForeignOneIsNot() {
        var id = UUID.randomUUID(); var position = new BlockPos(14, 70, 8);
        var current = new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 5);
        // Generation 6 is the direct successor that formation adopts itself; 7 and beyond can only be reconciled.
        assertTrue(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 7), current));
        assertTrue(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, Long.MAX_VALUE), current));
        assertTrue(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 3),
                new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 1)));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 2),
                new MultiblockPartBinding(1, Level.OVERWORLD, position, id, 1)));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position, UUID.randomUUID(), 7), current));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.NETHER, position, id, 7), current));
        assertFalse(ClassicHatchBlockEntity.reconcilableOwnedBinding(new MultiblockPartBinding(1, Level.OVERWORLD, position.above(), id, 7), current));
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
