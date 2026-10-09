package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;

/** Ordinary single-block pickaxe; native tier, progress, protection, wear and loot remain authoritative. */
public final class JackhammerItem extends PickaxeItem {
    public static final int DURABILITY = 1024;
    public static final float MINING_SPEED = 50.0F;
    public static final TagKey<Item> REPAIR_MATERIAL = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "rods/titanium"));

    public JackhammerItem() {
        super(Tiers.DIAMOND, 1, -2.8F, new Item.Properties().durability(DURABILITY));
    }

    @Override public float getDestroySpeed(ItemStack stack, BlockState state) {
        return CommonConfig.classicEquipmentEnabled() && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                ? MINING_SPEED : 1.0F;
    }

    @Override public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
        return ingredient.is(REPAIR_MATERIAL);
    }

    @Override public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return CommonConfig.classicEquipmentEnabled() && super.canPerformAction(stack, action);
    }

    @Override public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        return refuseDisabled(player);
    }

    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return refuseDisabled(player);
    }

    private static boolean refuseDisabled(Player player) {
        if (CommonConfig.classicEquipmentEnabled()) { return false; }
        if (!player.level().isClientSide) {
            player.sendSystemMessage(Component.translatable("message.advancedrocketrycommunity.jackhammer.disabled"));
        }
        return true;
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.advancedrocketrycommunity.jackhammer.mining"));
        lines.add(Component.translatable("tooltip.advancedrocketrycommunity.jackhammer.repair"));
        // COMMON client prediction is informational; the native server hooks decide admission.
        if (!CommonConfig.classicEquipmentEnabled()) {
            lines.add(Component.translatable("message.advancedrocketrycommunity.jackhammer.disabled"));
        }
    }
}
