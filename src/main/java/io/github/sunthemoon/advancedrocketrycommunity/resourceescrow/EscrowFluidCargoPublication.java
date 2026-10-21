package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** Device-side C2 and C8 authority. Only the reconciliation package can issue a permit. */
public final class EscrowFluidCargoPublication {
    private EscrowFluidCargoPublication() { }

    public static final class Permit {
        private final EscrowCargoPublication.Use use;
        private final CompoundTag before;
        private final RocketFluidCargoRoot after;
        private final BooleanSupplier authority;
        private Permit(int tick, CompoundTag before, RocketFluidCargoRoot after, BooleanSupplier authority) {
            use = new EscrowCargoPublication.Use(tick); this.before = before.copy(); this.after = after; this.authority = authority;
        }
        public RocketFluidCargoRoot consume(RocketFluidCargoBlockEntity device) {
            EscrowTags.require(device.live(), "Cargo publication requires the live owner-thread device");
            return consume(device.getLevel().getServer().getTickCount(), device::saveWithFullMetadata);
        }
        RocketFluidCargoRoot consume(int tick, Supplier<CompoundTag> capture) {
            use.consume(tick, () -> authority.getAsBoolean() && before.equals(capture.get()));
            return after;
        }
    }

    static Permit issue(EscrowLedger ledger, EscrowOperation op, CompoundTag before, int tick, BooleanSupplier authority) {
        EscrowTags.require(ledger.records().contains(op) && !op.reason().manual() && op.settlement().isEmpty()
                && op.nativeBinding().complete() && op.nativeBinding().kind() == EscrowSignatureKind.ROCKET_COMPOSITE_V1
                && authority.getAsBoolean(), "Cargo publication needs an acknowledged ordinary member");
        op.nativeBinding().requireOperation(op.owner(), op.source(), op.destination());
        EscrowReconciliationAction.requireNativeFluid(op);
        var after = EscrowFluidCargoEndpoint.change(ledger.id(), op, before); var binding = op.nativeBinding();
        boolean load = op.kind().cargoLoad();
        EscrowTags.require(EscrowNativeEnvelope.digest(before).equals(load ? binding.sourceBefore() : binding.destinationBefore())
                && EscrowNativeEnvelope.digest(after).equals(load ? binding.sourceAfter() : binding.destinationAfter()),
                "Cargo device publication differs from frozen native envelopes");
        return new Permit(tick, before, EscrowFluidCargoEndpoint.root(after), authority);
    }

    public static final class PruningPermit {
        private final EscrowCargoPublication.Use use;
        private final RocketFluidCargoBlockEntity target;
        private final CompoundTag before;
        private final EscrowPrefix.State state;
        private final BooleanSupplier authority;
        PruningPermit(RocketFluidCargoBlockEntity target, EscrowPrefix.State state, BooleanSupplier authority) {
            EscrowTags.require(target.live(), "Cargo pruning requires the live owner-thread device");
            this.target = target; this.state = state; this.authority = authority; before = target.saveWithFullMetadata();
            use = new EscrowCargoPublication.Use(target.getLevel().getServer().getTickCount());
        }
        public RocketEscrowSection consume(RocketFluidCargoBlockEntity device) {
            EscrowTags.require(device.live(), "Cargo pruning requires the live owner-thread device");
            use.consume(device.getLevel().getServer().getTickCount(), () -> device == target && authority.getAsBoolean()
                    && before.equals(device.saveWithFullMetadata()));
            return EscrowFluidCargoEndpoint.pruned(device.root().orElseThrow(), state);
        }
    }
}
