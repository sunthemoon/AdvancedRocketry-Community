package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Read-only F7/F8 writer boundaries; journal replacement uses R200's existing observer. */
final class EscrowLoaderS6CleanupProbe {
    private static final Set<String> CASES = Set.of("LF_S6_F7_BEFORE", "LF_S6_F7_REPLACED", "LF_S6_F7_ACK",
            "LF_S6_F8_JOURNAL_BEFORE", "LF_S6_F8_JOURNAL_REPLACED", "LF_S6_F8_JOURNAL_ACK",
            "LF_S6_F8_PRUNE_BEFORE", "LF_S6_F8_PRUNE_REPLACED", "LF_S6_F8_PRUNE_ACK");
    private final String cut;
    private final Path ledger;

    EscrowLoaderS6CleanupProbe(MinecraftServer server, String cut) {
        this.cut = cut;
        ledger = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
    }
    static boolean selected(String cut) { return cut != null && CASES.contains(cut); }
    static boolean acknowledgement(String cut, String point) {
        return "LF_S6_F7_ACK".equals(cut) && "F7_ACK".equals(point)
                || "LF_S6_F8_JOURNAL_ACK".equals(cut) && "F8_JOURNAL_ACK".equals(point)
                || "LF_S6_F8_PRUNE_ACK".equals(cut) && "F8_PRUNE_ACK".equals(point);
    }
    static boolean ledgerBoundary(String cut, String point, CompoundTag payload) {
        if (!selected(cut) || cut.contains("_JOURNAL_")) { return false; }
        boolean before = point.equals("K1") || point.equals("K8");
        boolean after = point.equals("K2") || point.equals("K9");
        if (!(before && cut.endsWith("_BEFORE") || after && cut.endsWith("_REPLACED"))) { return false; }
        var root = EscrowCodec.decode(payload);
        if (root.issuedThrough() != 1 || root.issuedLaunchSerial() != 1 || !root.records().isEmpty()
                || !root.floors().isEmpty() || !root.tombstones().isEmpty() || !root.rocketAuthorities().isEmpty()) { return false; }
        return cut.startsWith("LF_S6_F7_") ? root.writerGeneration() == 5 && root.armedFlights().size() == 1
                && root.armedFlights().get(0).outcome() == EscrowFlightTables.Outcome.HANDED_OFF
                : root.writerGeneration() == 6 && root.armedFlights().isEmpty();
    }

    void checkpoint(String point) {
        if (acknowledgement(cut, point)) {
            if (!cut.contains("_JOURNAL_")) { observe(CheckedSavedDataFile.readPayload(ledger, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()); }
            EscrowLoaderS6JournalProbe.pause(cut, point);
        }
        if (cut.contains("_JOURNAL_") || !(point.equals("K1") || point.equals("K2") || point.equals("K8") || point.equals("K9"))) { return; }
        boolean before = point.equals("K1") || point.equals("K8");
        var path = before ? ledger.resolveSibling(ledger.getFileName() + ".arce-pending") : ledger;
        var payload = CheckedSavedDataFile.readPayload(path, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow();
        if (ledgerBoundary(cut, point, payload)) { observe(payload); EscrowLoaderS6JournalProbe.pause(cut, point); }
    }

    private void observe(CompoundTag payload) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_LEDGER cut={} nbt={}", cut, EscrowLoaderS6TerminalProbe.encode(payload));
    }
}
