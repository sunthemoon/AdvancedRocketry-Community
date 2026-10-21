package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Typed C0/C2/C8 adapter for the item cargo device, independent of transfer direction. */
final class EscrowItemCargoEndpoint {
    private EscrowItemCargoEndpoint() { }
    static boolean is(EscrowEndpoint endpoint) { return EscrowAdapterIds.itemCargoDevice(endpoint); }
    static BlockPos position(EscrowEndpoint endpoint) { return new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()); }

    static RocketItemCargoRoot root(CompoundTag envelope) {
        for (var role : RocketCargoRole.values()) {
            if (ModIdentity.id(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock.id(role)).toString().equals(envelope.getString("id"))) {
                return RocketItemCargoRoot.decode(envelope.get(RocketItemCargoRoot.KEY), role);
            }
        }
        throw new IllegalArgumentException("Not an item cargo device envelope");
    }

    static UUID device(CompoundTag envelope) {
        try { return root(envelope).device(); }
        catch (IllegalArgumentException unreadable) { return null; }
    }

    static EscrowSettlementIntent.Observation observation(EscrowEndpoint endpoint, RocketItemCargoRoot root, UUID ledger) {
        EscrowTags.require(is(endpoint) && endpoint.identity().equals(root.device())
                && endpoint.type().equals(ModIdentity.id(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock.id(root.role())).toString()), "Cargo device identity/role changed");
        var section = root.escrow();
        EscrowTags.require(section.ledger() == null || section.ledger().equals(ledger), "Foreign cargo device ledger");
        return new EscrowSettlementIntent.Observation(endpoint,
                new EscrowPrefix.State(ledger, section.epoch(), root.signature(), section.markers()), section.ledger() != null,
                0, EscrowSignatureKind.ITEM_BUFFER_V1, EscrowInvariant.reference(endpoint));
    }

    static EscrowSettlementIntent.Observation observe(BlockEntity entity, EscrowEndpoint endpoint, UUID ledger) {
        if (entity == null) { return new EscrowSettlementIntent.Observation(endpoint, null, false, 0); }
        EscrowTags.require(entity instanceof RocketItemCargoBlockEntity, "Cargo device type changed");
        var device = (RocketItemCargoBlockEntity) entity;
        EscrowTags.require(device.dataStatus() == RocketItemCargoRoot.Status.VALID && !device.duplicate(),
                "Unsupported or duplicated cargo device root");
        return observation(endpoint, device.root().orElseThrow(), ledger);
    }

    /** Reuse admission's deterministic delta, but require the exact acknowledged BEFORE and AFTER frontiers. */
    static CompoundTag change(UUID ledger, EscrowOperation op, CompoundTag before) {
        EscrowTags.require(op.kind() == EscrowOperation.Kind.ITEM_CARGO_LOAD
                || op.kind() == EscrowOperation.Kind.ITEM_CARGO_UNLOAD, "Not item cargo");
        EscrowItemStackCodec.validateNative(EscrowItemCargoPayload.decode(op.payload()).stack());
        var root = root(before); var endpoint = op.device(); var observed = observation(endpoint, root, ledger);
        EscrowTags.require(op.owner().equals(root.owner()) && root.role() == (op.kind().cargoLoad() ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD)
                && position(endpoint).equals(new BlockPos(before.getInt("x"), before.getInt("y"), before.getInt("z")))
                && observed.state().epoch() == endpoint.beforeEpoch() && observed.state().markers().isEmpty()
                && observed.state().signature().equals(endpoint.beforeSignature()), "Cargo device requires exact BEFORE");
        var after = EscrowItemCargoProjection.after(before, ledger, op);
        EscrowTags.require(root(after).signature().equals(endpoint.afterSignature()), "Cargo device AFTER differs from recorded frontier");
        return after;
    }

    static RocketEscrowSection pruned(RocketItemCargoRoot root, EscrowPrefix.State state) {
        var section = root.escrow();
        EscrowTags.require(state.ledger().equals(section.ledger()) && state.epoch() == section.epoch()
                && state.signature().equals(root.signature()) && section.markers().containsAll(state.markers()),
                "Cargo pruning changes more than markers");
        return new RocketEscrowSection(state.ledger(), state.epoch(), state.markers());
    }
}
