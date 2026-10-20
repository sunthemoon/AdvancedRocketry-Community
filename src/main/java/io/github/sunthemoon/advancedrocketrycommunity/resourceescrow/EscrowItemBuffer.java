package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Immutable typed four-slot signature projection, not a mutable inventory or device save format. */
public record EscrowItemBuffer(List<CompoundTag> slots) {
    public static final int SLOTS = 4;
    public EscrowItemBuffer {
        EscrowTags.require(slots != null && slots.size() == SLOTS, "Item buffer must contain four slots");
        for (var slot : slots) {
            EscrowTags.require(slot != null, "Null item buffer slot");
            if (!slot.isEmpty()) { EscrowItemStackCodec.decode(slot); }
        }
        slots = slots.stream().map(CompoundTag::copy).toList();
    }
    @Override public List<CompoundTag> slots() { return slots.stream().map(CompoundTag::copy).toList(); }
    public CompoundTag encode() {
        var list = new ListTag(); slots.forEach(slot -> list.add(slot.copy()));
        var root = new CompoundTag(); root.put("slots", list); return root;
    }
    public String signature(UUID device) {
        java.util.Objects.requireNonNull(device);
        return RocketNativeResources.hash("arce-escrow-item-buffer-1:" + device + ":" + RocketNativeResources.signature(encode()));
    }
    /** Pure O1 projection of a typed buffer. It does not observe a live device or prove a save. */
    public EscrowSettlementIntent.Observation observation(EscrowEndpoint endpoint, UUID ledger, RocketEscrowSection wrapper) {
        EscrowTags.require(EscrowAdapterIds.itemCargoDevice(endpoint)
                && (wrapper.ledger() == null || wrapper.ledger().equals(ledger)), "Invalid item observation identity");
        return new EscrowSettlementIntent.Observation(endpoint,
                new EscrowPrefix.State(ledger, wrapper.epoch(), signature(endpoint.identity()), wrapper.markers()),
                wrapper.ledger() != null, 0, EscrowSignatureKind.ITEM_BUFFER_V1, EscrowInvariant.reference(endpoint));
    }
    public static EscrowItemBuffer decode(CompoundTag root) {
        // Four local maxima plus the list/root framing; this is not the future device-envelope limit.
        EscrowItemStackCodec.bound(root, SLOTS * EscrowItemStackCodec.MAX_BYTES + 16, 18, SLOTS * EscrowItemStackCodec.MAX_NODES + 2);
        EscrowTags.fields(root, "slots");
        return new EscrowItemBuffer(EscrowTags.list(root, "slots", SLOTS).stream().map(tag -> (CompoundTag) tag).toList());
    }
}
