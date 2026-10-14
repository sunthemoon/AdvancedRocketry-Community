package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.ClassicFirstEventDelivery;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.ClassicProgressionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDispositionSource;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventSourceView;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventSources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationCheckedWriter;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationFirstWarpGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/**
 * The isolated A1 world-first roots (W44 section 10): a detached journal, station registry and progression store,
 * each bound to its own file in a temporary directory, and a detached delivery service over them. Shared GameTest
 * elections cannot steal this slot, and nothing here touches the world's roots or files.
 */
public final class DetachedFirstEventRoots implements FirstEventSources, AutoCloseable {
    public final GameTestHelper helper;
    public final MinecraftServer server;
    public final Path directory;
    public final RocketTransferSavedData journal = new RocketTransferSavedData();
    public final StationRegistrySavedData stations;
    public final ClassicProgressionSavedData progression;
    public final ClassicFirstEventDelivery delivery;

    public DetachedFirstEventRoots(GameTestHelper helper, StationRegistrySavedData stations,
                                   ClassicProgressionSavedData progression) {
        this.helper = helper;
        this.server = helper.getLevel().getServer();
        this.stations = stations;
        this.progression = progression;
        try {
            directory = Files.createTempDirectory("arce-w44-a1-");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        RocketJournalGameTestSupport.confirmDetached(helper, journal, file(ManagedSavedDataType.ROCKET_TRANSFERS));
        CheckedSavedDataFile.replace(file(ManagedSavedDataType.STATIONS), ManagedSavedDataType.STATIONS,
                () -> stations.save(new CompoundTag()));
        delivery = ClassicFirstEventDelivery.detached(server, this, progression,
                file(ManagedSavedDataType.CLASSIC_PROGRESSION));
    }

    /**
     * A copy of the world's station registry with its own EMPTY first-warp slot, and an absent progression store. The
     * copy is a fixture: a shared election in the world's registry never decides this slot.
     */
    public static DetachedFirstEventRoots fromWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CompoundTag copy = StationRegistrySavedData.get(server).save(new CompoundTag());
        copy.put(ManagedSavedDataType.FIRST_STATION_WARP_KEY, io.github.sunthemoon.advancedrocketrycommunity.progression
                .classic.firstevent.FirstEventOutbox.empty().encode(FirstEventKind.FIRST_STATION_WARP));
        return new DetachedFirstEventRoots(helper, StationRegistrySavedData.load(copy),
                ClassicProgressionSavedData.provenAbsent());
    }

    public Path file(ManagedSavedDataType type) {
        return directory.resolve(type.fileName());
    }

    /** The detached delivery's dispositions, for the source under test. */
    public FirstEventDispositionSource dispositions() {
        return (ignored, kind, view, ram) -> delivery.disposition(kind, view, ram);
    }

    /** One synchronous writer generation of the detached journal and its continuations. */
    public void confirmJournal() {
        RocketJournalGameTestSupport.confirmDetached(helper, journal, file(ManagedSavedDataType.ROCKET_TRANSFERS));
    }

    @Override public FirstEventSourceView view(FirstEventKind kind) {
        return kind == FirstEventKind.FIRST_MOON_LANDING ? journal.durableFirstMoonLanding() : stations.durableFirstWarp();
    }

    @Override public boolean requestMoonDelivered(FirstEvent event) {
        if (!journal.deliverFirstMoonLanding(event)) {
            return false;
        }
        confirmJournal();
        return true;
    }

    @Override public StationRegistrySavedData.CheckedUpdate acknowledgeWarp(FirstEvent event, FirstEvent receipt,
                                                                     StationCheckedWriter.Diagnostics log) {
        return StationFirstWarpGameTestSupport.acknowledge(server, stations, file(ManagedSavedDataType.STATIONS), event,
                receipt, log);
    }

    @Override public int stationRecords() {
        return stations.recordCount();
    }

    @Override public ManagedRootPresence stationPresence() {
        return stations.presence();
    }

    @Override public ManagedRootPresence journalPresence() {
        return journal.presence();
    }

    /** The progression file's receipt of {@code kind}, as a restart would read it. */
    public java.util.Optional<FirstEvent> receiptOnDisk(FirstEventKind kind) {
        return CheckedSavedDataFile.readPayload(file(ManagedSavedDataType.CLASSIC_PROGRESSION),
                ManagedSavedDataType.CLASSIC_PROGRESSION).flatMap(payload -> ClassicProgressionSavedData.load(payload)
                .receipt(kind));
    }

    @Override public void close() {
        journal.abort();
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A temporary fixture directory only; the world's files were never touched.
        }
    }
}
