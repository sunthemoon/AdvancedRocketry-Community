package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortFilter;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRange;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortSide;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessCapabilityCache;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessFluidPortHandler;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessItemPortHandler;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessPortGameTests {
    private ProcessPortGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void itemPortEnforcesRangeFilterLockAndRevision(GameTestHelper helper) {
        ItemStackHandler inventory = new ItemStackHandler(3);
        AtomicBoolean locked = new AtomicBoolean();
        AtomicInteger changed = new AtomicInteger();
        ProcessPortRevision revision = new ProcessPortRevision(4, changed::incrementAndGet);
        ProcessItemPortHandler input = new ProcessItemPortHandler(
                inventory,
                port(
                        ProcessPortKind.ITEM,
                        ProcessPortMode.INPUT,
                        1,
                        1,
                        ProcessPortFilter.exact(Set.of("minecraft:iron_ingot"))
                ),
                locked::get,
                revision
        );

        helper.assertTrue(
                input.insertItem(0, new ItemStack(Items.GOLD_INGOT), false).is(Items.GOLD_INGOT),
                "Exact Item filter accepted the wrong resource"
        );
        helper.assertTrue(
                input.insertItem(0, new ItemStack(Items.IRON_INGOT), true).isEmpty(),
                "Simulated Item insert was rejected"
        );
        helper.assertTrue(revision.value() == 4, "Simulation changed the shared revision");
        helper.assertTrue(
                input.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                "Valid Item insert was rejected"
        );
        helper.assertTrue(inventory.getStackInSlot(0).isEmpty(), "Item port mutated a hidden slot");
        helper.assertTrue(inventory.getStackInSlot(1).is(Items.IRON_INGOT), "Item port missed its range");
        helper.assertTrue(revision.value() == 5 && changed.get() == 1, "Item mutation revision was not exact");
        helper.assertTrue(input.extractItem(0, 1, false).isEmpty(), "Input-only Item port allowed extraction");

        locked.set(true);
        helper.assertTrue(
                input.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                "Locked process input accepted external mutation"
        );
        helper.assertTrue(revision.value() == 5, "Rejected locked Item mutation changed revision");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidPortStaysInsideTankRangeAndRevisesOnce(GameTestHelper helper) {
        FluidTank hidden = new FluidTank(1_000);
        FluidTank first = new FluidTank(1_000, stack -> stack.getFluid() == Fluids.WATER);
        FluidTank second = new FluidTank(1_000, stack -> stack.getFluid() == Fluids.WATER);
        ProcessPortRevision revision = new ProcessPortRevision(2, () -> { });
        ProcessFluidPortHandler input = new ProcessFluidPortHandler(
                List.of(hidden, first, second),
                port(
                        ProcessPortKind.FLUID,
                        ProcessPortMode.INPUT,
                        1,
                        2,
                        ProcessPortFilter.exact(Set.of("minecraft:water"))
                ),
                () -> false,
                revision
        );

        int simulated = input.fill(
                new FluidStack(Fluids.WATER, 1_500),
                IFluidHandler.FluidAction.SIMULATE
        );
        helper.assertTrue(simulated == 1_500 && revision.value() == 2, "Fluid simulation was not side-effect free");
        int filled = input.fill(
                new FluidStack(Fluids.WATER, 1_500),
                IFluidHandler.FluidAction.EXECUTE
        );
        helper.assertTrue(filled == 1_500, "Fluid port did not fill its complete visible range");
        helper.assertTrue(hidden.isEmpty(), "Fluid port spilled into a hidden tank");
        helper.assertTrue(
                first.getFluidAmount() == 1_000 && second.getFluidAmount() == 500,
                "Fluid port range distribution was incorrect"
        );
        helper.assertTrue(revision.value() == 3, "One Fluid operation did not advance revision exactly once");
        helper.assertTrue(
                input.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "Input-only Fluid port allowed extraction"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void capabilityCacheReusesAndInvalidatesViews(GameTestHelper helper) {
        ProcessCapabilityCache cache = new ProcessCapabilityCache();
        EnergyStorage storage = new EnergyStorage(1_000);
        AtomicInteger creations = new AtomicInteger();
        cache.register(ForgeCapabilities.ENERGY, Direction.NORTH, () -> {
            creations.incrementAndGet();
            return storage;
        });

        var first = cache.get(ForgeCapabilities.ENERGY, Direction.NORTH);
        var second = cache.get(ForgeCapabilities.ENERGY, Direction.NORTH);
        helper.assertTrue(first.resolve().orElseThrow() == storage, "First cached capability view changed identity");
        helper.assertTrue(second.resolve().orElseThrow() == storage, "Second cached capability view changed identity");
        helper.assertTrue(creations.get() == 1, "Capability query allocated a second view");
        helper.assertTrue(
                !cache.get(ForgeCapabilities.ENERGY, Direction.SOUTH).isPresent(),
                "Capability cache exposed an unregistered side"
        );

        cache.invalidate();
        helper.assertTrue(!first.isPresent() && !cache.isValid(), "Capability cache did not invalidate its views");
        helper.succeed();
    }

    private static ProcessPortDefinition port(
            ProcessPortKind kind,
            ProcessPortMode mode,
            int first,
            int count,
            ProcessPortFilter filter
    ) {
        return new ProcessPortDefinition(
                "test_port",
                kind,
                mode,
                Set.of(ProcessPortSide.TOP),
                new ProcessPortRange(first, count),
                filter
        );
    }
}
