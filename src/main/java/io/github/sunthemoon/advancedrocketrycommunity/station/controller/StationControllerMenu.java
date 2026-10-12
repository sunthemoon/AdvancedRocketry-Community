package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

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
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ADR-065 section 5.1 and 11.2: the menu of one station controller. Changes arrive only as vanilla menu button
 * IDs, so the server already knows the sender, the open menu and its container; this class adds the exact
 * controller block, the 8-block reach, one mutation per player per 10 ticks and the observed station. Displayed
 * values travel as short data slots; nothing the client sends is a value, coordinate, cost or ray result.
 */
public abstract class StationControllerMenu extends AbstractContainerMenu {
    public static final int FORMAT_MARKER = -65;
    public static final int FORMAT_VERSION = 1;
    /** At most the current orbit and 32 warp targets travel in the open data. */
    public static final int MAX_NAMES = 33;
    public static final String KEY = "screen.advancedrocketrycommunity.station_controller.";
    private static final int STATUS = 0;

    private final StationControllerKind kind;
    private final Level level;
    private final BlockPos position;
    private final Block block;
    private final ContainerData data;
    private final List<ResourceLocation> names;
    /** Server only: the station the screen showed at the last broadcast; changes compare against it. */
    private StationState observed;
    private int status;

    protected StationControllerMenu(MenuType<?> type, StationControllerKind kind, int id, Level level,
                                    BlockPos position, Block block, OpenData open, int dataCount) {
        super(type, id);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.level = level;
        this.position = position.immutable();
        this.block = block;
        this.names = open.names();
        this.data = level == null ? new SimpleContainerData(dataCount + 1) : new ContainerData() {
            @Override
            public int get(int index) {
                return index == STATUS ? status : serverData(index - 1, observed);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return dataCount + 1;
            }
        };
        checkContainerDataCount(data, dataCount + 1);
        addDataSlots(data);
    }

    /** The server menu over the exact controller block, with the open data it was opened with. */
    public static StationControllerMenu server(StationControllerKind kind, int id, Inventory inventory, Level level,
                                               BlockPos position, Block block, OpenData open) {
        return switch (kind) {
            case WARP -> new WarpControllerMenu(id, level, position, block, open);
            case GRAVITY -> new GravityControllerMenu(id, level, position, block, open);
            case ALTITUDE -> new AltitudeControllerMenu(id, level, position, block, open);
            case ORIENTATION -> new OrientationControllerMenu(id, level, position, block, open);
        };
    }

    /** The client menu, from the open data; it has no Level and only mirrors data slots. */
    public static StationControllerMenu client(StationControllerKind kind, int id, Inventory inventory,
                                               FriendlyByteBuf buffer) {
        OpenData open = OpenData.read(buffer, kind);
        return switch (kind) {
            case WARP -> new WarpControllerMenu(id, null, open.position(), null, open);
            case GRAVITY -> new GravityControllerMenu(id, null, open.position(), null, open);
            case ALTITUDE -> new AltitudeControllerMenu(id, null, open.position(), null, open);
            case ORIENTATION -> new OrientationControllerMenu(id, null, open.position(), null, open);
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

    public BlockPos position() {
        return position;
    }

    /** Server and client: the names sent at opening (warp: the orbit at opening, then the targets). */
    public List<ResourceLocation> names() {
        return names;
    }

    /** Client and server: the last action's code, if any. */
    public Optional<StationManagementCode> status() {
        int value = data.get(STATUS);
        StationManagementCode[] codes = StationManagementCode.values();
        return value <= 0 || value > codes.length ? Optional.empty() : Optional.of(codes[value - 1]);
    }

    /** Client display slot {@code index} (after the status slot). */
    protected int value(int index) {
        return data.get(index + 1);
    }

    /** Two unsigned short slots as one int. */
    protected int wide(int index) {
        return (value(index) & 0xFFFF) | ((value(index + 1) & 0xFFFF) << 16);
    }

    protected static int low(int value) {
        return value & 0xFFFF;
    }

    protected static int high(int value) {
        return (value >>> 16) & 0xFFFF;
    }

    /** The fixed buttons of this screen. */
    public abstract List<Button> buttons();

    /** Client display lines from the data slots and open data. */
    public abstract List<Component> lines();

    /** Server: data slot {@code index} for the observed station (null outside a visible station). */
    protected abstract int serverData(int index, StationState station);

    /** Server: one admitted mutating button. */
    protected abstract StationManagementCode apply(int button, ServerPlayer player, StationManager stations,
                                                   StationActorContext context, Optional<StationState> expected);

    /** Server: a button that only changes this menu's own selection, never a station (no rate limit). */
    protected boolean navigate(int button) {
        return false;
    }

    @Override
    public final boolean clickMenuButton(Player player, int button) {
        if (level == null || !(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
                || buttons().stream().noneMatch(spec -> spec.id() == button)) {
            return false;
        }
        if (navigate(button)) {
            return true;
        }
        Optional<StationManager> stations = StationRuntime.manager();
        if (stations.isEmpty()) {
            status = StationManagementCode.AUTHORITY_UNAVAILABLE.ordinal() + 1;
            return true;
        }
        if (!stations.orElseThrow().admitControllerAction(serverPlayer)) {
            status = StationManagementCode.CONTROL_RATE_LIMITED.ordinal() + 1;
            return true;
        }
        StationActorContext context = StationActorContext.controller(level.dimension(), position, block);
        status = apply(button, serverPlayer, stations.orElseThrow(), context, Optional.ofNullable(observed))
                .ordinal() + 1;
        return true;
    }

    @Override
    public void broadcastChanges() {
        refreshObserved();
        super.broadcastChanges();
    }

    @Override
    public void sendAllDataToRemote() {
        refreshObserved();
        super.sendAllDataToRemote();
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

    private void refreshObserved() {
        if (level != null) {
            observed = liveStation();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return level == null || stillValid(ContainerLevelAccess.create(level, position), player, block);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** One fixed button: its ID, translation key and layout. */
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
