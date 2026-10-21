package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * The plain item device: it carries nothing (Q7), so the placer owns a placed device. It refuses a
 * {@code BlockEntityTag}, so item NBT never restores a device identity, binding, buffer or escrow marker, not even for
 * an operator.
 */
public final class RocketItemCargoItem extends BlockItem {
    public static final String BLOCK_DATA_REFUSED = "message.advancedrocketrycommunity.rocket_item_cargo.block_data_refused";

    public RocketItemCargoItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** No {@code BlockEntityTag}; the tag is read only by key presence, never copied or decoded. */
    public static boolean placeable(ItemStack stack) {
        return !stack.isEmpty() && (stack.getTag() == null || !stack.getTag().contains(BLOCK_ENTITY_TAG));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!placeable(context.getItemInHand())) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable(BLOCK_DATA_REFUSED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
