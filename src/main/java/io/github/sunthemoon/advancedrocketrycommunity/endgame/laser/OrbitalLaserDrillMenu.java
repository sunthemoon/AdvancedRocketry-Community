package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
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
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * ADR-055 section 5 menu: the lens slot, the 18 output slots, energy and the time to the next operation as data
 * slots, and the device view for status, codes and settings. Buttons are the only intents: start, stop and the
 * redstone mode. Open data format 1 carries only the position, for display.
 */
public final class OrbitalLaserDrillMenu extends EndgameDeviceMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;
    public static final int BUTTON_START = 0;
    public static final int BUTTON_STOP = 1;
    public static final int BUTTON_REDSTONE = 2;
    public static final int DATA_COUNT = 3;
    public static final String VIEW = "advancedrocketrycommunity.endgame.view.";
    public static final String VALUE = "advancedrocketrycommunity.endgame.value.";

    private static final int LENS_SLOT = 0;
    private static final int OUTPUT_START = 1;
    private static final int PLAYER_START = OUTPUT_START + LaserDrillStorage.OUTPUT_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final ContainerData data;

    public OrbitalLaserDrillMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, new ItemStackHandler(1), new ItemStackHandler(LaserDrillStorage.OUTPUT_SLOTS),
                readOpenData(buffer));
    }

    public OrbitalLaserDrillMenu(int id, Inventory inventory, OrbitalLaserDrillBlockEntity drill) {
        this(id, inventory, drill, drill.storage().lens(), drill.storage().output(), new Data(drill));
    }

    private OrbitalLaserDrillMenu(int id, Inventory inventory, OrbitalLaserDrillBlockEntity drill, IItemHandler lens,
                                  IItemHandler output, ContainerData data) {
        super(ModMenuTypes.ORBITAL_LASER_DRILL.get(), id, drill == null ? null : targetOf(drill), inventory.player);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        addSlot(new SlotItemHandler(lens, 0, 26, 36));
        for (int slot = 0; slot < LaserDrillStorage.OUTPUT_SLOTS; slot++) {
            addSlot(new OutputSlot(output, slot, 62 + (slot % 6) * 18, 18 + (slot / 6) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 124 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 182));
        }
        addDataSlots(data);
    }

    /** Open data format 1: marker, version and the position (display only; the server never reads it back). */
    public static void writeOpenData(FriendlyByteBuf buffer, OrbitalLaserDrillBlockEntity drill) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBlockPos(drill.getBlockPos());
    }

    private static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported laser drill menu format; update both host and client");
        }
        buffer.readBlockPos();
        return new SimpleContainerData(DATA_COUNT);
    }

    @Override
    protected Optional<EndgameIntentGuard.Intent> intent(int button) {
        return switch (button) {
            case BUTTON_START -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.OPERATE, IntentKind.STATE,
                    true, false));
            case BUTTON_STOP -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.OPERATE, IntentKind.STATE,
                    false, false));
            case BUTTON_REDSTONE -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.CONFIGURE, IntentKind.STATE,
                    false, false));
            default -> Optional.empty();
        };
    }

    @Override
    protected EndgameCode apply(int button, ServerPlayer player, EndgameDeviceBlockEntity device) {
        OrbitalLaserDrillBlockEntity drill = (OrbitalLaserDrillBlockEntity) device;
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        if (devices.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        return switch (button) {
            case BUTTON_START -> drill.start(devices.get(), player.getUUID());
            case BUTTON_STOP -> drill.stop(devices.get(), player.getUUID());
            case BUTTON_REDSTONE -> drill.cycleRedstone(player.getUUID());
            default -> EndgameCode.OK;
        };
    }

    /** Public status carries only the codes and the running flag; the orbit body and table need detail. */
    @Override
    protected EndgameDeviceView view(int containerId, EndgameDeviceBlockEntity device, boolean detail) {
        OrbitalLaserDrillBlockEntity drill = (OrbitalLaserDrillBlockEntity) device;
        List<EndgameDeviceView.Line> lines = new ArrayList<>();
        lines.add(EndgameDeviceView.Line.key(VIEW + "running", VALUE + (drill.running() ? "on" : "off")));
        if (detail) {
            lines.add(EndgameDeviceView.Line.key(VIEW + "structure", drill.structureCode().translationKey()));
            lines.add(EndgameDeviceView.Line.key(VIEW + "mode", VALUE + "mode." + lower(drill.mode().name())));
            lines.add(EndgameDeviceView.Line.key(VIEW + "redstone", VALUE + "redstone."
                    + lower(drill.redstoneMode().name())));
            lines.add(EndgameDeviceView.Line.key(VIEW + "lens", VALUE + (drill.storage().lensPresent() ? "present"
                    : "missing")));
            lines.add(EndgameDeviceView.Line.text(VIEW + "output", drill.storage().usedOutputSlots() + " / "
                    + LaserDrillStorage.OUTPUT_SLOTS));
            lines.add(EndgameDeviceView.Line.text(VIEW + "operations", Long.toString(drill.operationIndex())));
            drill.lastBody().ifPresent(body -> lines.add(EndgameDeviceView.Line.text(VIEW + "body", body)));
            drill.lastTable().ifPresent(table -> lines.add(EndgameDeviceView.Line.text(VIEW + "table", table)));
        }
        return new EndgameDeviceView(containerId, EndgameSystem.LASER_DRILL, drill.status(), drill.lastStop(), detail,
                lines);
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PLAYER_START
                ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
                : moveItemStackTo(stack, LENS_SLOT, OUTPUT_START, false);
        if (!moved) {
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

    public int energyStored() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int cooldown() {
        return data.get(2);
    }

    /** Players take output; nothing is ever inserted (ADR-055 section 1). */
    private static final class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return false;
        }
    }

    /** Read-only values that change every tick: the energy in two halves and the cooldown. */
    private static final class Data implements ContainerData {
        private final OrbitalLaserDrillBlockEntity drill;

        Data(OrbitalLaserDrillBlockEntity drill) {
            this.drill = drill;
        }

        @Override
        public int get(int index) {
            int energy = drill.storage().energy();
            return switch (index) {
                case 0 -> energy & 0xFFFF;
                case 1 -> (energy >>> 16) & 0xFFFF;
                case 2 -> drill.getLevel() == null ? 0 : drill.cooldown(drill.getLevel().getGameTime());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
