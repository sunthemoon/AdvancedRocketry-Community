package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;

/** Complete immutable F1 preparation. No candidate, serial preview or encoded record grants authority. */
final class EscrowGovernedLaunch {
    private final EscrowFlightAuthority.Launch launch;
    private final CompoundTag source;
    private final CompoundTag destination;
    private final RocketStructureSnapshot sourceSnapshot;
    private final RocketStructureSnapshot destinationSnapshot;
    private final RocketFlightData transit;
    private final RocketFlightData descent;
    private final long started;

    private EscrowGovernedLaunch(EscrowFlightAuthority.Launch launch, CompoundTag source, CompoundTag destination,
            RocketStructureSnapshot sourceSnapshot, RocketStructureSnapshot destinationSnapshot,
            RocketFlightData transit, RocketFlightData descent, long started) {
        this.launch = launch; this.source = source.copy(); this.destination = destination.copy();
        this.sourceSnapshot = sourceSnapshot; this.destinationSnapshot = destinationSnapshot;
        this.transit = transit; this.descent = descent; this.started = started;
    }

    /** Called before F1's store; validate all bodies and reservations without changing either store. */
    static EscrowGovernedLaunch prepare(EscrowLedger root, CompoundTag source,
            RocketStructureSnapshot sourceSnapshot, RocketFlightData countdown,
            RocketStructureSnapshot destinationSnapshot, UUID destinationId, EscrowGuard journal) {
        EscrowTags.bound(source, EscrowNativeEnvelope.MAX_BYTES, 24, 131_072);
        source = EscrowGovernedEnvelope.comparison(source);
        var initial = flight(source);
        var wrapper = RocketEscrowSection.decode(EscrowTags.compound(source, "wrapper"));
        var plan = countdown.plan().orElseThrow(() -> new IllegalArgumentException("Launch has no plan"));
        long started = countdown.stateStartedGameTime();
        EscrowTags.require(started >= initial.stateStartedGameTime()
                && countdown.state() == RocketFlightState.COUNTDOWN
                && RocketFlightNbtCodec.encode(planned(initial, countdown).startCountdown(started))
                .equals(RocketFlightNbtCodec.encode(countdown)), "Launch changed the checkpointed source flight");
        EscrowTags.require(source.getCompound("snapshot").equals(RocketSnapshotNbtCodec.encode(sourceSnapshot))
                && source.getList("pos", net.minecraft.nbt.Tag.TAG_DOUBLE).equals(position(sourceSnapshot.sourceOrigin(), 0)),
                "Launch source is not at its recorded origin");

        var transit = countdown.completeCountdown(Math.addExact(started, RocketFlightLimits.COUNTDOWN_TICKS))
                .beginTransit(plan.requestId(), Math.addExact(started,
                        (long) RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS));
        var debit = transit.fuel().debit(plan);
        EscrowTags.require(debit.success(), "Launch has no exact native fuel debit");
        var descent = transit.arriveAtDestination(debit.state(), plan.destinationBody(), plan.destinationDimension(),
                destinationSnapshot.sourceOrigin(), Math.addExact(transit.stateStartedGameTime(), RocketFlightLimits.TRANSIT_TICKS));
        var destination = destination(source, wrapper.ledger(), destinationId, destinationSnapshot, descent);
        UUID physical = EscrowTags.uuid(source, "physical"), logical = EscrowTags.uuid(source, "logical"), owner = EscrowTags.uuid(source, "owner");
        String invariant = EscrowInvariant.rocket(physical, logical, owner, EscrowAdapterIds.ROCKET, sourceSnapshot);
        // Both hashes have a canonical, versioned input; neither is caller-supplied provenance.
        var binding = new CompoundTag(); binding.putInt("schema_version", 1);
        binding.putString("invariant", invariant); binding.put("wrapper", wrapper.encode());
        var intent = new CompoundTag(); intent.putInt("schema_version", 1);
        intent.put("countdown", RocketFlightNbtCodec.encode(countdown));
        intent.putString("destination", EscrowNativeEnvelope.digest(destination));
        var launch = new EscrowFlightAuthority.Launch(logical, owner, physical, wrapper,
                initial.fuel().resourceSignature(), invariant, plan.requestId(), destinationId,
                EscrowNativeEnvelope.digest(binding), EscrowNativeEnvelope.digest(intent),
                EscrowNativeEnvelope.digest(source), EscrowNativeEnvelope.digest(destination), journal,
                Set.copyOf(initial.fuel().committedDebits()));
        var prepared = new EscrowGovernedLaunch(launch, source, destination, sourceSnapshot, destinationSnapshot, transit, descent, started);
        prepared.preflight(root, Math.addExact(root.writerGeneration(), 1));
        return prepared;
    }

    /** A landed successor can plan without mutating its durable E before F2. */
    static RocketFlightData planned(RocketFlightData initial, RocketFlightData plannedFlight) {
        EscrowTags.require(initial.activeTransferId().isEmpty() && initial.fuel().propulsion().isPresent()
                && (initial.state() == RocketFlightState.FUELED || initial.state() == RocketFlightState.LANDED),
                "Launch requires stationary native fuel");
        var fueled = initial.state() == RocketFlightState.LANDED
                ? initial.withFuel(initial.fuel(), initial.stateStartedGameTime()) : initial;
        return fueled.withPlan(plannedFlight.plan().orElseThrow());
    }

    EscrowFlightAuthority.Launch launch() { return launch; }

    /** Owner-thread callback boundary. A successful write alone does not prove the still-present E. */
    RocketTransferRecord afterArm(EscrowLedger acknowledged, EscrowFlightTables.Armed arm, CompoundTag observed,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData journal) {
        EscrowTags.require(acknowledged.writerGeneration() >= arm.generation()
                && acknowledged.armedFlights().contains(arm) && EscrowGovernedEnvelope.same(source, observed)
                && EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial()).getAsBoolean(),
                "F1 callback lost its acknowledged arm, exact source or checked no-T state");
        var record = record(arm);
        EscrowTags.require(EscrowGovernedDispatch.decide(acknowledged, record).route() == EscrowGovernedDispatch.Route.ACTIVE,
                "F1 callback lost its current launch authority");
        journal.validateNewRecord(record);
        return record;
    }

    /** Repeat this at writer capture: a batched launch may receive a different serial/generation. */
    EscrowLedger preflight(EscrowLedger root, long generation) {
        var candidate = EscrowFlightAuthority.arm(root, launch, generation);
        var arm = candidate.armedFlights().stream().filter(value -> value.logical().equals(launch.logical())).findFirst().orElseThrow();
        new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData()
                .validateNewRecord(record(arm)); // Complete projection, metadata and record bounds before ARMED.
        EscrowSettlementCapacity.preflight(candidate);
        return candidate;
    }

    /** The caller must still prove this exact arm belongs to an acknowledged root before publishing F2. */
    RocketTransferRecord record(EscrowFlightTables.Armed arm) {
        EscrowTags.require(arm.outcome() == EscrowFlightTables.Outcome.ARMED
                && arm.ledger().equals(launch.wrapper().ledger()) && arm.logical().equals(launch.logical())
                && arm.owner().equals(launch.owner()) && arm.source().equals(launch.source())
                && arm.epoch() == launch.wrapper().epoch() && arm.transfer().equals(launch.transfer())
                && arm.destination().equals(launch.destination()) && arm.journal().equals(launch.journal())
                && arm.binding().equals(launch.binding()) && arm.launch().equals(launch.launch())
                && arm.sourceEnvelope().equals(launch.sourceEnvelope()) && arm.destinationEnvelope().equals(launch.destinationEnvelope()),
                "ARMED no longer names this complete launch");
        var envelope = EscrowTransferEnvelope.capture(arm, source, destination, sourceSnapshot, destinationSnapshot, transit, descent);
        return RocketTransferRecord.createGoverned(launch.transfer(), launch.logical(), launch.owner(), launch.source(),
                sourceSnapshot, destinationSnapshot, transit, descent, transit.plan().orElseThrow().requiredFuel(), started, envelope);
    }

    private static CompoundTag destination(CompoundTag source, UUID ledger, UUID physical,
            RocketStructureSnapshot snapshot, RocketFlightData flight) {
        var result = source.copy();
        result.putUUID("physical", physical); result.putString("level", snapshot.sourceDimension().toString());
        var origin = snapshot.sourceOrigin(); result.putIntArray("chunk", new int[] {origin.x() >> 4, origin.z() >> 4});
        result.put("pos", position(origin, RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS));
        result.putUUID("assembly_snapshot", snapshot.snapshotId()); result.putString("content_hash", snapshot.contentHash());
        result.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot)); result.put("flight", RocketFlightNbtCodec.encode(flight));
        result.put("wrapper", new RocketEscrowSection(ledger, 0, List.of()).encode());
        result.put("passengers", new ListTag()); result.put("motion", vector(0, 0, 0));
        result.putBoolean("no_gravity", true); result.putBoolean("no_physics", true);
        result.putBoolean("on_ground", false); result.putBoolean("invulnerable", false);
        result.putFloat("yaw", 0); result.putFloat("pitch", 0);
        return result;
    }

    private static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(EscrowTags.compound(envelope, "flight")).data()
                .orElseThrow(() -> new IllegalArgumentException("Invalid checkpoint source flight"));
    }
    private static ListTag position(RocketPosition origin, int height) { return vector(origin.x() + 0.5, origin.y() + height, origin.z() + 0.5); }
    private static ListTag vector(double x, double y, double z) {
        var result = new ListTag(); result.add(DoubleTag.valueOf(x)); result.add(DoubleTag.valueOf(y)); result.add(DoubleTag.valueOf(z)); return result;
    }
}
