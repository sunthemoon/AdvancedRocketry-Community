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
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProcessForgePortAdapterTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

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

    @Test
    void retainedViewsBecomeReadEmptyAndMutationInertWhenAccessIsRevoked() {
        AtomicBoolean accessAllowed = new AtomicBoolean(true);
        AtomicInteger changed = new AtomicInteger();
        ProcessPortRevision revision = new ProcessPortRevision(0, changed::incrementAndGet);

        ItemStackHandler items = new ItemStackHandler(1);
        ProcessItemPortHandler itemInput = new ProcessItemPortHandler(
                items,
                port(ProcessPortKind.ITEM, ProcessPortMode.INPUT, 0, 1),
                () -> false,
                accessAllowed::get,
                revision
        );
        ProcessItemPortHandler itemOutput = new ProcessItemPortHandler(
                items,
                port(ProcessPortKind.ITEM, ProcessPortMode.OUTPUT, 0, 1),
                () -> false,
                accessAllowed::get,
                revision
        );
        FluidTank tank = new FluidTank(1_000, stack -> stack.getFluid() == Fluids.WATER);
        ProcessFluidPortHandler fluidInput = new ProcessFluidPortHandler(
                List.of(tank),
                port(ProcessPortKind.FLUID, ProcessPortMode.INPUT, 0, 1),
                () -> false,
                accessAllowed::get,
                revision
        );
        EnergyStorage energy = new EnergyStorage(1_000, 1_000, 0);
        ProcessEnergyPortStorage energyInput = new ProcessEnergyPortStorage(
                energy,
                port(ProcessPortKind.ENERGY, ProcessPortMode.INPUT, 0, 1),
                () -> false,
                accessAllowed::get,
                revision
        );

        assertTrue(itemInput.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty());
        assertEquals(250, fluidInput.fill(
                new FluidStack(Fluids.WATER, 250),
                IFluidHandler.FluidAction.EXECUTE
        ));
        assertEquals(500, energyInput.receiveEnergy(500, false));
        assertEquals(3, revision.value());

        accessAllowed.set(false);
        assertTrue(itemInput.getStackInSlot(0).isEmpty());
        assertEquals(1, itemInput.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount());
        assertTrue(itemOutput.extractItem(0, 1, false).isEmpty());
        assertTrue(fluidInput.getFluidInTank(0).isEmpty());
        assertEquals(0, fluidInput.fill(
                new FluidStack(Fluids.WATER, 100),
                IFluidHandler.FluidAction.EXECUTE
        ));
        assertEquals(0, energyInput.getEnergyStored());
        assertEquals(0, energyInput.receiveEnergy(100, false));
        assertFalse(energyInput.canReceive());
        assertEquals(3, revision.value());
        assertEquals(3, changed.get());
        assertEquals(2, items.getStackInSlot(0).getCount());
        assertEquals(250, tank.getFluidAmount());
        assertEquals(500, energy.getEnergyStored());
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
