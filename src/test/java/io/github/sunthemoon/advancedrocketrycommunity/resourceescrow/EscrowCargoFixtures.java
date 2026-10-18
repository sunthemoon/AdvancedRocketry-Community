package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Synthetic native commitments for core tests, not endpoint publication or native storage evidence. */
final class EscrowCargoFixtures {
    static final String ADAPTER = "advancedrocketrycommunity:pressurized_tank_v1";
    static final List<EscrowOperation.Kind> KINDS = List.of(EscrowOperation.Kind.FLUID_CARGO_LOAD, EscrowOperation.Kind.FLUID_CARGO_UNLOAD);
    private EscrowCargoFixtures() { }
    static EscrowCargoPayload.Cursor cursor() { return new EscrowCargoPayload.Cursor(id(900), 1, 2, 3, ADAPTER); }
    static CompoundTag fluid(int blob) {
        var fluid = new CompoundTag(); fluid.putString("FluidName", "minecraft:water"); fluid.putInt("Amount", 1_000);
        if (blob >= 0) { var tag = new CompoundTag(); tag.putByteArray("blob", new byte[blob]); fluid.put("Tag", tag); }
        return fluid;
    }
    static RocketEscrowSection wrapper(int retained) {
        var markers = new ArrayList<EscrowPrefix.Marker>();
        for (int i = 1; i <= retained; i++) { markers.add(new EscrowPrefix.Marker(id(90_000 + i), i, "DESTINATION", i)); }
        return retained == 0 ? RocketEscrowSection.neutral() : new RocketEscrowSection(id(500), retained, markers);
    }
    static EscrowOperation operation(EscrowOperation.Kind kind, long sequence, long logical, int retained, int blob, String level) {
        var payload = new EscrowCargoPayload(cursor(), fluid(blob)).encode();
        var rocket = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level, id(2_000 + logical), id(logical), EscrowAdapterIds.ROCKET,
                0, 0, 0, EscrowEndpoint.Unit.TANK, retained, hash(20), hash(21));
        var device = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, level, id(1_000_000 + sequence), null,
                "advancedrocketrycommunity:rocket_fluid_" + (kind == EscrowOperation.Kind.FLUID_CARGO_LOAD ? "loader" : "unloader"),
                0, 64, 0, EscrowEndpoint.Unit.TANK, 0, hash(30), hash(31));
        boolean load = kind == EscrowOperation.Kind.FLUID_CARGO_LOAD;
        var op = new EscrowOperation(id(sequence), sequence, id(99), 1, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                payload, RocketNativeResources.signature(payload), load ? device : rocket, load ? rocket : device,
                new EscrowGuard(id(logical), null, 1, hash(400)), kind, new CompoundTag());
        return nativeModel(op, wrapper(retained));
    }
    static EscrowOperation operation(EscrowOperation.Kind kind, long sequence, long logical) {
        return operation(kind, sequence, logical, 0, -1, "minecraft:overworld");
    }
    static EscrowOperation guarded(EscrowOperation op) {
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), new EscrowGuard(op.guard().rocket(), id(800_000 + op.sequence()), 1, hash(400)),
                op.kind(), op.settlement(), op.nativeBinding());
    }
    static List<EscrowSettlementIntent.Observation> observations(EscrowLedger root, EscrowOperation op, int retained, int missing, boolean neutral) {
        var result = new ArrayList<EscrowSettlementIntent.Observation>();
        for (var end : List.of(op.source(), op.destination())) {
            boolean rocket = end.equals(op.rocket());
            boolean absent = missing == (rocket ? 1 : 2);
            var markers = new ArrayList<>(rocket ? wrapper(retained).markers() : List.<EscrowPrefix.Marker>of());
            markers.add(new EscrowPrefix.Link(op, end, end.equals(op.source()) ? "SOURCE" : "DESTINATION").marker());
            var state = absent ? null : new EscrowPrefix.State(root.id(), rocket && neutral ? 0 : end.afterEpoch(),
                    rocket ? hash(777) : end.afterSignature(), rocket && neutral ? List.of() : markers);
            result.add(new EscrowSettlementIntent.Observation(end, state, !absent && !(rocket && neutral), absent ? 0 : Long.MAX_VALUE,
                    EscrowSignatureKind.settlement(end), rocket ? hash(778) : EscrowInvariant.reference(end)));
        }
        return result;
    }
    static EscrowSettlementIntent intent(EscrowLedger root, EscrowOperation op, int retained, int missing, boolean neutral) {
        var observations = observations(root, op, retained, missing, neutral);
        var preview = EscrowSettlementIntent.preview(root, op.id(), missing == 0 ? EscrowSettlementIntent.Choice.ACCEPT_CURRENT
                : EscrowSettlementIntent.Choice.ABANDON, id(700), "\0".repeat(128), 10, observations);
        return preview.confirm(preview.actor(), preview.digest(), missing == 0 ? 11 : 210, observations);
    }
    static EscrowSettlementIntent floorIntent(EscrowLedger root, int index) {
        var floor = root.floors().get(index);
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, floor.getLong("resource_epoch"),
                floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, id(30_000 + index), floor, id(700), "operator", 20, observations);
        return preview.confirm(preview.actor(), preview.digest(), 220, observations);
    }
}
