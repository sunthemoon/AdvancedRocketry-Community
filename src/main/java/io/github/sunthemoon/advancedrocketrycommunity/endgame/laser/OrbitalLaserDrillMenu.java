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
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * ADR-055 section 5 menu: the lens slot, the 18 output slots, energy and the time to the next operation as data
 * slots, and the device view for status, codes, settings, the selected target and the physical danger confirmation.
 * Buttons are the only intents: start, stop, redstone, mode, target previous/next, link, unlink and confirm. The
 * target selection is server-side menu state over the owner's laser targets in ID order (operators see all); the
 * client never sends an index. Open data format 1 carries only the position, for display.
 */
public final class OrbitalLaserDrillMenu extends EndgameDeviceMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;
    public static final int BUTTON_START = 0;
    public static final int BUTTON_STOP = 1;
    public static final int BUTTON_REDSTONE = 2;
    public static final int BUTTON_MODE = 3;
    public static final int BUTTON_TARGET_PREVIOUS = 4;
    public static final int BUTTON_TARGET_NEXT = 5;
    public static final int BUTTON_LINK = 6;
    public static final int BUTTON_UNLINK = 7;
    public static final int BUTTON_CONFIRM = 8;
    public static final int DATA_COUNT = 3;
    public static final String VIEW = "advancedrocketrycommunity.endgame.view.";
    public static final String VALUE = "advancedrocketrycommunity.endgame.value.";

    private static final int LENS_SLOT = 0;
    private static final int OUTPUT_START = 1;
    private static final int PLAYER_START = OUTPUT_START + LaserDrillStorage.OUTPUT_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final ContainerData data;
    @Nullable
    private UUID selected;

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
        Runnable changed = drill == null ? () -> { } : drill::setChanged;
        addSlot(new EndgameItemSlot(lens, 0, 26, 36, changed, this::itemActionAllowed));
        for (int slot = 0; slot < LaserDrillStorage.OUTPUT_SLOTS; slot++) {
            addSlot(new OutputSlot(output, slot, 62 + (slot % 6) * 18, 18 + (slot / 6) * 18, changed,
                    this::itemActionAllowed));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
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
            case BUTTON_REDSTONE, BUTTON_MODE, BUTTON_LINK, BUTTON_UNLINK -> Optional.of(new EndgameIntentGuard.Intent(
                    EndgameAction.CONFIGURE, IntentKind.STATE, false, false));
            case BUTTON_TARGET_PREVIOUS, BUTTON_TARGET_NEXT -> Optional.of(new EndgameIntentGuard.Intent(
                    EndgameAction.CONFIGURE, IntentKind.SELECTION, false, false));
            case BUTTON_CONFIRM -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.OPERATE, IntentKind.STATE,
                    true, false));
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
        Optional<EndgameRoot> root = EndgameRuntime.operational().flatMap(EndgameService::root);
        if (root.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        long now = player.serverLevel().getGameTime();
        UUID actor = player.getUUID();
        return switch (button) {
            case BUTTON_START -> LaserDrillIntents.start(drill, devices.get(), actor, now);
            case BUTTON_CONFIRM -> LaserDrillIntents.confirm(drill, devices.get(), actor, now);
            case BUTTON_STOP -> LaserDrillIntents.stop(drill, devices.get(), actor);
            case BUTTON_REDSTONE -> LaserDrillIntents.cycleRedstone(drill, actor);
            case BUTTON_MODE -> LaserDrillIntents.toggleMode(drill, devices.get(), actor);
            case BUTTON_TARGET_PREVIOUS, BUTTON_TARGET_NEXT -> move(targets(root.get(), drill, player),
                    button == BUTTON_TARGET_NEXT ? 1 : -1);
            case BUTTON_LINK -> selected == null ? EndgameCode.NO_TARGET : LaserDrillIntents.link(drill, devices.get(),
                    root.get(), actor, player.hasPermissions(2), selected);
            case BUTTON_UNLINK -> LaserDrillIntents.unlink(drill, devices.get(), root.get(), actor);
            default -> EndgameCode.OK;
        };
    }

    /**
     * ADR-054 section 9 selection: {@code ACTIVE} laser targets owned by the drill's owner (any owner for an
     * operator), with their footprint inside their chunk, in ID order.
     */
    static List<EndpointRecord> targets(EndgameRoot root, OrbitalLaserDrillBlockEntity drill, Player viewer) {
        boolean operator = viewer.hasPermissions(2);
        return root.endpoints().stream()
                .filter(record -> record.state() == EndpointRecord.State.ACTIVE
                        && record.kind().equals(LaserTargetBlockEntity.KIND)
                        && (operator || drill.ownerId().filter(record.owner()::equals).isPresent())
                        && LaserShaft.footprintInsideChunk(BlockPos.of(record.pos())))
                .sorted(Comparator.comparing(record -> record.id().toString()))
                .toList();
    }

    private EndgameCode move(List<EndpointRecord> targets, int step) {
        if (targets.isEmpty()) {
            selected = null;
            return EndgameCode.NO_TARGET;
        }
        int index = position(targets);
        selected = targets.get(Math.floorMod(index < 0 ? (step > 0 ? -1 : 0) + step : index + step,
                targets.size())).id();
        return EndgameCode.OK;
    }

    private int position(List<EndpointRecord> targets) {
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).id().equals(selected)) {
                return i;
            }
        }
        return -1;
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
            if (drill.mode() == LaserDrillMode.PHYSICAL) {
                physicalLines(drill, lines);
            }
        }
        return new EndgameDeviceView(containerId, EndgameSystem.LASER_DRILL, drill.status(), drill.lastStop(), detail,
                lines.subList(0, Math.min(lines.size(), EndgameDeviceView.MAX_LINES)));
    }

    /** The selected target, the link and, while a start waits for this viewer's confirmation, what it will dig. */
    private void physicalLines(OrbitalLaserDrillBlockEntity drill, List<EndgameDeviceView.Line> lines) {
        Optional<EndgameRoot> root = EndgameRuntime.operational().flatMap(EndgameService::root);
        Player viewer = viewer();
        if (root.isEmpty() || viewer == null || !(drill.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<EndpointRecord> targets = targets(root.get(), drill, viewer);
        int index = position(targets);
        if (index < 0 && !targets.isEmpty()) {
            selected = targets.get(0).id();
            index = 0;
        }
        lines.add(EndgameDeviceView.Line.text(VIEW + "target", index < 0 ? "-"
                : label(targets.get(index)) + " (" + (index + 1) + " / " + targets.size() + ")"));
        if (index >= 0) {
            BlockPos at = BlockPos.of(targets.get(index).pos());
            lines.add(EndgameDeviceView.Line.text(VIEW + "target_at", targets.get(index).level() + " "
                    + at.toShortString()));
        }
        Optional<EndpointRecord> linked = drill.linkedMarker().flatMap(root.get()::endpoint);
        lines.add(EndgameDeviceView.Line.text(VIEW + "link", drill.linkedMarker().isEmpty() ? "-"
                : linked.map(OrbitalLaserDrillMenu::label).orElse(drill.linkedMarker().get().toString().substring(0, 8))
                + " paid=" + drill.opsPaid()));
        if (drill.confirmationPendingFor(viewer.getUUID()) && linked.isPresent()) {
            BlockPos marker = BlockPos.of(linked.get().pos());
            ServerLevel markerLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                    linked.get().level()));
            int maxDepth = EndgameRuntime.devices().map(EndgameDevices::laserSettings)
                    .orElse(LaserDrillSettings.DEFAULTS).maxDepth();
            lines.add(EndgameDeviceView.Line.key(VIEW + "warning", VALUE + "removes_blocks"));
            lines.add(EndgameDeviceView.Line.text(VIEW + "footprint", (marker.getX() - 1) + ".." + (marker.getX() + 1)
                    + ", " + (marker.getZ() - 1) + ".." + (marker.getZ() + 1)));
            if (markerLevel != null) {
                lines.add(EndgameDeviceView.Line.text(VIEW + "floor", Integer.toString(LaserShaft.floor(marker,
                        markerLevel.getMinBuildHeight(), maxDepth))));
                if (markerLevel.getChunkSource().getChunkNow(marker.getX() >> 4, marker.getZ() >> 4) != null
                        && markerLevel.getBlockEntity(marker) instanceof LaserTargetBlockEntity target) {
                    lines.add(EndgameDeviceView.Line.text(VIEW + "cursor", Integer.toString(target.nextLayer())));
                }
            }
        }
    }

    /** A generated label: the kind, the first 8 hex digits of the ID and the body (ADR-054 section 4). */
    static String label(EndpointRecord record) {
        String body = EndgameRuntime.devices().flatMap(EndgameDevices::celestial)
                .flatMap(catalog -> catalog.forLevel(ResourceKey.create(Registries.DIMENSION, record.level())))
                .map(definition -> definition.id().getPath()).orElse("?");
        return "laser_target " + record.id().toString().substring(0, 8) + " @ " + body;
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) {
            return ItemStack.EMPTY;
        }
        if (!itemActionAllowed(index < PLAYER_START ? EndgameAction.WITHDRAW : EndgameAction.CONFIGURE)) {
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
    private static final class OutputSlot extends EndgameItemSlot {
        OutputSlot(IItemHandler handler, int index, int x, int y, Runnable changed,
                   Predicate<EndgameAction> allowed) {
            super(handler, index, x, y, changed, allowed);
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
