package io.github.sunthemoon.advancedrocketrycommunity.content;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Item whose tooltip makes the bootstrap-only behavior explicit to players. */
public final class DevelopmentComponentItem extends Item {
    public DevelopmentComponentItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable(
                "tooltip.advancedrocketrycommunity.development_component"
        ).withStyle(ChatFormatting.DARK_GRAY));
    }
}
