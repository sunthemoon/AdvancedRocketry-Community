package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Ground-side assembled package consumed only after durable launch registration. */
public final class DataSatellitePackageItem extends Item {
    public DataSatellitePackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        SatelliteItemData.DecodeResult decoded = SatelliteItemData.read(stack);
        decoded.identity().ifPresentOrElse(identity -> {
            tooltip.add(Component.translatable(
                    "tooltip.advancedrocketrycommunity.satellite_package.bound",
                    identity.satelliteId().toString().substring(0, 8)
            ).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(identity.definitionId().toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }, () -> tooltip.add(Component.translatable(
                "tooltip.advancedrocketrycommunity.satellite_item.unsupported"
        ).withStyle(ChatFormatting.RED)));
    }
}
