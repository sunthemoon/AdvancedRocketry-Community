package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * {@code warp_controller} (ADR-065 section 5.1): the warp service's quote/request, confirm, cancel and status
 * actions through the shared context validator. The target is an index into the server's own list sent at
 * opening; costs, balances and checks stay on the server, and this menu stores no energy.
 */
public final class WarpControllerMenu extends StationControllerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_REQUEST = 2;
    public static final int BUTTON_CONFIRM = 3;
    public static final int BUTTON_CANCEL = 4;
    private static final int SELECTED = 0;
    private static final int BALANCE = 1;
    private static final int COST = 3;
    private static final int COST_CLASS = 5;
    private static final int COUNTDOWN = 6;
    private static final int PENDING = 7;
    private static final int ORBIT = 9;
    private static final List<Button> BUTTONS = List.of(
            new Button(BUTTON_PREVIOUS, KEY + "previous", 8, 20, 20), new Button(BUTTON_NEXT, KEY + "next", 148, 20, 20),
            new Button(BUTTON_REQUEST, KEY + "request", 8, 38, 52), new Button(BUTTON_CONFIRM, KEY + "confirm", 62, 38, 52),
            new Button(BUTTON_CANCEL, KEY + "cancel", 116, 38, 52));

    /** Server: the selected target index into {@code names().subList(1, ...)}, and the last quote shown. */
    private int selected;
    private WarpQuote lastQuote;

    WarpControllerMenu(int id, Level level, BlockPos position, Block block, OpenData open) {
        super(ModMenuTypes.WARP_CONTROLLER.get(), StationControllerKind.WARP, id, level, position, block, open, 10);
    }

    @Override
    public List<Button> buttons() {
        return BUTTONS;
    }

    private List<net.minecraft.resources.ResourceLocation> targets() {
        return names().size() <= 1 ? List.of() : names().subList(1, names().size());
    }

    @Override
    protected boolean navigate(int button) {
        if (button != BUTTON_PREVIOUS && button != BUTTON_NEXT) {
            return false;
        }
        int count = targets().size();
        if (count > 0) {
            selected = Math.floorMod(selected + (button == BUTTON_NEXT ? 1 : -1), count);
            lastQuote = null;
        }
        return true;
    }

    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        int orbit = value(ORBIT);
        lines.add(Component.translatable(KEY + "orbit", orbit >= 0 && orbit < names().size()
                ? Component.literal(names().get(orbit).toString()) : Component.translatable(KEY + "unknown")));
        int index = value(SELECTED);
        lines.add(index >= 0 && index < targets().size()
                ? Component.translatable(KEY + "target", Component.literal(targets().get(index).toString()))
                : Component.translatable(KEY + "no_target"));
        lines.add(Component.translatable(KEY + "balance", wide(BALANCE), wide(PENDING)));
        int costClass = value(COST_CLASS);
        if (costClass > 0) {
            lines.add(Component.translatable(KEY + "cost", wide(COST),
                    Component.translatable(KEY + "class." + (costClass == 1 ? "in_system" : "interstellar"))));
        }
        int countdown = value(COUNTDOWN);
        if (countdown >= 0) {
            lines.add(Component.translatable(KEY + "countdown", countdown));
        }
        return lines;
    }

    @Override
    protected int serverData(int index, StationState station) {
        if (index == SELECTED) {
            return targets().isEmpty() ? -1 : selected;
        }
        if (station == null) {
            return index == COUNTDOWN || index == ORBIT ? -1 : 0;
        }
        StationRegistrySavedData registry = StationRegistrySavedData.get(level().getServer());
        Optional<StationWarpService> warp = StationWarpRuntime.service();
        return switch (index) {
            case BALANCE -> low(registry.warpEnergy(station.stationId()));
            case BALANCE + 1 -> high(registry.warpEnergy(station.stationId()));
            case COST -> lastQuote == null ? 0 : low(lastQuote.cost());
            case COST + 1 -> lastQuote == null ? 0 : high(lastQuote.cost());
            case COST_CLASS -> lastQuote == null ? 0 : lastQuote.costClass() == WarpCostClass.IN_SYSTEM ? 1 : 2;
            case COUNTDOWN -> warp.flatMap(service -> service.countdown(station.stationId()))
                    .map(running -> (int) Math.min(Short.MAX_VALUE,
                            (running.ticksLeft(level().getServer().getTickCount()) + 19L) / 20L)).orElse(-1);
            case PENDING -> low(warp.map(service -> service.pendingCredit(station.stationId())).orElse(0));
            case PENDING + 1 -> high(warp.map(service -> service.pendingCredit(station.stationId())).orElse(0));
            case ORBIT -> names().indexOf(station.orbitBody());
            default -> 0;
        };
    }

    @Override
    protected StationManagementCode apply(int button, ServerPlayer player, StationManager stations,
                                          StationActorContext context, Optional<StationState> expected) {
        Optional<StationWarpService> warp = StationWarpRuntime.service();
        if (warp.isEmpty()) {
            return StationManagementCode.AUTHORITY_UNAVAILABLE;
        }
        StationWarpResult result;
        switch (button) {
            case BUTTON_REQUEST -> {
                if (targets().isEmpty()) {
                    return StationManagementCode.WARP_TARGET_UNAVAILABLE;
                }
                result = warp.orElseThrow().request(player, context, targets().get(selected));
                lastQuote = result.quote().orElse(null);
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
}
