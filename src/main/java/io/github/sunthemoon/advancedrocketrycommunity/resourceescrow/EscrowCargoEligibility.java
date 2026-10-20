package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.Collection;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** Shared capture-envelope rules; the staged path uses verified resumable snapshots instead of rehashing. */
final class EscrowCargoEligibility {
    static final String DEVICE_ROOT = "arce_rocket_fluid_cargo";
    private EscrowCargoEligibility() { }

    record Result(Status status, EscrowNativeBinding.Admission capture, EscrowCoordinator.Transfer refusal) {
        Result(Status status, EscrowNativeBinding.Admission capture) {
            this(status, capture, status == Status.ELIGIBLE ? null : status == Status.CARGO_TOO_LARGE
                    ? EscrowCoordinator.Transfer.CARGO_TOO_LARGE : EscrowCoordinator.Transfer.INVALID);
        }
        static Result refused(EscrowCoordinator.Transfer refusal) {
            return new Result(refusal == EscrowCoordinator.Transfer.CARGO_TOO_LARGE ? Status.CARGO_TOO_LARGE
                    : Status.INVALID_DATA, null, refusal);
        }
    }

    static Result check(EscrowLedger root, Collection<EscrowOperation> pending, EscrowOperation operation,
                        CompoundTag rocketBefore, CompoundTag rocketAfter,
                        CompoundTag deviceBefore, CompoundTag deviceAfter, int tankCapacity) {
        try {
            envelope(rocketBefore); envelope(rocketAfter);
            for (var value : java.util.List.of(deviceBefore, deviceAfter)) {
                envelope(value);
                bound(EscrowTags.compound(value, DEVICE_ROOT), 32_768, 20, 2_048);
            }
            bound(operation.payload(), EscrowLimits.FLUID_BYTES, 12, 384);
            var delta = EscrowCargoPayload.decode(operation.payload());
            EscrowTags.require(operation.kind().cargo() && tankCapacity > 0, "Invalid cargo kind or capacity");
            boolean load = operation.kind() == EscrowOperation.Kind.FLUID_CARGO_LOAD;
            var beforeTag = EscrowTags.compound(rocketBefore, "snapshot");
            var afterTag = EscrowTags.compound(rocketAfter, "snapshot");
            bound(beforeTag, RocketLimits.MAX_TOTAL_NBT_BYTES, 32, 65_536);
            bound(afterTag, RocketLimits.MAX_TOTAL_NBT_BYTES, 32, 65_536);
            var before = RocketSnapshotNbtCodec.decode(beforeTag).snapshot().orElseThrow();
            var computed = RocketCargoMutation.apply(before, delta, load);
            EscrowTags.require(RocketSnapshotNbtCodec.encode(computed).equals(afterTag), "Cargo changed more than its frozen payload");
            var target = computed.blocks().stream().filter(block -> block.position().x() == delta.cursor().x()
                    && block.position().y() == delta.cursor().y() && block.position().z() == delta.cursor().z()).findFirst().orElseThrow();
            var afterFluid = io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter
                    .fluid(target.blockEntityPayload().orElseThrow().data());
            // A frozen replay does not recheck capacity; fresh eligibility always does.
            EscrowTags.require(!load || afterFluid.getAmount() <= tankCapacity, "Cargo capture exceeds tank capacity");
            device(root, operation, delta, deviceBefore, deviceAfter, load);
            var capture = EscrowCargoCapture.capture(root.id(), operation, rocketBefore, rocketAfter, deviceBefore, deviceAfter);
            var refusal = EscrowCargoRefusals.capacity(root, pending, capture);
            if (refusal.isPresent()) { return Result.refused(refusal.get()); }
            return new Result(Status.ELIGIBLE, capture);
        } catch (TooLarge exceeded) { return new Result(Status.CARGO_TOO_LARGE, null); }
        catch (EscrowTags.TooLarge exceeded) { return new Result(Status.CARGO_TOO_LARGE, null); }
        catch (EscrowSettlementCapacity.StaleCapture stale) { return Result.refused(EscrowCoordinator.Transfer.STALE); }
        catch (io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException invalid) {
            return new Result(switch (invalid.code()) {
                case SNAPSHOT_DATA_TOO_LARGE, BLOCK_ENTITY_DATA_TOO_LARGE -> Status.CARGO_TOO_LARGE;
                default -> Status.INVALID_DATA;
            }, null);
        }
        catch (IllegalArgumentException | ArithmeticException | java.util.NoSuchElementException invalid) {
            return new Result(Status.INVALID_DATA, null);
        }
    }

    static void device(EscrowLedger ledger, EscrowOperation op, EscrowCargoPayload delta,
                               CompoundTag before, CompoundTag after, boolean load) {
        var a = EscrowTags.compound(before, DEVICE_ROOT); var b = EscrowTags.compound(after, DEVICE_ROOT);
        var endpoint = op.device();
        EscrowTags.require(EscrowAdapterIds.fluidCargoDevice(endpoint)
                && endpoint.type().equals("advancedrocketrycommunity:rocket_fluid_" + (load ? "loader" : "unloader"))
                && EscrowTags.string(a, "role").equals(load ? "LOAD" : "UNLOAD")
                && op.owner().equals(EscrowTags.uuid(a, "owner")) && endpoint.identity().equals(EscrowTags.uuid(a, "device"))
                && EscrowTags.integer(a, "schema_version") == 1 && endpoint.type().equals(EscrowTags.string(before, "id"))
                && endpoint.x() == EscrowTags.integer(before, "x") && endpoint.y() == EscrowTags.integer(before, "y")
                && endpoint.z() == EscrowTags.integer(before, "z"), "Cargo device identity changed");
        var tankA = EscrowTags.compound(a, "tank"); var tankB = EscrowTags.compound(b, "tank");
        bound(tankA, LoaderTank.MAX_BYTES, LoaderTank.MAX_DEPTH, LoaderTank.MAX_NODES);
        bound(tankB, LoaderTank.MAX_BYTES, LoaderTank.MAX_DEPTH, LoaderTank.MAX_NODES);
        var beforeTank = LoaderTank.decode(tankA); var afterTank = LoaderTank.decode(tankB);
        var source = tankA.isEmpty() ? FluidStack.EMPTY : FluidStack.loadFluidStackFromNBT(tankA);
        var expected = LoaderTank.of(RocketCargoMutation.change(source, FluidStack.loadFluidStackFromNBT(delta.fluid()), !load));
        EscrowTags.require(expected.encode().equals(tankB) && beforeTank.signature(endpoint.identity()).equals(endpoint.beforeSignature())
                && afterTank.signature(endpoint.identity()).equals(endpoint.afterSignature()), "Cargo device delta or signature changed");
        var wrapperA = EscrowTags.compound(a, "resource_escrow"); var wrapperB = EscrowTags.compound(b, "resource_escrow");
        bound(wrapperA, 256, 8, 128); bound(wrapperB, 256, 8, 128);
        var section = RocketEscrowSection.decode(wrapperA);
        EscrowTags.require(section.markers().isEmpty() && section.epoch() == endpoint.beforeEpoch()
                && (section.ledger() == null || ledger.id().equals(section.ledger())), "Cargo device is held or stale");
        var next = new RocketEscrowSection(ledger.id(), endpoint.afterEpoch(), java.util.List.of(
                new EscrowPrefix.Link(op, endpoint, load ? "SOURCE" : "DESTINATION").marker()));
        EscrowTags.require(next.encode().equals(wrapperB), "Cargo device marker changed");
        var beforeFrame = before.copy(); var afterFrame = after.copy();
        for (String key : java.util.List.of("tank", "resource_escrow")) {
            beforeFrame.getCompound(DEVICE_ROOT).remove(key); afterFrame.getCompound(DEVICE_ROOT).remove(key);
        }
        EscrowTags.require(beforeFrame.equals(afterFrame), "Cargo changed the device frame");
    }

    static void envelope(CompoundTag value) { bound(value, EscrowNativeEnvelope.MAX_BYTES, 24, 131_072); }
    static void bound(net.minecraft.nbt.Tag value, int bytes, int depth, int nodes) {
        if (!BoundedNbt.fits(value, bytes, depth, nodes)) { throw new TooLarge(); }
    }
    static final class TooLarge extends IllegalArgumentException { }
}
