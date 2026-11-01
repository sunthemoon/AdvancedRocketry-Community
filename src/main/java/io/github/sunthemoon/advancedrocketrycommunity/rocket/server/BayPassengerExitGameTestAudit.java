package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Bounded fixture diagnostics; no production hooks or observer changes. */
final class BayPassengerExitGameTestAudit {
    private static final String UNKNOWN_33 = "Ticket[unknown 33 ";
    private BayPassengerExitGameTestAudit() { }
    static String difference(Object before, Object after) {
        var left = (Map<?, ?>) before; var right = (Map<?, ?>) after;
        var changed = new TreeMap<String, String>();
        left.forEach((key, value) -> { if (!value.equals(right.get(key))) { changed.put(key.toString(), value + " -> " + right.get(key)); } });
        right.forEach((key, value) -> { if (!left.containsKey(key)) { changed.put(key.toString(), "absent -> " + value); } });
        return changed.toString();
    }
    static void firstTickLanding(BayPassengerExitGameTestFixture fixture) {
        var player = fixture.player; var helper = fixture.helper;
        double target = Math.floor(player.getY()) - 3;
        float distance = (float) (player.getY() - target);
        helper.assertTrue(distance > 3 && distance <= 4, "First-tick descent did not fit its unchanged allowance");
        fixture.pad(BlockPos.containing(player.getX(), target, player.getZ()));
        player.setOnGround(false); player.setPos(player.getX(), target, player.getZ());
        player.fallDistance = distance;
        player.doCheckFallDamage(0, -distance, 0, true);
        helper.assertTrue(player.getHealth() == 20 && fixture.falls == 1
                && fixture.attacks.stream().filter(net.minecraftforge.event.entity.living.LivingAttackEvent::isCanceled).count() == 1,
                "First-tick ordinary landing did not consume exactly one FALL cancellation");
        helper.assertTrue(fixture.fallDistance == distance && fixture.multiplier == 1 && fixture.offer.descent.closed,
                "First-tick landing changed the source-less event or kept its capability");
        player.invulnerableTime = 0; player.causeFallDamage(distance, 1, player.damageSources().fall());
        helper.assertTrue(player.getHealth() < 20, "A second first-tick fall was protected");
    }

    /** W242-R3: the operator/space-safety travel move detaches a bay-held rider as a trusted move, never as an exit. */
    static void trustedTravel(BayPassengerExitGameTestFixture fixture) {
        var player = fixture.player; var target = fixture.origin.offset(0, 1, 3);
        var destination = new io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel.Destination(
                target.getX() + .5, target.getY(), target.getZ() + .5);
        new io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel()
                .teleport(player, fixture.helper.getLevel(), destination);
        fixture.helper.assertTrue(!player.isPassenger() && player.getX() == destination.x() && player.getY() == destination.y()
                && player.getZ() == destination.z(), "Trusted travel did not move the rider: " + player.position());
        fixture.helper.assertTrue(fixture.exits.state.live.get(player) == null
                && fixture.exits.state.history(player.getUUID(), fixture.flight.record().transferId()) == null,
                "Trusted travel from a held rocket created an exit row");
    }

    /** The rider's chat lines since the last drain, as "chat:key" or "overlay:key"; at most 65,536 packets. */
    static List<String> chat(BayPassengerExitGameTestFixture fixture, ServerPlayer player) {
        var channel = fixture.channels.get(fixture.players.indexOf(player));
        channel.runPendingTasks();
        var lines = new ArrayList<String>();
        for (int count = 0; count < 1 << 16; count++) {
            Object packet = channel.readOutbound();
            if (packet == null) { return lines; }
            try {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket line
                        && line.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text) {
                    lines.add((line.overlay() ? "overlay:" : "chat:") + text.getKey());
                }
            } finally { io.netty.util.ReferenceCountUtil.release(packet); }
        }
        throw new IllegalStateException("Bay-exit packet drain reached its bound");
    }

    /** W242-R10: a held menu LEAVE reports only its exit message, not "Flight request accepted". */
    static void heldLeaveFeedback(BayPassengerExitGameTestFixture fixture) {
        var lines = chat(fixture, fixture.player);
        fixture.helper.assertTrue(lines.stream().filter(line -> line.contains(".bay_exit.")).toList()
                        .equals(List.of("chat:message.advancedrocketrycommunity.bay_exit.safe"))
                && !lines.contains("overlay:" + io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.SUCCESS.translationKey()),
                "Held LEAVE feedback was not exactly the exit message: " + lines);
    }

    /** W242-R1: the rocket column's cache slot and tickets at the fixture's own dismount event, before any W242 code. */
    record Mount(long column, int slot, List<String> tickets) { }

    static Mount mount(ServerLevel level, RocketEntity rocket) {
        long column = rocket.chunkPosition().toLong();
        try { return new Mount(column, cacheSlot(level, column), tickets(level, column)); }
        catch (ReflectiveOperationException error) { return new Mount(column, -2, List.of(error.toString())); }
    }

    /**
     * W242-R1 probe, called when the rocket's location hook is entered. Read-only: it reflects over the chunk cache and the
     * ticket map, and makes no chunk lookup, ticket or write. Vanilla's dismount read precedes the hook, so the column is
     * always cached here; whether that read missed shows as an added UNKNOWN level-33 ticket on the rocket's column.
     */
    static void probe(BayPassengerExitGameTestFixture fixture, ServerPlayer player, RocketEntity rocket) {
        if (fixture.closed || !fixture.players.contains(player)) { return; }
        var level = fixture.helper.getLevel();
        long column = rocket.chunkPosition().toLong();
        var mount = fixture.probeMount;
        fixture.probeMount = null;
        try {
            var prior = mount != null && mount.column() == column ? mount.tickets() : List.<String>of();
            var hook = tickets(level, column);
            boolean added = hook.stream().anyMatch(ticket -> ticket.startsWith(UNKNOWN_33) && !prior.contains(ticket));
            boolean preexisting = prior.stream().anyMatch(ticket -> ticket.startsWith(UNKNOWN_33));
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_BAY_EXIT_PROBE case={} player={} column={} mountSlot={} hookSlot={} added={} preexisting={} mount={} hook={}",
                    fixture.scenario, player.getUUID(), new ChunkPos(column),
                    mount != null && mount.column() == column ? mount.slot() : "none", cacheSlot(level, column), added, preexisting, prior, hook);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_BAY_EXIT_PROBE case={} player={} error={}", fixture.scenario, player.getUUID(), error.toString());
        }
    }

    private static Object field(Object target, Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static int cacheSlot(ServerLevel level, long column) throws ReflectiveOperationException {
        var cache = level.getChunkSource();
        var positions = (long[]) field(cache, ServerChunkCache.class, "lastChunkPos");
        var statuses = (ChunkStatus[]) field(cache, ServerChunkCache.class, "lastChunkStatus");
        for (int slot = 0; slot < positions.length; slot++) {
            if (positions[slot] == column && statuses[slot] == ChunkStatus.FULL) { return slot; }
        }
        return -1;
    }
    private static List<String> tickets(ServerLevel level, long column) throws ReflectiveOperationException {
        var entries = (Map<?, ?>) field(level.getChunkSource().chunkMap.getDistanceManager(), DistanceManager.class, "tickets");
        var set = (Iterable<?>) entries.get(column);
        var strings = new ArrayList<String>();
        if (set != null) { for (Object ticket : set) { strings.add(ticket.toString()); } }
        return strings;
    }
}
