package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations.Ticket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Bounded capture checks shared by selection/admission. No sequence, reservation, queue or endpoint changes. */
final class EscrowCargoPreparedCapture {
    private final Ticket ticket;
    private final RocketCargoPreparation prepared;
    private final EscrowOperation operation;
    private final CompoundTag[] inputs;
    private List<java.util.function.Supplier<CompoundTag>> captures;
    private java.util.function.BooleanSupplier current;
    private final CompoundTag[] owned = new CompoundTag[4];
    private final String[] digests = new String[4];
    private final int[] sizes = new int[4];
    private final int capacity;
    private int step;
    private boolean discarded;
    private EscrowCargoEligibility.Result result;

    /** Input roots are borrowed only until each is copied under a reservation; final capture must recheck them. */
    EscrowCargoPreparedCapture(Ticket ticket, EscrowOperation operation,
            CompoundTag rocketBefore, CompoundTag rocketAfter, CompoundTag deviceBefore, CompoundTag deviceAfter, int capacity) {
        this.ticket = ticket; this.prepared = ticket.cursor(); this.operation = operation;
        inputs = new CompoundTag[] {rocketBefore, rocketAfter, deviceBefore, deviceAfter}; this.capacity = capacity;
        captures = null; current = () -> true;
    }

    /** Native encoders run here, inside the reservation, rather than in the caller before preparation starts. */
    EscrowCargoPreparedCapture(Ticket ticket, EscrowOperation operation,
            List<java.util.function.Supplier<CompoundTag>> captures, java.util.function.BooleanSupplier current, int capacity) {
        EscrowTags.require(captures.size() == 4, "Cargo needs four native captures");
        this.ticket = ticket; this.prepared = ticket.cursor(); this.operation = operation; this.captures = List.copyOf(captures);
        this.current = current; this.capacity = capacity; inputs = new CompoundTag[4];
    }

    /** C-E's native entry: it supplies only its two device projections and exact unchanged-device predicate. */
    static EscrowCargoPreparedCapture nativeCapture(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations.Ticket ticket,
            java.util.UUID ledger, EscrowOperation operation, java.util.function.Supplier<CompoundTag> deviceBefore,
            java.util.function.Supplier<CompoundTag> deviceAfter, java.util.function.BooleanSupplier deviceCurrent, int capacity) {
        var frame = new CompoundTag[1];
        var rocket = ticket.rocket();
        java.util.function.Supplier<CompoundTag> before = () -> {
            EscrowTags.require(ticket.valid(), "Cargo native capture lost its preparation lock");
            frame[0] = EscrowNativeEnvelope.frame(rocket);
            return EscrowNativeEnvelope.rocket(rocket);
        };
        java.util.function.Supplier<CompoundTag> after = () -> {
            var endpoint = operation.rocket(); var markers = new ArrayList<>(rocket.escrowSection().markers());
            markers.add(new EscrowPrefix.Link(operation, endpoint,
                    operation.kind().cargoLoad() ? "DESTINATION" : "SOURCE").marker());
            return EscrowNativeEnvelope.rocket(rocket, ticket.cursor().after(), rocket.flightData().orElseThrow(),
                    new RocketEscrowSection(ledger, endpoint.afterEpoch(), markers));
        };
        return new EscrowCargoPreparedCapture(ticket, operation, List.of(before, after, deviceBefore, deviceAfter),
                () -> ticket.valid() && deviceCurrent.getAsBoolean()
                        && (frame[0] == null || frame[0].equals(EscrowNativeEnvelope.frame(rocket))), capacity);
    }

    boolean advance(EscrowLedger root, List<EscrowOperation> pending) {
        EscrowTags.require(!discarded, "Cargo capture was discarded");
        if (result != null) { return true; }
        if (prepared.failure().isPresent()) {
            result = new EscrowCargoEligibility.Result(prepared.failure().orElseThrow(), null);
            discard(); return true;
        }
        if (!ticket.valid()) {
            result = new EscrowCargoEligibility.Result(Status.INVALID_DATA, null);
            discard(); return true;
        }
        if (prepared.state() != RocketCargoPreparation.State.READY) { return false; }
        while (result == null) {
            int visits = step == 4 || step == 5 || step < 4 && captures != null ? 8 * prepared.before().blocks().size()
                    : 3 * prepared.before().blocks().size();
            try {
                if (!ticket.performWork(8L * 1_048_576, visits,
                        () -> advanceStep(root, pending, visits))) { return false; }
            } catch (EscrowCargoEligibility.TooLarge exceeded) {
                result = new EscrowCargoEligibility.Result(Status.CARGO_TOO_LARGE, null);
                discard();
            } catch (EscrowTags.TooLarge exceeded) {
                result = EscrowCargoEligibility.Result.refused(EscrowCoordinator.Transfer.CARGO_TOO_LARGE);
                discard();
            } catch (EscrowSettlementCapacity.StaleCapture stale) {
                result = EscrowCargoEligibility.Result.refused(EscrowCoordinator.Transfer.STALE);
                discard();
            } catch (io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation.Refusal refused) {
                result = new EscrowCargoEligibility.Result(refused.status(), null);
                discard();
            } catch (io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException invalid) {
                result = new EscrowCargoEligibility.Result(switch (invalid.code()) {
                    case SNAPSHOT_DATA_TOO_LARGE, BLOCK_ENTITY_DATA_TOO_LARGE -> Status.CARGO_TOO_LARGE;
                    default -> Status.INVALID_DATA;
                }, null);
                discard();
            } catch (IllegalArgumentException | java.util.NoSuchElementException | ArithmeticException invalid) {
                result = new EscrowCargoEligibility.Result(Status.INVALID_DATA, null);
                discard();
            }
        }
        return true;
    }

    private Work advanceStep(EscrowLedger root, List<EscrowOperation> pending, int visits) {
        EscrowTags.require(current.getAsBoolean(), "Cargo input changed during capture");
        if (step < 4) {
            if (captures != null) { inputs[step] = captures.get(step).get(); }
            EscrowCargoEligibility.envelope(inputs[step]);
            owned[step] = inputs[step].copy();
            sizes[step] = RocketNbtSize.uncompressedBytes(owned[step]);
            long bytes = (captures == null ? 3L : 7L) * sizes[step]; step++;
            return new Work(bytes, visits);
        }
        if (step == 4 || step == 5) {
            var expected = step == 4 ? prepared.before() : prepared.after();
            var envelope = owned[step - 4];
            EscrowCargoCapture.snapshot(operation, envelope, expected);
            long bytes = 7L * sizes[step - 4]; step++;
            return new Work(bytes, visits);
        }
        if (step < 10) {
            int at = step - 6;
            digests[at] = EscrowNativeEnvelope.digest(owned[at]); step++;
            return new Work(3L * sizes[at], visits);
        }
        if (operation.kind().itemCargo()) {
            EscrowItemCargoEligibility.prepared(root, operation, prepared, owned[2], owned[3]);
        } else { fluid(root); }
        var capture = EscrowCargoCapture.bind(root.id(), operation, owned[0], owned[1], owned[2], owned[3],
                prepared.before(), prepared.after(), digests[0], digests[1], digests[2], digests[3]);
        var refusal = EscrowCargoRefusals.capacity(root, pending, capture);
        if (refusal.isPresent()) {
            result = EscrowCargoEligibility.Result.refused(refusal.get());
            return new Work(8L * 1_048_576, visits);
        }
        result = new EscrowCargoEligibility.Result(Status.ELIGIBLE, capture);
        return new Work(8L * 1_048_576, visits);
    }
    private void fluid(EscrowLedger root) {
        var delta = EscrowCargoPayload.decode(operation.payload());
        boolean loading = operation.kind() == EscrowOperation.Kind.FLUID_CARGO_LOAD;
        EscrowTags.require(operation.kind().cargo() && capacity > 0 && prepared.matches(delta, loading), "Prepared cargo delta changed");
        EscrowCargoEligibility.bound(operation.payload(), EscrowLimits.FLUID_BYTES, 12, 384);
        for (var value : List.of(owned[2], owned[3])) {
            EscrowCargoEligibility.bound(EscrowTags.compound(value, EscrowCargoEligibility.DEVICE_ROOT), 32_768, 20, 2_048);
        }
        var target = prepared.after().blocks().stream().filter(block -> block.position().x() == delta.cursor().x()
                && block.position().y() == delta.cursor().y() && block.position().z() == delta.cursor().z()).findFirst().orElseThrow();
        var fluid = PressurizedTankRocketAdapter.fluid(target.blockEntityPayload().orElseThrow().data());
        EscrowTags.require(!loading || fluid.getAmount() <= capacity, "Cargo capture exceeds tank capacity");
        EscrowCargoEligibility.device(root, operation, delta, owned[2], owned[3], loading);
    }
    EscrowCargoEligibility.Result result() {
        if (result == null) { throw new IllegalStateException("Cargo capture is incomplete"); }
        return result;
    }
    boolean admission(java.util.function.Supplier<Work> work) {
        return ticket.performWork(16L * 1_048_576, 16_384, work);
    }
    boolean valid() { return ticket.valid(); }
    /** Changed input requires a new full preparation; an old after snapshot never follows a changed before state. */
    boolean unchanged() {
        if (discarded || !ticket.valid()) { return false; }
        if (captures != null) { return current.getAsBoolean(); }
        for (int i = 0; i < inputs.length; i++) {
            EscrowCargoEligibility.envelope(inputs[i]);
            if (!owned[i].equals(inputs[i])) { return false; }
        }
        return true;
    }
    void discard() {
        discarded = true; ticket.close();
        java.util.Arrays.fill(inputs, null); java.util.Arrays.fill(owned, null);
        captures = null; current = () -> false;
    }
}
