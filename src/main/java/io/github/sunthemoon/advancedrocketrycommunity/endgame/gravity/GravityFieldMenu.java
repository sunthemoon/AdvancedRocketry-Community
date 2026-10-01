package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.IntentKind;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-058 section 6 menu: radius ±1, multiplier ±5, start, stop and the redstone mode as buttons; energy as data
 * slots; status, settings and the effective clipped box size in the device view, its coordinates only for viewers
 * with detail. Inside a station every change needs {@code MANAGE_STATION}.
 */
public final class GravityFieldMenu extends EndgameDeviceMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;
    public static final int BUTTON_START = 0;
    public static final int BUTTON_STOP = 1;
    public static final int BUTTON_REDSTONE = 2;
    public static final int BUTTON_RADIUS_DOWN = 3;
    public static final int BUTTON_RADIUS_UP = 4;
    public static final int BUTTON_MULTIPLIER_DOWN = 5;
    public static final int BUTTON_MULTIPLIER_UP = 6;
    public static final int DATA_COUNT = 2;

    private final ContainerData data;

    public GravityFieldMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, readOpenData(buffer));
    }

    public GravityFieldMenu(int id, Inventory inventory, GravityFieldBlockEntity device) {
        this(id, inventory, device, new Data(device));
    }

    private GravityFieldMenu(int id, Inventory inventory, GravityFieldBlockEntity device, ContainerData data) {
        super(ModMenuTypes.GRAVITY_FIELD_CONTROLLER.get(), id, device == null ? null : targetOf(device),
                inventory.player);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        addDataSlots(data);
    }

    public static void writeOpenData(FriendlyByteBuf buffer, GravityFieldBlockEntity device) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBlockPos(device.getBlockPos());
    }

    private static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported gravity field menu format; update both host and client");
        }
        buffer.readBlockPos();
        return new SimpleContainerData(DATA_COUNT);
    }

    @Override
    protected Optional<EndgameIntentGuard.Intent> intent(int button) {
        return switch (button) {
            case BUTTON_START -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.OPERATE, IntentKind.STATE,
                    true, true));
            case BUTTON_STOP -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.OPERATE, IntentKind.STATE,
                    false, true));
            case BUTTON_REDSTONE, BUTTON_RADIUS_DOWN, BUTTON_RADIUS_UP, BUTTON_MULTIPLIER_DOWN, BUTTON_MULTIPLIER_UP ->
                    Optional.of(new EndgameIntentGuard.Intent(EndgameAction.CONFIGURE, IntentKind.STATE, false, true));
            default -> Optional.empty();
        };
    }

    @Override
    protected EndgameCode apply(int button, ServerPlayer player, EndgameDeviceBlockEntity device) {
        GravityFieldBlockEntity field = (GravityFieldBlockEntity) device;
        return switch (button) {
            case BUTTON_START -> field.running(true, player.getUUID());
            case BUTTON_STOP -> field.running(false, player.getUUID());
            case BUTTON_REDSTONE -> field.cycleRedstone(player.getUUID());
            case BUTTON_RADIUS_DOWN -> field.radius(-1, player.getUUID());
            case BUTTON_RADIUS_UP -> field.radius(1, player.getUUID());
            case BUTTON_MULTIPLIER_DOWN -> field.multiplier(-GravityField.MULTIPLIER_STEP, player.getUUID());
            case BUTTON_MULTIPLIER_UP -> field.multiplier(GravityField.MULTIPLIER_STEP, player.getUUID());
            default -> EndgameCode.OK;
        };
    }

    @Override
    protected EndgameDeviceView view(int containerId, EndgameDeviceBlockEntity device, boolean detail) {
        GravityFieldBlockEntity field = (GravityFieldBlockEntity) device;
        String view = OrbitalLaserDrillMenu.VIEW;
        String value = OrbitalLaserDrillMenu.VALUE;
        List<EndgameDeviceView.Line> lines = new ArrayList<>();
        lines.add(EndgameDeviceView.Line.key(view + "running", value + (field.running() ? "on" : "off")));
        lines.add(EndgameDeviceView.Line.key(view + "redstone", value + "redstone."
                + field.redstoneMode().name().toLowerCase(Locale.ROOT)));
        lines.add(EndgameDeviceView.Line.text(view + "radius", Integer.toString(field.radius())));
        lines.add(EndgameDeviceView.Line.text(view + "multiplier", String.format(Locale.ROOT, "%.2f g",
                field.multiplier() / 100.0D)));
        lines.add(EndgameDeviceView.Line.text(view + "upkeep", GravityField.upkeep(field.radius()) + " FE/t"));
        field.activeBox().ifPresent(box -> {
            lines.add(EndgameDeviceView.Line.text(view + "box", (box.maxX() - box.minX() + 1) + " x "
                    + (box.maxY() - box.minY() + 1) + " x " + (box.maxZ() - box.minZ() + 1)));
            if (detail) {
                lines.add(EndgameDeviceView.Line.text(view + "box_at", box.minX() + " " + box.minY() + " "
                        + box.minZ() + " .. " + box.maxX() + " " + box.maxY() + " " + box.maxZ()));
            }
        });
        return new EndgameDeviceView(containerId, EndgameSystem.GRAVITY_FIELD, field.status(), field.status(), detail,
                lines);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public int energyStored() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    private static final class Data implements ContainerData {
        private final GravityFieldBlockEntity device;

        Data(GravityFieldBlockEntity device) {
            this.device = device;
        }

        @Override
        public int get(int index) {
            int energy = device.energy().energy();
            return index == 0 ? energy & 0xFFFF : (energy >>> 16) & 0xFFFF;
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
