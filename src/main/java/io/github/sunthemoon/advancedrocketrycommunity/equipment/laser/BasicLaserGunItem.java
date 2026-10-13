package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla use intents only. Diamond pickaxe qualification is real; it is never a blanket harvest override. */
public final class BasicLaserGunItem extends PickaxeItem {
    public static final int DURABILITY = 512;
    public BasicLaserGunItem() { super(Tiers.DIAMOND, 0, -2.8F, new Item.Properties().durability(DURABILITY)); }
    @Override public int getUseDuration(ItemStack stack) { return 72_000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !CommonConfig.classicEquipmentEnabled()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(CommonConfig.classicEquipmentEnabled()
                        ? "message.advancedrocketrycommunity.basic_laser_gun.main_hand"
                        : "message.advancedrocketrycommunity.basic_laser_gun.disabled"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        return context.getPlayer() == null ? InteractionResult.PASS
                : use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();
    }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer player && LaserGunRuntime.service() != null) {
            LaserGunRuntime.service().tick(player, stack);
        }
    }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player && LaserGunRuntime.service() != null) { LaserGunRuntime.service().stop(player); }
    }
    @Override public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        return !(player instanceof ServerPlayer serverPlayer) || LaserGunRuntime.service() == null
                || !LaserGunRuntime.service().admitBreak(serverPlayer, stack, pos);
    }
    // Native destruction invokes this before it knows whether removal succeeds. Wear is committed afterward by the service.
    @Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) { return true; }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.advancedrocketrycommunity.basic_laser_gun.use"));
        lines.add(Component.translatable("tooltip.advancedrocketrycommunity.basic_laser_gun.limits"));
    }
}
