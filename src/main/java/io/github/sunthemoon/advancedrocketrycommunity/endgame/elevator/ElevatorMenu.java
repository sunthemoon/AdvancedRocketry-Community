package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo.CargoStorage;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameItemSlot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPair;
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
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitDestinationState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitSourceState;
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
 * ADR-059 section 9 menu of a terminal or an anchor: the 4 input and 9 receive slots, energy as data slots, and the
 * device view for the placement, the pair state with the failing rule, the far end (for its owner, the station owner
 * and operators), the anchor selection (terminal), the settings and the cargo counts. Buttons: anchor previous/next and
 * bind (terminal only), unbind, launch, auto, redstone and ride. Riding, shipping, binding and unbinding follow ADR-054
 * section 3's per-action overrides, so those intents pass the guard with {@code VIEW} and the elevator decides the
 * rest; auto and redstone are {@code CONFIGURE}. Open data format 1 carries the kind and the position.
 */
public final class ElevatorMenu extends EndgameDeviceMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_BIND = 2;
    public static final int BUTTON_UNBIND = 3;
    public static final int BUTTON_LAUNCH = 4;
    public static final int BUTTON_AUTO = 5;
    public static final int BUTTON_REDSTONE = 6;
    public static final int BUTTON_RIDE = 7;
    public static final int DATA_COUNT = 2;
    private static final String VIEW = "advancedrocketrycommunity.endgame.view.";
    private static final String VALUE = "advancedrocketrycommunity.endgame.value.";
    private static final int PLAYER_START = CargoStorage.INPUT_SLOTS + CargoStorage.RECEIVE_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final ContainerData data;
    private final boolean terminal;
    @Nullable
    private UUID selected;

    public ElevatorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, new ItemStackHandler(CargoStorage.INPUT_SLOTS),
                new ItemStackHandler(CargoStorage.RECEIVE_SLOTS), new SimpleContainerData(DATA_COUNT),
                readOpenData(buffer));
    }

    public ElevatorMenu(int id, Inventory inventory, ElevatorEndpointBlockEntity endpoint) {
        this(id, inventory, endpoint, endpoint.storage().input(), endpoint.storage().receive(), new Data(endpoint),
                !endpoint.anchor());
    }

    private ElevatorMenu(int id, Inventory inventory, @Nullable ElevatorEndpointBlockEntity endpoint,
                         IItemHandler input, IItemHandler receive, ContainerData data, boolean terminal) {
        super(ModMenuTypes.ELEVATOR.get(), id, endpoint == null ? null : targetOf(endpoint), inventory.player);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.terminal = terminal;
        Runnable changed = endpoint == null ? () -> { } : endpoint::setChanged;
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

    /** Open data format 1: marker, version, whether it is a terminal, and the position (display only). */
    public static void writeOpenData(FriendlyByteBuf buffer, ElevatorEndpointBlockEntity endpoint) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBoolean(!endpoint.anchor());
        buffer.writeBlockPos(endpoint.getBlockPos());
    }

    private static boolean readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported elevator menu format; update both host and client");
        }
        boolean terminal = buffer.readBoolean();
        buffer.readBlockPos();
        return terminal;
    }

    public boolean terminal() {
        return terminal;
    }

    @Override
    protected Optional<EndgameIntentGuard.Intent> intent(int button) {
        return switch (button) {
            case BUTTON_PREVIOUS, BUTTON_NEXT -> terminal ? Optional.of(new EndgameIntentGuard.Intent(
                    EndgameAction.VIEW, IntentKind.SELECTION, false, false)) : Optional.empty();
            case BUTTON_BIND -> terminal ? Optional.of(new EndgameIntentGuard.Intent(EndgameAction.VIEW,
                    IntentKind.STATE, true, false)) : Optional.empty();
            case BUTTON_UNBIND -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.VIEW, IntentKind.STATE,
                    false, false));
            case BUTTON_LAUNCH, BUTTON_RIDE -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.VIEW,
                    IntentKind.STATE, true, false));
            case BUTTON_AUTO, BUTTON_REDSTONE -> Optional.of(new EndgameIntentGuard.Intent(EndgameAction.CONFIGURE,
                    IntentKind.STATE, false, false));
            default -> Optional.empty();
        };
    }

    @Override
    protected EndgameCode apply(int button, ServerPlayer player, EndgameDeviceBlockEntity device) {
        ElevatorEndpointBlockEntity endpoint = (ElevatorEndpointBlockEntity) device;
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        Optional<EndgameService> service = EndgameRuntime.operational();
        Optional<EndgameRoot> root = service.flatMap(EndgameService::root);
        if (devices.isEmpty() || root.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        UUID actor = player.getUUID();
        boolean operator = player.hasPermissions(2);
        return switch (button) {
            case BUTTON_PREVIOUS, BUTTON_NEXT -> move(anchors(root.get(), player), button == BUTTON_NEXT ? 1 : -1);
            case BUTTON_BIND -> selected == null ? EndgameCode.ANCHOR_UNAVAILABLE : ElevatorPairs.bind(
                    level.getServer(), service.get(), devices.get(), (ElevatorTerminalBlockEntity) endpoint, selected,
                    actor, operator, now).code();
            case BUTTON_UNBIND -> endpoint.deviceId().flatMap(root.get().pairs()::forEndpoint)
                    .map(pair -> ElevatorPairs.unbind(level.getServer(), service.get(), devices.get(), pair, actor,
                            operator, now).code()).orElse(EndgameCode.NOT_BOUND);
            case BUTTON_LAUNCH -> launch(endpoint, player, service.get(), devices.get());
            case BUTTON_AUTO -> setting(endpoint, service.get(), actor, "auto", () -> endpoint.auto(!endpoint.auto()),
                    () -> Boolean.toString(endpoint.auto()));
            case BUTTON_REDSTONE -> setting(endpoint, service.get(), actor, "redstone",
                    () -> endpoint.redstoneMode(endpoint.redstoneMode().next()), () -> endpoint.redstoneMode().name());
            case BUTTON_RIDE -> devices.get().elevatorRides().request(player, endpoint, service.get(), devices.get())
                    .code();
            default -> EndgameCode.OK;
        };
    }

    private static EndgameCode setting(ElevatorEndpointBlockEntity endpoint, EndgameService service, UUID actor,
                                       String name, Runnable change, java.util.function.Supplier<String> value) {
        change.run();
        endpoint.audit(service, endpoint.getLevel().getGameTime(), name, EndgameCode.OK, actor, name + "="
                + value.get());
        return EndgameCode.OK;
    }

    /** A dry check refuses now with its code; an accepted launch waits for the cadence and the server's cap. */
    private static EndgameCode launch(ElevatorEndpointBlockEntity endpoint, ServerPlayer player,
                                      EndgameService service, EndgameDevices devices) {
        ElevatorRules.Check check = ElevatorCargo.launch(endpoint, player.serverLevel(), service, devices,
                player.getUUID(), false);
        if (!check.ok()) {
            endpoint.audit(service, player.serverLevel().getGameTime(), "launch", check.code(), player.getUUID(),
                    check.rule().map(rule -> "rule=" + rule.name()).orElse(""));
            return check.code();
        }
        endpoint.requestLaunch(player.getUUID());
        return EndgameCode.OK;
    }

    /** ADR-054 section 9 selection: the viewer's own ACTIVE anchors (operators: every anchor), in ID order. */
    static List<EndpointRecord> anchors(EndgameRoot root, Player viewer) {
        boolean operator = viewer.hasPermissions(2);
        return root.endpoints().stream()
                .filter(record -> record.kind().equals(ElevatorAnchorBlockEntity.KIND)
                        && record.state() == EndpointRecord.State.ACTIVE
                        && (operator || record.owner().equals(viewer.getUUID())))
                .sorted(Comparator.comparing(record -> record.id().toString()))
                .toList();
    }

    private EndgameCode move(List<EndpointRecord> anchors, int step) {
        if (anchors.isEmpty()) {
            selected = null;
            return EndgameCode.ANCHOR_UNAVAILABLE;
        }
        int index = position(anchors);
        selected = anchors.get(Math.floorMod(index < 0 ? (step > 0 ? -1 : 0) + step : index + step,
                anchors.size())).id();
        return EndgameCode.OK;
    }

    private int position(List<EndpointRecord> anchors) {
        for (int i = 0; i < anchors.size(); i++) {
            if (anchors.get(i).id().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    /** Public status is the placement only; the pair, the far end, the selection and the counts need detail. */
    @Override
    protected EndgameDeviceView view(int containerId, EndgameDeviceBlockEntity device, boolean detail) {
        ElevatorEndpointBlockEntity endpoint = (ElevatorEndpointBlockEntity) device;
        EndgameCode status = ElevatorCargo.ownState(endpoint);
        List<EndgameDeviceView.Line> lines = new ArrayList<>();
        lines.add(EndgameDeviceView.Line.key(VIEW + "structure", endpoint.placement().translationKey()));
        if (detail) {
            if (endpoint.frozen()) {
                lines.add(EndgameDeviceView.Line.key(VIEW + "warning", VALUE + "break_resolves"));
            }
            lines.add(EndgameDeviceView.Line.text(VIEW + "pair", endpoint.pairCheck().describe()));
            farEnd(endpoint, lines);
            if (terminal) {
                selection(lines);
            }
            lines.add(EndgameDeviceView.Line.key(VIEW + "auto", VALUE + (endpoint.auto() ? "on" : "off")));
            lines.add(EndgameDeviceView.Line.key(VIEW + "redstone", VALUE + "redstone."
                    + endpoint.redstoneMode().name().toLowerCase(Locale.ROOT)));
            lines.add(EndgameDeviceView.Line.text(VIEW + "outbox", endpoint.source().outbox().size() + " / "
                    + TransitSourceState.MAX_OUTBOX));
            lines.add(EndgameDeviceView.Line.text(VIEW + "in_transit", Long.toString(inTransit(endpoint))));
            lines.add(EndgameDeviceView.Line.text(VIEW + "incoming", Integer.toString(
                    endpoint.destination().incoming().size())));
            lines.add(EndgameDeviceView.Line.text(VIEW + "receipts", endpoint.destination().receipts().size() + " / "
                    + TransitDestinationState.MAX_RECEIPTS));
        }
        return new EndgameDeviceView(containerId, EndgameSystem.SPACE_ELEVATOR, status, endpoint.lastCode(), detail,
                lines.subList(0, Math.min(lines.size(), EndgameDeviceView.MAX_LINES)));
    }

    /** The other end of the pair, for viewers with detail (its owner, the station owner and operators). */
    private void farEnd(ElevatorEndpointBlockEntity endpoint, List<EndgameDeviceView.Line> lines) {
        Optional<EndgameRoot> root = EndgameRuntime.operational().flatMap(EndgameService::root);
        Optional<ElevatorPair> pair = root.flatMap(found -> endpoint.deviceId().flatMap(found.pairs()::forEndpoint));
        if (pair.isEmpty()) {
            return;
        }
        UUID other = pair.get().otherEnd(endpoint.endpointId());
        Optional<EndpointRecord> record = root.get().endpoint(other);
        lines.add(EndgameDeviceView.Line.text(VIEW + "far_end", (endpoint.anchor() ? "elevator_terminal "
                : "elevator_anchor ") + other.toString().substring(0, 8) + " @ " + pair.get().bodyId().getPath()));
        record.ifPresent(found -> lines.add(EndgameDeviceView.Line.text(VIEW + "far_end_at", found.level() + " "
                + BlockPos.of(found.pos()).toShortString())));
    }

    private void selection(List<EndgameDeviceView.Line> lines) {
        Optional<EndgameRoot> root = EndgameRuntime.operational().flatMap(EndgameService::root);
        Player viewer = viewer();
        if (root.isEmpty() || viewer == null) {
            return;
        }
        List<EndpointRecord> anchors = anchors(root.get(), viewer);
        int index = position(anchors);
        if (index < 0 && !anchors.isEmpty()) {
            selected = anchors.get(0).id();
            index = 0;
        }
        lines.add(EndgameDeviceView.Line.text(VIEW + "anchor", index < 0 ? "-" : "elevator_anchor "
                + anchors.get(index).id().toString().substring(0, 8) + " " + anchors.get(index).level() + " "
                + BlockPos.of(anchors.get(index).pos()).toShortString() + " (" + (index + 1) + " / " + anchors.size()
                + ")"));
    }

    private static long inTransit(ElevatorEndpointBlockEntity endpoint) {
        return EndgameRuntime.operational().flatMap(EndgameService::root).map(root -> root.transits()
                .fromSource(endpoint.endpointId()).stream().filter(record -> record.state()
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
                : moveItemStackTo(stack, 0, CargoStorage.INPUT_SLOTS, false);
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

    private static final class Data implements ContainerData {
        private final ElevatorEndpointBlockEntity endpoint;

        Data(ElevatorEndpointBlockEntity endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public int get(int index) {
            int energy = endpoint.storage().energy().energy();
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
