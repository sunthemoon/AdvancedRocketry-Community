package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;

/** Read-only, fixed-chunk, opt-in console probe for actual packaged-server restarts. */
public final class FluidReleaseTestCommands {
    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) { return; }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("fluids")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(4) && source.getEntity() == null)
                        .then(Commands.literal("report").executes(context -> {
                            var level = context.getSource().getServer().overworld();
                            var chunk = level.getChunkSource().getChunkNow(8, 8);
                            if (chunk == null || !(chunk.getBlockEntity(new BlockPos(132, 80, 132)) instanceof ChestBlockEntity chest)) {
                                throw new IllegalStateException("Explicitly load the fixed fluid fixture chunk first");
                            }
                            for (int slot = 0; slot < 4; slot++) {
                                var stack = chest.getItem(slot);
                                var before = stack.save(new net.minecraft.nbt.CompoundTag());
                                var direct = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                                        .orElseThrow(IllegalStateException::new);
                                if (!direct.drain(1_000, FluidAction.SIMULATE).isEmpty()
                                        || !direct.drain(1_000, FluidAction.EXECUTE).isEmpty()) {
                                    throw new IllegalStateException("A stacked canister can be drained");
                                }
                                var unit = stack.copyWithCount(1);
                                var handler = unit.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                                        .orElseThrow(IllegalStateException::new);
                                var fluid = handler.getFluidInTank(0);
                                if (!before.equals(stack.save(new net.minecraft.nbt.CompoundTag()))) {
                                    throw new IllegalStateException("Read-only probe changed saved inventory");
                                }
                                String line = "ARCE_FLUID_REPORT slot=" + slot + " item="
                                        + ForgeRegistries.ITEMS.getKey(stack.getItem()) + " count=" + stack.getCount()
                                        + " fluid=" + ForgeRegistries.FLUIDS.getKey(fluid.getFluid())
                                        + " amount=" + fluid.getAmount();
                                AdvancedRocketryCommunity.LOGGER.info(line);
                                context.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return 4;
                        })))));
    }
}
