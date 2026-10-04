package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** The tank root is carried directly in the Item tag, not a second BlockEntityTag resource copy. */
public final class PressurizedTankItem extends BlockItem {
    public PressurizedTankItem(Block block, Properties properties) { super(block, properties); }

    public static boolean placeable(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() <= 0 || stack.getCount() > Math.min(64, stack.getMaxStackSize())) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        // 8-KiB owned root plus bounded ordinary Item metadata/framing. Never copy an unbounded Item input.
        if (tag != null && (!BoundedNbt.fits(tag, 16_384, 20, 2_048) || tag.contains("BlockEntityTag"))) {
            return false;
        }
        CompoundTag serialized = stack.save(new CompoundTag());
        if (serialized.contains("ForgeCaps") || !BoundedNbt.fits(serialized, 16_384, 20, 2_048)) { return false; }
        if (tag == null || !tag.contains(TankSave.ROOT)) { return true; }
        try { TankSave.decode(tag.get(TankSave.ROOT)); return true; }
        catch (IllegalArgumentException refused) { return false; }
    }

    @Override public InteractionResult place(BlockPlaceContext context) {
        if (!placeable(context.getItemInHand())) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.advancedrocketrycommunity.tank.repair"), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        if (!placeable(stack)) {
            lines.add(Component.translatable("message.advancedrocketrycommunity.tank.repair"));
        } else if (stack.hasTag() && stack.getTag().contains(TankSave.ROOT)) {
            var fluid = TankSave.decode(stack.getTag().get(TankSave.ROOT));
            lines.add(Component.translatable("tooltip.advancedrocketrycommunity.tank.contents",
                    fluid.isEmpty() ? Component.translatable("message.advancedrocketrycommunity.tank.empty")
                            : fluid.getDisplayName(), fluid.getAmount()));
        }
    }
}
