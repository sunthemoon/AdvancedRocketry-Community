package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;

/** Cargo-only R82 composition; the original BENCH fixture remains historical. */
final class CargoNativePressureWorkload {
    static final int RECORDS = 64;
    private CargoNativePressureWorkload() { }
    static int component(int record) {
        if (record < 0 || record >= RECORDS) { throw new IllegalArgumentException("Pressure record index"); }
        return record < 8 ? record / 4 * 4 : record;
    }
    static EscrowOperation operation(EscrowOperation base, int record) {
        component(record);
        return record < 8 ? chained(base, record % 4) : base;
    }
    static EscrowOperation chained(EscrowOperation op, int index) {
        var to = op.destination();
        var endpoint = new EscrowEndpoint(to.kind(), to.level(), to.identity(), to.logicalRocket(), to.type(),
                to.x(), to.y(), to.z(), to.unit(), index,
                RocketNativeResources.hash("arce-escrow-fixture-1:" + to.logicalRocket() + ":" + index * 1000),
                RocketNativeResources.hash("arce-escrow-fixture-1:" + to.logicalRocket() + ":" + (index + 1) * 1000));
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                op.payload(), op.payloadHash(), op.source(), endpoint, op.guard());
    }
}
