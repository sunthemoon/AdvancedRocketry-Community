package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Two additional built-in payloads in the same native four-chunk census. */
final class W152NativeResourceHosts {
    static void add(List<RocketBlock> blocks, UUID owner) {
        var fluid = new FluidStack(Fluids.WATER, 1000); fluid.getOrCreateTag().putString("W152", "original-fluid");
        blocks.add(new RocketBlock(new RocketPosition(2, 0, 0),
                RocketBlockStateAdapter.capture(ModBlocks.PRESSURIZED_TANK.get().defaultBlockState()),
                PressurizedTankRocketAdapter.payload(fluid)));
        var item = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get());
        SatelliteItemData.write(item, new SatelliteIdentity(W152NativeProbe.id("satellite"), owner, ModIdentity.id("w152_data")));
        item.getOrCreateTag().putString("W152", "original-package");
        blocks.add(new RocketBlock(new RocketPosition(2, 0, 1),
                RocketBlockStateAdapter.capture(ModBlocks.ROCKET_SATELLITE_BAY.get().defaultBlockState()),
                new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID,
                        SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner, item.save(new CompoundTag()), null)))));
    }
    private W152NativeResourceHosts() { }
}
