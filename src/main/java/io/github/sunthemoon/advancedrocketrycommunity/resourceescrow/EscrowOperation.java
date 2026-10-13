package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Fluid admissions and explicit operator floor intents; neither enables production admission. */
public record EscrowOperation(UUID id, long sequence, UUID owner, long createdTick, Phase phase,
                              EscrowReason reason, CompoundTag payload, String payloadHash,
                              EscrowEndpoint source, EscrowEndpoint destination, EscrowGuard guard,
                              Kind kind, CompoundTag settlement) {
    public enum Kind { FLUID_PROPULSION_FILL, TRANSFER_FLOOR_RETIRE }
    public enum Phase { PREPARED, STALLED, REPAIR, SETTLING_ACCEPT, SETTLING_ABANDON }

    public EscrowOperation(UUID id, long sequence, UUID owner, long createdTick, Phase phase,
                           EscrowReason reason, CompoundTag payload, String payloadHash,
                           EscrowEndpoint source, EscrowEndpoint destination, EscrowGuard guard) {
        this(id, sequence, owner, createdTick, phase, reason, payload, payloadHash, source, destination, guard,
                Kind.FLUID_PROPULSION_FILL, new CompoundTag());
    }

    public EscrowOperation {
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(phase);
        Objects.requireNonNull(reason); Objects.requireNonNull(source); Objects.requireNonNull(destination);
        Objects.requireNonNull(guard); Objects.requireNonNull(kind); Objects.requireNonNull(settlement);
        EscrowTags.require(sequence > 0 && createdTick >= 0, "Invalid operation counters");
        EscrowTags.bound(payload, EscrowLimits.FLUID_BYTES, 12, 384);
        if (kind == Kind.FLUID_PROPULSION_FILL) {
            EscrowTags.fields(payload, "FluidName", "Amount", "Tag");
            EscrowTags.id(EscrowTags.string(payload, "FluidName"));
            int amount = EscrowTags.integer(payload, "Amount");
            EscrowTags.require(amount >= 1 && amount <= 1_000, "Invalid admitted fluid amount");
            if (payload.contains("Tag")) { EscrowTags.compound(payload, "Tag"); }
            var fluid = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(payload.copy());
            EscrowTags.require(!fluid.isEmpty() && fluid.writeToNBT(new CompoundTag()).equals(payload), "Noncanonical or unavailable native Fluid");
            EscrowTags.require(source.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY
                    && destination.kind() == EscrowEndpoint.Kind.ENTITY
                    && source.level().equals(destination.level())
                    && guard.rocket().equals(destination.logicalRocket()), "Invalid propulsion endpoints");
        } else {
            EscrowCodec.validateSection(payload, false);
            EscrowTags.require(source.equals(destination) && destination.kind() == EscrowEndpoint.Kind.ENTITY
                    && destination.identity().equals(payload.getUUID("entity_id"))
                    && destination.logicalRocket().equals(payload.getUUID("rocket_id"))
                    && destination.beforeEpoch() == payload.getLong("resource_epoch")
                    && destination.beforeSignature().equals(payload.getString("signature"))
                    && destination.afterSignature().equals(payload.getString("signature"))
                    && guard.equals(EscrowCodec.floorGuard(payload)) && guard.transfer() != null
                    && phase == Phase.SETTLING_ABANDON && reason == EscrowReason.FLOOR_CONFLICT,
                    "Invalid floor retirement intent");
        }
        EscrowTags.digest(payloadHash);
        EscrowTags.require(payloadHash.equals(RocketNativeResources.signature(payload)), "Payload hash mismatch");
        EscrowTags.require(phase != Phase.REPAIR || reason.manual(), "Repair needs a manual reason");
        EscrowTags.require(phase != Phase.STALLED || reason.retryableProofFailure(), "Stalled needs a proof failure reason");
        EscrowTags.require(!reason.manual() || phase == Phase.REPAIR
                || phase == Phase.SETTLING_ACCEPT || phase == Phase.SETTLING_ABANDON, "Manual conflict phase");
        if (!settlement.isEmpty()) {
            var intent = EscrowSettlementIntent.decode(settlement);
            intent.requireConfirmed();
            EscrowTags.require(intent.ids().contains(id)
                    && phase == (intent.choice() == EscrowSettlementIntent.Choice.ACCEPT_CURRENT
                    ? Phase.SETTLING_ACCEPT : Phase.SETTLING_ABANDON), "Settlement phase/member mismatch");
            EscrowTags.require(kind != Kind.TRANSFER_FLOOR_RETIRE
                    || intent.choice() == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE, "Floor settlement choice mismatch");
        } else { EscrowTags.require(kind != Kind.TRANSFER_FLOOR_RETIRE, "Floor retirement needs a frozen intent"); }
        payload = payload.copy(); settlement = settlement.copy();
    }

    @Override public CompoundTag payload() { return payload.copy(); }
    @Override public CompoundTag settlement() { return settlement.copy(); }
    public EscrowOperation settle(EscrowSettlementIntent intent) {
        EscrowTags.require(kind == Kind.FLUID_PROPULSION_FILL && settlement.isEmpty(), "Already settling");
        return new EscrowOperation(id, sequence, owner, createdTick,
                intent.choice() == EscrowSettlementIntent.Choice.ACCEPT_CURRENT ? Phase.SETTLING_ACCEPT : Phase.SETTLING_ABANDON,
                reason, payload, payloadHash, source, destination, guard, kind, intent.encode());
    }
    public EscrowOperation withReason(EscrowReason next) {
        if (phase == Phase.SETTLING_ACCEPT || phase == Phase.SETTLING_ABANDON) {
            EscrowTags.require(next == reason, "Settlement cannot become an ordinary retry"); return this;
        }
        EscrowTags.require(!reason.manual() || next == reason, "Manual repair cannot clear through an automatic retry");
        return new EscrowOperation(id, sequence, owner, createdTick,
                next.manual() ? Phase.REPAIR : next.retryableProofFailure() ? Phase.STALLED : Phase.PREPARED,
                next, payload, payloadHash, source, destination, guard, kind, settlement);
    }
}
