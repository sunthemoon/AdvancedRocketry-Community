package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in OP-only, fixed five-cell packaged fixture. Requires an explicitly FULL-loaded fresh copied-world chunk. */
public final class TankReleaseTestCommands {
    private static final String[] NAMES = {"current", "overflow", "tagged", "future", "corrupt"};
    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) { return; }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("tank")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("prepare").executes(context -> prepare(context.getSource())))
                        .then(Commands.literal("report").executes(context -> report(context.getSource())))
                        .then(Commands.literal("drain").executes(context -> drain(context.getSource())))
                        .then(Commands.literal("package").executes(context -> packageItem(context.getSource()))))));
    }
    private static BlockPos position(int index) { return new BlockPos(180 + index * 2, 180, 180); }
    private static ServerLevel level(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        if (level.getChunkSource().getChunkNow(11, 11) == null) {
            throw new IllegalStateException("Explicitly FULL-load the single tank fixture chunk before this command");
        }
        return level;
    }
    private static PressurizedTankBlockEntity tank(ServerLevel level, int index) {
        var entity = level.getChunkSource().getChunkNow(11, 11).getBlockEntity(position(index));
        if (!(entity instanceof PressurizedTankBlockEntity tank)) { throw new IllegalStateException("Missing tank fixture: " + index); }
        return tank;
    }
    private static CompoundTag seed(int index) {
        FluidStack fluid = new FluidStack(index == 2 ? Fluids.LAVA : Fluids.WATER,
                index == 1 ? 300_000 : index == 0 ? 12_345 : 5_000);
        if (index == 2) { fluid.setTag(new CompoundTag()); fluid.getTag().putString("batch", "native-retained"); }
        CompoundTag root = TankSave.encode(fluid);
        if (index == 3) { root.putInt("schema", 2); root.putString("extension", "retain-verbatim"); }
        if (index == 4) { root.getCompound("fluid").putInt("Amount", -1); }
        return root;
    }
    private static int prepare(CommandSourceStack source) {
        ServerLevel level = level(source);
        for (int index = 0; index < NAMES.length; index++) {
            if (!level.getBlockState(position(index)).isAir() || !level.getBlockState(position(index).above()).isAir()
                    || !level.getBlockState(position(index).below()).isAir()) {
                throw new IllegalStateException("Tank fixture requires fresh empty cells in a backed-up world copy");
            }
        }
        var block = ForgeRegistries.BLOCKS.getValue(ModIdentity.id("pressurized_tank"));
        if (!(block instanceof PressurizedTankBlock)) { throw new IllegalStateException("Tank integration missing"); }
        for (int index = 0; index < NAMES.length; index++) {
            level.setBlockAndUpdate(position(index).below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(position(index), block.defaultBlockState());
            var tank = tank(level, index);
            CompoundTag outer = new CompoundTag(); outer.put(TankSave.ROOT, seed(index));
            tank.load(outer); tank.setChanged();
        }
        return report(source);
    }
    private static int report(CommandSourceStack source) {
        ServerLevel level = level(source);
        for (int index = 0; index < NAMES.length; index++) {
            var entity = level.getChunkSource().getChunkNow(11, 11).getBlockEntity(position(index));
            CompoundTag root;
            String carrier;
            boolean repair = false;
            boolean capability = false;
            int capacity = -1;
            if (entity instanceof PressurizedTankBlockEntity tank) {
                root = tank.saveWithoutMetadata().getCompound(TankSave.ROOT);
                carrier = "be"; repair = tank.repairRequired(); capacity = tank.capacity();
                capability = tank.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent();
            } else if (index == 0) {
                var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(position(index)).inflate(2),
                        item -> item.getItem().getItem() instanceof PressurizedTankItem);
                if (items.size() != 1 || !PressurizedTankItem.placeable(items.get(0).getItem())) {
                    throw new IllegalStateException("Expected one persisted current tank Item carrier");
                }
                root = items.get(0).getItem().getTag().getCompound(TankSave.ROOT); carrier = "item";
            } else { throw new IllegalStateException("Missing retained tank cell " + index); }
            if (index >= 1 && !root.equals(seed(index))) { throw new IllegalStateException("Tank fixture root changed: " + index); }
            String line = "ARCE_TANK_REPORT cell=" + NAMES[index] + " carrier=" + carrier
                    + " amount=" + root.getCompound("fluid").getInt("Amount") + " capacity=" + capacity
                    + " repair=" + repair + " fluid_cap=" + capability + " root=" + root;
            AdvancedRocketryCommunity.LOGGER.info(line);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return NAMES.length;
    }
    private static int drain(CommandSourceStack source) {
        var tank = tank(level(source), 0);
        int amount = tank.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new)
                .drain(1_000, FluidAction.EXECUTE).getAmount();
        if (amount != 1_000) { throw new IllegalStateException("Fixture drain did not conserve one full unit"); }
        return report(source);
    }
    private static int packageItem(CommandSourceStack source) {
        ServerLevel level = level(source); var tank = tank(level, 0); CompoundTag before = tank.resourceRoot();
        if (!level.destroyBlock(position(0), true)) { throw new IllegalStateException("Fixture removal refused"); }
        var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(position(0)).inflate(2),
                item -> item.getItem().getItem() instanceof PressurizedTankItem);
        if (items.size() != 1 || !before.equals(items.get(0).getItem().getTag().get(TankSave.ROOT))) {
            throw new IllegalStateException("Packaged tank drop did not retain its one exact root");
        }
        return report(source);
    }
}
