package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;

/** Fixed isolated negative fixtures, exported beside an owned native world; never modifies that world's roots. */
public final class FirstEventCaseExports {
    private FirstEventCaseExports() { }
    static final String JOURNAL = RocketTransferSavedData.DATA_NAME + ".dat";
    static final String STATIONS = StationRegistrySavedData.DATA_NAME + ".dat";
    static final String PROGRESSION = ClassicProgressionSavedData.DATA_NAME + ".dat";

    public static void write(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        if (FirstEventCutSeams.configured().orElseThrow() != FirstEventCutSeams.Cut.FW9) { throw new IllegalStateException("Export fixture is not armed"); }
        var journal = RocketTransferSavedData.get(server);
        var stations = StationRegistrySavedData.get(server);
        if (!journal.clean() || journal.firstMoonLandingRam().state() != FirstEventOutbox.State.DELIVERED
                || stations.firstWarpRam().state() != FirstEventOutbox.State.DELIVERED || journal.entries().size() != 2) {
            throw new IllegalStateException("The complete paired seed has not settled");
        }
        Path home = server.getServerDirectory().toPath().toAbsolutePath().normalize();
        Path output = home.resolve("w44l-case-roots");
        if (Files.exists(output)) { throw new IllegalStateException("The case export already exists"); }
        try {
            CompoundTag preWarp = NbtIo.readCompressed(home.resolve("w44l-before-warp.dat").toFile()).getCompound("data");
            for (var fixture : fixtures(journal.save(new CompoundTag()), stations.save(new CompoundTag()), preWarp).entrySet()) {
                Path directory = Files.createDirectories(output.resolve(fixture.getKey()));
                for (var root : fixture.getValue().entrySet()) {
                    CompoundTag outer = new CompoundTag();
                    outer.put("data", root.getValue());
                    NbtUtils.addCurrentDataVersion(outer);
                    NbtIo.writeCompressed(outer, directory.resolve(root.getKey()).toFile());
                }
                if (fixture.getKey().equals("FP2")) { Files.writeString(directory.resolve("remove-progression.txt"), "Isolated missing-receipt fixture.\n"); }
            }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture cases_exported count=9");
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** Pure production-codec encodings; the native runner overlays these on copies of the complete seed world. */
    static Map<String, Map<String, CompoundTag>> fixtures(CompoundTag moon, CompoundTag station, CompoundTag preWarp) {
        var event = (FirstEvent.MoonLanding) RocketTransferSavedData.load(moon.copy()).firstMoonLandingRam().event();
        var warp = (FirstEvent.StationWarp) StationRegistrySavedData.load(station.copy()).firstWarpRam().event();
        if (event == null || warp == null || !StationRegistrySavedData.load(preWarp.copy()).firstWarpRam().isEmpty()) {
            throw new IllegalArgumentException("Case exports require valid settled paired authority and its pre-warp root");
        }
        var different = FirstEvent.MoonLanding.create(UUID.randomUUID(), event.transferId(), event.logicalRocketId(),
                event.ownerId(), event.terminalRocketId(), event.recordChecksum(), event.originX(), event.originY(),
                event.originZ(), event.completedAtGameTime(), event.participants());
        CompoundTag elected = moon.copy();
        elected.put(RocketTransferSavedData.FIRST_MOON_LANDING, FirstEventOutbox.elected(event).encode(event.kind()));
        var result = new LinkedHashMap<String, Map<String, CompoundTag>>();
        result.put("FP2", Map.of(JOURNAL, moon.copy(), STATIONS, station.copy()));
        result.put("FP3", Map.of(JOURNAL, moon.copy(), STATIONS, station.copy(), PROGRESSION, receipts(different, warp)));
        result.put("FP4", Map.of(JOURNAL, moon.copy(), STATIONS, preWarp.copy(), PROGRESSION, receipts(event, warp)));
        result.put("FP7b", Map.of(JOURNAL, elected.copy(), STATIONS, station.copy(), PROGRESSION, receipts(event, warp)));
        result.put("FP9", Map.of(JOURNAL, elected.copy(), STATIONS, station.copy(), PROGRESSION, receipts(null, warp)));
        result.put("FP10", Map.of(JOURNAL, moon.copy(), STATIONS, station.copy(), PROGRESSION, receipts(event, warp)));
        result.put("FP11", Map.of(JOURNAL, moon.copy(), STATIONS, station.copy(), PROGRESSION, receipts(event, warp)));
        result.put("FP8", Map.of(JOURNAL, moon.copy(), STATIONS, preWarp.copy(), PROGRESSION, receipts(event, null)));
        result.put("FW8a", Map.of(JOURNAL, moon.copy(), STATIONS, FirstEventBudgetFixture.nearBound(preWarp, warp),
                PROGRESSION, receipts(event, null)));
        return result;
    }

    private static CompoundTag receipts(FirstEvent moon, FirstEvent warp) {
        var root = ClassicProgressionSavedData.provenAbsent();
        if (moon != null) { root.insert(moon); }
        if (warp != null) { root.insert(warp); }
        return root.save(new CompoundTag());
    }
}
