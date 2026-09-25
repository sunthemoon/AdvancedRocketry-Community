package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** Property-gated packaged-server fixture commands; never registered in normal runs. */
public final class RollingMachineCommands {
    private static final String RELEASE_TEST_HOOK_PROPERTY =
            "advancedrocketrycommunity.releaseTestHooks";
    private static final String POSITION_ARGUMENT = "controller";
    private static final int INPUT_COUNT = 2;
    private static final int WATER_AMOUNT = 500;
    private static final int ENERGY_AMOUNT = 4_000;

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(RELEASE_TEST_HOOK_PROPERTY)) {
            return;
        }
        event.getDispatcher().register(Commands.literal("arce")
                .then(Commands.literal("rolling")
                        .then(Commands.literal("release-test")
                                .requires(source -> source.hasPermission(2))
                                .then(positioned("prepare", this::prepare))
                                .then(positioned("seed", this::seed))
                                .then(positioned("pause", this::pause))
                                .then(positioned("resume", this::resume))
                                .then(positioned("report", this::report)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> positioned(
            String name,
            com.mojang.brigadier.Command<CommandSourceStack> command
    ) {
        return Commands.literal(name)
                .then(Commands.argument(POSITION_ARGUMENT, BlockPosArgument.blockPos())
                        .executes(command));
    }

    private int prepare(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos controllerPosition = position(context);
        BlockPos base = controllerPosition.offset(-2, 0, 0);
        for (int z = 0; z < 2; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 5; x++) {
                    BlockPos cell = base.offset(x, y, z);
                    if (!level.isInWorldBounds(cell) || !level.hasChunkAt(cell)) {
                        return fail(source, "Rolling fixture footprint is not fully loaded");
                    }
                }
            }
        }

        level.setBlock(controllerPosition.relative(Direction.NORTH), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int z = 0; z < 2; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 5; x++) {
                    level.setBlock(
                            base.offset(x, y, z),
                            ModBlocks.MACHINE_CASING.get().defaultBlockState(),
                            Block.UPDATE_ALL
                    );
                }
            }
        }
        level.setBlock(base, ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base.offset(1, 0, 0),
                ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base.offset(3, 0, 0),
                ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base.offset(4, 0, 0),
                ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(
                controllerPosition,
                ModBlocks.ROLLING_MACHINE.get().defaultBlockState()
                        .setValue(RollingMachineBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL
        );
        RollingMachineRuntime.markDirty(level, controllerPosition);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_ROLLING_PREPARE controller={} cells=30",
                compact(controllerPosition)
        );
        source.sendSuccess(() -> Component.literal("Prepared Rolling Machine fixture"), false);
        return 1;
    }

    private int seed(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RollingFixture fixture = fixture(source, position(context));
        if (fixture == null) {
            return 0;
        }
        if (!fixture.ports().itemInput().storedItemCopy().isEmpty()
                || !fixture.ports().fluidInput().storedFluidCopy().isEmpty()
                || fixture.ports().energyInput().storedEnergy() != 0
                || !fixture.ports().itemOutput().storedItemCopy().isEmpty()) {
            return fail(source, "Rolling fixture resources must be empty before seeding");
        }

        IItemHandler items = fixture.ports().itemInput()
                .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        IFluidHandler fluids = fixture.ports().fluidInput()
                .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        IEnergyStorage energy = fixture.ports().energyInput()
                .getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        ItemStack itemInput = new ItemStack(Items.IRON_INGOT, INPUT_COUNT);
        FluidStack water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, WATER_AMOUNT);
        if (items == null || fluids == null || energy == null
                || !items.insertItem(0, itemInput, true).isEmpty()
                || fluids.fill(water, IFluidHandler.FluidAction.SIMULATE) != WATER_AMOUNT
                || !canReceiveEnergy(energy, ENERGY_AMOUNT)) {
            return fail(source, "Rolling fixture rejected simulated seed resources");
        }
        if (!receiveEnergyFully(energy, ENERGY_AMOUNT)
                || fluids.fill(water, IFluidHandler.FluidAction.EXECUTE) != WATER_AMOUNT
                || !items.insertItem(0, itemInput, false).isEmpty()) {
            return fail(source, "Rolling fixture rejected committed seed resources");
        }
        RollingMachineRuntime.markProcessReady(source.getLevel(), fixture.controller().getBlockPos());
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_ROLLING_SEED controller={} input={} water={} energy={}",
                compact(fixture.controller().getBlockPos()),
                INPUT_COUNT,
                WATER_AMOUNT,
                ENERGY_AMOUNT
        );
        source.sendSuccess(() -> Component.literal("Seeded Rolling Machine fixture"), false);
        return 1;
    }

    private int pause(CommandContext<CommandSourceStack> context) {
        return setPaused(context, true);
    }

    private int resume(CommandContext<CommandSourceStack> context) {
        return setPaused(context, false);
    }

    private int setPaused(CommandContext<CommandSourceStack> context, boolean paused) {
        CommandSourceStack source = context.getSource();
        BlockPos controllerPosition = position(context);
        if (!(source.getLevel().getBlockEntity(controllerPosition) instanceof RollingMachineBlockEntity)) {
            return fail(source, "Rolling fixture controller is missing");
        }
        BlockPos signalPosition = controllerPosition.relative(Direction.NORTH);
        source.getLevel().setBlock(
                signalPosition,
                paused ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL
        );
        RollingMachineRuntime.markDirty(source.getLevel(), controllerPosition);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_ROLLING_POWER controller={} paused={}",
                compact(controllerPosition),
                paused
        );
        source.sendSuccess(
                () -> Component.literal(paused ? "Paused Rolling fixture" : "Resumed Rolling fixture"),
                false
        );
        return 1;
    }

    private int report(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos position = position(context);
        if (!(source.getLevel().getBlockEntity(position) instanceof RollingMachineBlockEntity controller)) {
            return fail(source, "Rolling fixture controller is missing");
        }
        ReportResources resources = RollingMachinePortSet.resolve(source.getLevel(), controller)
                .map(ReportResources::from)
                .orElse(ReportResources.UNAVAILABLE);
        CompoundTag saved = controller.saveWithFullMetadata();
        CompoundTag process = saved.getCompound(RollingMachineProcessPersistence.ROOT);
        String lastApplied = process.getString("last_applied_transaction");
        if (lastApplied.isEmpty()) {
            lastApplied = "none";
        }
        String journal = saved.contains(RollingMachineJournalPersistence.ROOT, Tag.TAG_COMPOUND)
                ? saved.getCompound(RollingMachineJournalPersistence.ROOT).getString("phase")
                : "none";
        int progress = controller.processProgress().map(value -> value.progressTicks()).orElse(0);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_ROLLING_REPORT controller={} formation={} generation={} "
                        + "process={} failure={} progress={} total={} input_item={} input_count={} "
                        + "water={} energy={} output_item={} output_count={} revision={} journal={} "
                        + "last_applied={}",
                compact(position),
                controller.formationState(),
                controller.generation(),
                controller.processState(),
                controller.processFailure().code(),
                progress,
                controller.totalProcessingTicks(),
                resources.inputItem(),
                resources.inputCount(),
                resources.water(),
                resources.energy(),
                resources.outputItem(),
                resources.outputCount(),
                controller.resourceRevision(),
                journal,
                lastApplied
        );
        source.sendSuccess(() -> Component.literal("Reported Rolling Machine fixture"), false);
        return 1;
    }

    private static RollingFixture fixture(CommandSourceStack source, BlockPos position) {
        if (!(source.getLevel().getBlockEntity(position) instanceof RollingMachineBlockEntity controller)) {
            fail(source, "Rolling fixture controller is missing");
            return null;
        }
        if (controller.formationState() != MultiblockFormationState.FORMED) {
            fail(source, "Rolling fixture is not formed");
            return null;
        }
        return RollingMachinePortSet.resolve(source.getLevel(), controller)
                .map(ports -> new RollingFixture(controller, ports))
                .orElseGet(() -> {
                    fail(source, "Rolling fixture ports are unavailable");
                    return null;
                });
    }

    private static BlockPos position(CommandContext<CommandSourceStack> context) {
        return BlockPosArgument.getBlockPos(context, POSITION_ARGUMENT);
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static boolean canReceiveEnergy(IEnergyStorage storage, int requested) {
        long available = (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
        return requested > 0
                && storage.canReceive()
                && available >= requested
                && storage.receiveEnergy(requested, true) > 0;
    }

    private static boolean receiveEnergyFully(IEnergyStorage storage, int requested) {
        int remaining = requested;
        while (remaining > 0) {
            int received = storage.receiveEnergy(remaining, false);
            if (received <= 0) {
                return false;
            }
            remaining -= received;
        }
        return true;
    }

    private static String compact(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private record RollingFixture(
            RollingMachineBlockEntity controller,
            RollingMachinePortSet ports
    ) {
    }

    private record ReportResources(
            String inputItem,
            int inputCount,
            int water,
            int energy,
            String outputItem,
            int outputCount
    ) {
        private static final ReportResources UNAVAILABLE =
                new ReportResources("unavailable", -1, -1, -1, "unavailable", -1);

        private static ReportResources from(RollingMachinePortSet ports) {
            ItemStack input = ports.itemInput().storedItemCopy();
            ItemStack output = ports.itemOutput().storedItemCopy();
            return new ReportResources(
                    BuiltInRegistries.ITEM.getKey(input.getItem()).toString(),
                    input.getCount(),
                    ports.fluidInput().storedFluidCopy().getAmount(),
                    ports.energyInput().storedEnergy(),
                    BuiltInRegistries.ITEM.getKey(output.getItem()).toString(),
                    output.getCount()
            );
        }
    }
}
