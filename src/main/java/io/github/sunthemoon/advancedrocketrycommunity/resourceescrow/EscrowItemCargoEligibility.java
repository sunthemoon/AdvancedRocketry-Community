package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/**
 * The item counterpart of {@link EscrowCargoEligibility}: one side-effect-free predicate over the native stack, the
 * framed payload, the complete operation with its O1 reservation and the complete device and snapshot envelopes,
 * before any sequence, queue, reservation or mutation. Under R39 the native stack check runs here, at admission.
 */
final class EscrowItemCargoEligibility {
    /** The item device root (W11CD section 5.1); {@code buffer} holds {@link EscrowItemBuffer}'s encoded form. */
    static final String DEVICE_ROOT = "arce_rocket_item_cargo";
    private EscrowItemCargoEligibility() { }

    static EscrowCargoEligibility.Result check(EscrowLedger root, Collection<EscrowOperation> pending, EscrowOperation operation,
                                               CompoundTag rocketBefore, CompoundTag rocketAfter,
                                               CompoundTag deviceBefore, CompoundTag deviceAfter) {
        try {
            EscrowCargoEligibility.envelope(rocketBefore); EscrowCargoEligibility.envelope(rocketAfter);
            for (var value : List.of(deviceBefore, deviceAfter)) {
                EscrowCargoEligibility.envelope(value); deviceRoot(value);
            }
            boolean load = operation.kind().cargoLoad();
            var delta = delta(operation, load);
            var beforeTag = EscrowTags.compound(rocketBefore, "snapshot");
            var afterTag = EscrowTags.compound(rocketAfter, "snapshot");
            EscrowCargoEligibility.bound(beforeTag, RocketLimits.MAX_TOTAL_NBT_BYTES, 32, 65_536);
            EscrowCargoEligibility.bound(afterTag, RocketLimits.MAX_TOTAL_NBT_BYTES, 32, 65_536);
            var before = RocketSnapshotNbtCodec.decode(beforeTag).snapshot().orElseThrow();
            var computed = RocketItemCargoMutation.apply(before, delta, load);
            EscrowTags.require(RocketSnapshotNbtCodec.encode(computed).equals(afterTag), "Item cargo changed more than its frozen payload");
            device(root, operation, delta, deviceBefore, deviceAfter, load);
            var capture = EscrowCargoCapture.capture(root.id(), operation, rocketBefore, rocketAfter, deviceBefore, deviceAfter);
            try {
                var candidates = new ArrayList<>(pending); candidates.add(capture.operation());
                var prospective = root.append(candidates);
                EscrowSettlementCapacity.requireAdmission(prospective, capture.operation(), capture.before());
                EscrowSettlementCapacity.preflight(prospective, operation.id());
            } catch (IllegalArgumentException full) { return new EscrowCargoEligibility.Result(Status.CARGO_TOO_LARGE, null); }
            return new EscrowCargoEligibility.Result(Status.ELIGIBLE, capture);
        } catch (EscrowCargoEligibility.TooLarge exceeded) { return new EscrowCargoEligibility.Result(Status.CARGO_TOO_LARGE, null); }
        catch (RocketItemCargoMutation.Refusal refused) { return new EscrowCargoEligibility.Result(refused.status(), null); }
        catch (EscrowItemStackCodec.Refusal refused) {
            return new EscrowCargoEligibility.Result(refused.status() == EscrowItemStackCodec.Status.CARGO_TOO_LARGE
                    ? Status.CARGO_TOO_LARGE : Status.INVALID_DATA, null);
        } catch (RocketSnapshotException invalid) {
            return new EscrowCargoEligibility.Result(switch (invalid.code()) {
                case SNAPSHOT_DATA_TOO_LARGE, BLOCK_ENTITY_DATA_TOO_LARGE -> Status.CARGO_TOO_LARGE;
                default -> Status.INVALID_DATA;
            }, null);
        } catch (IllegalArgumentException | ArithmeticException | java.util.NoSuchElementException invalid) {
            return new EscrowCargoEligibility.Result(Status.INVALID_DATA, null);
        }
    }

    /** The framed payload bound, D-K's structural decode, the direction and the native stack check. */
    static EscrowItemCargoPayload delta(EscrowOperation operation, boolean load) {
        EscrowTags.require(operation.kind().itemCargo(), "Not an item cargo operation");
        EscrowCargoEligibility.bound(operation.payload(), EscrowLimits.FLUID_BYTES, 12, 384);
        var delta = EscrowItemCargoPayload.decode(operation.payload());
        delta.requireDirection(load);
        EscrowItemStackCodec.validateNative(delta.stack());
        return delta;
    }

    /**
     * The staged capture's final item step, after the resumable preparation verified both snapshots. Refusals keep
     * the staged path's shared vocabulary: a size refusal is {@link EscrowCargoEligibility.TooLarge}, the rest invalid.
     */
    static void prepared(EscrowLedger root, EscrowOperation operation, RocketCargoPreparation prepared,
                         CompoundTag deviceBefore, CompoundTag deviceAfter) {
        try {
            boolean load = operation.kind().cargoLoad();
            var delta = delta(operation, load);
            EscrowTags.require(prepared.matches(delta, load), "Prepared item cargo delta changed");
            deviceRoot(deviceBefore); deviceRoot(deviceAfter);
            device(root, operation, delta, deviceBefore, deviceAfter, load);
        } catch (EscrowItemStackCodec.Refusal refused) {
            throw refused.status() == EscrowItemStackCodec.Status.CARGO_TOO_LARGE ? new EscrowCargoEligibility.TooLarge() : refused;
        } catch (RocketItemCargoMutation.Refusal refused) {
            throw refused.status() == Status.CARGO_TOO_LARGE ? new EscrowCargoEligibility.TooLarge() : refused;
        }
    }

    static void deviceRoot(CompoundTag envelope) {
        EscrowCargoEligibility.bound(EscrowTags.compound(envelope, DEVICE_ROOT), 32_768, 20, 2_048);
    }

    /** The device's buffer moves by exactly the frozen delta; identity, marker and every other field are unchanged. */
    static void device(EscrowLedger ledger, EscrowOperation op, EscrowItemCargoPayload delta,
                       CompoundTag before, CompoundTag after, boolean load) {
        var a = EscrowTags.compound(before, DEVICE_ROOT); var b = EscrowTags.compound(after, DEVICE_ROOT);
        var endpoint = op.device();
        EscrowTags.require(EscrowAdapterIds.itemCargoDevice(endpoint, load)
                && EscrowTags.string(a, "role").equals(load ? "LOAD" : "UNLOAD")
                && op.owner().equals(EscrowTags.uuid(a, "owner")) && endpoint.identity().equals(EscrowTags.uuid(a, "device"))
                && EscrowTags.integer(a, "schema_version") == 1 && endpoint.type().equals(EscrowTags.string(before, "id"))
                && endpoint.x() == EscrowTags.integer(before, "x") && endpoint.y() == EscrowTags.integer(before, "y")
                && endpoint.z() == EscrowTags.integer(before, "z"), "Item cargo device identity changed");
        var bufferA = EscrowTags.compound(a, "buffer"); var bufferB = EscrowTags.compound(b, "buffer");
        var beforeBuffer = EscrowItemBuffer.decode(bufferA); var afterBuffer = EscrowItemBuffer.decode(bufferB);
        var expected = RocketItemCargoMutation.buffer(beforeBuffer, delta, load);
        EscrowTags.require(expected.encode().equals(bufferB)
                && beforeBuffer.signature(endpoint.identity()).equals(endpoint.beforeSignature())
                && afterBuffer.signature(endpoint.identity()).equals(endpoint.afterSignature()), "Item cargo device delta or signature changed");
        var wrapperA = EscrowTags.compound(a, "resource_escrow"); var wrapperB = EscrowTags.compound(b, "resource_escrow");
        EscrowCargoEligibility.bound(wrapperA, 256, 8, 128); EscrowCargoEligibility.bound(wrapperB, 256, 8, 128);
        var section = RocketEscrowSection.decode(wrapperA);
        EscrowTags.require(section.markers().isEmpty() && section.epoch() == endpoint.beforeEpoch()
                && (section.ledger() == null || ledger.id().equals(section.ledger())), "Item cargo device is held or stale");
        var next = new RocketEscrowSection(ledger.id(), endpoint.afterEpoch(), List.of(
                new EscrowPrefix.Link(op, endpoint, load ? "SOURCE" : "DESTINATION").marker()));
        EscrowTags.require(next.encode().equals(wrapperB), "Item cargo device marker changed");
        var beforeFrame = before.copy(); var afterFrame = after.copy();
        for (String key : List.of("buffer", "resource_escrow")) {
            beforeFrame.getCompound(DEVICE_ROOT).remove(key); afterFrame.getCompound(DEVICE_ROOT).remove(key);
        }
        EscrowTags.require(beforeFrame.equals(afterFrame), "Item cargo changed the device frame");
    }
}
