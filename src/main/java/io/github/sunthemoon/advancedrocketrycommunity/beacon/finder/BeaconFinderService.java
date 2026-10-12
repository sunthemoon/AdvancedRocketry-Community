package io.github.sunthemoon.advancedrocketrycommunity.beacon.finder;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconServices;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconWorld;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconMarkerSelection;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconIndexSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * The finder's private marker stream (ADR-065 section 7). A connected, living, non-spectator player is eligible while
 * a beacon finder is held in either hand, or while the server equipment summary of the correctly worn built-in helmet
 * reports {@code beaconFinderEnabled}; both together still give one stream. Each player is evaluated at most once per
 * {@link BeaconLimits#UPDATE_INTERVAL_TICKS} and sent a snapshot only when it differs from the last one sent, so at
 * most one snapshot per player per interval. An eligible player's snapshot holds only that player's own beacons, last
 * known active on the player's server-resolved body and Level, nearest first, at most eight. Building it removes the
 * player's own records that are provably stale at a loaded position. A player who stops being eligible is sent one
 * empty snapshot that clears the markers. The per-player state is runtime only, bounded by the players online and
 * forgotten at logout.
 */
public final class BeaconFinderService {
    /** The network adapter; the server tick and GameTests supply their own. */
    @FunctionalInterface
    public interface Sender {
        void send(ServerPlayer player, BeaconMarkerSnapshotPacket snapshot);
    }

    private static final class PlayerState {
        private long nextEvaluation = Long.MIN_VALUE;
        /** What the client shows; {@code null} when it must be sent again whatever it is. */
        private BeaconMarkerSnapshotPacket shown = BeaconMarkerSnapshotPacket.EMPTY;
    }

    private final Sender sender;
    private final BeaconServices services;
    private final Map<UUID, PlayerState> players = new HashMap<>();

    public BeaconFinderService(Sender sender, BeaconServices services) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.services = Objects.requireNonNull(services, "services");
    }

    /** One server tick: every online player whose interval has elapsed is evaluated. */
    public void tick(MinecraftServer server, long now) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            evaluate(player, now);
        }
    }

    /** Evaluates one player if its interval has elapsed; returns the snapshot sent, if any. */
    public Optional<BeaconMarkerSnapshotPacket> evaluate(ServerPlayer player, long now) {
        if (player instanceof FakePlayer) {
            return Optional.empty();
        }
        PlayerState state = players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        if (now < state.nextEvaluation) {
            return Optional.empty();
        }
        state.nextEvaluation = now + BeaconLimits.UPDATE_INTERVAL_TICKS;
        BeaconMarkerSnapshotPacket snapshot;
        if (eligible(player)) {
            snapshot = snapshot(player);
        } else if (state.shown == null || !state.shown.markers().isEmpty()) {
            snapshot = BeaconMarkerSnapshotPacket.EMPTY;
        } else {
            return Optional.empty();
        }
        if (snapshot.equals(state.shown)) {
            return Optional.empty();
        }
        state.shown = snapshot;
        sender.send(player, snapshot);
        return Optional.of(snapshot);
    }

    /** Held in either hand, or reported by the server equipment summary of the worn helmet. */
    public static boolean eligible(ServerPlayer player) {
        if (player instanceof FakePlayer || player.hasDisconnected() || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        return held(player) || ArmorEquipment.summarize(player).beaconFinderEnabled();
    }

    public static boolean held(ServerPlayer player) {
        return ModItems.BEACON_FINDER.isPresent() && (player.getMainHandItem().is(ModItems.BEACON_FINDER.get())
                || player.getOffhandItem().is(ModItems.BEACON_FINDER.get()));
    }

    /**
     * The player's snapshot now, read from at most the player's own 32 index records; never sent from here. Each of
     * those records whose own position is loaded and provably holds no such beacon ({@link BeaconWorld#stale}) is
     * removed first: its beacon was removed before the index was last saved, while the index was unavailable or while
     * its block-entity data was kept unreadable. An unloaded position proves nothing, and no chunk is loaded.
     */
    public BeaconMarkerSnapshotPacket snapshot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return BeaconMarkerSnapshotPacket.EMPTY;
        }
        BeaconIndexSavedData index = BeaconIndexSavedData.get(server);
        if (!index.operational()) {
            return BeaconMarkerSnapshotPacket.EMPTY;
        }
        List<BeaconRecord> owned = new ArrayList<>(BeaconLimits.MAX_PER_OWNER);
        for (BeaconRecord record : index.ownedBy(player.getUUID())) {
            if (BeaconWorld.stale(server, record) && index.remove(record.beaconId())) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_BEACON_STALE_RECORD: removed the index record of beacon {} "
                        + "at {} {} {} {}, whose loaded position holds no such beacon", record.beaconId(), record.level(),
                        record.x(), record.y(), record.z());
            } else {
                owned.add(record);
            }
        }
        BlockPos position = player.blockPosition();
        Optional<String> body = services.body(server, player.level().dimension(), position);
        if (body.isEmpty()) {
            return BeaconMarkerSnapshotPacket.EMPTY;
        }
        BeaconMarkerSelection.Viewer viewer = new BeaconMarkerSelection.Viewer(player.getUUID(), body.get(),
                player.level().dimension().location().toString(), position.getX(), position.getY(), position.getZ());
        List<BeaconMarkerSnapshotPacket.Marker> markers = new ArrayList<>(BeaconLimits.MAX_MARKERS);
        for (BeaconRecord record : BeaconMarkerSelection.select(owned, viewer)) {
            markers.add(new BeaconMarkerSnapshotPacket.Marker(record.beaconId(), record.x(), record.y(), record.z(),
                    BeaconWorld.live(server, record), record.name()));
        }
        return new BeaconMarkerSnapshotPacket(markers);
    }

    /** The client dropped its markers (a Level change or respawn); the next snapshot is compared with nothing. */
    public void clientCleared(UUID player) {
        PlayerState state = players.get(player);
        if (state != null) {
            state.shown = BeaconMarkerSnapshotPacket.EMPTY;
        }
    }

    /** A data reload: every player's next evaluation sends its snapshot again. */
    public void reloaded() {
        for (PlayerState state : players.values()) {
            if (state.shown != null && !state.shown.markers().isEmpty()) {
                state.shown = null;
            }
        }
    }

    public void forget(UUID player) {
        players.remove(player);
    }

    public int tracked() {
        return players.size();
    }

    public void clear() {
        players.clear();
    }
}
