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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Composes and paces the ADR-049 section 10 terminal view for one open menu: sent on open and on change,
 * at most once per 5 ticks. Resource-mission sections stay empty until their missions exist (ADR-051).
 */
public final class SatelliteTerminalViews {
    static final int SEND_INTERVAL_TICKS = 5;

    private final ServerPlayer player;
    private final int containerId;
    private SatelliteTerminalViewPacket lastSent;
    private int cooldown;

    SatelliteTerminalViews(ServerPlayer player, int containerId) {
        this.player = player;
        this.containerId = containerId;
    }

    /** Called from the menu's per-tick change broadcast; composes at most once per 5 ticks. */
    void tick(SatelliteTerminalBlockEntity terminal) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = SEND_INTERVAL_TICKS - 1;
        SatelliteTerminalViewPacket view = compose(terminal, containerId, player.getServer());
        if (!view.equals(lastSent)) {
            SatelliteNetwork.sendTerminalView(player, view);
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
