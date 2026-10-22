package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** C2 authority is private, one-use, owner-thread and bound to both complete current captures. */
public final class EscrowReconciliationAction {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
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
        /** The loader's debit: its exact current envelope in, the fixed AFTER root out, once. */
        public CompoundTag consume(io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity loader) {
            EscrowTags.require(!destination && EscrowLoaderEndpoint.is(operation.source())
                    && loader.getBlockPos().equals(EscrowLoaderEndpoint.position(operation.source())), "Fill permit names another endpoint");
            return consume(loader::saveWithFullMetadata).getCompound(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot.KEY);
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
        requireNativeFluid(operation);
        CompoundTag after;
        try { after = destination ? EscrowReconciliationChange.rocket(root.id(), operation, before)
                : EscrowReconciliationChange.source(root.id(), operation, before); }
        catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) {
            throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
        }
        if (EscrowReconciliationValidation.action(root, operation, before, after, destination) != EscrowReason.NONE) {
            throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
        }
        return new Permit(operation, destination, before, after, authority);
    }

    /** Shared R39 boundary; callers must run this before any native projection or publication. */
    static void requireNativeResource(EscrowOperation operation) {
        if (!operation.kind().itemCargo()) { requireNativeFluid(operation); return; }
        CompoundTag stack;
        try { stack = EscrowItemCargoPayload.decode(operation.payload()).stack(); }
        catch (IllegalArgumentException invalid) { throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE); }
        try { EscrowItemStackCodec.validateNative(stack); }
        catch (IllegalArgumentException unavailable) {
            // Structural saved data stays intact. The scheduler marks only this member REPAIR and never revisits it,
            // so, as for fluids, this logs at most once per operation and session. The ID passed the structural check.
            LOGGER.warn("ARCE_ESCROW_ITEM_UNAVAILABLE op={} item={}", operation.id(), stack.getString("id"));
            throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
        }
    }

    /** Fluid logging and the existing fill boundary remain unchanged. */
    static void requireNativeFluid(EscrowOperation operation) {
        if (operation.kind().fluid()) {
            try { operation.nativeFluid(); }
            catch (IllegalArgumentException unavailable) {
                // R39 for fluids: holds this operation only (sticky REPAIR through the scheduler), before any projection.
                // The scheduler never revisits a manual record, so this logs at most once per operation and session.
                reportFluidUnavailable(operation);
                throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
            }
        }
    }

    /** The one R39 WARN; each caller logs it at most once per operation and session. */
    static void reportFluidUnavailable(EscrowOperation operation) {
        LOGGER.warn("ARCE_ESCROW_FLUID_UNAVAILABLE op={} fluid={}", operation.id(), fluidName(operation));
    }

    /** The structurally checked ID: canonical, printable and at most 128 characters. */
    private static String fluidName(EscrowOperation op) {
        var payload = op.payload();
        return (op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? payload : payload.getCompound("fluid")).getString("FluidName");
    }

    static void apply(EscrowReconciliationView view, EscrowOperation op, boolean destination, Permit permit) {
        var end = view.ends.get((destination ? op.destination() : op.source()).key());
        var runtime = EscrowRuntime.of(end.level().getServer());
        runtime.checkpoint(destination ? "C2_DESTINATION_BEFORE" : "C2_SOURCE_BEFORE");
        if (destination) { end.rocket().applyEscrowFill(permit); }
        else if (end.source() instanceof io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity loader
                && EscrowLoaderEndpoint.is(op.source())) { loader.applyEscrowDebit(permit); }
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
