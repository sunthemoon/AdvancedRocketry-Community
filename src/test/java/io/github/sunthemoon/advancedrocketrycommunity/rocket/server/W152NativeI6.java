package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

/** W198's diagnostic-only predecessor input and installed I6 request. Never part of the production JAR. */
final class W152NativeI6 {
    private final MinecraftServer server;
    private RocketEntity destination;
    private RocketTransferRecord predecessor;
    private boolean requested;

    W152NativeI6(MinecraftServer server) { this.server = server; }

    RocketEntity setup(RocketStructureSnapshot snapshot) {
        var level = server.overworld(); long now = level.getGameTime();
        var origin = new RocketPosition(40, 100, 8);
        var sourceSnapshot = snapshot.relocated(W152NativeProbe.id("source-snapshot"),
                level.dimension().location(), origin, now);
        var fuel = RocketFuelState.empty(1000).fill(1000).state();
        var initial = RocketFlightData.initial(W152NativeProbe.id("logical"), 1000, 1, CelestialIds.MOON_ID,
                level.dimension().location(), origin, now).withFuel(fuel, now);
        var source = ModEntities.ROCKET.get().create(level);
        require(source != null, "Missing source type"); source.setUUID(W152NativeProbe.id("source-entity"));
        source.initializeTransferred(sourceSnapshot, initial.logicalRocketId(), W152NativeProbe.id("owner"), initial);
        RocketTransferEntities.positionAtOrigin(source, origin);
        require(level.addFreshEntity(source), "Source publication refused");
        // Only the input S exists at this save. NEVER_SAVED D is published afterward.
        server.saveEverything(true, true, true);
        var plan = new RocketFlightPlan(3, W152NativeProbe.id("transfer"), CelestialIds.MOON_ID, CelestialIds.EARTH_ID,
                level.dimension().location(), level.dimension().location(), 500, now);
        var countdown = initial.withPlan(plan).startCountdown(now);
        var transit = RocketTransferService.scheduledTransit(countdown);
        var descent = transit.arriveAtDestination(fuel.debit(plan).state(), CelestialIds.EARTH_ID,
                level.dimension().location(), snapshot.sourceOrigin(), RocketTransferService.scheduledArrival(countdown));
        var current = descent.land(now).withFuel(descent.fuel().fill(500).state(), now);
        destination = ModEntities.ROCKET.get().create(level); require(destination != null, "Missing destination type");
        destination.setUUID(W152NativeProbe.id("entity"));
        destination.initializeTransferred(snapshot, initial.logicalRocketId(), W152NativeProbe.id("owner"), current);
        RocketTransferEntities.positionAtOrigin(destination, snapshot.sourceOrigin());
        require(level.addFreshEntity(destination), "Destination publication refused");
        predecessor = RocketTransferRecord.create(plan.requestId(), initial.logicalRocketId(), W152NativeProbe.id("owner"),
                source.getUUID(), sourceSnapshot, snapshot, transit, descent, 500, now).destinationSpawned(destination.getUUID())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
        var journal = RocketTransferSavedData.get(server); journal.put(predecessor);
        try {
            @SuppressWarnings("unchecked") var settled = (Set<UUID>) W152NativeProbe.field(
                    RocketTransferService.class, "settledTransfers").get(RocketRuntime.installedTransfers());
            settled.add(predecessor.transferId());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        journal.flushChecked(server);
        var input = new CompoundTag(); input.put("transfer_root", journal.save(new CompoundTag()));
        var image = new CompoundTag(); require(destination.save(image), "Cannot freeze D1"); input.put("destination", image);
        var old = new CompoundTag(); require(source.save(old), "Cannot freeze S"); input.put("source", old);
        try { NbtIo.writeCompressed(input, server.getWorldPath(LevelResource.ROOT).resolve("w198-input.nbt").toFile()); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
        return destination;
    }

    boolean requested() { return requested; }

    void tick(boolean neverSaved, Consumer<String> observe) {
        if (requested || destination == null) { return; }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.logicalAcknowledged(predecessor.logicalRocketId())
                || !server.overworld().areEntitiesLoaded(new ChunkPos(2, 0).toLong())) { return; }
        // This is still before the destructive request. No save follows admission.
        if (!neverSaved) { server.saveEverything(true, true, true); observe.accept("C0_BASELINE_SAVED"); }
        else { observe.accept("C0_D_NEVER_SAVED"); }
        try {
            var manager = (RocketManager) W152NativeProbe.field(RocketRuntime.class, "service").get(null);
            var result = manager.disassembleForReleaseTest(destination, 1000);
            require(result == RocketValidationCode.DISASSEMBLY_PENDING, "Integrated I6 admission: " + result);
            requested = true;
            observe.accept("W198_I6_REQUESTED");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
}
