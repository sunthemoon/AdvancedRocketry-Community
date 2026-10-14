package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationCheckedWriter;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/**
 * The two source roots as the delivery service sees them (W44 section 3.3): their tagged durable views and the one
 * write each source performs for delivery. The world's roots are re-read from vanilla's storage on every call; the A1
 * GameTest fixtures pass detached roots bound to their own files, so a shared election cannot steal their slot.
 */
public interface FirstEventSources {
    FirstEventSourceView view(FirstEventKind kind);

    /** ELECTED(E) to DELIVERED(E) in journal RAM, then one capture through the existing writer; false if refused. */
    boolean requestMoonDelivered(FirstEvent event);

    /** The root-only checked acknowledgement of the station registry (W44 section 1.3). */
    StationRegistrySavedData.CheckedUpdate acknowledgeWarp(FirstEvent event, FirstEvent receipt,
                                                           StationCheckedWriter.Diagnostics log);

    int stationRecords();

    ManagedRootPresence stationPresence();

    ManagedRootPresence journalPresence();

    /** The world's roots, obtained through their root-presence suppliers on every call. */
    static FirstEventSources world(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return new FirstEventSources() {
            @Override public FirstEventSourceView view(FirstEventKind kind) {
                return kind == FirstEventKind.FIRST_MOON_LANDING
                        ? RocketTransferSavedData.get(server).durableFirstMoonLanding()
                        : StationRegistrySavedData.get(server).durableFirstWarp();
            }

            @Override public boolean requestMoonDelivered(FirstEvent event) {
                RocketTransferSavedData journal = RocketTransferSavedData.get(server);
                if (!journal.deliverFirstMoonLanding(event)) {
                    return false;
                }
                journal.flushChecked(server);
                return true;
            }

            @Override public StationRegistrySavedData.CheckedUpdate acknowledgeWarp(FirstEvent event, FirstEvent receipt,
                                                                             StationCheckedWriter.Diagnostics log) {
                return StationRegistrySavedData.get(server).checkedDeliverFirstWarp(server, event, receipt, log);
            }

            @Override public int stationRecords() {
                return StationRegistrySavedData.get(server).recordCount();
            }

            @Override public ManagedRootPresence stationPresence() {
                return StationRegistrySavedData.get(server).presence();
            }

            @Override public ManagedRootPresence journalPresence() {
                return RocketTransferSavedData.get(server).presence();
            }
        };
    }
}
