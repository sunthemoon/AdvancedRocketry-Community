package io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRange;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortSide;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraftforge.energy.EnergyStorage;
import org.junit.jupiter.api.Test;

class ProcessForgePortAdapterTest {
    @Test
    void everyLocalSideMapsFromControllerFacing() {
        assertEquals(Direction.EAST, ProcessPortDirections.toWorld(ProcessPortSide.FRONT, Direction.EAST));
        assertEquals(Direction.WEST, ProcessPortDirections.toWorld(ProcessPortSide.BACK, Direction.EAST));
        assertEquals(Direction.NORTH, ProcessPortDirections.toWorld(ProcessPortSide.LEFT, Direction.EAST));
        assertEquals(Direction.SOUTH, ProcessPortDirections.toWorld(ProcessPortSide.RIGHT, Direction.EAST));
        assertEquals(Direction.UP, ProcessPortDirections.toWorld(ProcessPortSide.TOP, Direction.EAST));
        assertEquals(Direction.DOWN, ProcessPortDirections.toWorld(ProcessPortSide.BOTTOM, Direction.EAST));
        assertEquals(null, ProcessPortDirections.toWorld(ProcessPortSide.UNSIDED, Direction.EAST));
    }

    @Test
    void energyInputRemainsReceivableDuringProcessAndCannotExtract() {
        EnergyStorage storage = new EnergyStorage(10_000, 1_000, 1_000);
        ProcessPortRevision revision = new ProcessPortRevision(0, () -> { });
        ProcessEnergyPortStorage input = new ProcessEnergyPortStorage(
                storage,
                port(ProcessPortKind.ENERGY, ProcessPortMode.INPUT, 0, 1),
                () -> true,
                revision
        );

        assertEquals(1_000, input.receiveEnergy(1_000, false));
        assertEquals(1, revision.value());
        assertEquals(0, input.extractEnergy(1_000, false));
        assertTrue(input.canReceive());
        assertFalse(input.canExtract());
    }

    private static ProcessPortDefinition port(
            ProcessPortKind kind,
            ProcessPortMode mode,
            int first,
            int count
    ) {
        return new ProcessPortDefinition(
                "test_port",
                kind,
                mode,
                Set.of(ProcessPortSide.TOP),
                new ProcessPortRange(first, count),
                io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortFilter.any()
        );
    }
}
