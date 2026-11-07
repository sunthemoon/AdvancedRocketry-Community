package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The four endgame panels share W311's refusal ownership, button/slot actions and server-view release points. */
abstract class EndgameRefusalScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M>
        implements ScreenActionMessages.RefusalOwner {
    private final IntSupplier energy;
    private EndgameCode refusalCode;
    private EndgameDeviceView refusalView;
    private int refusalEnergy;
    private List<ItemStack> refusalItems = List.of();

    protected EndgameRefusalScreen(M menu, Inventory inventory, Component title, IntSupplier energy) {
        super(menu, inventory, title);
        this.energy = energy;
    }

    @Override
    public final boolean refusal(Component message) {
        var code = EndgameScreenFeedback.refusal(message);
        if (code.isEmpty()) { return false; }
        refusalCode = code.get();
        refusalView = EndgameDeviceViewCache.view(menu.containerId).orElse(null);
        refusalEnergy = energy.getAsInt();
        refusalItems = EndgameScreenFeedback.inventoryRelevant(refusalCode) ? deviceSlots().stream()
                .map(slot -> slot.getItem().copy()).toList() : List.of();
        return true;
    }

    /** All menu buttons use this, including keyboard activation of a focused button. */
    protected final void sendButton(int id) {
        releaseRefusal();
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    /** Includes shift-click, hotbar swaps, drops and quick-craft as well as ordinary slot clicks. */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType type) {
        releaseRefusal();
        super.slotClicked(slot, slotId, button, type);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshRefusal();
    }

    protected final void refreshRefusal() {
        if (refusalCode == null) { return; }
        if (!refusalItems.isEmpty()) {
            var slots = deviceSlots();
            for (int index = 0; index < refusalItems.size(); index++) {
                if (!ItemStack.matches(refusalItems.get(index), slots.get(index).getItem())) {
                    releaseRefusal();
                    return;
                }
            }
        }
        var view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isEmpty()) { return; }
        if (refusalView == null) {
            // An initial/delayed view is not evidence that the refused action is now possible.
            refusalView = view.get();
            refusalEnergy = energy.getAsInt();
        } else {
            int nowEnergy = energy.getAsInt();
            if (EndgameScreenFeedback.answers(refusalCode, refusalView, view.get(), refusalEnergy, nowEnergy)) {
                releaseRefusal();
            } else {
                refusalView = view.get();
                refusalEnergy = nowEnergy;
            }
        }
    }

    private List<Slot> deviceSlots() {
        // These four menus put the device slots before the unchanged 36-slot player inventory.
        return menu.slots.subList(0, Math.max(0, menu.slots.size() - 36));
    }

    protected final void releaseRefusal() {
        refusalCode = null;
        refusalView = null;
        refusalItems = List.of();
        ScreenActionMessages.release(this);
    }
}
