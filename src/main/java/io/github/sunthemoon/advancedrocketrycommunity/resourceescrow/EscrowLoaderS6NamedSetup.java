package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** S6-only named fixture: finish ordinary setup and natural source unload before the measured fill. */
final class EscrowLoaderS6NamedSetup {
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64), DEVICE = ORIGIN.offset(2, 0, 0);
    private final MinecraftServer server;
    private RocketEntity rocket;
    private FluidFuelLoaderBlockEntity loader;
    private boolean first, placed, reserved;

    EscrowLoaderS6NamedSetup(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Named setup requires per-device test gates");
        this.server = server;
    }

    void startTick() {
        if (first) { return; }
        first = true;
        var delegate = EscrowRuntime.of(server).propulsion(server);
        EscrowTags.require(ResourceEscrowSavedData.existing(server) == null
                && delegate.state() == EscrowAdmissionAuthority.Delegate.State.NO_FILE
                && delegate.authority().pendingCount() == 0, "Named setup requires fresh authority");
    }

    void tick() {
        if (reserved || !server.overworld().isPositionEntityTicking(ORIGIN)) { return; }
        var level = server.overworld();
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (!placed) {
            var owner = UUID.randomUUID();
            rocket = rocket(level, ORIGIN, owner, mono(2_000));
            EscrowTags.require(level.addFreshEntity(rocket), "Named rocket publication failed");
            loader = loader(level, DEVICE, owner, tank(ROCKET_FUEL, 5_000), binding(rocket));
            EscrowLoaderFloorProbe.landed(level, journal, rocket, CelestialIds.MOON_LEVEL.location(),
                    new RocketPosition(65_536, 100, 65_536));
            journal.flushChecked(server);
            var empty = ResourceEscrowSavedData.forTests(server);
            empty.setDirty(); empty.bind(server).autosave();
            // Ordinary recovery must first classify this synthetic landed journal as settled.
            EscrowTags.require(server.saveEverything(false, true, true), "Named empty baseline save refused");
            placed = true;
            return;
        }
        var data = ResourceEscrowSavedData.existing(server);
        var coordinator = data.bind(server);
        var moon = server.getLevel(CelestialIds.MOON_LEVEL);
        EscrowTags.require(moon != null, "Named setup lost the old source Level");
        if (data.ledger().writerGeneration() == 1 && coordinator.writerIdle()
                && RocketRuntime.resourceMutationAllowed(rocket)
                && sourceReady(false, moon.getChunkSource().getChunkNow(4096, 4096) == null,
                        !moon.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(4096, 4096)))) {
            var runtime = EscrowRuntime.of(server);
            runtime.loaderAdmissionForTests(server, device(loader), true);
            try {
                EscrowTags.require(loader.attempt() == FluidFuelLoaderStatus.READY, "Named fill did not enter its fair queue");
                runtime.fills(server).visit();
                EscrowTags.require(loader.lastAttempt() == FluidFuelLoaderStatus.PREPARING
                        && coordinator.admissionAuthority().pendingCount() == 1,
                        "Named setup returned without its fill reservation");
            } finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
            reserved = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_NAMED_RESERVED tick={} pending=1 source_chunk=UNLOADED source_entities=UNLOADED", server.getTickCount());
        }
    }

    boolean observing() { return reserved; }

    /** Setup may wait for native ticket expiry; after admission a reload is a failure, never another wait. */
    static boolean sourceReady(boolean observing, boolean chunkAbsent, boolean entitiesAbsent) {
        EscrowTags.require(!observing || chunkAbsent && entitiesAbsent, "Named operator fixture loaded its old source");
        return chunkAbsent && entitiesAbsent;
    }
}
