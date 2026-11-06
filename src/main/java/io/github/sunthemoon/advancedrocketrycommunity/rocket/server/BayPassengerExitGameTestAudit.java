package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

/** Bounded fixture diagnostics; no production hooks or observer changes. */
final class BayPassengerExitGameTestAudit {
    private static final String UNKNOWN_33 = "Ticket[unknown 33 ";
    private BayPassengerExitGameTestAudit() { }
    /** Read-only placement census, using a private budget rather than the measured service budget. */
    static void placement(BayPassengerExitGameTestFixture fixture) {
        var level = fixture.helper.getLevel();
        var origin = fixture.origin;
        var rocket = fixture.flight.source();
        var positions = io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.ordered(
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Position(
                        level.dimension().location().toString(), rocket.getX(), rocket.getY(), rocket.getZ()),
                java.util.Optional.empty(), java.util.Optional.of(
                        new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Anchor(
                                level.dimension().location().toString(), origin.getX(), origin.getY(), origin.getZ())));
        int ordinal = 0;
        for (var position : positions) {
            var box = new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box(position, .6F, 1.8F);
            var world = new BayPassengerExitWorld(fixture.player);
            var budget = new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget(0);
            try (var probe = budget.openProbe().orElseThrow()) {
                var verdict = io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.loaded(world)
                        .inspect(box, probe, (candidate, allowance) -> true);
                var fluids = new ArrayList<String>();
                int supportY = (int) Math.floor(Math.nextDown(position.y()));
                for (int x = (int) Math.floor(box.minX()) - 1; x < (int) Math.ceil(box.maxX()) + 1; x++) {
                    for (int z = (int) Math.floor(box.minZ()) - 1; z < (int) Math.ceil(box.maxZ()) + 1; z++) {
                        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                        if (chunk == null) { continue; }
                        for (int y = supportY - 1; y < (int) Math.ceil(box.maxY()) + 1; y++) {
                            var at = new BlockPos(x, y, z);
                            var state = chunk.getBlockState(at);
                            if (!state.getFluidState().isEmpty()) { fluids.add(at + "=" + state); }
                        }
                    }
                }
                AdvancedRocketryCommunity.LOGGER.info(
                        "W313_PLACEMENT case={} origin={} ordinal={} feet={} verdict={} reads={} columnChecks={} contact={} support={} fluids={}",
                        fixture.scenario, origin, ++ordinal, position, verdict, world.reads, world.columnChecks,
                        world.firstContact, level.getBlockState(BlockPos.containing(position.x(), supportY, position.z())), fluids);
            }
        }
    }
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
        var dispatch = fixture.probeDispatch;
        fixture.probeMount = null; fixture.probeDispatch = null;
        try {
            var prior = mount != null && mount.column() == column ? mount.tickets() : List.<String>of();
            var hook = tickets(level, column);
            boolean added = hook.stream().anyMatch(ticket -> ticket.startsWith(UNKNOWN_33) && !prior.contains(ticket));
            boolean preexisting = prior.stream().anyMatch(ticket -> ticket.startsWith(UNKNOWN_33));
            // Cache stores between the dispatch's end and the hook: none, or exactly one store of this column.
            String since = "none";
            if (dispatch != null && dispatch.column() == column) {
                var now = cache(level);
                since = now.equals(dispatch.cache()) ? "unchanged" : now.get(0).equals(entry(column, ChunkStatus.FULL))
                        && now.subList(1, now.size()).equals(dispatch.cache().subList(0, now.size() - 1)) ? "inserted" : "other";
            }
            boolean ended = dispatch != null && dispatch.column() == column;
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_BAY_EXIT_PROBE case={} player={} column={} mountSlot={} hookSlot={} added={} preexisting={}"
                            + " dispatchSlot={} dispatchFull={} sinceDispatch={} mount={} hook={}",
                    fixture.scenario, player.getUUID(), new ChunkPos(column),
                    mount != null && mount.column() == column ? mount.slot() : "none", cacheSlot(level, column), added, preexisting,
                    ended ? dispatch.slot() : "none", ended ? dispatch.full() : "none", since, prior, hook);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_BAY_EXIT_PROBE case={} player={} error={}", fixture.scenario, player.getUUID(), error.toString());
        }
    }

    /** W242-R1 supplement: the cache, the column's slot and its FULL readiness when the dismount dispatch has ended. */
    record DispatchEnd(long column, List<String> cache, int slot, boolean full) { }

    /**
     * Registers, at setup and many ticks before any detach, one listener per fixture rocket in the column that the
     * dismount game-event dispatch visits last (x outer, z inner, one getChunkNow per column before its listeners).
     * It therefore runs after every dispatch lookup and before vanilla's portal read, and in the final source also before
     * RocketEntity.removePassenger's getChunkNow. During the dispatch it only reads by reflection.
     */
    static Runnable observeDispatchEnd(BayPassengerExitGameTestFixture fixture, Runnable then) {
        var cleanups = new ArrayList<Runnable>(List.of(then));
        for (var flight : java.util.Arrays.asList(fixture.flight, fixture.otherFlight)) {
            if (flight != null) { cleanups.add(observeDispatchEnd(fixture, flight.source())); }
        }
        return () -> cleanups.forEach(Runnable::run);
    }
    private static Runnable observeDispatchEnd(BayPassengerExitGameTestFixture fixture, RocketEntity rocket) {
        var at = BlockPos.containing(rocket.position());
        int radius = GameEvent.ENTITY_DISMOUNT.getNotificationRadius();
        var chunk = fixture.helper.getLevel().getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(at.getX() + radius),
                SectionPos.blockToSectionCoord(at.getZ() + radius));
        if (chunk == null) { return () -> { }; }
        var registry = chunk.getListenerRegistry(SectionPos.blockToSectionCoord(at.getY()));
        long column = rocket.chunkPosition().toLong();
        var listener = new GameEventListener() {
            @Override public PositionSource getListenerSource() { return new BlockPositionSource(at); }
            @Override public int getListenerRadius() { return radius; }
            @Override public boolean handleGameEvent(ServerLevel world, GameEvent event, GameEvent.Context context, Vec3 position) {
                if (!fixture.closed && event == GameEvent.ENTITY_DISMOUNT && fixture.players.contains(context.sourceEntity())
                        && position.distanceToSqr(rocket.position()) < 1e-6) {
                    try { fixture.probeDispatch = new DispatchEnd(column, cache(world), cacheSlot(world, column), full(world, column)); }
                    catch (ReflectiveOperationException | RuntimeException error) { fixture.probeDispatch = null; }
                }
                return false;
            }
        };
        registry.register(listener);
        return () -> registry.unregister(listener);
    }

    private static Object field(Object target, Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static String entry(long column, ChunkStatus status) { return new ChunkPos(column) + ":" + status; }
    private static List<String> cache(ServerLevel level) throws ReflectiveOperationException {
        var cache = level.getChunkSource();
        var positions = (long[]) field(cache, ServerChunkCache.class, "lastChunkPos");
        var statuses = (ChunkStatus[]) field(cache, ServerChunkCache.class, "lastChunkStatus");
        var entries = new ArrayList<String>();
        for (int slot = 0; slot < positions.length; slot++) { entries.add(entry(positions[slot], statuses[slot])); }
        return entries;
    }
    /** Whether the column's holder has completed FULL, which is when getChunkNow returns and caches it. */
    private static boolean full(ServerLevel level, long column) throws ReflectiveOperationException {
        var method = net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("getVisibleChunkIfPresent", long.class);
        method.setAccessible(true);
        var holder = (net.minecraft.server.level.ChunkHolder) method.invoke(level.getChunkSource().chunkMap, column);
        var either = holder == null ? null : holder.getFullChunkFuture().getNow(null);
        return either != null && either.left().isPresent();
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
