package io.github.sunthemoon.advancedrocketrycommunity.beacon.finder;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The C17 {@code beacon_finder} (ADR-065 section 7, ADR-066 section 5.1): held in either hand, or installed in one of
 * the built-in helmet's two HEAD module positions through the suit workstation, it lets the server stream its owner's
 * beacon markers. The item itself carries no beacon data.
 */
public final class BeaconFinderItem extends Item {
    public BeaconFinderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.advancedrocketrycommunity.beacon_finder")
                .withStyle(ChatFormatting.GRAY));
    }
}
