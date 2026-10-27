package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.TerminalActionTable;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-074 items 6 and 7, design section 5.2: the terminal's acknowledged effects. A launch, decommission or data action
 * changes the registry in RAM (the service makes a prompt request) and is recorded as a pending entry; the package is
 * extracted, the chip blanked or the stored result shown only once a published acknowledgement covers the change.
 * Every refusal happens before the registry mutation.
 */
final class TerminalRegistryActions {
    private TerminalRegistryActions() { }

    static TerminalActionTable.Key key(SatelliteTerminalBlockEntity terminal) {
        return new TerminalActionTable.Key(terminal.getLevel().dimension(), terminal.getBlockPos());
    }

    /** Null when this terminal may act for {@code satellite} now; otherwise the refusal, before any mutation. */
    static SatelliteOperationCode admit(SatelliteTerminalBlockEntity terminal, UUID satellite) {
        return table().map(actions -> actions.admission(key(terminal), satellite)).orElse(null);
    }

    /** Resource delivery and unlink keep their own persistence semantics, but cannot bypass a pending satellite. */
    static SatelliteOperationCode admitted(SatelliteTerminalBlockEntity terminal, SatelliteIdentity chip,
                                           java.util.function.Supplier<SatelliteOperationCode> action) {
        SatelliteOperationCode refused = admit(terminal, chip.satelliteId());
        return refused != null ? refused : action.get();
    }

    /** Whether any action of this terminal waits; its state-changing buttons are refused meanwhile. */
    static boolean pending(SatelliteTerminalBlockEntity terminal) {
        return server(terminal) && table().flatMap(actions -> actions.at(key(terminal))).isPresent();
    }

    /** Whether the package and chip slots are locked by a waiting launch or decommission. */
    static boolean locked(SatelliteTerminalBlockEntity terminal) {
        return server(terminal) && table().map(actions -> actions.locked(key(terminal))).orElse(false);
    }

    /** The RAM result a waiting entry will show, for a chunk save of this terminal. */
    static Optional<SatelliteOperationCode> pendingResult(SatelliteTerminalBlockEntity terminal) {
        if (!server(terminal)) { return Optional.empty(); }
        return table().flatMap(actions -> actions.at(key(terminal))).map(TerminalActionTable.Entry::result);
    }

    /** After the RAM launch: the package leaves at once only when the acknowledgement already proves it (a clean replay). */
    static SatelliteOperationCode launched(SatelliteTerminalBlockEntity terminal, ServerPlayer player,
                                           SatelliteIdentity satellitePackage, SatelliteOperationResult result) {
        if (!result.success()) { return result.code(); }
        var data = SatelliteMissionSavedData.get(player.getServer());
        var watch = data.watch(satellitePackage.satelliteId());
        if (data.proves(watch)) {
            terminal.extractLaunchedPackage();
            return result.code();
        }
        table().orElseThrow().launch(key(terminal), satellitePackage, watch, result.code(), player.getUUID());
        return SatelliteOperationCode.REGISTRY_PENDING;
    }

    /** After the RAM removal: the chip is blanked once a commit covers the removal token. */
    static SatelliteOperationCode decommissioned(SatelliteTerminalBlockEntity terminal, ServerPlayer player,
                                                 SatelliteIdentity chip, SatelliteOperationResult result) {
        if (!result.success()) { return result.code(); }
        var token = SatelliteMissionSavedData.get(player.getServer()).token();
        table().orElseThrow().decommission(key(terminal), chip, token, result.code(), player.getUUID());
        return SatelliteOperationCode.REGISTRY_PENDING;
    }

    /**
     * ADR-074 item 7: the matching effect of a covered entry. It returns false, a diagnostic hold that keeps the lock,
     * when the slot no longer holds the same package or chip; a data entry only delivers its stored result.
     */
    static boolean complete(SatelliteTerminalBlockEntity terminal, TerminalActionTable.Entry entry) {
        var inventory = terminal.inventory();
        switch (entry.kind()) {
            case LAUNCH -> {
                var stack = inventory.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE);
                if (terminal.blocked() || !SatelliteTerminalInventory.validPackage(stack)
                        || !SatelliteItemData.read(stack).identity().filter(entry.identity()::equals).isPresent()) {
                    return false;
                }
                inventory.extractOwned(SatelliteTerminalBlockEntity.SLOT_PACKAGE);
            }
            case DECOMMISSION -> {
                if (terminal.blocked() || !terminal.chipIdentity().filter(entry.identity()::equals).isPresent()) {
                    return false;
                }
                inventory.setStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP,
                        new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()));
            }
            case DATA -> { }
        }
        terminal.delivered(entry.result(), entry.player());
        return true;
    }

    /**
     * After a data start, claim or cancel: a changed RAM result is stored with the token fixed now, and shown once a
     * commit covers that token; later polls never replace it with a moving sequence. No item is locked.
     */
    static SatelliteOperationCode dataAction(SatelliteTerminalBlockEntity terminal, ServerPlayer player,
                                             SatelliteIdentity chip, SatelliteOperationResult result) {
        if (!result.changed()) { return result.code(); }
        var token = SatelliteMissionSavedData.get(player.getServer()).token();
        table().orElseThrow().data(key(terminal), chip, token, result.code(), player.getUUID());
        return SatelliteOperationCode.REGISTRY_PENDING;
    }

    private static Optional<TerminalActionTable> table() { return SatelliteRuntime.terminalActions(); }

    private static boolean server(SatelliteTerminalBlockEntity terminal) {
        return terminal.getLevel() != null && !terminal.getLevel().isClientSide;
    }
}
