package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Only propulsion fills are admitted. Reserved future kinds have no decoder/admission fallback. */
public record EscrowOperation(UUID id, long sequence, UUID owner, long createdTick, Phase phase,
                              EscrowReason reason, CompoundTag payload, String payloadHash,
                              EscrowEndpoint source, EscrowEndpoint destination, EscrowGuard guard) {
    public enum Phase { PREPARED, STALLED, REPAIR, SETTLING_ACCEPT, SETTLING_ABANDON }

    public EscrowOperation {
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(phase);
        Objects.requireNonNull(reason); Objects.requireNonNull(source); Objects.requireNonNull(destination);
        Objects.requireNonNull(guard);
        EscrowTags.require(sequence > 0 && createdTick >= 0, "Invalid operation counters");
        EscrowTags.bound(payload, EscrowLimits.FLUID_BYTES, 12, 384);
        EscrowTags.fields(payload, "FluidName", "Amount", "Tag");
        EscrowTags.id(EscrowTags.string(payload, "FluidName"));
        int amount = EscrowTags.integer(payload, "Amount");
        EscrowTags.require(amount >= 1 && amount <= 1_000, "Invalid admitted fluid amount");
        if (payload.contains("Tag")) { EscrowTags.compound(payload, "Tag"); }
        var fluid = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(payload.copy());
        EscrowTags.require(!fluid.isEmpty() && fluid.writeToNBT(new CompoundTag()).equals(payload), "Noncanonical or unavailable native Fluid");
        EscrowTags.digest(payloadHash);
        EscrowTags.require(payloadHash.equals(RocketNativeResources.signature(payload)), "Payload hash mismatch");
        EscrowTags.require(source.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY
                && destination.kind() == EscrowEndpoint.Kind.ENTITY
                && source.level().equals(destination.level())
                && guard.rocket().equals(destination.logicalRocket()), "Invalid propulsion endpoints");
        EscrowTags.require(phase != Phase.REPAIR || reason.manual(), "Repair needs a manual reason");
        EscrowTags.require(phase != Phase.STALLED || reason.retryableProofFailure(), "Stalled needs a proof failure reason");
        EscrowTags.require(!reason.manual() || phase == Phase.REPAIR
                || phase == Phase.SETTLING_ACCEPT || phase == Phase.SETTLING_ABANDON, "Manual conflict phase");
        payload = payload.copy();
    }

    @Override public CompoundTag payload() { return payload.copy(); }
    public EscrowOperation withReason(EscrowReason next) {
        if (phase == Phase.SETTLING_ACCEPT || phase == Phase.SETTLING_ABANDON) {
            EscrowTags.require(next == reason, "Settlement cannot become an ordinary retry"); return this;
        }
        EscrowTags.require(!reason.manual() || next == reason, "Manual repair cannot clear through an automatic retry");
        return new EscrowOperation(id, sequence, owner, createdTick,
                next.manual() ? Phase.REPAIR : next.retryableProofFailure() ? Phase.STALLED : Phase.PREPARED,
                next, payload, payloadHash, source, destination, guard);
    }
}
