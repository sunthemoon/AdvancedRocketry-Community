package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameItemSlot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.IntentKind;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * ADR-055 section 5 marker menu: the 27-slot drop buffer (take only), and in the device view the endpoint state,
 * the cursor, the floor, the link and the drill's last code at this marker, for its owner and operators. One button
 * resets the link.
 */
public final class LaserTargetMenu extends EndgameDeviceMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;
    public static final int BUTTON_RESET = 0;
    private static final int PLAYER_START = LaserTargetBlockEntity.BUFFER_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    public LaserTargetMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, new ItemStackHandler(LaserTargetBlockEntity.BUFFER_SLOTS), readOpenData(buffer));
    }

    public LaserTargetMenu(int id, Inventory inventory, LaserTargetBlockEntity target) {
        this(id, inventory, target, target.buffer(), true);
    }

    private LaserTargetMenu(int id, Inventory inventory, LaserTargetBlockEntity target, IItemHandler buffer,
                            boolean ignored) {
        super(ModMenuTypes.LASER_TARGET.get(), id, target == null ? null : targetOf(target), inventory.player);
        Runnable changed = target == null ? () -> { } : target::setChanged;
        for (int slot = 0; slot < LaserTargetBlockEntity.BUFFER_SLOTS; slot++) {
            addSlot(new TakeOnlySlot(buffer, slot, 8 + (slot % 9) * 18, 18 + (slot / 9) * 18, changed,
                    this::itemActionAllowed));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        }
    }

    public static void writeOpenData(FriendlyByteBuf buffer, LaserTargetBlockEntity target) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBlockPos(target.getBlockPos());
    }

    private static boolean readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported laser target menu format; update both host and client");
        }
        buffer.readBlockPos();
        return true;
    }

    @Override
    protected Optional<EndgameIntentGuard.Intent> intent(int button) {
        return button == BUTTON_RESET ? Optional.of(new EndgameIntentGuard.Intent(EndgameAction.CONFIGURE,
                IntentKind.STATE, false, false)) : Optional.empty();
    }

    @Override
    protected EndgameCode apply(int button, ServerPlayer player, EndgameDeviceBlockEntity device) {
        return button == BUTTON_RESET ? ((LaserTargetBlockEntity) device).reset(player.getUUID()) : EndgameCode.OK;
    }

    @Override
    protected EndgameDeviceView view(int containerId, EndgameDeviceBlockEntity device, boolean detail) {
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) device;
        List<EndgameDeviceView.Line> lines = new ArrayList<>();
        lines.add(EndgameDeviceView.Line.key(OrbitalLaserDrillMenu.VIEW + "endpoint",
                target.endpointStatus().translationKey()));
        if (detail) {
            int maxDepth = EndgameRuntime.devices().map(EndgameDevices::laserSettings)
                    .orElse(LaserDrillSettings.DEFAULTS).maxDepth();
            int floor = target.getLevel() == null ? target.nextLayer()
                    : LaserShaft.floor(target.getBlockPos(), target.getLevel().getMinBuildHeight(), maxDepth);
            lines.add(EndgameDeviceView.Line.text(OrbitalLaserDrillMenu.VIEW + "cursor", Integer.toString(
                    target.nextLayer())));
            lines.add(EndgameDeviceView.Line.text(OrbitalLaserDrillMenu.VIEW + "floor", Integer.toString(floor)));
            lines.add(EndgameDeviceView.Line.key(OrbitalLaserDrillMenu.VIEW + "linked", OrbitalLaserDrillMenu.VALUE
                    + (target.linkedController().isPresent() ? "on" : "off")));
            lines.add(EndgameDeviceView.Line.text(OrbitalLaserDrillMenu.VIEW + "layers", Long.toString(
                    target.opsDone())));
            lines.add(EndgameDeviceView.Line.key(OrbitalLaserDrillMenu.VIEW + "footprint",
                    LaserShaft.footprintInsideChunk(target.getBlockPos()) ? EndgameCode.OK.translationKey()
                            : EndgameCode.FOOTPRINT_AT_CHUNK_EDGE.translationKey()));
        }
        return new EndgameDeviceView(containerId, EndgameSystem.LASER_DRILL, target.endpointStatus(),
                target.lastCode(), detail, lines);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= PLAYER_START || !slots.get(index).hasItem()
                || !itemActionAllowed(EndgameAction.WITHDRAW)) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, stack);
        return original;
    }

    private static final class TakeOnlySlot extends EndgameItemSlot {
        TakeOnlySlot(IItemHandler handler, int index, int x, int y, Runnable changed,
                     Predicate<EndgameAction> allowed) {
            super(handler, index, x, y, changed, allowed);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return false;
        }
    }
}
