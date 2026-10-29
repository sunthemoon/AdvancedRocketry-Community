package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.*;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** W204 native test fixture and observations. No command exists without the explicit release hooks. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RocketN3cReleaseProbe {
    private static RocketN3cReleaseProbe active;
    private final MinecraftServer server;
    private final RocketTransferService service;
    private RocketJournalSchedulingGameTestSupport.ReleaseLimit lowered;
    private RocketTransferSavedData.Reservation seedToken, filler;
    private RocketTransferRecord record;
    private RocketEntity source;
    private LandingPadEntry pad;
    private int completions;

    private RocketN3cReleaseProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; service = RocketRuntime.installedTransfers();
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                || !Boolean.getBoolean(RocketJournalSchedulingGameTestSupport.RELEASE_PROPERTY)) { return; }
        event.getDispatcher().register(Commands.literal("arce-w204").requires(s -> s.hasPermission(4))
                .then(Commands.literal("seed").then(Commands.argument("rocket", EntityArgument.entity())
                        .then(Commands.argument("mode", StringArgumentType.word()).executes(context -> {
                            var server = context.getSource().getServer(); EscrowTestEndpoints.requireEnabled(server);
                            if (active != null) { throw new IllegalStateException("W204 already seeded"); }
                            var probe = new RocketN3cReleaseProbe(server); active = probe;
                            try {
                                probe.seed((RocketEntity) EntityArgument.getEntity(context, "rocket"),
                                        StringArgumentType.getString(context, "mode"));
                            } catch (RuntimeException failure) { probe.close(); active = null; throw failure; }
                            return 1;
                        }))))
                .then(Commands.literal("act").then(Commands.argument("mode", StringArgumentType.word()).executes(context -> {
                    var probe = require(context.getSource().getServer());
                    try { probe.act(StringArgumentType.getString(context, "mode")); }
                    catch (RuntimeException failure) { probe.close(); throw failure; }
                    return 1;
                })))
                .then(Commands.literal("observe").then(Commands.argument("rocket", EntityArgument.entity())
                        .then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                            var server = context.getSource().getServer(); EscrowTestEndpoints.requireEnabled(server);
                            var probe = active == null ? new RocketN3cReleaseProbe(server) : require(server);
                            probe.source = (RocketEntity) EntityArgument.getEntity(context, "rocket");
                            probe.observe(StringArgumentType.getString(context, "label")); return 1;
                        })))));
    }

    private static RocketN3cReleaseProbe require(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        if (active == null || active.server != server) { throw new IllegalStateException("No W204 fixture"); }
        return active;
    }

    private void seed(RocketEntity rocket, String mode) {
        if (!mode.equals("refused") && !mode.equals("stopped") && !mode.equals("removal")) {
            throw new IllegalArgumentException("Unknown W204 mode");
        }
        if (!journal().clean()) { throw new IllegalStateException("W204 needs a settled baseline"); }
        if (mode.equals("refused")) { lowered = RocketJournalSchedulingGameTestSupport.lowerReleaseLimit(server, 1); }
        source = rocket;
        var snapshot = source.snapshot().orElseThrow();
        var flight = source.flightData().orElseThrow();
        if (flight.state() != RocketFlightState.FUELED || !flight.passengers().assignments().isEmpty()) {
            throw new IllegalArgumentException("W204 requires an uncrewed fueled source");
        }
        UUID station = UUID.randomUUID(), transfer = UUID.randomUUID();
        pad = LandingPadIndex.get(server).register(station, source.ownerId().orElseThrow(), UUID.randomUUID(),
                "W204", new StationPosition(0, 100, 0));
        long now = server.overworld().getGameTime();
        var destination = snapshot.relocated(UUID.randomUUID(), CelestialIds.SPACE_LEVEL.location(), new RocketPosition(72, 100, 72), now);
        var plan = new RocketFlightPlan(3, transfer, flight.currentBody(), ModIdentity.id("space"),
                snapshot.sourceDimension(), destination.sourceDimension(), station, 500, now).withLandingPad(pad.reference());
        var transit = flight.withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
        var arrival = transit.arriveAtDestination(transit.fuel().debit(plan).state(), plan.destinationBody(),
                plan.destinationDimension(), destination.sourceOrigin(), now);
        record = RocketTransferRecord.create(transfer, source.assemblyTransactionId().orElseThrow(), source.ownerId().orElseThrow(),
                source.getUUID(), snapshot, destination, transit, arrival, 500, now);
        seedToken = journal().reserve(record.logicalRocketId()).orElseThrow();
        journal().put(record); source.updateFlightData(transit);
        RocketTransferEntities.positionAtAltitude(source, snapshot.sourceOrigin());
        if (!LandingPadIndex.get(server).claim(station, pad.reference(), pad.physicalId(), transfer)) {
            throw new IllegalStateException("W204 claim refused");
        }
        RocketSourceRetirementFixtures.live(service).add(transfer);
        // Fixture setup only: keep the actual two proof endpoints loaded, not a product ticket change.
        server.getLevel(CelestialIds.SPACE_LEVEL).setChunkForced(4, 4, true);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W204_SEEDED logical={} transfer={}", record.logicalRocketId(), transfer);
    }

    private RocketTransferSavedData journal() { return RocketTransferSavedData.get(server); }

    private void act(String mode) {
        EscrowTestEndpoints.requireEnabled(server);
        if (!journal().acknowledgedCurrent()) { throw new IllegalStateException("W204 seed not acknowledged"); }
        if (!mode.equals("refused") && !mode.equals("stopped") && !mode.equals("removal")) {
            throw new IllegalArgumentException("Unknown W204 mode");
        }
        seedToken.close(); seedToken = null;
        if (mode.equals("refused")) { filler = journal().reserve(UUID.randomUUID()).orElseThrow(); }
        if (mode.equals("stopped")) { journal().stop(); }
        write("before", snapshot());
        boolean accepted = service.failBackToSource(server, journal(), record, source,
                RocketTransferReturnReason.COUNTDOWN_CANCELLED, new RocketRequestCompletion(() -> completions++, () -> { }));
        var after = snapshot(); after.putBoolean("accepted", accepted); write("after", after);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W204_ACT mode={} accepted={}", mode, accepted);
        if (!mode.equals("removal")) {
            // The observation and halt share this owner-thread callback: no intervening recovery tick or save.
            Runtime.getRuntime().halt(75);
        }
    }

    private CompoundTag snapshot() {
        var root = new CompoundTag();
        root.put("source", source.saveWithoutId(new CompoundTag()));
        root.put("journal", journal().save(new CompoundTag()));
        root.put("pads", LandingPadIndex.get(server).save(new CompoundTag()));
        root.putBoolean("live", record != null && RocketSourceRetirementFixtures.live(service).contains(record.transferId()));
        root.putBoolean("acknowledged", journal().acknowledgedCurrent());
        root.putInt("completions", completions);
        return root;
    }

    private void observe(String label) {
        if (!label.matches("pending|restart1|restart2|seed")) { throw new IllegalArgumentException("Unknown observation label"); }
        var observation = snapshot();
        if (label.equals("pending")) {
            var index = LandingPadIndex.get(server);
            var entry = index.find(pad.stationId(), pad.reference()).orElseThrow();
            observation.putBoolean("reuse", LandingPadService.claim(server, entry, UUID.randomUUID()));
            observation.put("pads", index.save(new CompoundTag()));
        }
        if (label.equals("restart1")) { reconcile(observation); }
        write(label, observation);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W204_OBSERVED label={}", label);
    }

    /** Exercise lazy reconciliation once; retain each ownership image and explicitly clean up the test contender. */
    private void reconcile(CompoundTag observation) {
        var index = LandingPadIndex.get(server);
        var entries = LandingPadIndexCodec.decode(index.save(new CompoundTag())).entries();
        if (entries.size() != 1 || !entries.get(0).name().equals("W204")
                || !entries.get(0).ownerId().equals(source.ownerId().orElseThrow())) {
            throw new IllegalStateException("W204 reconciliation needs its single seeded pad");
        }
        var old = entries.get(0);
        if (old.claim() == null) { return; } // Ordinary recovery may already have completed its acknowledged release.
        var proof = new CompoundTag();
        UUID contender = UUID.randomUUID();
        proof.putUUID("old_transfer", old.claim()); proof.putUUID("contender", contender);
        proof.putBoolean("absence_acknowledged", journal().transferAbsenceAcknowledged(old.claim()));
        proof.put("before", index.save(new CompoundTag()));
        proof.putBoolean("accepted", LandingPadService.claim(server, old, contender));
        proof.put("after_claim", index.save(new CompoundTag()));
        // Replayed requests still carry the old entry, as does the delayed old release callback.
        proof.putBoolean("repeat", LandingPadService.claim(server, old, contender));
        proof.putBoolean("competing", LandingPadService.claim(server, old, UUID.randomUUID()));
        proof.putBoolean("old_release", index.release(old.stationId(), old.reference(), old.claim()));
        proof.put("after_retries", index.save(new CompoundTag()));
        // This release is fixture cleanup of the contender, not another release of the old transfer.
        proof.putBoolean("cleanup", index.release(old.stationId(), old.reference(), contender));
        proof.put("after_cleanup", index.save(new CompoundTag()));
        observation.put("reconciliation", proof);
        observation.put("pads", index.save(new CompoundTag()));
    }

    private void write(String label, CompoundTag observation) {
        CompoundTag wrapper = new CompoundTag(); wrapper.put("data", observation);
        try { NbtIo.writeCompressed(wrapper, server.getWorldPath(LevelResource.ROOT).resolve("../w204-" + label + ".dat").normalize().toFile()); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private void close() {
        if (seedToken != null) { seedToken.close(); seedToken = null; }
        if (filler != null) { filler.close(); filler = null; }
        if (lowered != null) { lowered.close(); lowered = null; }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (active != null && active.server == event.getServer()) {
            try { active.close(); } finally { active = null; }
        }
    }
}
