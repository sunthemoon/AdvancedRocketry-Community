package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Typed projections of the exact native captures. No world lookup, reservation or mutable resource state. */
final class EscrowAccountingCapture {
    private EscrowAccountingCapture() { }

    static EscrowFluidAccount.Vector tank(CompoundTag tank) {
        if (tank.isEmpty()) { return EscrowFluidAccount.Vector.EMPTY; }
        return require(EscrowFluidAccount.Vector.of(require(EscrowFluidAccountCodec.key(tank)), tank.getInt("Amount")));
    }

    static EscrowFluidAccount.Vector fuel(RocketFuelState fuel) {
        var propulsion = fuel.propulsion().orElseThrow(() -> new IllegalArgumentException("Abstract fuel is not fluid accounting"));
        return require(EscrowFluidAccount.Vector.aggregate(List.of(bank(propulsion.primary()), bank(propulsion.oxidizer()))));
    }

    private static EscrowFluidAccount.Unit bank(RocketFuelBank bank) {
        if (bank.branch() == RocketFuelBank.Branch.EMPTY) { return EscrowFluidAccount.Unit.EMPTY; }
        EscrowTags.require(bank.branch() == RocketFuelBank.Branch.FLUID, "Abstract bank is not fluid accounting");
        var nativeFluid = new CompoundTag(); nativeFluid.putString("FluidName", bank.fluidId().toString());
        nativeFluid.putInt("Amount", Math.toIntExact(bank.amount()));
        if (bank.fluidTag() != null) { nativeFluid.put("Tag", bank.fluidTag()); }
        return new EscrowFluidAccount.Unit(require(EscrowFluidAccountCodec.key(nativeFluid)), bank.amount());
    }

    static EscrowFluidAccount.Vector cargo(EscrowOperation op, RocketStructureSnapshot snapshot) {
        var cursor = EscrowCargoPayload.decode(op.payload()).cursor(); cursor.requirePayload(snapshot);
        var block = snapshot.blocks().stream().filter(value -> value.position().x() == cursor.x()
                && value.position().y() == cursor.y() && value.position().z() == cursor.z()).findFirst().orElseThrow();
        var fluid = PressurizedTankRocketAdapter.fluid(block.blockEntityPayload().orElseThrow().data());
        return tank(fluid.writeToNBT(new CompoundTag()));
    }

    static EscrowFluidAccount.Vector nativeVector(EscrowOperation op, EscrowEndpoint endpoint, CompoundTag envelope) {
        if (endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY) {
            return tank(EscrowLoaderEndpoint.is(endpoint) ? EscrowLoaderEndpoint.root(envelope).tank().encode()
                    : EscrowFluidCargoEndpoint.root(envelope).tank().encode());
        }
        if (op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL) {
            return fuel(RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow().fuel());
        }
        return cargo(op, RocketSnapshotNbtCodec.decode(envelope.getCompound("snapshot")).snapshot().orElseThrow());
    }

    static Map<UUID, EscrowFluidAccount.Vector> vectors(List<EscrowOperation> operations, EscrowEndpoint endpoint,
            CompoundTag tank, RocketFuelState fuel, RocketStructureSnapshot snapshot) {
        var values = new java.util.HashMap<UUID, EscrowFluidAccount.Vector>();
        for (var op : operations) {
            if (op.accountingBaseline() == null) { continue; }
            values.put(op.id(), endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY ? tank(tank)
                    : op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? fuel(fuel) : cargo(op, snapshot));
        }
        return Map.copyOf(values);
    }

    static EscrowNativeBinding.Admission bind(EscrowOperation op, RocketEscrowSection wrapper,
            CompoundTag rocketBefore, CompoundTag rocketAfter, CompoundTag deviceBefore, CompoundTag deviceAfter,
            RocketStructureSnapshot before, RocketStructureSnapshot after) {
        boolean sourceDevice = op.source().kind() == EscrowEndpoint.Kind.BLOCK_ENTITY;
        var deviceA = nativeVector(op, op.device(), deviceBefore); var deviceB = nativeVector(op, op.device(), deviceAfter);
        var rocketA = before == null ? nativeVector(op, op.rocket(), rocketBefore) : cargo(op, before);
        var rocketB = after == null ? nativeVector(op, op.rocket(), rocketAfter) : cargo(op, after);
        return bind(op, wrapper, sourceDevice ? deviceA : rocketA, sourceDevice ? rocketA : deviceA,
                sourceDevice ? deviceB : rocketB, sourceDevice ? rocketB : deviceB);
    }

    /** Fuel preparation already owns these immutable native values; do not decode the captured flight a second time. */
    static EscrowNativeBinding.Admission fuel(EscrowOperation op, RocketEscrowSection wrapper,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank source,
            RocketFuelState before, RocketFuelState after) {
        return bind(op, wrapper, tank(source.encode()), fuel(before),
                tank(source.withAmount(source.amount() - op.payload().getInt("Amount")).encode()), fuel(after));
    }

    private static EscrowNativeBinding.Admission bind(EscrowOperation op, RocketEscrowSection wrapper,
            EscrowFluidAccount.Vector sourceBefore, EscrowFluidAccount.Vector destinationBefore,
            EscrowFluidAccount.Vector sourceAfter, EscrowFluidAccount.Vector destinationAfter) {
        var payload = op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? op.payload() : op.payload().getCompound("fluid");
        var baseline = require(EscrowFluidAccount.Baseline.capture(op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL
                ? EscrowFluidAccount.Scope.FUEL : EscrowFluidAccount.Scope.CARGO,
                sourceBefore, destinationBefore,
                require(EscrowFluidAccountCodec.key(payload)), payload.getInt("Amount")));
        EscrowTags.require(baseline.verifyPlan(sourceAfter, destinationAfter)
                == EscrowFluidAccount.Status.OK, "Native accounting plan differs from capture");
        if (op.accountingBaseline() != null) {
            EscrowTags.require(op.accountingBaseline().equals(require(EscrowFluidAccountCodec.baselineDigest(baseline))),
                    "Captured accounting baseline changed");
        }
        var accounted = op.accounting(baseline); EscrowCodec.operation(accounted);
        return new EscrowNativeBinding.Admission(accounted, wrapper, new EscrowFluidAccount.Row(op.id(), baseline, null));
    }

    /** Construct operation and row together, including queued rows, before any mutation or writer request. */
    static EscrowLedger append(EscrowLedger root, List<EscrowOperation> additions, Map<UUID, EscrowFluidAccount.Row> rows) {
        if (root.schema() == 2) { return root.append(additions); }
        var all = new ArrayList<>(root.records()); all.addAll(additions);
        var accounts = new ArrayList<>(root.fluidAccounts());
        for (var op : additions) {
            if (op.accountingBaseline() != null) {
                EscrowTags.require(rows.containsKey(op.id()), "Missing captured account"); accounts.add(rows.get(op.id()));
            }
        }
        return new EscrowLedger(root.id(), Math.max(root.issuedThrough(), additions.stream().mapToLong(EscrowOperation::sequence).max().orElse(0)),
                root.writerGeneration(), all, root.floors(), root.tombstones(), root.issuedLaunchSerial(),
                root.rocketAuthorities(), root.armedFlights(), 3, accounts);
    }

    static EscrowLedger append(EscrowLedger root, List<EscrowOperation> pending, EscrowNativeBinding.Admission capture) {
        var all = new ArrayList<>(pending); all.add(capture.operation());
        return append(root, all, capture.account() == null ? Map.of() : Map.of(capture.operation().id(), capture.account()));
    }

    private static <T> T require(EscrowFluidAccount.Result<T> result) {
        EscrowTags.require(result.ok(), "Native accounting refused: " + result.status()); return result.value();
    }
}
