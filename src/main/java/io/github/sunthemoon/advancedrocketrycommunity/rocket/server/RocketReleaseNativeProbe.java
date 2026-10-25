package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded native fixture, extending W63's seed/restart protocol. Never manufactures a storage acknowledgement. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketReleaseNativeProbe {
    private static final BlockPos ORIGIN = new BlockPos(72, 100, 72);
    private static final UUID FIRST = UUID.fromString("01580000-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("01580000-0000-4000-8000-000000000002");
    private static final UUID NEWCOMER = UUID.fromString("01580000-0000-4000-8000-000000000003");
    private static RocketReleaseNativeProbe active;
    private final MinecraftServer server;
    private final String hold, cut, mode;
    private final RocketRejoinNativeProbe rejoin;
    private RocketTransferRecord original;
    private UUID logical;
    private boolean admitted, prepared, done, coldRequested, conflictInstalled;
    private int ticks;

    private RocketReleaseNativeProbe(MinecraftServer server) {
        RocketReleaseNativeCuts.validate(server); this.server = server;
        var parts = RocketReleaseNativeCuts.selection().split(":"); hold = parts[0]; cut = parts[1]; mode = parts[2];
        rejoin = RocketReleaseNativeCuts.rejoin() ? new RocketRejoinNativeProbe(server) : null;
        server.overworld().setChunkForced(4, 4, true);
        if (mode.equals("seed") || hold.equals("H2")) { server.getLevel(CelestialIds.MOON_LEVEL).setChunkForced(4, 4, true); }
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!RocketReleaseNativeCuts.selection().isEmpty()) { active = new RocketReleaseNativeProbe(event.getServer()); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (active != null && active.server == event.getServer()) { active = null; RocketReleaseNativeCuts.reset(); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (active == null || active.server != event.getServer() || event.phase != TickEvent.Phase.END || active.done) { return; }
        try { active.advance(); }
        catch (RuntimeException failure) {
            active.done = true;
            AdvancedRocketryCommunity.LOGGER.error("ARCE_W158_FAILURE case={} reason={}", RocketReleaseNativeCuts.selection(), failure.toString(), failure);
        }
    }
    static void notice(MinecraftServer server, RocketRejoinRoot.Batch batch, String state) {
        var probe = active; if (probe == null || probe.server != server) { return; }
        if (state.equals("E1")) {
            if (probe.mode.equals("seed") && probe.cut.equals("C16")) {
                var current = probe.journal().find(batch.transfer()).orElseThrow();
                probe.journal().put(current.destinationSpawned(UUID.randomUUID())); probe.conflictInstalled = true;
            }
            if (probe.mode.equals("seed") && probe.cut.equals("C14")) {
                var entity = probe.rocket(); require(entity != null, "Gone fixture lost authority before its explicit deletion"); entity.discard();
            }
            RocketReleaseNativeCuts.checkpoint("C3");
        }
        if (state.equals("B1")) { RocketReleaseNativeCuts.checkpoint("C4"); }
        if (state.equals("S2") && probe.mode.equals("seed") && List.of("C14", "C15").contains(probe.cut)) {
            RocketReleaseNativeCuts.checkpoint(probe.cut);
        }
    }
    private RocketTransferSavedData journal() { return RocketTransferSavedData.get(server); }
    private RocketEntity rocket() {
        var found = new java.util.ArrayList<RocketEntity>();
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof RocketEntity rocket && !rocket.isRemoved()) {
                    found.add(rocket); require(found.size() <= 2, "Additional native fixture authority");
                }
            }
        }
        if (found.size() == 2 && mode.equals("seed") && hold.equals("H2") && original != null
                && transientPair(journal().find(original.transferId()).orElse(null), found.stream().map(RocketEntity::getUUID).toList())) { return null; }
        require(found.size() <= 1, "Duplicate native fixture authority");
        return found.isEmpty() ? null : found.get(0);
    }
    static boolean transientPair(RocketTransferRecord record, List<UUID> copies) {
        return record != null && copies.size() == 2 && !copies.get(0).equals(copies.get(1))
                && record.phase() != RocketTransferPhase.PREPARED && record.phase() != RocketTransferPhase.COMMITTED
                && copies.contains(record.sourceEntityId()) && record.destinationEntityId().filter(copies::contains).isPresent();
    }
    static boolean authorityReady(String hold, boolean source, boolean destination) {
        return source && (!hold.equals("H2") || destination);
    }
    private void advance() {
        require(++ticks <= 2400, "W158 native fixture exceeded 2400 ticks");
        if (!authorityReady(hold, server.overworld().areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(4, 4)),
                server.getLevel(CelestialIds.MOON_LEVEL).areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(4, 4)))) { return; }
        if (!journal().retryChecked(server)) { return; }
        if (original == null && mode.equals("seed")) {
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            if (!moon.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(4, 4))) { return; }
            // Same bounded legacy fixture used by the source-retirement native-store tests. For H2 the real
            // live transfer executor performs countdown, ascent, transfer, descent and landing in this JVM.
            var fixture = RocketSourceRetirementFixtures.countdown(server.overworld(), ORIGIN, moon, ORIGIN, List.of(FIRST, SECOND), 0);
            original = fixture.record(); logical = original.logicalRocketId();
            var entity = ModEntities.ROCKET.get().create(server.overworld()); require(entity != null, "No fixture rocket type");
            entity.setUUID(original.sourceEntityId());
            entity.initializeTransferred(original.sourceSnapshot(), logical, original.ownerId(),
                    hold.equals("H2") ? fixture.countdown() : original.sourceFlightData());
            RocketTransferEntities.positionAtOrigin(entity, original.sourceSnapshot().sourceOrigin());
            journal().put(original);
            require(server.overworld().addFreshEntity(entity), "Native source publication failed");
            if (hold.equals("H2")) { RocketSourceRetirementFixtures.live(RocketRuntime.installedTransfers()).add(original.transferId()); }
            return;
        }
        var sidecar = RocketRejoinEvents.get(server); var entity = rocket();
        if (logical == null) {
            logical = !sidecar.root().batches().isEmpty() ? sidecar.root().batches().get(0).rocket()
                    : entity == null ? null : entity.assemblyTransactionId().orElseThrow();
        }
        if (rejoin != null && sidecar.root().nextSequence() > 0) {
            if (rejoin.advance(sidecar)) { complete("rejoined"); }
            return;
        }
        if (cut.equals("C16") && !mode.equals("seed")) {
            if (ticks < 450) { return; }
            require(logical != null && RocketReleaseAccess.fenced(server, logical), "Unrecorded T lost release fence");
            require(sidecar.root().entries().stream().allMatch(e -> e.seat() == RocketRejoinRoot.Seat.PENDING), "Conflict retired a seat");
            complete("conflict"); return;
        }
        if (!mode.equals("seed") && (logical == null || !RocketReleaseAccess.fenced(server, logical))
                && sidecar.root().nextSequence() > 0 && sidecar.root().entries().stream().noneMatch(e -> e.seat() == RocketRejoinRoot.Seat.PENDING)) {
            if (!cut.equals("C14")) { require(entity != null, "Restart lost release authority"); }
            if (entity != null) {
                require(entity.flightData().orElseThrow().passengers().assignment(SECOND).isEmpty(), "Released rider returned");
                require(entity.flightData().orElseThrow().fuel().amount() == (hold.equals("H1") ? 1000 : 500), "Restart changed fuel");
            }
            complete("released"); return;
        }
        if (!mode.equals("seed") && sidecar.root().nextSequence() > 0) { return; }
        if (entity == null || !entity.flightData().orElseThrow().state().acceptsFuel()) { return; }
        var current = journal().findByLogicalRocket(logical).orElse(null); if (current == null) { return; }
        if (hold.equals("H2") && current.phase() != RocketTransferPhase.COMMITTED) { return; }
        if (!prepared) {
            if (hold.equals("H2")) {
                var flight = entity.flightData().orElseThrow();
                entity.updateFlightData(flight.withPassengers(flight.passengers().remove(FIRST).assign(NEWCOMER).orElseThrow()));
            } else if (cut.equals("C17")) {
                var flight = entity.flightData().orElseThrow(); entity.updateFlightData(flight.withPassengers(flight.passengers().remove(FIRST).remove(SECOND)));
            }
            if (!hold.equals("H2") && !RocketSourceRetirementFixtures.settled(RocketRuntime.installedTransfers()).contains(current.transferId())) { return; }
            require(server.saveEverything(false, true, true), "Native baseline save failed");
            var resources = RocketReleasePlan.resources(entity.snapshot().orElseThrow(), entity.flightData().orElseThrow());
            AdvancedRocketryCommunity.LOGGER.info("ARCE_W158_BASELINE case={} logical={} transfer={} resources={}",
                    RocketReleaseNativeCuts.selection(), logical, current.transferId(), resources);
            prepared = true;
        }
        if (cut.equals("C15")) {
            var moon = server.getLevel(CelestialIds.MOON_LEVEL);
            if (!coldRequested) {
                RocketTransferEntities.releaseFlightTickets(server, current); moon.setChunkForced(4, 4, false); coldRequested = true;
            }
            if (moon.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(4, 4))) { return; }
        }
        if (!admitted) {
            if (rejoin != null) { rejoin.seedOldPlayer(entity); }
            if (Boolean.getBoolean("advancedrocketrycommunity.rejoinReleaseCommand")) {
                // Already inside the disposable-server gate; use the real registered operator command unchanged.
                admitted = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "arce rocket recover " + current.transferId() + " release-absent") == 1;
            } else { admitted = RocketRuntime.releaseAbsentForTests(server, current.transferId()).isPresent(); }
            if (admitted && rejoin != null) { rejoin.admitted(); }
            return;
        }
        if (conflictInstalled && ticks >= 550 && journal().acknowledgedCurrent()) { RocketReleaseNativeCuts.checkpoint("C16"); }
    }
    private void complete(String result) {
        require(server.saveEverything(false, true, true), "Native final save failed");
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W158_RESULT case={} result={} sequence={} journal={}",
                RocketReleaseNativeCuts.selection(), result, RocketRejoinEvents.get(server).root().nextSequence(), journal().entries().size());
    }
    private static void require(boolean condition, String reason) { if (!condition) { throw new IllegalStateException(reason); } }
}
