package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import io.netty.channel.embedded.EmbeddedChannel;

/** W63 disposable native fixtures. No recovery/write acknowledgments are synthesized. */
final class ResourceRestartReleaseFixture {
    static final BlockPos ORIGIN = new BlockPos(72, 100, 72);
    static final UUID OWNER = UUID.fromString("00630000-0000-4000-8000-000000000001");
    static final UUID FIRST = UUID.fromString("00630000-0000-4000-8000-000000000002");
    static final UUID SECOND = UUID.fromString("00630000-0000-4000-8000-000000000003");
    static final UUID TRANSFER = UUID.fromString("00630000-0000-4000-8000-000000000004");
    static final UUID LOGICAL = UUID.fromString("00630000-0000-4000-8000-000000000005");
    private final List<Session> sessions = new ArrayList<>();

    static void require(boolean value, String message) {
        if (!value) { throw new IllegalStateException(message); }
    }

    static RocketEntity seed(MinecraftServer server, boolean source, boolean twoPassengers) {
        var earth = server.overworld();
        var moon = server.getLevel(CelestialIds.MOON_LEVEL);
        require(RocketTransferSavedData.get(server).entries().isEmpty(), "Seed journal is not empty");
        earth.setBlockAndUpdate(ORIGIN, (source ? ModBlocks.BIPROPELLANT_ROCKET_MOTOR : ModBlocks.ROCKET_MOTOR)
                .get().defaultBlockState());
        earth.setBlockAndUpdate(ORIGIN.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState());
        earth.setBlockAndUpdate(ORIGIN.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState());
        for (var at : List.of(ORIGIN.west(), ORIGIN.east(), ORIGIN.north())) {
            earth.setBlockAndUpdate(at, (source ? ModBlocks.BIPROPELLANT_FUEL_TANK : ModBlocks.ROCKET_FUEL_TANK)
                    .get().defaultBlockState());
        }
        if (source) {
            earth.setBlockAndUpdate(ORIGIN.south(), ModBlocks.OXIDIZER_FUEL_TANK.get().defaultBlockState());
            earth.setBlockAndUpdate(ORIGIN.south(2), ModBlocks.OXIDIZER_FUEL_TANK.get().defaultBlockState());
            earth.setBlockAndUpdate(ORIGIN.above(3), ModBlocks.ROCKET_SEAT.get().defaultBlockState());
        }
        long now = earth.getGameTime();
        var scan = new RocketStructureScanTask(new ServerLevelRocketScanWorld(earth, RocketBlockEntityAdapters.defaults()),
                earth.dimension().location(), new RocketPosition(72, 100, 72), UUID.randomUUID(), now);
        var result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        for (int i = 0; result.status() == RocketScanResult.Status.RUNNING && i < 64; i++) {
            result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        require(result.status() == RocketScanResult.Status.SUCCESS, "Seed structure scan failed: " + result.issues());
        var snapshot = result.snapshot().orElseThrow();
        for (var block : snapshot.blocks()) {
            var at = snapshot.sourceOrigin().add(block.position());
            earth.setBlockAndUpdate(new BlockPos(at.x(), at.y(), at.z()), Blocks.AIR.defaultBlockState());
        }
        var destination = snapshot.relocated(UUID.randomUUID(), moon.dimension().location(), snapshot.sourceOrigin(), now);
        var fuel = RocketFuelState.empty(snapshot.propulsion().orElseThrow()).withFluidBank(false,
                ModIdentity.id(source ? "hydrogen" : "rocket_fuel"), null, source ? 2_000 : 3_000);
        if (source) { fuel = fuel.withFluidBank(true, ModIdentity.id("oxygen"), null, 1_000); }
        var base = new RocketFlightPlan(3, TRANSFER, CelestialIds.EARTH_ID, CelestialIds.MOON_ID,
                earth.dimension().location(), moon.dimension().location(), 417, now);
        var catalog = source ? RocketFluidFuelCatalog.defaults() : new RocketFluidFuelCatalog(Map.of(
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.MONO, ModIdentity.id("rocket_fuel")),
                new RocketFluidFuelCatalog.Efficiency(417, 1_000)));
        var plan = RocketPropulsionQuoter.quote(fuel, base, catalog).plan();
        var flight = RocketFlightData.initial(LOGICAL, 3_000, snapshot.stats().seatCount(), CelestialIds.EARTH_ID,
                earth.dimension().location(), snapshot.sourceOrigin(), now).withFuel(fuel, now);
        if (source) {
            var manifest = flight.passengers().assign(FIRST).orElseThrow();
            if (twoPassengers) { manifest = manifest.assign(SECOND).orElseThrow(); }
            flight = flight.withPassengers(manifest);
        }
        var transit = flight.withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(TRANSFER, now);
        var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), CelestialIds.MOON_ID,
                moon.dimension().location(), destination.sourceOrigin(), now);
        var record = RocketTransferRecord.create(TRANSFER, LOGICAL, OWNER, UUID.randomUUID(), snapshot,
                destination, transit, arrival, plan.requiredFuel(), now);
        var rocket = ModEntities.ROCKET.get().create(source ? earth : moon);
        require(rocket != null, "Rocket entity type unavailable");
        if (source) {
            rocket.setUUID(record.sourceEntityId());
            rocket.initializeTransferred(snapshot, LOGICAL, OWNER, transit);
        } else {
            rocket.initializeTransferred(destination, LOGICAL, OWNER, arrival.land(now));
            record = record.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        }
        require((source ? earth : moon).addFreshEntity(rocket), "Seed rocket admission failed");
        RocketTransferSavedData.get(server).put(record);
        return rocket;
    }

    Player login(MinecraftServer server, ServerLevel level, UUID id) {
        require(server.getPlayerList().getPlayer(id) == null, "Fixture player already online");
        var player = new Player(level, id);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        sessions.add(new Session(player, channel));
        // The native login event performs reconnect. Never teleport a passenger after this call.
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    void pump() {
        for (var session : sessions) {
            session.channel().runPendingTasks();
            Object packet;
            while ((packet = session.channel().readOutbound()) != null) {
                if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                    session.player().connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
                io.netty.util.ReferenceCountUtil.release(packet);
            }
        }
    }

    void close() {
        for (var session : sessions) { session.channel().finishAndReleaseAll(); }
        sessions.clear();
    }

    private record Session(Player player, EmbeddedChannel channel) { }

    static final class Player extends ServerPlayer {
        Component message;
        Player(ServerLevel level, UUID id) {
            super(level.getServer(), level, new GameProfile(id, id.equals(OWNER) ? "W63Owner"
                    : id.equals(FIRST) ? "W63First" : "W63Second"));
        }
        @Override public void displayClientMessage(Component value, boolean actionBar) { message = value; }
    }
}
