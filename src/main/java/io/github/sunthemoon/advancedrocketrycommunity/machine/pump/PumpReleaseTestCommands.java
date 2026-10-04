package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Opt-in console-only fixture; it never loads chunks or replaces an occupied fixture. */
public final class PumpReleaseTestCommands {
    static final List<String> NAMES = List.of("current", "overflow", "future", "corrupt", "unsupported", "tagged", "search");
    static final UUID OWNER = UUID.fromString("18000000-0000-0000-0000-000000000006");
    private static final BlockPos INTAKE = new BlockPos(216, 136, 216);
    private static final BlockPos SOURCE = INTAKE.east();

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) { return; }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("pump")
                .then(Commands.literal("release-test").requires(source -> consolePermission(
                        source.hasPermission(4), source.getEntity() == null, source.getTextName()))
                        .then(Commands.literal("prepare-search-stop").executes(context -> prepare(context.getSource())))
                        .then(Commands.literal("report").executes(context -> report(context.getSource(), false))))));
    }
    static boolean consolePermission(boolean highestPermission, boolean noEntity, String sourceName) {
        return highestPermission && noEntity && "Server".equals(sourceName);
    }
    static boolean terminalReady(PumpCode code, int cooldown) {
        return code == PumpCode.NO_ENERGY && cooldown == 0;
    }
    static BlockPos position(int index) {
        if (index < 0 || index >= NAMES.size()) { throw new IllegalArgumentException("Invalid Pump fixture cell"); }
        return index == 6 ? new BlockPos(216, 200, 216) : new BlockPos(210 + index * 2, 200, 212);
    }
    static List<BlockPos> emptyCells() {
        List<BlockPos> cells = new ArrayList<>();
        for (int index = 0; index < NAMES.size(); index++) { cells.add(position(index)); }
        for (int y = 136; y < 200; y++) { cells.add(new BlockPos(216, y, 216)); }
        cells.add(SOURCE);
        return List.copyOf(cells);
    }
    static Tag seed(int index) {
        position(index);
        if (index == 4) { return StringTag.valueOf("opaque-pump-root"); }
        int[] amounts = {12_345, 16_001, 8_000, -1, 0, 7_000, 0};
        int[] energies = {7_654, 437, 999, 17, 0, 4_321, 100};
        CompoundTag root = new CompoundTag();
        root.putInt("schema", index == 2 ? 2 : 1);
        root.putInt("energy", energies[index]);
        root.putInt("cooldown", index == 1 ? 3 : 0);
        CompoundTag fluid = new CompoundTag();
        if (index != 6) {
            fluid.putString("FluidName", index == 2 || index == 5 ? "minecraft:lava" : "minecraft:water");
            fluid.putInt("Amount", amounts[index]);
        }
        if (index == 2 || index == 5) {
            CompoundTag tag = new CompoundTag(); tag.putString("batch", "native-retained"); fluid.put("Tag", tag);
        }
        if (index == 2) { root.putString("extension", "native-future-retained"); }
        if (index == 6) { root.putUUID("owner", OWNER); }
        root.put("fluid", fluid);
        return root;
    }
    private static ServerLevel level(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        if (level.getChunkSource().getChunkNow(13, 13) == null) {
            throw new IllegalStateException("Explicitly FULL-load the fixed Pump fixture chunk first");
        }
        return level;
    }
    private static PumpBlockEntity pump(ServerLevel level, int index) {
        var entity = level.getChunkSource().getChunkNow(13, 13).getBlockEntity(position(index));
        if (!(entity instanceof PumpBlockEntity pump)) { throw new IllegalStateException("Missing Pump fixture cell " + index); }
        return pump;
    }
    private static int prepare(CommandSourceStack source) {
        ServerLevel level = level(source);
        var chunk = level.getChunkSource().getChunkNow(13, 13);
        for (BlockPos cell : emptyCells()) {
            if (!level.isInWorldBounds(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !chunk.getBlockState(cell).isAir() || chunk.getBlockEntity(cell) != null) {
                throw new IllegalStateException("Pump fixture requires fresh empty cells in a backed-up world copy");
            }
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int index = 0; index < NAMES.size(); index++) {
            if (!level.setBlock(position(index), PumpContent.BLOCK.get().defaultBlockState(), flags)) {
                throw new IllegalStateException("Could not place fixed Pump fixture");
            }
            CompoundTag outer = new CompoundTag(); outer.put(PumpSave.ROOT, seed(index));
            PumpBlockEntity pump = pump(level, index); pump.load(outer); pump.setChanged();
        }
        if (!level.setBlock(INTAKE, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), flags)
                || !level.setBlock(SOURCE, Blocks.LAVA.defaultBlockState(), flags)) {
            throw new IllegalStateException("Could not place fixed Pump source fixture");
        }
        PumpBlockEntity search = pump(level, 6);
        PumpBlockEntity.serverTick(level, position(6), search.getBlockState(), search);
        if (search.status() != PumpCode.SEARCHING || search.energy() != 100 || !search.fluid().isEmpty()) {
            throw new IllegalStateException("Pump did not reach a bounded partial-search checkpoint");
        }
        int result = report(source, true);
        source.getServer().halt(false);
        return result;
    }
    private static int report(CommandSourceStack source, boolean checkpoint) {
        ServerLevel level = level(source);
        if (!checkpoint && !terminalReady(pump(level, 6).status(), pump(level, 6).cooldown())) {
            source.sendSuccess(() -> Component.literal("ARCE_PUMP_REPORT_WAIT"), false);
            return 0;
        }
        String phase = checkpoint ? "checkpoint" : "terminal";
        for (int index = 0; index < NAMES.size(); index++) {
            PumpBlockEntity pump = pump(level, index);
            JsonObject row = new JsonObject();
            row.addProperty("phase", phase); row.addProperty("cell", NAMES.get(index));
            row.addProperty("repair", pump.repairRequired());
            row.addProperty("fluid_cap", pump.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent());
            row.addProperty("energy_cap", pump.getCapability(ForgeCapabilities.ENERGY).isPresent());
            row.addProperty("energy", pump.energy()); row.addProperty("amount", pump.fluid().getAmount());
            row.addProperty("cooldown", pump.cooldown()); row.addProperty("status", pump.status().name());
            source.sendSuccess(() -> Component.literal("ARCE_PUMP_REPORT " + row), false);
        }
        source.sendSuccess(() -> Component.literal("ARCE_PUMP_REPORT_END phase=" + phase), false);
        return NAMES.size();
    }
}
