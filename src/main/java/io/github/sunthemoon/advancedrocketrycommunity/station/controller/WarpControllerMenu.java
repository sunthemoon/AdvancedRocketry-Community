package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpService;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpCostClass;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpQuote;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * {@code warp_controller} (ADR-065 section 5.1): the warp service's quote/request, confirm, cancel and status
 * actions through the shared context validator. The target is an index into the server's own list sent at
 * opening; the request names the index the screen shows, the confirmation the token of the quote it shows. Costs,
 * balances and checks stay on the server, and this menu stores no energy.
 */
public final class WarpControllerMenu extends StationControllerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_REQUEST = 2;
    public static final int BUTTON_CONFIRM = 3;
    public static final int BUTTON_CANCEL = 4;
    /** View values, in the {@code WARP_CONTROLLER} layout. */
    public static final int SELECTED = 0;
    public static final int ORBIT = 1;
    public static final int BALANCE = 2;
    public static final int PENDING = 3;
    public static final int COST = 4;
    public static final int COST_CLASS = 5;
    public static final int COUNTDOWN = 6;
    static final List<Button> BUTTONS = List.of(
            new Button(BUTTON_PREVIOUS, KEY + "previous", 8, 20, 20), new Button(BUTTON_NEXT, KEY + "next", 148, 20, 20),
            new Button(BUTTON_REQUEST, KEY + "request", 8, 38, 52), new Button(BUTTON_CONFIRM, KEY + "confirm", 62, 38, 52),
            new Button(BUTTON_CANCEL, KEY + "cancel", 116, 38, 52));
    private static final SecureRandom TOKENS = new SecureRandom();

    /** Server: the selected target index into {@code names().subList(1, ...)}, and the quote shown. */
    private int selected;
    @Nullable
    private WarpQuote displayed;
    /** Server: the token of {@link #displayed} while it is an issued confirmation, else 0. */
    private long token;

    WarpControllerMenu(int id, Level level, BlockPos position, Block block, OpenData open,
                       @Nullable ServerPlayer viewer) {
        super(ModMenuTypes.WARP_CONTROLLER.get(), StationControllerKind.WARP, id, level, position, block, open, viewer);
    }

    @Override
    public List<Button> buttons() {
        return BUTTONS;
    }

    private List<ResourceLocation> targets() {
        return names().size() <= 1 ? List.of() : names().subList(1, names().size());
    }

    @Override
    public Optional<ClassicControlIntentPacket> intent(int action, Optional<ClassicControlViewPacket> view) {
        if (action == BUTTON_REQUEST) {
            return view.filter(shown -> shown.value(SELECTED) >= 0).map(shown -> new ClassicControlIntentPacket(
                    containerId, classicControlKind(), action, shown.value(SELECTED), 0L));
        }
        if (action == BUTTON_CONFIRM) {
            return view.filter(shown -> shown.token() > 0L).map(shown -> new ClassicControlIntentPacket(
                    containerId, classicControlKind(), action, 0, shown.token()));
        }
        return super.intent(action, view);
    }

    @Override
    protected boolean navigate(ClassicControlIntentPacket intent) {
        if (intent.action() != BUTTON_PREVIOUS && intent.action() != BUTTON_NEXT) {
            return false;
        }
        int count = targets().size();
        if (count > 0) {
            selected = Math.floorMod(selected + (intent.action() == BUTTON_NEXT ? 1 : -1), count);
            displayed = null;
            token = 0L;
        }
        return true;
    }

    @Override
    public List<Component> lines(ClassicControlViewPacket view) {
        List<Component> lines = new ArrayList<>();
        int orbit = view.value(ORBIT);
        lines.add(Component.translatable(KEY + "orbit", orbit >= 0 && orbit < names().size()
                ? Component.literal(names().get(orbit).toString()) : Component.translatable(KEY + "unknown")));
        int index = view.value(SELECTED);
        lines.add(index >= 0 && index < targets().size()
                ? Component.translatable(KEY + "target", Component.literal(targets().get(index).toString()))
                : Component.translatable(KEY + "no_target"));
        lines.add(Component.translatable(KEY + "balance", view.value(BALANCE), view.value(PENDING)));
        int costClass = view.value(COST_CLASS);
        if (costClass > 0) {
            lines.add(Component.translatable(KEY + "cost", view.value(COST),
                    Component.translatable(KEY + "class." + (costClass == 1 ? "in_system" : "interstellar"))));
        }
        int countdown = view.value(COUNTDOWN);
        if (countdown >= 0) {
            lines.add(Component.translatable(KEY + "countdown", countdown));
        }
        return lines;
    }

    @Override
    protected List<Integer> viewValues(StationState station) {
        StationRegistrySavedData registry = StationRegistrySavedData.get(level().getServer());
        Optional<StationWarpService> warp = StationWarpRuntime.service();
        int countdown = warp.flatMap(service -> service.countdown(station.stationId()))
                .map(running -> (int) Math.min(Short.MAX_VALUE,
                        (running.ticksLeft(level().getServer().getTickCount()) + 19L) / 20L)).orElse(-1);
        return List.of(
                targets().isEmpty() ? -1 : selected,
                names().indexOf(station.orbitBody()),
                Math.max(0, registry.warpEnergy(station.stationId())),
                Math.max(0, warp.map(service -> service.pendingCredit(station.stationId())).orElse(0)),
                displayed == null ? 0 : displayed.cost(),
                displayed == null ? 0 : displayed.costClass() == WarpCostClass.IN_SYSTEM ? 1 : 2,
                Math.max(-1, countdown));
    }

    @Override
    protected long viewToken() {
        return token;
    }

    @Override
    protected StationManagementCode apply(ClassicControlIntentPacket intent, ServerPlayer player,
                                          StationManager stations, StationActorContext context,
                                          Optional<StationState> expected) {
        Optional<StationWarpService> warp = StationWarpRuntime.service();
        if (warp.isEmpty()) {
            return StationManagementCode.AUTHORITY_UNAVAILABLE;
        }
        StationWarpResult result;
        switch (intent.action()) {
            case BUTTON_REQUEST -> {
                if (targets().isEmpty()) {
                    return StationManagementCode.WARP_TARGET_UNAVAILABLE;
                }
                if (intent.value() != selected) {
                    // The screen showed another target than this menu's selection.
                    return StationManagementCode.STATION_CHANGED;
                }
                result = warp.orElseThrow().request(player, context, targets().get(selected));
                displayed = result.quote().orElse(null);
                token = result.code() == StationManagementCode.WARP_ISSUED ? nextToken() : 0L;
            }
            case BUTTON_CONFIRM -> {
                if (expected.isEmpty()) {
                    return StationManagementCode.NOT_IN_STATION;
                }
                result = warp.orElseThrow().confirm(player, context, expected.orElseThrow().stationId());
            }
            case BUTTON_CANCEL -> result = warp.orElseThrow().cancel(player, context);
            default -> {
                return StationManagementCode.AUTHORITY_UNAVAILABLE;
            }
        }
        return result.code();
    }

    /** A fresh positive token; it only names a quote shown by this menu and grants nothing by itself. */
    private static long nextToken() {
        long next;
        do {
            next = TOKENS.nextLong() & Long.MAX_VALUE;
        } while (next == 0L);
        return next;
    }
}
