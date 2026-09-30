package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket.Selection;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Composes and paces the ADR-049 section 10 terminal view for one open menu: sent on open and on change,
 * at most once per 5 ticks. Resource-mission sections stay empty until their missions exist (ADR-051).
 */
public final class SatelliteTerminalViews {
    public static final int SEND_INTERVAL_TICKS = 5;

    private final ServerPlayer player;
    private final int containerId;
    private final BiConsumer<ServerPlayer, SatelliteTerminalViewPacket> sender;
    private SatelliteTerminalViewPacket lastSent;
    private int lastComposeTick = Integer.MIN_VALUE;

    SatelliteTerminalViews(ServerPlayer player, int containerId) {
        this(player, containerId, SatelliteNetwork::sendTerminalView);
    }

    public SatelliteTerminalViews(ServerPlayer player, int containerId,
                                  BiConsumer<ServerPlayer, SatelliteTerminalViewPacket> sender) {
        this.player = player;
        this.containerId = containerId;
        this.sender = sender;
    }

    /**
     * Called from every change broadcast. C7-M2: vanilla also broadcasts after each button and slot click, so the
     * pace is measured in server ticks: at most one composition, and one send, per 5 ticks, whatever the clicks.
     */
    public void tick(SatelliteTerminalBlockEntity terminal) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        int now = server.getTickCount();
        if (lastComposeTick != Integer.MIN_VALUE && now - lastComposeTick < SEND_INTERVAL_TICKS) {
            return;
        }
        lastComposeTick = now;
        SatelliteTerminalViewPacket view = compose(terminal, containerId, server);
        if (!view.equals(lastSent)) {
            sender.accept(player, view);
            lastSent = view;
        }
    }

    /** The server-selected view of one terminal for the container that displays it. */
    public static SatelliteTerminalViewPacket compose(SatelliteTerminalBlockEntity terminal, int containerId,
                                                      MinecraftServer server) {
        ResourceLocation definitionId = terminal.selectedDefinition();
        if (definitionId == null) {
            return SatelliteTerminalViewPacket.empty(containerId);
        }
        Optional<SatelliteIdentity> chip = terminal.chipIdentity();
        SatelliteKind kind = chip.map(SatelliteIdentity::kind).orElse(SatelliteKind.DATA);
        Selection definition = definitionSelection(definitionId, kind, SatelliteRuntime.catalog());
        Selection target = Selection.of(terminal.targetList(), terminal.selectedTargetIndex());
        Optional<ResourceLocation> orbitBody = server == null ? Optional.empty()
                : chip.flatMap(identity -> SatelliteRuntime.satellite(server, identity.satelliteId()))
                        .flatMap(SatelliteState::orbitBody);
        return new SatelliteTerminalViewPacket(containerId, Optional.of(kind), definition, target, orbitBody,
                Selection.NONE, Optional.empty(), Selection.NONE, 0, 0, List.of(), List.of());
    }

    private static Selection definitionSelection(ResourceLocation id, SatelliteKind kind,
                                                 Optional<SatelliteCatalog> catalog) {
        if (catalog.isEmpty()) {
            return Selection.NONE;
        }
        List<ResourceLocation> ids = kind == SatelliteKind.DATA
                ? catalog.orElseThrow().definitions().stream().map(SatelliteDefinition::id).toList()
                : catalog.orElseThrow().kindDefinitions().stream().map(SatelliteKindDefinition::id).toList();
        int index = ids.indexOf(id);
        return index < 0 ? Selection.NONE : new Selection(Optional.of(id), index, ids.size());
    }
}
