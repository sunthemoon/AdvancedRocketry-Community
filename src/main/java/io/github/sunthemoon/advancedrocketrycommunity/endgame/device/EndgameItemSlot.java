package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * A menu slot over a device's local item handler that keeps ADR-054 sections 3 and 8.
 *
 * <p>Same store: Forge's {@link SlotItemHandler} hands the menu the handler's live stack, and its
 * {@code setChanged()} only reaches a dummy container, so a partial shift-click (which shrinks that stack in place)
 * or a merge into a non-empty slot would never mark the block entity changed and an unload kept the older stack on
 * disk (review C11R-C1). This slot forwards {@code setChanged()} to the owning block entity.
 *
 * <p>Authority: taking an item needs {@code WITHDRAW} and putting one needs {@code CONFIGURE}, for the menu's viewer
 * (review C11R-H2); a viewer with public {@code VIEW} only sees the slots.
 */
public class EndgameItemSlot extends SlotItemHandler {
    private final Runnable changed;
    private final Predicate<EndgameAction> allowed;

    /**
     * @param changed the owning block entity's {@code setChanged}; a no-op for the client-side copy
     * @param allowed the menu's decision for its viewer ({@link EndgameDeviceMenu#itemActionAllowed})
     */
    public EndgameItemSlot(IItemHandler handler, int index, int x, int y, Runnable changed,
                           Predicate<EndgameAction> allowed) {
        super(handler, index, x, y);
        this.changed = Objects.requireNonNull(changed, "changed");
        this.allowed = Objects.requireNonNull(allowed, "allowed");
    }

    @Override
    public boolean mayPickup(Player player) {
        return allowed.test(EndgameAction.WITHDRAW) && super.mayPickup(player);
    }

    @Override
    public boolean mayPlace(@Nonnull ItemStack stack) {
        return allowed.test(EndgameAction.CONFIGURE) && super.mayPlace(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        changed.run();
    }
}
