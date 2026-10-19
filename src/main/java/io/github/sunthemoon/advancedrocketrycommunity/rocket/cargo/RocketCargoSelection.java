package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** Canonical server-side selection. The supplied pure check must include the complete capture/O1 envelopes. */
public final class RocketCargoSelection {
    public enum Status { ELIGIBLE, NO_CARGO_STORAGE, UNSUPPORTED_DATA, CARGO_TOO_LARGE, INVALID_DATA }
    public record Result(Status status, EscrowCargoPayload delta) { }
    private RocketCargoSelection() { }

    public static Result select(RocketStructureSnapshot snapshot, FluidStack device, boolean load, int tankCapacity,
                                Function<EscrowCargoPayload, Status> eligibility) {
        Objects.requireNonNull(eligibility);
        if (tankCapacity <= 0 || device.getAmount() > 16_000 || !TankSave.fits(device)) {
            return new Result(Status.INVALID_DATA, null);
        }
        Status refusal = Status.NO_CARGO_STORAGE;
        // Loading prefers existing compatible contents even when an earlier empty tank exists.
        for (int pass = 0; pass < (load ? 2 : 1); pass++) {
            for (var block : snapshot.blocks()) {
                var payload = block.blockEntityPayload().orElse(null);
                if (payload == null || !PressurizedTankRocketAdapter.ID.equals(payload.adapterId())
                        || !PressurizedTankRocketAdapter.TYPE.equals(block.state().blockId())) { continue; }
                FluidStack tank;
                try { tank = PressurizedTankRocketAdapter.fluid(payload.data()); }
                catch (IllegalArgumentException invalid) { refusal = Status.UNSUPPORTED_DATA; continue; }
                if (load && (pass == 0) == tank.isEmpty()) { continue; }
                var source = load ? device : tank;
                var destination = load ? tank : device;
                long room = (long) (load ? tankCapacity : 16_000) - destination.getAmount();
                if (source.isEmpty() || room <= 0 || !destination.isEmpty() && !destination.isFluidEqual(source)) { continue; }
                int amount = (int) Math.min(1_000L, Math.min(source.getAmount(), room));
                var moved = source.copy(); moved.setAmount(amount);
                EscrowCargoPayload delta;
                try {
                    var p = block.position();
                    delta = new EscrowCargoPayload(new EscrowCargoPayload.Cursor(snapshot.snapshotId(), p.x(), p.y(), p.z(),
                            payload.adapterId().toString()), moved.writeToNBT(new CompoundTag()));
                } catch (IllegalArgumentException invalid) { refusal = Status.CARGO_TOO_LARGE; continue; }
                Status status = Objects.requireNonNull(eligibility.apply(delta));
                if (status == Status.ELIGIBLE) { return new Result(status, delta); }
                refusal = status;
            }
        }
        return new Result(refusal, null);
    }
}
