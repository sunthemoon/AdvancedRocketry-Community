package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Opt-in, operator-only, four-cell packaged-server fixture; never registered in normal play. */
public final class CombustionReleaseTestCommands {
    private static final String[] NAMES = {"coal", "lava", "future", "corrupt"};

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) { return; }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("combustion")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("prepare").executes(context -> prepare(context.getSource())))
                        .then(Commands.literal("report").executes(context -> report(context.getSource())))
                        .then(Commands.literal("export").executes(context -> export(context.getSource()))))));
    }

    private static BlockPos position(int index) { return new BlockPos(132 + index * 2, 80, 132); }

    private static CombustionGeneratorBlockEntity generator(ServerLevel level, int index) {
        BlockPos position = position(index);
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(position) instanceof CombustionGeneratorBlockEntity generator)) {
            throw new IllegalStateException("Combustion fixture cell is not FULL loaded: " + index);
        }
        return generator;
    }

    private static int prepare(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        if (level.getChunkSource().getChunkNow(8, 8) == null) {
            throw new IllegalStateException("Load the single fixture chunk explicitly before prepare");
        }
        for (int index = 0; index < NAMES.length; index++) {
            BlockPos position = position(index);
            if (CombustionGeneratorBlock.protectedData(level, position)) {
                throw new IllegalStateException("Prepare requires a fresh backed-up fixture, not retained repair data");
            }
            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos neighbour = position.relative(direction);
                if (CombustionGeneratorBlock.protectedData(level, neighbour)) {
                    throw new IllegalStateException("Fixture neighbour has retained repair data");
                }
                level.setBlockAndUpdate(neighbour, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(position, ModBlocks.COMBUSTION_GENERATOR.get().defaultBlockState());
            CombustionGeneratorBlockEntity generator = generator(level, index);
            if (index < 2) {
                if (!generator.setFuelFromMenu(new ItemStack(index == 0 ? Items.COAL : Items.LAVA_BUCKET))) {
                    throw new IllegalStateException("Cannot seed furnace fuel");
                }
                // A bounded test burst fills the buffer through the production tick, without editing its accounting.
                for (int tick = 0; tick < 500; tick++) {
                    CombustionGeneratorBlockEntity.serverTick(level, position, generator.getBlockState(), generator);
                }
            } else {
                CompoundTag root = CombustionSave.encode(new CombustionBurn.State(1_250, 1_600, 700),
                        new ItemStack(Items.COAL, 3));
                if (index == 2) { root.putInt("schema", 2); root.putString("extension", "retain-verbatim"); }
                else { root.putInt("energy", 20_001); }
                CompoundTag outer = new CompoundTag();
                outer.put(CombustionSave.ROOT, root);
                generator.load(outer);
                generator.setChanged();
            }
        }
        return report(source);
    }

    private static int report(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        for (int index = 0; index < NAMES.length; index++) {
            var generator = generator(level, index);
            var fuel = generator.fuelStack();
            var state = generator.burnState();
            String line = "ARCE_COMBUSTION_REPORT cell=" + NAMES[index] + " energy=" + state.energy()
                    + " duration=" + state.duration() + " remaining=" + state.remaining()
                    + " item=" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(fuel.getItem())
                    + " count=" + fuel.getCount() + " repair=" + generator.repairRequired()
                    + " energy_cap=" + generator.getCapability(ForgeCapabilities.ENERGY).isPresent()
                    + " item_cap=" + generator.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
            AdvancedRocketryCommunity.LOGGER.info(line);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return NAMES.length;
    }

    private static int export(CommandSourceStack source) {
        var generator = generator(source.getServer().overworld(), 0);
        int amount = generator.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                .extractEnergy(1_000, false);
        String line = "ARCE_COMBUSTION_EXPORT amount=" + amount;
        AdvancedRocketryCommunity.LOGGER.info(line);
        source.sendSuccess(() -> Component.literal(line), false);
        return amount;
    }
}
