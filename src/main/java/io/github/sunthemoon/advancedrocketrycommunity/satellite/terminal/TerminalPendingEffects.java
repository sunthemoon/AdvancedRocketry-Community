package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.TerminalActionTable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * ADR-074 item 7, design section 5.2. At tick START, after the registry's publication (HIGHEST), at most
 * {@value TerminalActionTable#MAX_COMPLETIONS_PER_TICK} covered entries complete their effect, at loaded terminals only;
 * no chunk is loaded or ticketed. Coverage alone releases nothing: an entry leaves the table only after its matching
 * effect, and a mismatch keeps a diagnostic hold. A terminal with a waiting launch or decommission refuses player
 * breaking and leaves explosion block lists (as the bay does, R-038). Both shutdown paths discard every entry unrun.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class TerminalPendingEffects {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TerminalPendingEffects() { }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) { return; }
        var table = SatelliteRuntime.terminalActions().orElse(null);
        if (table == null || table.size() == 0) { return; }
        MinecraftServer server = event.getServer();
        if (server.isStopped() || !server.isRunning()) { table.discard(); return; }
        var registry = SatelliteMissionSavedData.existing(server);
        if (registry == null) { return; }
        int completed = 0;
        for (var entry : table.rotation()) {
            if (completed >= TerminalActionTable.MAX_COMPLETIONS_PER_TICK) { break; }
            if (!entry.covered(registry)) { continue; }
            var level = server.getLevel(entry.key().level());
            var pos = entry.key().pos();
            if (level == null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                continue; // Covered but unloaded: the entry and its lock stay.
            }
            if (!(level.getBlockEntity(pos) instanceof SatelliteTerminalBlockEntity terminal) || terminal.isRemoved()) {
                if (entry.kind() == TerminalActionTable.Kind.DATA) { table.completed(entry); completed++; }
                else { hold(table, entry, "TERMINAL_MISSING"); }
                continue;
            }
            if (TerminalRegistryActions.complete(terminal, entry)) { table.completed(entry); completed++; }
            else { hold(table, entry, "SLOT_MISMATCH"); }
        }
    }

    /** {@code ServerStoppingEvent} (normal path); the tick check above covers a thrown tick without this event. */
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        SatelliteRuntime.terminalActions().ifPresent(TerminalActionTable::discard);
    }

    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof SatelliteTerminalBlock && event.getLevel() instanceof Level level
                && locked(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().displayClientMessage(Component.translatable(SatelliteOperationCode.REGISTRY_PENDING
                        .translationKey()), true);
            }
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> level.hasChunkAt(pos)
                && level.getBlockState(pos).getBlock() instanceof SatelliteTerminalBlock && locked(level, pos));
    }

    private static boolean locked(Level level, net.minecraft.core.BlockPos pos) {
        return !level.isClientSide && SatelliteRuntime.terminalActions()
                .map(table -> table.locked(new TerminalActionTable.Key(level.dimension(), pos))).orElse(false);
    }

    private static void hold(TerminalActionTable table, TerminalActionTable.Entry entry, String reason) {
        if (table.hold(entry)) {
            LOGGER.warn("ARCE_SATELLITE_TERMINAL_HOLD level={} pos={} kind={} satellite={} reason={}",
                    entry.key().level().location(), entry.key().pos().toShortString(), entry.kind(),
                    entry.identity().satelliteId(), reason);
        }
    }
}
