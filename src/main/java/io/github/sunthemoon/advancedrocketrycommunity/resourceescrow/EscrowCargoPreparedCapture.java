package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Bounded capture checks shared by selection/admission. No sequence, reservation, queue or endpoint changes. */
final class EscrowCargoPreparedCapture {
    private final RocketCargoPreparation prepared;
    private final EscrowOperation operation;
    private final CompoundTag[] inputs;
    private final CompoundTag[] owned = new CompoundTag[4];
    private final String[] digests = new String[4];
    private final int[] sizes = new int[4];
    private final int capacity;
    private int step;
    private boolean discarded;
    private EscrowCargoEligibility.Result result;

    /** Input roots are borrowed only until each is copied under a reservation; final capture must recheck them. */
    EscrowCargoPreparedCapture(RocketCargoPreparation prepared, EscrowOperation operation,
            CompoundTag rocketBefore, CompoundTag rocketAfter, CompoundTag deviceBefore, CompoundTag deviceAfter, int capacity) {
        this.prepared = prepared; this.operation = operation;
        inputs = new CompoundTag[] {rocketBefore, rocketAfter, deviceBefore, deviceAfter}; this.capacity = capacity;
    }

    boolean advance(long tick, RocketCargoWorkBudget budget, EscrowLedger root, List<EscrowOperation> pending) {
        EscrowTags.require(!discarded, "Cargo capture was discarded");
        if (result != null) { return true; }
        if (prepared.state() != RocketCargoPreparation.State.READY || !budget.claim(tick, prepared)) { return false; }
        while (result == null) {
            int visits = step == 4 || step == 5 ? 8 * prepared.before().blocks().size()
                    : 3 * prepared.before().blocks().size();
            try {
                if (!budget.perform(tick, 8L * 1_048_576, visits, false,
                        () -> advanceStep(root, pending, visits))) { return false; }
            } catch (EscrowCargoEligibility.TooLarge exceeded) {
                result = new EscrowCargoEligibility.Result(Status.CARGO_TOO_LARGE, null);
            } catch (IllegalArgumentException | java.util.NoSuchElementException | ArithmeticException invalid) {
                result = new EscrowCargoEligibility.Result(Status.INVALID_DATA, null);
            }
        }
        return true;
    }

    private Work advanceStep(EscrowLedger root, List<EscrowOperation> pending, int visits) {
        if (step < 4) {
            EscrowCargoEligibility.envelope(inputs[step]);
            owned[step] = inputs[step].copy();
            sizes[step] = RocketNbtSize.uncompressedBytes(owned[step]);
            long bytes = 3L * sizes[step]; step++;
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
        var capture = EscrowCargoCapture.bind(root.id(), operation, owned[0], owned[1], owned[2], owned[3],
                prepared.before(), prepared.after(), digests[0], digests[1], digests[2], digests[3]);
        try {
            var candidates = new ArrayList<>(pending); candidates.add(capture.operation());
            var prospective = root.append(candidates);
            EscrowSettlementCapacity.requireAdmission(prospective, capture.operation(), capture.before());
            EscrowSettlementCapacity.preflight(prospective, operation.id());
        } catch (IllegalArgumentException full) {
            result = new EscrowCargoEligibility.Result(Status.CARGO_TOO_LARGE, null);
            return new Work(8L * 1_048_576, visits);
        }
        result = new EscrowCargoEligibility.Result(Status.ELIGIBLE, capture);
        return new Work(8L * 1_048_576, visits);
    }
    EscrowCargoEligibility.Result result() {
        if (result == null) { throw new IllegalStateException("Cargo capture is incomplete"); }
        return result;
    }
    /** Changed input requires a new full preparation; an old after snapshot never follows a changed before state. */
    boolean unchanged() {
        if (discarded) { return false; }
        for (int i = 0; i < inputs.length; i++) {
            EscrowCargoEligibility.envelope(inputs[i]);
            if (!owned[i].equals(inputs[i])) { return false; }
        }
        return true;
    }
    void discard() {
        discarded = true; prepared.close();
        java.util.Arrays.fill(inputs, null); java.util.Arrays.fill(owned, null);
    }
}
