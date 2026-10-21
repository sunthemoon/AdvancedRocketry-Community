package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Read-only native device projections for C-E1. Publishing either side belongs to reconciliation. */
final class EscrowItemCargoProjection {
    private EscrowItemCargoProjection() { }
    static EscrowOperation operation(RocketItemCargoBlockEntity device, RocketItemCargoRoot root,
            RocketCargoPreparations.Ticket ticket, EscrowItemCargoPayload delta, EscrowOperation.Kind kind,
            long sequence, RocketTransferSavedData.JournalGuard journal) {
        var rocket = ticket.rocket(); var snapshot = ticket.cursor(); var logical = snapshot.logical();
        var fuel = rocket.flightData().orElseThrow().fuel().resourceSignature(); var chunk = rocket.chunkPosition();
        var level = rocket.level().dimension().location().toString(); var position = device.getBlockPos();
        var nextBuffer = RocketItemCargoMutation.buffer(root.buffer(), delta, kind.cargoLoad());
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, level, root.device(), null,
                "advancedrocketrycommunity:" + io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock.id(root.role()), position.getX(), position.getY(), position.getZ(),
                EscrowEndpoint.Unit.CONTAINER, root.escrow().epoch(), root.signature(), nextBuffer.signature(root.device()));
        var target = rocketEndpoint(level, rocket.getUUID(), logical, chunk.x, chunk.z, rocket.escrowSection().epoch(),
                snapshot.beforeHash(), snapshot.after().contentHash(), fuel);
        var guard = new EscrowGuard(logical, journal.transfer(), Math.max(1, journal.sequence()),
                journal.transfer() == null ? journal.rootDigest() : journal.recordDigest());
        var payload = delta.encode();
        return new EscrowOperation(UUID.randomUUID(), sequence, root.owner(), rocket.level().getGameTime(),
                EscrowOperation.Phase.PREPARED, EscrowReason.NONE, payload, RocketNativeResources.signature(payload),
                kind.cargoLoad() ? source : target, kind.cargoLoad() ? target : source, guard, kind, new CompoundTag());
    }
    /** Pure endpoint projection shared by live admission and its contract test. */
    static EscrowEndpoint rocketEndpoint(String level, UUID physical, UUID logical, int chunkX, int chunkZ, long epoch,
            String beforeHash, String afterHash, String fuel) {
        return new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level, physical, logical, EscrowAdapterIds.ROCKET,
                chunkX, 0, chunkZ, EscrowEndpoint.Unit.CONTAINER, epoch,
                EscrowSignatureKind.composite(logical, beforeHash, fuel), EscrowSignatureKind.composite(logical, afterHash, fuel));
    }
    static CompoundTag after(CompoundTag before, UUID ledger, EscrowOperation op) {
        // R39 native decoding is required even though a durable operation decodes structurally.
        var delta = EscrowItemCargoPayload.decode(op.payload());
        var result = before.copy(); var root = result.getCompound(RocketItemCargoRoot.KEY);
        EscrowItemStackCodec.validateNative(delta.stack());
        root.put("buffer", RocketItemCargoMutation.buffer(EscrowItemBuffer.decode(root.getCompound("buffer")), delta, op.kind().cargoLoad()).encode());
        root.put(RocketItemCargoRoot.ESCROW, new RocketEscrowSection(ledger, op.device().afterEpoch(), List.of(
                new EscrowPrefix.Link(op, op.device(), op.kind().cargoLoad() ? "SOURCE" : "DESTINATION").marker())).encode());
        return result;
    }
}
