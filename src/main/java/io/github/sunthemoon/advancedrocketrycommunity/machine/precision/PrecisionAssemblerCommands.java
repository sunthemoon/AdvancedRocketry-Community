package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraftforge.items.IItemHandler;

/** Opt-in packaged-server fixture commands, absent from normal gameplay. */
public final class PrecisionAssemblerCommands {
    private static final String RELEASE_TEST_HOOK_PROPERTY =
            "advancedrocketrycommunity.releaseTestHooks";
    private static final String POSITION_ARGUMENT = "controller";
    private static final int ENERGY_AMOUNT = 1_600;

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(RELEASE_TEST_HOOK_PROPERTY)) {
            return;
        }
        event.getDispatcher().register(Commands.literal("arce")
                .then(Commands.literal("precision")
                        .then(Commands.literal("release-test")
                                .requires(source -> source.hasPermission(2))
                                .then(positioned("prepare", this::prepare))
                                .then(positioned("seed", this::seed))
                                .then(positioned("pause", context -> setPaused(context, true)))
                                .then(positioned("resume", context -> setPaused(context, false)))
                                .then(positioned("report", this::report)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> positioned(
            String name,
            Command<CommandSourceStack> command
    ) {
        return Commands.literal(name)
                .then(Commands.argument(POSITION_ARGUMENT, BlockPosArgument.blockPos())
                        .executes(command));
    }

    private int prepare(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos controllerPosition = position(context);
        BlockPos base = controllerPosition.offset(-1, 0, 0);
        for (int z = 0; z < 4; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    BlockPos cell = base.offset(x, y, z);
                    if (!level.isInWorldBounds(cell) || !level.hasChunkAt(cell)) {
                        return fail(source, "Precision fixture footprint is not fully loaded");
                    }
                }
            }
        }

        level.setBlock(controllerPosition.relative(Direction.NORTH),
                Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int z = 0; z < 4; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    level.setBlock(base.offset(x, y, z),
                            ModBlocks.MACHINE_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (int z = 0; z < 3; z++) {
            level.setBlock(base.offset(0, 0, z),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        for (int z = 0; z < 2; z++) {
            level.setBlock(base.offset(2, 0, z),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(base.offset(2, 0, 2),
                ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base.offset(0, 0, 3),
                ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base.offset(2, 0, 3),
                ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(controllerPosition,
                ModBlocks.PRECISION_ASSEMBLER.get().defaultBlockState()
                        .setValue(PrecisionAssemblerBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        PrecisionAssemblerRuntime.markDirty(level, controllerPosition);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_PRECISION_PREPARE controller={} cells=36",
                compact(controllerPosition));
        source.sendSuccess(() -> Component.literal("Prepared Precision Assembler fixture"), false);
        return 1;
    }

    private int seed(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Fixture fixture = fixture(source, position(context));
        if (fixture == null) {
            return 0;
        }
        if (fixture.ports().inputs().stream().anyMatch(port -> !port.storedItemCopy().isEmpty())
                || fixture.ports().outputs().stream().anyMatch(port -> !port.storedItemCopy().isEmpty())
                || fixture.ports().energy().storedEnergy() != 0) {
            return fail(source, "Precision fixture resources must be empty before seeding");
        }
        IItemHandler first = fixture.ports().inputs().get(0)
                .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        IItemHandler second = fixture.ports().inputs().get(1)
                .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        IEnergyStorage energy = fixture.ports().energy()
                .getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 2);
        ItemStack redstone = new ItemStack(Items.REDSTONE, 2);
        if (first == null || second == null || energy == null
                || !first.insertItem(0, iron, true).isEmpty()
                || !second.insertItem(0, redstone, true).isEmpty()
                || !energy.canReceive()
                || (long) energy.getMaxEnergyStored() - energy.getEnergyStored() < ENERGY_AMOUNT) {
            return fail(source, "Precision fixture rejected simulated seed resources");
        }
        if (!receiveEnergyFully(energy, ENERGY_AMOUNT)
                || !first.insertItem(0, iron, false).isEmpty()
                || !second.insertItem(0, redstone, false).isEmpty()) {
            return fail(source, "Precision fixture rejected committed seed resources");
        }
        PrecisionAssemblerRuntime.markProcessReady(source.getLevel(), fixture.controller().getBlockPos());
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_PRECISION_SEED controller={} iron=2 redstone=2 energy={}",
                compact(fixture.controller().getBlockPos()), ENERGY_AMOUNT);
        source.sendSuccess(() -> Component.literal("Seeded Precision Assembler fixture"), false);
        return 1;
    }

    private int setPaused(CommandContext<CommandSourceStack> context, boolean paused) {
        CommandSourceStack source = context.getSource();
        BlockPos controllerPosition = position(context);
        if (!source.getLevel().hasChunkAt(controllerPosition)
                || !(source.getLevel().getBlockEntity(controllerPosition)
                instanceof PrecisionAssemblerBlockEntity)) {
            return fail(source, "Precision fixture controller is missing or unloaded");
        }
        source.getLevel().setBlock(controllerPosition.relative(Direction.NORTH),
                paused ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        PrecisionAssemblerRuntime.markProcessReady(source.getLevel(), controllerPosition);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_PRECISION_POWER controller={} paused={}",
                compact(controllerPosition), paused);
        source.sendSuccess(() -> Component.literal(paused
                ? "Paused Precision fixture" : "Resumed Precision fixture"), false);
        return 1;
    }

    private int report(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos controllerPosition = position(context);
        if (!source.getLevel().hasChunkAt(controllerPosition)
                || !(source.getLevel().getBlockEntity(controllerPosition)
                instanceof PrecisionAssemblerBlockEntity controller)) {
            return fail(source, "Precision fixture controller is missing or unloaded");
        }
        PrecisionAssemblerPortSet ports = PrecisionAssemblerPortSet.resolve(source.getLevel(), controller)
                .orElse(null);
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        for (int index = 0; index < PrecisionAssemblerChannels.INPUT_COUNT; index++) {
            inputs.add(ports == null ? "unavailable:-1" : stack(ports.inputs().get(index).storedItemCopy()));
        }
        for (int index = 0; index < PrecisionAssemblerChannels.OUTPUT_COUNT; index++) {
            outputs.add(ports == null ? "unavailable:-1" : stack(ports.outputs().get(index).storedItemCopy()));
        }
        CompoundTag saved = controller.saveWithFullMetadata();
        CompoundTag process = saved.getCompound(ProcessStatePersistence.ROOT);
        String lastApplied = process.getString("last_applied_transaction");
        if (lastApplied.isEmpty()) {
            lastApplied = "none";
        }
        String journal = saved.contains(ProcessJournalPersistence.ROOT, Tag.TAG_COMPOUND)
                ? saved.getCompound(ProcessJournalPersistence.ROOT).getString("phase")
                : "none";
        int progress = controller.processProgress().map(value -> value.progressTicks()).orElse(0);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_PRECISION_REPORT controller={} formation={} generation={} "
                        + "process={} failure={} progress={} total={} inputs={} outputs={} "
                        + "energy={} revision={} journal={} last_applied={}",
                compact(controllerPosition), controller.formationState(), controller.generation(),
                controller.processState(), controller.processFailure().code(), progress,
                controller.totalProcessingTicks(), String.join(",", inputs), String.join(",", outputs),
                ports == null ? -1 : ports.energy().storedEnergy(), controller.resourceRevision(),
                journal, lastApplied);
        source.sendSuccess(() -> Component.literal("Reported Precision Assembler fixture"), false);
        return 1;
    }

    private static Fixture fixture(CommandSourceStack source, BlockPos position) {
        if (!source.getLevel().hasChunkAt(position)
                || !(source.getLevel().getBlockEntity(position) instanceof PrecisionAssemblerBlockEntity controller)) {
            fail(source, "Precision fixture controller is missing or unloaded");
            return null;
        }
        if (controller.formationState() != MultiblockFormationState.FORMED) {
            fail(source, "Precision fixture is not formed");
            return null;
        }
        return PrecisionAssemblerPortSet.resolve(source.getLevel(), controller)
                .map(ports -> new Fixture(controller, ports))
                .orElseGet(() -> {
                    fail(source, "Precision fixture ports are unavailable");
                    return null;
                });
    }

    private static boolean receiveEnergyFully(IEnergyStorage energy, int requested) {
        int remaining = requested;
        while (remaining > 0) {
            int received = energy.receiveEnergy(remaining, false);
            if (received <= 0) {
                return false;
            }
            remaining -= received;
        }
        return true;
    }

    private static String stack(ItemStack item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem()) + ":" + item.getCount();
    }

    private static BlockPos position(CommandContext<CommandSourceStack> context) {
        return BlockPosArgument.getBlockPos(context, POSITION_ARGUMENT);
    }

    private static String compact(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private record Fixture(PrecisionAssemblerBlockEntity controller, PrecisionAssemblerPortSet ports) {
    }
}
