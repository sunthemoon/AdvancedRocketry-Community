package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** Pure frozen-delta replay. Capacity is checked at capture, never reinterpreted during replay. */
public final class RocketCargoMutation {
    private RocketCargoMutation() { }

    /** Synchronous pure replay, not a tick-budgeted server preparation or publication entry point. */
    public static RocketStructureSnapshot apply(RocketStructureSnapshot before, EscrowCargoPayload delta, boolean load) {
        var after = reconstruct(before, delta, load);
        verifyRoundTrip(RocketSnapshotNbtCodec.encode(after));
        return after;
    }

    static RocketStructureSnapshot reconstruct(RocketStructureSnapshot before, EscrowCargoPayload delta, boolean load) {
        delta.cursor().requirePayload(before);
        if (before.propulsion().isEmpty() || !PressurizedTankRocketAdapter.ID.toString().equals(delta.cursor().adapter())) {
            throw new IllegalArgumentException("Cargo requires modern propulsion and the tank adapter");
        }
        var blocks = new ArrayList<RocketBlock>(before.blocks().size());
        long total = 0;
        for (var block : before.blocks()) {
            var payload = block.blockEntityPayload().orElse(null);
            if (matches(block, delta.cursor())) {
                if (!block.state().blockId().equals(PressurizedTankRocketAdapter.TYPE)) {
                    throw new IllegalArgumentException("Cargo cursor does not name a pressurized tank block");
                }
                var fluid = PressurizedTankRocketAdapter.fluid(payload.data());
                var moved = FluidStack.loadFluidStackFromNBT(delta.fluid());
                payload = PressurizedTankRocketAdapter.payload(change(fluid, moved, load));
                block = new RocketBlock(block.position(), block.state(), payload);
            }
            if (payload != null) {
                total += payload.uncompressedBytes();
                if (total > RocketLimits.MAX_TOTAL_NBT_BYTES || !BoundedNbt.fits(payload.data(),
                        RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES, 32, 65_536)) {
                    throw new RocketSnapshotException(RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE,
                            "Cargo payload aggregate exceeded");
                }
            }
            blocks.add(block);
        }
        var after = RocketStructureSnapshot.create(before.snapshotId(), before.sourceDimension(), before.sourceOrigin(),
                blocks, before.passengerAnchors(), before.stats(), before.createdAtGameTime(), before.propulsion().orElseThrow());
        if (!before.structureDigest().equals(after.structureDigest())) {
            throw new IllegalArgumentException("Cargo changed structure or propulsion");
        }
        return after;
    }

    /** Shared native tank arithmetic; all nonzero tags must match, and no overflow is representable. */
    public static FluidStack change(FluidStack before, FluidStack moved, boolean add) {
        if (moved.isEmpty() || moved.getAmount() < 1 || moved.getAmount() > 1_000
                || (!before.isEmpty() && !before.isFluidEqual(moved))
                || (!add && (before.isEmpty() || before.getAmount() < moved.getAmount()))) {
            throw new IllegalArgumentException("Incompatible cargo delta");
        }
        int amount = Math.addExact(before.getAmount(), add ? moved.getAmount() : -moved.getAmount());
        var result = amount == 0 ? FluidStack.EMPTY : moved.copy();
        if (!result.isEmpty()) { result.setAmount(amount); }
        TankSave.encode(result);
        return result;
    }

    static boolean matches(RocketBlock block, EscrowCargoPayload.Cursor cursor) {
        return block.position().x() == cursor.x() && block.position().y() == cursor.y() && block.position().z() == cursor.z();
    }

    static RocketStructureSnapshot verifyRoundTrip(CompoundTag encoded) {
        if (!BoundedNbt.fits(encoded, RocketLimits.MAX_TOTAL_NBT_BYTES, 32, 65_536)) {
            throw new RocketSnapshotException(RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                    "Cargo snapshot envelope exceeded");
        }
        var decoded = RocketSnapshotNbtCodec.decode(encoded).snapshot().orElseThrow(
                () -> new IllegalArgumentException("Cargo snapshot cannot round-trip"));
        if (!RocketSnapshotNbtCodec.encode(decoded).equals(encoded)) {
            throw new IllegalArgumentException("Cargo snapshot changed on round-trip");
        }
        return decoded;
    }
}
