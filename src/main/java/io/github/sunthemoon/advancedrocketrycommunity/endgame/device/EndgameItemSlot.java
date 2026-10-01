package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import java.util.Objects;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * A menu slot over a device's local item handler that keeps ADR-054 section 8's same-store rule. Forge's
 * {@link SlotItemHandler} hands the menu the handler's live stack, and its {@code setChanged()} only reaches a dummy
 * container: a partial shift-click (which shrinks that stack in place) or a merge into a non-empty slot would never
 * mark the block entity changed, so an unload kept the older stack on disk (review C11R-C1). This slot forwards
 * {@code setChanged()} to the owning block entity.
 */
public class EndgameItemSlot extends SlotItemHandler {
    private final Runnable changed;

    /** @param changed the owning block entity's {@code setChanged}; a no-op for the client-side copy */
    public EndgameItemSlot(IItemHandler handler, int index, int x, int y, Runnable changed) {
        super(handler, index, x, y);
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @Override
    public void setChanged() {
        super.setChanged();
        changed.run();
    }
}
