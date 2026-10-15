package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The nonstackable loader item, which is also the drop carrier: its item tag holds at most one schema-1
 * {@link LoaderCarrier} (the owner and the tank). It refuses a {@code BlockEntityTag}, so item NBT never restores a
 * device identity, binding or escrow marker.
 */
public final class FluidFuelLoaderItem extends BlockItem {
    public static final String EXCHANGE_REFUSED = "message.advancedrocketrycommunity.fluid_fuel_loader.exchange_refused";
    public static final String CARRIER_REFUSED = "message.advancedrocketrycommunity.fluid_fuel_loader.carrier_refused";
    public static final String TOOLTIP_CONTENTS = "tooltip.advancedrocketrycommunity.fluid_fuel_loader.contents";
    public static final String TOOLTIP_EMPTY = "tooltip.advancedrocketrycommunity.fluid_fuel_loader.empty";
    /** The 12-KiB carrier plus bounded ordinary item metadata; an unbounded item tag is never copied or decoded. */
    private static final int ITEM_TAG_BYTES = 16_384;
    private static final int ITEM_TAG_DEPTH = 20;
    private static final int ITEM_TAG_NODES = 2_048;

    public FluidFuelLoaderItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** One item with no {@code BlockEntityTag}, a bounded tag and, when present, a carrier that decodes. */
    public static boolean placeable(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return true;
        }
        if (!BoundedNbt.fits(tag, ITEM_TAG_BYTES, ITEM_TAG_DEPTH, ITEM_TAG_NODES) || tag.contains(BLOCK_ENTITY_TAG)) {
            return false;
        }
        if (!tag.contains(LoaderCarrier.KEY)) {
            return true;
        }
        try {
            LoaderCarrier.decode(tag.get(LoaderCarrier.KEY));
            return true;
        } catch (IllegalArgumentException refused) {
            return false;
        }
    }

    /** The carried owner and tank of a placeable item; empty when the item carries none or is not placeable. */
    public static Optional<LoaderCarrier> carrier(ItemStack stack) {
        if (!placeable(stack) || !stack.hasTag() || !stack.getTag().contains(LoaderCarrier.KEY)) {
            return Optional.empty();
        }
        return Optional.of(LoaderCarrier.decode(stack.getTag().get(LoaderCarrier.KEY)));
    }

    /** {@code stack} with exactly this carrier in its tag. */
    public static ItemStack withCarrier(ItemStack stack, LoaderCarrier carrier) {
        stack.getOrCreateTag().put(LoaderCarrier.KEY, carrier.encode());
        return stack;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!placeable(context.getItemInHand())) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable(CARRIER_REFUSED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        if (!placeable(stack)) {
            lines.add(Component.translatable(CARRIER_REFUSED));
            return;
        }
        LoaderTank tank = carrier(stack).map(LoaderCarrier::tank).orElse(LoaderTank.EMPTY);
        lines.add(tank.isEmpty() ? Component.translatable(TOOLTIP_EMPTY)
                : Component.translatable(TOOLTIP_CONTENTS, tank.fluidStack().getDisplayName(), tank.amount()));
    }
}
