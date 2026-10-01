package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameNetwork;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ADR-054 section 4 menu base: buttons are the only intents and pass {@link EndgameIntentGuard} first; the server
 * sends the device view when the menu opens and on change, at most once per 5 ticks, with detail only for viewers
 * that ADR-054 section 3 allows to see it. Item slots ({@link EndgameItemSlot}) ask {@link #itemActionAllowed}: the
 * server decides for the viewer at every click, and one data slot carries the result to the client for display.
 */
public abstract class EndgameDeviceMenu extends AbstractContainerMenu {
    private static final int TAKE = 1;
    private static final int PUT = 2;

    @Nullable
    private final EndgameIntentGuard.Target target;
    @Nullable
    private final ServerPlayer viewer;
    private final DataSlot itemAuthority;
    @Nullable
    private EndgameDeviceView lastSent;
    private long lastSentTick = Long.MIN_VALUE / 2;

    protected EndgameDeviceMenu(MenuType<?> type, int id, @Nullable EndgameIntentGuard.Target target,
                                @Nullable Player player) {
        super(type, id);
        this.target = target;
        this.viewer = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        this.itemAuthority = target == null ? DataSlot.standalone() : new DataSlot() {
            @Override
            public int get() {
                return (itemActionAllowed(EndgameAction.WITHDRAW) ? TAKE : 0)
                        | (itemActionAllowed(EndgameAction.CONFIGURE) ? PUT : 0);
            }

            @Override
            public void set(int value) {
            }
        };
        addDataSlot(itemAuthority);
    }

    /**
     * ADR-054 section 3 for item slots (review C11R-H2): taking needs {@code WITHDRAW}, putting needs
     * {@code CONFIGURE}. On the server it is decided now for the menu's viewer, a connected non-fake player; the
     * client copy shows the server's last decision and changes nothing by itself.
     */
    public boolean itemActionAllowed(EndgameAction action) {
        if (action != EndgameAction.WITHDRAW && action != EndgameAction.CONFIGURE) {
            throw new IllegalArgumentException("Item slots take or put: " + action);
        }
        if (target == null) {
            return (itemAuthority.get() & (action == EndgameAction.WITHDRAW ? TAKE : PUT)) != 0;
        }
        if (viewer == null || viewer instanceof FakePlayer || !(viewer.level() instanceof ServerLevel level)) {
            return false;
        }
        Optional<EndgameDeviceBlockEntity> device = device(level);
        return device.isPresent() && !device.get().quarantined()
                && authority(level, viewer, device.get(), action).allowed();
    }

    /** The intent of a button ID, or empty for an unknown button. */
    protected abstract Optional<EndgameIntentGuard.Intent> intent(int button);

    /** Applies an accepted intent to the checked device; returns the code to report ({@code OK} for none). */
    protected abstract EndgameCode apply(int button, ServerPlayer player, EndgameDeviceBlockEntity device);

    /** The view for this menu's viewer; without detail it carries public status only. */
    protected abstract EndgameDeviceView view(int containerId, EndgameDeviceBlockEntity device, boolean detail);

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (target == null) {
            return false;
        }
        Optional<EndgameIntentGuard.Intent> intent = intent(button);
        if (intent.isEmpty()) {
            return false;
        }
        EndgameIntentGuard.Result result = EndgameIntentGuard.check(player, target, intent.get());
        if (!result.allowed()) {
            return false;
        }
        EndgameCode code = apply(button, (ServerPlayer) player, result.device().orElseThrow());
        if (code != EndgameCode.OK) {
            EndgameIntentGuard.statusLine(player, code);
        }
        return true;
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

    private void sendView(boolean force) {
        if (viewer == null || target == null || !(viewer.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (!force && now - lastSentTick < EndgameLimits.DEVICE_VIEW_INTERVAL_TICKS) {
            return;
        }
        Optional<EndgameDeviceBlockEntity> device = device(level);
        if (device.isEmpty()) {
            return;
        }
        EndgameAuthority.Decision decision = viewAuthority(level, viewer, device.get());
        EndgameDeviceView view;
        if (decision.allowed()) {
            view = view(containerId, device.get(), decision.detail());
        } else if (authority(level, viewer, device.get(), EndgameAction.WITHDRAW).allowed()) {
            // A withdraw-only menu shows only why it is one (review C11R-M4).
            view = new EndgameDeviceView(containerId, device.get().system(), decision.refusal(), decision.refusal(),
                    false, List.of());
        } else {
            return;
        }
        lastSentTick = now;
        if (!view.equals(lastSent)) {
            lastSent = view;
            EndgameNetwork.sendView(viewer, view);
        }
    }

    /** The menu's device when its chunk is loaded and it is still the same device. */
    protected Optional<EndgameDeviceBlockEntity> device(ServerLevel level) {
        if (target == null || !level.dimension().equals(target.level()) || level.getChunkSource()
                .getChunkNow(target.position().getX() >> 4, target.position().getZ() >> 4) == null) {
            return Optional.empty();
        }
        return level.getBlockEntity(target.position()) instanceof EndgameDeviceBlockEntity device && !device.isRemoved()
                && device.deviceId().filter(target.deviceId()::equals).isPresent()
                ? Optional.of(device) : Optional.empty();
    }

    @Override
    public boolean stillValid(Player player) {
        if (target == null) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Optional<EndgameDeviceBlockEntity> device = device(level);
        return device.isPresent() && player.distanceToSqr(target.position().getCenter())
                <= EndgameLimits.INTENT_DISTANCE_BLOCKS * EndgameLimits.INTENT_DISTANCE_BLOCKS
                && openRefusal(level, player, device.get()) == EndgameCode.OK;
    }

    /**
     * Whether a player may open (and keep open) this device's menu: with {@code VIEW}, or with {@code WITHDRAW} alone
     * where {@code VIEW} is refused, as for the owner of a device in an unavailable station (ADR-054 section 3, review
     * C11R-M4). Returns the {@code VIEW} refusal otherwise.
     */
    public static EndgameCode openRefusal(ServerLevel level, Player viewer, EndgameDeviceBlockEntity device) {
        EndgameAuthority.Decision view = viewAuthority(level, viewer, device);
        if (view.allowed() || authority(level, viewer, device, EndgameAction.WITHDRAW).allowed()) {
            return EndgameCode.OK;
        }
        return view.refusal();
    }

    /** ADR-054 section 3 {@code VIEW} for a viewer of this device. */
    public static EndgameAuthority.Decision viewAuthority(ServerLevel level, Player viewer,
                                                          EndgameDeviceBlockEntity device) {
        return authority(level, viewer, device, EndgameAction.VIEW);
    }

    /** ADR-054 section 3 for a player and an action on this device (not a station-managed system). */
    public static EndgameAuthority.Decision authority(ServerLevel level, Player actor, EndgameDeviceBlockEntity device,
                                                      EndgameAction action) {
        return EndgameAuthority.decide(new EndgameAuthority.Request(actor.getUUID(), actor.hasPermissions(2),
                device.ownerId(), EndgameStations.at(level, device.getBlockPos()).context(), action, false));
    }

    /** The server-side viewer; null on the client. */
    @Nullable
    protected ServerPlayer viewer() {
        return viewer;
    }

    protected Optional<EndgameIntentGuard.Target> target() {
        return Optional.ofNullable(target);
    }

    protected static EndgameIntentGuard.Target targetOf(EndgameDeviceBlockEntity device) {
        Objects.requireNonNull(device.getLevel(), "level");
        return new EndgameIntentGuard.Target(device.getLevel().dimension(), device.getBlockPos(),
                device.deviceId().orElseThrow());
    }
}
