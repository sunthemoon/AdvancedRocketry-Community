package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo.CargoStorage;
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
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
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
import net.minecraft.network.FriendlyByteBuf;
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
 * ADR-056 section 6 menu: the 4 input and 9 receive slots, energy as data slots, and the device view for the
 * endpoint state, the selected destination (label, body, Level, position, class, cost and travel), the settings, the
 * outbox, in-transit, incoming and receipt counts and the last code. Buttons are the only intents: destination
 * previous/next, launch, auto, redstone mode and the minimum stack size ±1/±16. The destination list is the owner's
 * {@code ACTIVE} railguns that pass the route rule, in ID order (operators see every owner's); the client never sends
 * an index. Open data format 1 carries only the position, for display.
 */
public final class RailgunMenu extends EndgameDeviceMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_LAUNCH = 2;
    public static final int BUTTON_AUTO = 3;
    public static final int BUTTON_REDSTONE = 4;
    public static final int BUTTON_MIN_DOWN_1 = 5;
    public static final int BUTTON_MIN_UP_1 = 6;
    public static final int BUTTON_MIN_DOWN_16 = 7;
    public static final int BUTTON_MIN_UP_16 = 8;
    public static final int DATA_COUNT = 2;
    private static final String VIEW = "advancedrocketrycommunity.endgame.view.";
    private static final String VALUE = "advancedrocketrycommunity.endgame.value.";
    private static final int INPUT_START = 0;
    private static final int RECEIVE_START = CargoStorage.INPUT_SLOTS;
    private static final int PLAYER_START = RECEIVE_START + CargoStorage.RECEIVE_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    private static final int LIST_REFRESH_TICKS = 100;

    private final ContainerData data;
    private List<EndpointRecord> listed = List.of();
    private long listedAt = Long.MIN_VALUE / 2;

    public RailgunMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, new ItemStackHandler(CargoStorage.INPUT_SLOTS),
                new ItemStackHandler(CargoStorage.RECEIVE_SLOTS), readOpenData(buffer));
    }

    public RailgunMenu(int id, Inventory inventory, RailgunBlockEntity railgun) {
        this(id, inventory, railgun, railgun.storage().input(), railgun.storage().receive(), new Data(railgun));
    }

    private RailgunMenu(int id, Inventory inventory, @Nullable RailgunBlockEntity railgun, IItemHandler input,
                        IItemHandler receive, ContainerData data) {
        super(ModMenuTypes.RAILGUN.get(), id, railgun == null ? null : targetOf(railgun), inventory.player);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        Runnable changed = railgun == null ? () -> { } : railgun::setChanged;
        for (int slot = 0; slot < CargoStorage.INPUT_SLOTS; slot++) {
            addSlot(new EndgameItemSlot(input, slot, 8 + slot * 18, 18, changed, this::itemActionAllowed));
        }
        for (int slot = 0; slot < CargoStorage.RECEIVE_SLOTS; slot++) {
            addSlot(new ReceiveSlot(receive, slot, 8 + slot * 18, 54, changed, this::itemActionAllowed));
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
    public static void writeOpenData(FriendlyByteBuf buffer, RailgunBlockEntity railgun) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBlockPos(railgun.getBlockPos());
    }

    private static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported railgun menu format; update both host and client");
        }
        buffer.readBlockPos();
        return new SimpleContainerData(DATA_COUNT);
    }

    @Override
    protected Optional<EndgameIntentGuard.Intent> intent(int button) {
        return switch (button) {
            case BUTTON_PREVIOUS, BUTTON_NEXT -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.CONFIGURE,
                    IntentKind.SELECTION, false, false));
            case BUTTON_LAUNCH -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.OPERATE, IntentKind.STATE,
                    true, false));
            case BUTTON_AUTO, BUTTON_REDSTONE, BUTTON_MIN_DOWN_1, BUTTON_MIN_UP_1, BUTTON_MIN_DOWN_16,
                    BUTTON_MIN_UP_16 -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.CONFIGURE,
                    IntentKind.STATE, false, false));
            default -> Optional.empty();
        };
    }

    @Override
    protected EndgameCode apply(int button, ServerPlayer player, EndgameDeviceBlockEntity device) {
        RailgunBlockEntity railgun = (RailgunBlockEntity) device;
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        Optional<EndgameService> service = EndgameRuntime.operational();
        Optional<EndgameRoot> root = service.flatMap(EndgameService::root);
        if (devices.isEmpty() || root.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        UUID actor = player.getUUID();
        return switch (button) {
            case BUTTON_PREVIOUS, BUTTON_NEXT -> select(railgun, listed(root.get(), railgun, devices.get(), player,
                    true), button == BUTTON_NEXT ? 1 : -1, player.hasPermissions(2), actor, service.get());
            case BUTTON_LAUNCH -> launch(railgun, player, service.get(), devices.get());
            case BUTTON_AUTO -> setting(railgun, service.get(), actor, "auto", () -> railgun.auto(!railgun.auto()),
                    () -> Boolean.toString(railgun.auto()));
            case BUTTON_REDSTONE -> setting(railgun, service.get(), actor, "redstone",
                    () -> railgun.redstoneMode(railgun.redstoneMode().next()), () -> railgun.redstoneMode().name());
            case BUTTON_MIN_DOWN_1, BUTTON_MIN_UP_1, BUTTON_MIN_DOWN_16, BUTTON_MIN_UP_16 -> setting(railgun,
                    service.get(), actor, "min_stack", () -> railgun.minStack(RailgunLaunch.minimumStack(
                            railgun.minStack(), delta(button))), () -> Integer.toString(railgun.minStack()));
            default -> EndgameCode.OK;
        };
    }

    private static int delta(int button) {
        return switch (button) {
            case BUTTON_MIN_DOWN_1 -> -1;
            case BUTTON_MIN_UP_1 -> 1;
            case BUTTON_MIN_DOWN_16 -> -16;
            default -> 16;
        };
    }

    private static EndgameCode setting(RailgunBlockEntity railgun, EndgameService service, UUID actor, String name,
                                       Runnable change, java.util.function.Supplier<String> value) {
        change.run();
        railgun.audit(service, railgun.getLevel().getGameTime(), name, EndgameCode.OK, actor,
                name + "=" + value.get());
        return EndgameCode.OK;
    }

    /** A dry check refuses now with its code; an accepted launch waits for the cadence and the server's cap. */
    private static EndgameCode launch(RailgunBlockEntity railgun, ServerPlayer player, EndgameService service,
                                      EndgameDevices devices) {
        EndgameCode code = RailgunLauncher.launch(railgun, player.serverLevel(), service, devices, player.getUUID(),
                false);
        if (code != EndgameCode.OK) {
            railgun.audit(service, player.serverLevel().getGameTime(), "launch", code,
                    player.getUUID(), "");
            return code;
        }
        railgun.requestLaunch(player.getUUID());
        return EndgameCode.OK;
    }

    /**
     * ADR-054 section 9 selection: {@code ACTIVE} railguns other than this one, of the railgun's owner (any owner for
     * an operator), that pass the route rule, in ID order.
     */
    static List<EndpointRecord> destinations(EndgameRoot root, RailgunBlockEntity railgun, EndgameDevices devices,
                                             Player viewer) {
        if (railgun.ownerId().isEmpty() || !(railgun.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        boolean operator = viewer.hasPermissions(2);
        return root.endpoints().stream()
                .filter(record -> record.kind().equals(RailgunBlockEntity.KIND)
                        && record.state() == EndpointRecord.State.ACTIVE && !record.id().equals(railgun.endpointId())
                        && (operator || railgun.ownerId().get().equals(record.owner())))
                .sorted(Comparator.comparing(record -> record.id().toString()))
                .filter(record -> RailgunLauncher.route(railgun, level, root, devices, Optional.of(record.id()),
                        operator).code() == EndgameCode.OK)
                .toList();
    }

    /**
     * The destination list for this menu's viewer, recomputed on a selection button or after 100 ticks, so the view
     * (sent up to every 5 ticks) does not resolve every candidate's body each time.
     */
    private List<EndpointRecord> listed(EndgameRoot root, RailgunBlockEntity railgun, EndgameDevices devices,
                                        Player viewer, boolean refresh) {
        long now = railgun.getLevel() == null ? 0L : railgun.getLevel().getGameTime();
        if (refresh || now - listedAt >= LIST_REFRESH_TICKS) {
            listed = destinations(root, railgun, devices, viewer);
            listedAt = now;
        }
        return listed;
    }

    private static EndgameCode select(RailgunBlockEntity railgun, List<EndpointRecord> destinations, int step,
                                      boolean operator, UUID actor, EndgameService service) {
        if (destinations.isEmpty()) {
            return EndgameCode.NO_TARGET;
        }
        int index = position(destinations, railgun.target().orElse(null));
        EndpointRecord chosen = destinations.get(Math.floorMod(index < 0 ? (step > 0 ? -1 : 0) + step
                : index + step, destinations.size()));
        boolean foreign = !railgun.ownerId().filter(chosen.owner()::equals).isPresent();
        railgun.target(chosen.id(), operator && foreign);
        railgun.audit(service, railgun.getLevel().getGameTime(), "destination", EndgameCode.OK,
                actor, "to=" + chosen.id() + (operator && foreign ? " operator_selection=true" : ""));
        return EndgameCode.OK;
    }

    private static int position(List<EndpointRecord> destinations, @Nullable UUID selected) {
        for (int i = 0; i < destinations.size(); i++) {
            if (destinations.get(i).id().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    /** Public status is the endpoint state only; the destination, buffers and counts need detail. */
    @Override
    protected EndgameDeviceView view(int containerId, EndgameDeviceBlockEntity device, boolean detail) {
        RailgunBlockEntity railgun = (RailgunBlockEntity) device;
        EndgameCode status = RailgunLauncher.sourceState(railgun);
        List<EndgameDeviceView.Line> lines = new ArrayList<>();
        lines.add(EndgameDeviceView.Line.key(VIEW + "structure", railgun.structureCode().translationKey()));
        if (detail) {
            if (railgun.frozen()) {
                lines.add(EndgameDeviceView.Line.key(VIEW + "warning", VALUE + "break_resolves"));
            }
            destinationLines(railgun, lines);
            lines.add(EndgameDeviceView.Line.key(VIEW + "auto", VALUE + (railgun.auto() ? "on" : "off")));
            lines.add(EndgameDeviceView.Line.key(VIEW + "redstone", VALUE + "redstone."
                    + railgun.redstoneMode().name().toLowerCase(Locale.ROOT)));
            lines.add(EndgameDeviceView.Line.text(VIEW + "min_stack", Integer.toString(railgun.minStack())));
            lines.add(EndgameDeviceView.Line.text(VIEW + "outbox", railgun.source().outbox().size() + " / "
                    + io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitSourceState.MAX_OUTBOX));
            lines.add(EndgameDeviceView.Line.text(VIEW + "in_transit", Long.toString(inTransit(railgun))));
            lines.add(EndgameDeviceView.Line.text(VIEW + "incoming", Integer.toString(
                    railgun.destination().incoming().size())));
            lines.add(EndgameDeviceView.Line.text(VIEW + "receipts", railgun.destination().receipts().size() + " / "
                    + io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitDestinationState
                    .MAX_RECEIPTS));
        }
        return new EndgameDeviceView(containerId, EndgameSystem.RAILGUN, status, railgun.lastCode(), detail,
                lines.subList(0, Math.min(lines.size(), EndgameDeviceView.MAX_LINES)));
    }

    /** The selected destination for viewers with detail: label and list position, place, class, cost and travel. */
    private void destinationLines(RailgunBlockEntity railgun, List<EndgameDeviceView.Line> lines) {
        Optional<EndgameRoot> root = EndgameRuntime.operational().flatMap(EndgameService::root);
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        Player viewer = viewer();
        if (root.isEmpty() || devices.isEmpty() || viewer == null
                || !(railgun.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<EndpointRecord> destinations = listed(root.get(), railgun, devices.get(), viewer, false);
        Optional<EndpointRecord> selected = railgun.target().flatMap(root.get()::endpoint);
        int index = position(destinations, railgun.target().orElse(null));
        lines.add(EndgameDeviceView.Line.text(VIEW + "destination", selected.isEmpty() ? "-"
                : label(devices.get(), level, selected.get()) + (index < 0 ? "" : " (" + (index + 1) + " / "
                + destinations.size() + ")")));
        if (selected.isEmpty()) {
            return;
        }
        BlockPos at = BlockPos.of(selected.get().pos());
        lines.add(EndgameDeviceView.Line.text(VIEW + "destination_at", selected.get().level() + " "
                + at.toShortString()));
        RailgunLauncher.Route route = RailgunLauncher.route(railgun, level, root.get(), devices.get(),
                railgun.target(), railgun.operatorTarget());
        lines.add(EndgameDeviceView.Line.text(VIEW + "route", route.quote().map(quote -> quote.routeClass().name()
                + " " + quote.cost() + " FE " + quote.travel() + " t").orElse(route.code().name())));
    }

    /** A generated label: the kind, the first 8 hex digits of the ID and the body (ADR-054 section 4). */
    static String label(EndgameDevices devices, ServerLevel level, EndpointRecord record) {
        String body = devices.body(level.getServer(), record.level(), record.pos())
                .map(found -> found.body().getPath()).orElse("?");
        return "railgun " + record.id().toString().substring(0, 8) + " @ " + body;
    }

    /** Records from this railgun still travelling. */
    private static long inTransit(RailgunBlockEntity railgun) {
        return EndgameRuntime.operational().flatMap(EndgameService::root).map(root -> root.transits()
                .fromSource(railgun.endpointId()).stream().filter(record -> record.state()
                        == TransitRecord.State.IN_TRANSIT).count()).orElse(0L);
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
        boolean moved = index < PLAYER_START ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
                : moveItemStackTo(stack, INPUT_START, RECEIVE_START, false);
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

    /** Players take received cargo; nothing is ever put into the receive buffer by hand. */
    private static final class ReceiveSlot extends EndgameItemSlot {
        ReceiveSlot(IItemHandler handler, int index, int x, int y, Runnable changed,
                    Predicate<EndgameAction> allowed) {
            super(handler, index, x, y, changed, allowed);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return false;
        }
    }

    /** The energy in two halves; it changes every tick. */
    private static final class Data implements ContainerData {
        private final RailgunBlockEntity railgun;

        Data(RailgunBlockEntity railgun) {
            this.railgun = railgun;
        }

        @Override
        public int get(int index) {
            int energy = railgun.storage().energy().energy();
            return switch (index) {
                case 0 -> energy & 0xFFFF;
                case 1 -> (energy >>> 16) & 0xFFFF;
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
