package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/**
 * W116 (W98-R12): the ordinary retirement coordinator's server side: the survivor and its census slots, the crew, the
 * shared escrow barrier, the bay and escrow rules, the flight tickets and the journal's continuation. Owned by one
 * transfer service's recovery; the bay dispatch is that service's, passed by reference.
 */
class RocketRetirementWorld implements RocketOrdinaryRetirement.Port {
    protected final MinecraftServer server;
    protected final RocketTransferBayDispatch bays;

    RocketRetirementWorld(MinecraftServer server, RocketTransferBayDispatch bays) { this.server = server; this.bays = bays; }

    @Override public long now() { return server.getTickCount(); }

    @Override
    public RocketOrdinaryRetirement.Presence survivor(RocketTransferRecord record, UUID survivor, RocketRetirementProof.Slot slot) {
        var level = RocketTransferEntities.level(server, slot.dimension());
        if (level != null && level.getEntity(survivor) instanceof RocketEntity) { return RocketOrdinaryRetirement.Presence.LOADED; }
        return level != null && level.areEntitiesLoaded(slot.chunk().toLong())
                ? RocketOrdinaryRetirement.Presence.GONE : RocketOrdinaryRetirement.Presence.UNLOADED;
    }

    @Override
    public boolean crewReady(RocketTransferRecord record, UUID survivor, boolean offlineAllowed, Set<UUID> delivered) {
        var rocket = rocket(record, survivor);
        return rocket != null && RocketRetirementGuards.crewReady(server, record, rocket, offlineAllowed, delivered);
    }

    @Override
    public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
        var level = RocketTransferEntities.level(server, slot.dimension());
        if (level == null || !level.areEntitiesLoaded(slot.chunk().toLong())) { return RocketOrdinaryRetirement.SlotState.UNLOADED; }
        return level.noSave() ? RocketOrdinaryRetirement.SlotState.SAVE_OFF : RocketOrdinaryRetirement.SlotState.LOADED;
    }

    @Override
    public boolean saveOff(RocketRetirementProof.Slot slot) {
        var level = RocketTransferEntities.level(server, slot.dimension());
        return level != null && level.noSave();
    }

    @Override
    public boolean captureSupported(RocketRetirementProof.Slot slot) {
        var runtime = EscrowRuntime.of(server);
        var level = RocketTransferEntities.level(server, slot.dimension());
        return level == null || runtime.store(level).entityCaptureSupported();
    }

    @Override
    public boolean captureAvailable(RocketRetirementProof.Slot slot, long now) {
        return EscrowRuntime.of(server).barrier().availableOrdinary(slot.dimension().toString(), now);
    }

    @Override
    public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long now, Consumer<String> sink,
                           Consumer<EscrowBarrier.Result> callback) {
        var runtime = EscrowRuntime.of(server);
        var level = RocketTransferEntities.level(server, slot.dimension());
        return runtime.barrier().beginOrdinary(runtime.store(level), slot.dimension().toString(), slot.chunk(), validator, now, sink, callback);
    }

    @Override public void retryNextTick(Runnable retry) { EscrowRuntime.of(server).barrier().retryOrdinaryNextTick(retry); }

    @Override
    public boolean bayAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferBayRecoveryGate.Action action) {
        return bays.allowed(server, journal, record, action);
    }

    @Override public boolean bayClaimed(UUID transfer) { return bays.ids().contains(transfer); }

    @Override
    public boolean escrowAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferSavedData.Reservation own) {
        return RocketTransferEscrowRules.flightAllowed(server, journal, record, bays, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL, own);
    }

    @Override
    public CompoundTag projection(RocketTransferRecord record, UUID survivor) {
        var rocket = rocket(record, survivor);
        return rocket == null ? null : RocketRetirementGuards.projection(rocket, record);
    }

    /** W90: never a governed or margin ticket. W98-R3: the destination's origin column too, when the census holds it. */
    @Override
    public void keepLoaded(RocketTransferRecord record, List<RocketRetirementProof.Slot> census) {
        var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        RocketTransferEntities.keepLoaded(level, record.sourceSnapshot(), record.transferId());
        var destination = record.destinationSnapshot();
        var destinationLevel = RocketTransferEntities.level(server, destination.sourceDimension());
        if (destinationLevel != null && census.contains(RocketRetirementProof.Slot.origin(destination))) {
            RocketTransferEntities.refreshOriginTicket(destinationLevel, destination, record.transferId());
        }
    }

    @Override public Consumer<String> sink() { return RocketRetirementTestHook.sink(server); }

    @Override
    public boolean afterAcknowledged(RocketTransferSavedData journal, RocketTransferSavedData.Reservation token,
                                     BooleanSupplier effect, Runnable discarded) {
        return journal.afterAcknowledged(server, token, effect, discarded);
    }

    protected RocketEntity rocket(RocketTransferRecord record, UUID survivor) {
        var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        return level != null && level.getEntity(survivor) instanceof RocketEntity rocket ? rocket : null;
    }
}
