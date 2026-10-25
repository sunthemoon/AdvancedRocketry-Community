package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Explicit disposable pressure fixtures, not gameplay admission or manufactured proof for the command's batch. */
public final class RocketRejoinNativePressure {
    private static final UUID PLAYER = UUID.fromString("01580000-0000-4000-8000-000000000002");
    private static final UUID FIRST = UUID.fromString("01580000-0000-4000-8000-000000000001");
    private static final String PADDING = "w160f_native_padding";
    private final MinecraftServer server;
    private final String cut, mode;
    private final List<ServerPlayer> players = new ArrayList<>();
    private RocketRejoinRoot frozen;
    private boolean setup, connected, capacity, unsafe, oversized;
    private long oversizedBytes;
    private ServerPlayer first;

    public RocketRejoinNativePressure(MinecraftServer server, String cut, String mode) {
        RocketReleaseNativeCuts.validate(server); this.server = server; this.cut = cut; this.mode = mode;
        if (!List.of("C28", "C29", "C30", "C33").contains(cut)) { throw new IllegalArgumentException("Unknown pressure row"); }
    }
    public boolean advance(RocketRejoinSavedData data) {
        if (!setup) {
            if (mode.equals("seed") && List.of("C28", "C30").contains(cut)) {
                frozen = pressureRoot(data.root(), cut.equals("C28"));
                require(data.submit(frozen), "Native pressure root refused");
            } else { frozen = data.root(); }
            if (cut.equals("C30")) { hazards(frozen.batches().get(0)); }
            setup = true; return false;
        }
        // A restart may load an already-installed root without submitting any new generation.
        if (!data.root().equals(data.acknowledgedRoot()) || !data.pendingRoots().isEmpty()) { return false; }
        if (!connected) {
            var ids = cut.equals("C30") ? frozen.entries().stream().map(Entry::player).distinct().filter(id -> !id.equals(FIRST)).toList() : List.of(PLAYER);
            for (var id : ids) {
                var p = ReleaseTestPlayers.login(server, id, id.equals(PLAYER) ? "W160FSecond" : "W160FQueue", server.overworld());
                p.setHealth(1); players.add(p);
            }
            if (cut.equals("C33") && mode.equals("seed")) { padding(players.get(0), 65 << 20); }
            connected = true; return false;
        }
        if (cut.equals("C33")) { return largeFile(data); }
        require(data.root().equals(frozen) && data.acknowledgedRoot().equals(frozen), "Conflict/queue wait mutated a retained obligation");
        boolean allDisconnected = true;
        for (var player : players) {
            var s = data.guards(server).session(player.getUUID());
            require(s != null && player.isAlive() && player.getHealth() == 1 && player.getVehicle() == null,
                    "Native pressure exposed a passenger");
            capacity |= s.reason.equals("capacity"); unsafe |= s.reason.equals("unsafe");
            if (cut.equals("C28") || cut.equals("C29")) { require(s.reason.equals("conflict"), "Conflicting history was selected instead of refused"); }
            allDisconnected &= s.disconnected;
            if (s.disconnected) { require(s.disconnectedAt == s.deadline && player.position().equals(s.position), "Protected timeout moved or exceeded 200 ticks"); }
        }
        if (!allDisconnected) { return false; }
        if (cut.equals("C30")) { require(capacity && unsafe && players.size() == 130, "Queue/hazard fixture did not exercise both limits"); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W160F_PRESSURE case={} mode={} players={} capacity={} unsafe={} retained={} timeout_ticks=200",
                cut, mode, players.size(), capacity, unsafe, frozen.entries().size());
        return true;
    }
    private boolean largeFile(RocketRejoinSavedData data) {
        var p = players.get(0); var session = data.guards(server).session(PLAYER);
        if (mode.equals("seed") && !oversized) {
            require(session != null && !session.disconnected && data.root().entries().stream().anyMatch(e -> e.player().equals(PLAYER)
                    && e.placement() == Placement.PENDING), "Oversized file lost its pending obligation");
            if (!session.reason.equals("oversized")) { return false; }
            try { oversizedBytes = Files.size(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(PLAYER + ".dat")); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            require(oversizedBytes > (64L << 20), "Disk ceiling was not crossed by the vanilla writer");
            oversized = true;
            padding(p, 3 << 20);
            server.getPlayerList().saveAll(); // Ordinary online save; no offline writer or synthetic save event.
            AdvancedRocketryCommunity.LOGGER.info("ARCE_W160F_DISK_CEILING bytes={} ceiling={} pending=true", oversizedBytes, 64L << 20);
        }
        if (data.root().entries().stream().anyMatch(e -> e.player().equals(PLAYER)) || RocketRejoinProtection.guarded(p)) { return false; }
        require(p.getPersistentData().getByteArray(PADDING).length == 3 << 20 && !p.getPersistentData().contains(RocketRejoinMarker.KEY)
                && p.isAlive() && p.getVehicle() == null, "Expanded installed player did not complete cleanly");
        if (first == null) { first = ReleaseTestPlayers.login(server, FIRST, "W160FFirst", server.overworld()); return false; }
        if (!data.root().entries().isEmpty() || RocketRejoinProtection.guarded(first)
                || !data.root().equals(data.acknowledgedRoot()) || !data.pendingRoots().isEmpty()) { return false; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W160F_EXPANDED mode={} payload_bytes={} entries=0", mode, 3 << 20);
        return true;
    }
    private static void padding(ServerPlayer p, int size) {
        byte[] bytes = new byte[size]; new java.util.Random(160).nextBytes(bytes); p.getPersistentData().putByteArray(PADDING, bytes);
    }
    private static RocketRejoinRoot pressureRoot(RocketRejoinRoot original, boolean conflict) {
        var batches = new ArrayList<>(original.batches()); var entries = new ArrayList<>(original.entries());
        var template = batches.get(0); long sequence = original.nextSequence();
        // Synthetic already-seat-revoked history supplies queue/conflict pressure only. The original command's
        // batch and both of its independently proven seat states are copied without alteration.
        var ids = conflict ? List.of(PLAYER) : java.util.stream.IntStream.range(0, 129).mapToObj(i -> new UUID(160, i + 1)).toList();
        for (int at = 0; at < ids.size(); at += MAX_PER_BATCH) {
            var group = ids.subList(at, Math.min(ids.size(), at + MAX_PER_BATCH)); var release = UUID.randomUUID();
            batches.add(new Batch(release, sequence++, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Hold.H1,
                    template.dimension(), template.pad(), template.minimum(), template.maximum(), template.resources(),
                    template.before(), template.scrubbed(), group, template.slots()));
            for (int seat = 0; seat < group.size(); seat++) { entries.add(new Entry(release, group.get(seat), seat, Placement.PENDING, Seat.VERIFIED, null)); }
        }
        return new RocketRejoinRoot(sequence, batches, entries);
    }
    private void hazards(Batch batch) {
        var spawn = server.overworld().getSharedSpawnPos();
        for (var c : RocketRejoinCandidates.columns(batch.minimum(), batch.maximum(), spawn.getX(), spawn.getZ())) {
            for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) {
                server.overworld().setBlockAndUpdate(new BlockPos(c.x() + x, server.overworld().getMinBuildHeight() + 1, c.z() + z), Blocks.MAGMA_BLOCK.defaultBlockState());
            } }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
}
