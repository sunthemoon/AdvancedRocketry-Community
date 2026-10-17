package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** C2 authority is private, one-use, owner-thread and bound to both complete current captures. */
public final class EscrowReconciliationAction {
    private EscrowReconciliationAction() { }
    public record Change(RocketFlightData flight, RocketEscrowSection wrapper) { }

    public static final class Permit {
        private final Thread owner = Thread.currentThread();
        private final EscrowOperation operation;
        private final boolean destination;
        private final CompoundTag before, after;
        private final BooleanSupplier authority;
        private boolean consumed;
        private Permit(EscrowOperation operation, boolean destination, CompoundTag before, CompoundTag after, BooleanSupplier authority) {
            this.operation = operation; this.destination = destination; this.before = before.copy(); this.after = after.copy(); this.authority = authority;
        }
        public Change consume(RocketEntity rocket) {
            EscrowTags.require(destination && rocket.getUUID().equals(operation.destination().identity()), "Fill permit names another endpoint");
            var value = consume(() -> EscrowNativeEnvelope.rocket(rocket));
            return new Change(RocketFlightNbtCodec.decode(value.getCompound("flight")).data().orElseThrow(),
                    RocketEscrowSection.decode(value.getCompound("wrapper")));
        }
        CompoundTag consume(Supplier<CompoundTag> capture) {
            EscrowTags.require(Thread.currentThread() == owner && !consumed, "Fill permit is off-thread or already consumed");
            consumed = true;
            EscrowTags.require(authority.getAsBoolean() && before.equals(capture.get()), "Fill permit lost its exact authority or capture");
            return after.copy();
        }
    }

    static Permit issue(EscrowLedger root, EscrowOperation operation, CompoundTag before, boolean destination, BooleanSupplier authority) {
        EscrowTags.require(root.records().contains(operation) && !operation.reason().manual() && operation.settlement().isEmpty()
                && authority.getAsBoolean(), "Fill needs an acknowledged ordinary member");
        CompoundTag after;
        try { after = destination ? EscrowReconciliationChange.rocket(root.id(), operation, before)
                : EscrowReconciliationChange.fixture(root.id(), operation, before); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
        }
        if (EscrowReconciliationValidation.action(root, operation, before, after, destination) != EscrowReason.NONE) {
            throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
        }
        return new Permit(operation, destination, before, after, authority);
    }

    static void apply(EscrowReconciliationView view, EscrowOperation op, boolean destination, Permit permit) {
        var end = view.ends.get((destination ? op.destination() : op.source()).key());
        var runtime = EscrowRuntime.of(end.level().getServer());
        runtime.checkpoint(destination ? "C2_DESTINATION_BEFORE" : "C2_SOURCE_BEFORE");
        if (destination) { end.rocket().applyEscrowFill(permit); }
        else {
            EscrowTestEndpoints.requireEnabled(end.level().getServer());
            var after = permit.consume(end.source()::saveWithFullMetadata).getCompound("ForgeData");
            var persistent = end.source().getPersistentData();
            persistent.put("arce_escrow_fixture", after.getCompound("arce_escrow_fixture").copy());
            persistent.put("resource_escrow", after.getCompound("resource_escrow").copy());
            end.source().setChanged();
        }
        var reference = end.reference();
        runtime.monitor().observed(reference.level(), destination, destination
                ? new net.minecraft.world.level.ChunkPos(reference.x(), reference.z())
                : new net.minecraft.world.level.ChunkPos(new net.minecraft.core.BlockPos(reference.x(), reference.y(), reference.z())));
        runtime.checkpoint(destination ? "C2_DESTINATION_AFTER" : "C2_SOURCE_AFTER");
    }
}
