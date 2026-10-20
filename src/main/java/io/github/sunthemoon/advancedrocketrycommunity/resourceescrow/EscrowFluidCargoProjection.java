package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** Read-only native device projections for C-E1. Publishing either side belongs to reconciliation. */
final class EscrowFluidCargoProjection {
    private EscrowFluidCargoProjection() { }
    static EscrowOperation operation(RocketFluidCargoBlockEntity device, RocketFluidCargoRoot root,
            RocketCargoPreparations.Ticket ticket, EscrowCargoPayload delta, EscrowOperation.Kind kind,
            long sequence, RocketTransferSavedData.JournalGuard journal) {
        var rocket = ticket.rocket(); var snapshot = ticket.cursor(); var logical = snapshot.logical();
        var fuel = rocket.flightData().orElseThrow().fuel().resourceSignature(); var chunk = rocket.chunkPosition();
        var level = rocket.level().dimension().location().toString(); var position = device.getBlockPos();
        var nextTank = changed(root.tank(), delta, kind.cargoLoad());
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, level, root.device(), null,
                "advancedrocketrycommunity:" + root.role().id(), position.getX(), position.getY(), position.getZ(),
                EscrowEndpoint.Unit.TANK, root.escrow().epoch(), root.signature(), nextTank.signature(root.device()));
        var target = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level, rocket.getUUID(), logical, EscrowAdapterIds.ROCKET,
                chunk.x, 0, chunk.z, EscrowEndpoint.Unit.TANK, rocket.escrowSection().epoch(),
                EscrowSignatureKind.composite(logical, snapshot.beforeHash(), fuel),
                EscrowSignatureKind.composite(logical, snapshot.after().contentHash(), fuel));
        var guard = new EscrowGuard(logical, journal.transfer(), Math.max(1, journal.sequence()),
                journal.transfer() == null ? journal.rootDigest() : journal.recordDigest());
        var payload = delta.encode();
        return new EscrowOperation(UUID.randomUUID(), sequence, root.owner(), rocket.level().getGameTime(),
                EscrowOperation.Phase.PREPARED, EscrowReason.NONE, payload, RocketNativeResources.signature(payload),
                kind.cargoLoad() ? source : target, kind.cargoLoad() ? target : source, guard, kind, new CompoundTag());
    }
    static CompoundTag after(CompoundTag before, UUID ledger, EscrowOperation op) {
        // R39 native decoding is required even though a durable operation decodes structurally.
        var delta = EscrowCargoPayload.decode(op.payload());
        var result = before.copy(); var root = result.getCompound(RocketFluidCargoRoot.KEY);
        root.put("tank", changed(LoaderTank.decode(root.getCompound("tank")), delta, op.kind().cargoLoad()).encode());
        root.put(RocketFluidCargoRoot.ESCROW, new RocketEscrowSection(ledger, op.device().afterEpoch(), List.of(
                new EscrowPrefix.Link(op, op.device(), op.kind().cargoLoad() ? "SOURCE" : "DESTINATION").marker())).encode());
        return result;
    }
    private static LoaderTank changed(LoaderTank before, EscrowCargoPayload delta, boolean load) {
        return LoaderTank.of(RocketCargoMutation.change(before.amount() == 0 ? FluidStack.EMPTY
                : FluidStack.loadFluidStackFromNBT(before.encode()), FluidStack.loadFluidStackFromNBT(delta.fluid()), !load));
    }
}
