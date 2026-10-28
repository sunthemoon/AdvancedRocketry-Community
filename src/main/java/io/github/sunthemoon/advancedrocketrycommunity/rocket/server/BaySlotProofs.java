package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/** One D6 attempt: every bounded end slot remains proved together through the D7 guard. Owner-thread scheduling only. */
final class BaySlotProofs {
    record Proof(List<EscrowBarrier.Proof> slots) {
        Proof { slots = List.copyOf(slots); }
        boolean valid() { return !slots.isEmpty() && slots.stream().allMatch(EscrowBarrier.Proof::valid); }
        void release() { slots.forEach(EscrowBarrier.Proof::release); }
    }
    record Result(Proof proof, EscrowReason reason) {
        boolean confirmed() { return proof != null && proof.valid(); }
    }

    private final List<RocketRetirementProof.Slot> slots;
    private final UUID logical, survivor;
    private final CompoundTag frozen;
    private final BooleanSupplier current;
    private final Runnable wake;
    private final Consumer<Result> completion;
    private final List<EscrowBarrier.Proof> proofs = new ArrayList<>();
    private boolean pending, closed;

    BaySlotProofs(List<RocketRetirementProof.Slot> slots, UUID logical, UUID survivor, CompoundTag frozen,
                  BooleanSupplier current, Runnable wake, Consumer<Result> completion) {
        if (slots.isEmpty() || slots.size() > RocketOrdinaryRetirement.MAX_SLOTS
                || slots.stream().distinct().count() != slots.size()) { throw new IllegalArgumentException("Invalid bay census"); }
        this.slots = List.copyOf(slots); this.logical = logical; this.survivor = survivor; this.frozen = frozen;
        this.current = current; this.wake = wake; this.completion = completion;
    }

    BayStepQueue.Result step(RocketRetirementWorld port, long now) {
        if (closed || pending) { return BayStepQueue.Result.IDLE; }
        if (!current.getAsBoolean()) { fail(EscrowReason.BARRIER_UNSTABLE); return BayStepQueue.Result.IDLE; }
        if (proofs.stream().anyMatch(proof -> !proof.valid())) { fail(EscrowReason.SUPERSEDING_SAVE); return BayStepQueue.Result.IDLE; }
        var slot = slots.get(proofs.size());
        if (port.slot(slot) != RocketOrdinaryRetirement.SlotState.LOADED || !port.captureSupported(slot)) {
            fail(EscrowReason.BARRIER_UNSTABLE); return BayStepQueue.Result.IDLE;
        }
        if (!port.captureAvailable(slot, now)) { return BayStepQueue.Result.NEXT_TICK; }
        boolean first = proofs.isEmpty();
        pending = true;
        if (!port.capture(slot, root -> first ? RocketRetirementProof.survivorSlot(root, logical, frozen)
                : RocketRetirementProof.absentSlot(root, logical, survivor), now, ignored -> { }, this::captured)) {
            pending = false; return BayStepQueue.Result.NEXT_TICK;
        }
        return BayStepQueue.Result.IDLE;
    }

    private void captured(EscrowBarrier.Result result) {
        pending = false;
        if (closed) { if (result.proof() != null) { result.proof().release(); } return; }
        if (!result.confirmed()) {
            if (result.proof() != null) { result.proof().release(); }
            fail(result.reason()); return;
        }
        proofs.add(result.proof());
        if (proofs.size() == slots.size()) {
            closed = true;
            completion.accept(new Result(new Proof(proofs), EscrowReason.NONE));
        } else { wake.run(); }
    }

    private void fail(EscrowReason reason) {
        close(); completion.accept(new Result(null, reason));
    }

    void close() { closed = true; proofs.forEach(EscrowBarrier.Proof::release); }
}
