package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlKind;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlTarget;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlsNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ADR-065 sections 5.1, 11.1 and 11.2: the menu of one station controller. Actions arrive only as
 * {@code classic_controls} intents for this container and kind; this class adds the holder, the exact loaded
 * controller block, the 8-block reach, one mutation per player per 10 ticks and the observed station, and the
 * services add the station rules. The protected status travels as {@link ClassicControlViewPacket}s to the
 * menu's holder only; nothing the client sends is a coordinate, cost, ray result or final value.
 */
public abstract class StationControllerMenu extends AbstractContainerMenu implements ClassicControlTarget {
    public static final int FORMAT_MARKER = -65;
    public static final int FORMAT_VERSION = 1;
    /** At most the current orbit and 32 warp targets travel in the open data. */
    public static final int MAX_NAMES = 33;
    public static final String KEY = "screen.advancedrocketrycommunity.station_controller.";
    /** A changed view goes out at most once per this many ticks, unless an action or the opening forces it. */
    public static final int VIEW_INTERVAL_TICKS = 5;

    private final StationControllerKind kind;
    private final Level level;
    private final BlockPos position;
    private final Block block;
    private final List<ResourceLocation> names;
    /** Server only: the player who opened the menu, the one holder its views go to. */
    @Nullable
    private final ServerPlayer viewer;
    /** Server only: the station that the holder's current view shows; changes compare against it. */
    private StationState observed;
    private Optional<StationManagementCode> status = Optional.empty();
    @Nullable
    private ClassicControlViewPacket lastSent;
    private long lastSentTick = Long.MIN_VALUE / 2;

    protected StationControllerMenu(MenuType<?> type, StationControllerKind kind, int id, Level level,
                                    BlockPos position, Block block, OpenData open, @Nullable ServerPlayer viewer) {
        super(type, id);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.level = level;
        this.position = position.immutable();
        this.block = block;
        this.names = open.names();
        this.viewer = viewer;
    }

    /** The server menu over the exact controller block for the player of {@code inventory}. */
    public static StationControllerMenu server(StationControllerKind kind, int id, Inventory inventory, Level level,
                                               BlockPos position, Block block, OpenData open) {
        ServerPlayer viewer = inventory.player instanceof ServerPlayer player ? player : null;
        return switch (kind) {
            case WARP -> new WarpControllerMenu(id, level, position, block, open, viewer);
            case GRAVITY -> new GravityControllerMenu(id, level, position, block, open, viewer);
            case ALTITUDE -> new AltitudeControllerMenu(id, level, position, block, open, viewer);
            case ORIENTATION -> new OrientationControllerMenu(id, level, position, block, open, viewer);
        };
    }

    /** The client menu, from the open data; it has no Level and shows the views the server sends. */
    public static StationControllerMenu client(StationControllerKind kind, int id, Inventory inventory,
                                               FriendlyByteBuf buffer) {
        OpenData open = OpenData.read(buffer, kind);
        return switch (kind) {
            case WARP -> new WarpControllerMenu(id, null, open.position(), null, open, null);
            case GRAVITY -> new GravityControllerMenu(id, null, open.position(), null, open, null);
            case ALTITUDE -> new AltitudeControllerMenu(id, null, open.position(), null, open, null);
            case ORIENTATION -> new OrientationControllerMenu(id, null, open.position(), null, open, null);
        };
    }

    /** The open data for a station the player may see: the warp screen also lists its current targets. */
    public static OpenData openData(StationControllerKind kind, ServerPlayer player, BlockPos position,
                                    StationState station) {
        List<ResourceLocation> names = new ArrayList<>();
        if (kind == StationControllerKind.WARP && player.getServer() != null) {
            names.add(station.orbitBody());
            StationWarpRuntime.service().ifPresent(warp -> names.addAll(
                    warp.warpTargets(player.getServer(), station, MAX_NAMES - 1)));
        }
        return new OpenData(kind, position, names);
    }

    public StationControllerKind kind() {
        return kind;
    }

    @Override
    public ClassicControlKind classicControlKind() {
        return kind.wire();
    }

    public BlockPos position() {
        return position;
    }

    /** Server and client: the names sent at opening (warp: the orbit at opening, then the targets). */
    public List<ResourceLocation> names() {
        return names;
    }

    /** Server: the last action's code, if any. The client reads it from the view. */
    public Optional<StationManagementCode> status() {
        return status;
    }

    /** Server: the last view sent to the holder, if any. */
    public Optional<ClassicControlViewPacket> lastView() {
        return Optional.ofNullable(lastSent);
    }

    /** The fixed buttons of this screen; a button's ID is its {@code classic_controls} action. */
    public abstract List<Button> buttons();

    /** Client display lines of a view of this menu. */
    public abstract List<Component> lines(ClassicControlViewPacket view);

    /**
     * Client: the intent that button {@code action} sends with the current view, or empty while it cannot be sent
     * (the screen then shows the button inactive).
     */
    public Optional<ClassicControlIntentPacket> intent(int action, Optional<ClassicControlViewPacket> view) {
        return Optional.of(new ClassicControlIntentPacket(containerId, kind.wire(), action, 0, 0L));
    }

    /** Server: the kind's exact view values for the observed station, inside the kind's ranges. */
    protected abstract List<Integer> viewValues(StationState station);

    /** Server: the token of the quote this menu displays, or 0. */
    protected long viewToken() {
        return 0L;
    }

    /** Server: one admitted mutating action. */
    protected abstract StationManagementCode apply(ClassicControlIntentPacket intent, ServerPlayer player,
                                                   StationManager stations, StationActorContext context,
                                                   Optional<StationState> expected);

    /** Server: an action that only changes this menu's own selection, never a station (no rate limit). */
    protected boolean navigate(ClassicControlIntentPacket intent) {
        return false;
    }

    /**
     * ADR-065 section 11.2 for one intent: the sender is this menu's connected, living, non-spectator holder, the
     * menu is still valid (the exact controller block loaded within 8 blocks in the same Level), and the action
     * is one of this screen's buttons. Navigation changes only this menu; every other action is rate limited and
     * then rechecked in full by the station services (owner or operator, region, state and range).
     */
    @Override
    public final void acceptClassicControl(ServerPlayer sender, ClassicControlIntentPacket intent) {
        if (level == null || sender != viewer || sender instanceof FakePlayer || intent.kind() != kind.wire()
                || intent.containerId() != containerId || !stillValid(sender)
                || buttons().stream().noneMatch(spec -> spec.id() == intent.action())) {
            return;
        }
        if (!navigate(intent)) {
            status = Optional.of(act(sender, intent));
        }
        sendView(true);
    }

    private StationManagementCode act(ServerPlayer sender, ClassicControlIntentPacket intent) {
        Optional<StationManager> stations = StationRuntime.manager();
        if (stations.isEmpty()) {
            return StationManagementCode.AUTHORITY_UNAVAILABLE;
        }
        if (!stations.orElseThrow().admitControllerAction(sender)) {
            return StationManagementCode.CONTROL_RATE_LIMITED;
        }
        StationActorContext context = StationActorContext.controller(level.dimension(), position, block);
        return apply(intent, sender, stations.orElseThrow(), context, Optional.ofNullable(observed));
    }

    /** Station controllers take no vanilla menu buttons; their actions are {@code classic_controls} intents. */
    @Override
    public final boolean clickMenuButton(Player player, int button) {
        return false;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        sendView(false);
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        sendView(true);
    }

    /**
     * Server: sends the holder a changed view, at most once per {@link #VIEW_INTERVAL_TICKS} unless forced, and
     * nothing once the menu is no longer valid. The observed station moves on only when the holder's view matches
     * it, so a change the holder has not seen yet refuses the next action as {@code STATION_CHANGED}.
     */
    private void sendView(boolean force) {
        if (level == null || viewer == null || !stillValid(viewer)) {
            return;
        }
        StationState live = liveStation();
        MinecraftServer server = level.getServer();
        if (live == null || server == null) {
            return;
        }
        ClassicControlViewPacket view = new ClassicControlViewPacket(containerId, kind.wire(), status, viewToken(),
                viewValues(live));
        if (view.equals(lastSent)) {
            observed = live;
            return;
        }
        long now = server.getTickCount();
        if (!force && now - lastSentTick < VIEW_INTERVAL_TICKS) {
            return;
        }
        lastSent = view;
        lastSentTick = now;
        observed = live;
        ClassicControlsNetwork.sendView(viewer, view);
    }

    /** Server: the live station whose region holds the controller, or null. */
    protected StationState liveStation() {
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null) {
            return null;
        }
        StationRegistrySavedData registry = StationRegistrySavedData.get(server);
        return registry.operational() ? registry.findAt(position.getX(), position.getZ()).orElse(null) : null;
    }

    protected Level level() {
        return level;
    }

    /**
     * Server: {@link StationControllerHolder} over the live state. The holder is the connected, living,
     * non-spectator player who opened the menu, in the controller's Level and within reach of the exact controller
     * block, whose chunk is loaded. Only loaded chunks are read.
     */
    @Override
    public boolean stillValid(Player player) {
        if (level == null) {
            return true;
        }
        return StationControllerHolder.check(new StationControllerHolder.Probe() {
            @Override
            public boolean holder() {
                return player == viewer && player instanceof ServerPlayer && !(player instanceof FakePlayer);
            }

            @Override
            public boolean connected() {
                MinecraftServer server = level.getServer();
                return !viewer.hasDisconnected() && server != null
                        && server.getPlayerList().getPlayer(viewer.getUUID()) == viewer;
            }

            @Override
            public boolean alive() {
                return viewer.isAlive();
            }

            @Override
            public boolean spectator() {
                return viewer.isSpectator();
            }

            @Override
            public boolean sameLevel() {
                return viewer.level() == level;
            }

            @Override
            public boolean controllerChunkLoaded() {
                return level.hasChunkAt(position);
            }

            @Override
            public boolean controllerPresent() {
                return level.getBlockState(position).is(block);
            }

            @Override
            public double distanceSquared() {
                return viewer.distanceToSqr(Vec3.atCenterOf(position));
            }
        }).isEmpty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** One fixed button: its ID (the intent's action), translation key and layout. */
    public record Button(int id, String key, int x, int y, int width) {
    }

    /** The bounded open data: a format marker and version, the kind, the controller position and the names. */
    public record OpenData(StationControllerKind kind, BlockPos position, List<ResourceLocation> names) {
        public OpenData {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(position, "position");
            names = List.copyOf(names);
            if (names.size() > MAX_NAMES || names.stream().anyMatch(name -> name.toString().length() > 128)) {
                throw new IllegalArgumentException("Station controller open data exceeds its bound");
            }
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(FORMAT_MARKER);
            buffer.writeVarInt(FORMAT_VERSION);
            buffer.writeVarInt(kind.ordinal());
            buffer.writeBlockPos(position);
            buffer.writeVarInt(names.size());
            names.forEach(name -> buffer.writeUtf(name.toString(), 128));
        }

        static OpenData read(FriendlyByteBuf buffer, StationControllerKind expected) {
            if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION
                    || buffer.readVarInt() != expected.ordinal()) {
                throw new IllegalArgumentException("Unsupported station controller menu format; update both sides");
            }
            BlockPos position = buffer.readBlockPos();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_NAMES) {
                throw new IllegalArgumentException("Station controller open data exceeds its bound");
            }
            List<ResourceLocation> names = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ResourceLocation name = ResourceLocation.tryParse(buffer.readUtf(128));
                if (name == null) {
                    throw new IllegalArgumentException("Invalid station controller name");
                }
                names.add(name);
            }
            return new OpenData(expected, position, names);
        }
    }
}
