package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Fixed C2 projections: only the selected fuel bank/revision and the next wrapper link change. */
final class EscrowReconciliationChange {
    private EscrowReconciliationChange() { }

    static CompoundTag rocket(UUID ledger, EscrowOperation op, CompoundTag before) {
        var flight = RocketFlightNbtCodec.decode(before.getCompound("flight")).data().orElseThrow();
        var fuel = flight.fuel(); var propulsion = fuel.propulsion().orElseThrow();
        var unit = op.destination().unit();
        EscrowTags.require(unit == EscrowEndpoint.Unit.PRIMARY || unit == EscrowEndpoint.Unit.OXIDIZER, "Unsupported fill bank");
        boolean oxidizer = unit == EscrowEndpoint.Unit.OXIDIZER;
        var bank = oxidizer ? propulsion.oxidizer() : propulsion.primary();
        var payload = op.payload(); var fluid = ResourceLocation.tryParse(payload.getString("FluidName"));
        var tag = payload.contains("Tag") ? payload.getCompound("Tag") : null;
        EscrowTags.require(bank.branch() == RocketFuelBank.Branch.EMPTY || bank.branch() == RocketFuelBank.Branch.FLUID
                && bank.fluidId().equals(fluid) && Objects.equals(bank.fluidTag(), tag), "Fill changes existing native fluid");
        var changed = fuel.withFluidBank(oxidizer, fluid, tag, Math.addExact(bank.amount(), payload.getInt("Amount")));
        var wrapper = RocketEscrowSection.decode(before.getCompound("wrapper"));
        EscrowTags.require((wrapper.ledger() == null || ledger.equals(wrapper.ledger()))
                && wrapper.epoch() == op.destination().beforeEpoch(), "Fill needs exact epoch");
        var markers = new ArrayList<>(wrapper.markers());
        markers.add(new EscrowPrefix.Link(op, op.destination(), "DESTINATION").marker());
        var after = before.copy();
        after.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight, changed)));
        after.put("wrapper", new RocketEscrowSection(ledger, op.destination().afterEpoch(), markers).encode());
        return after;
    }

    static CompoundTag fixture(UUID ledger, EscrowOperation op, CompoundTag before) {
        var after = before.copy(); var persistent = after.getCompound("ForgeData");
        var state = EscrowTestEndpoints.state(persistent, ledger, false);
        EscrowTags.require(state.epoch() == op.source().beforeEpoch() && state.signature().equals(op.source().beforeSignature())
                && state.markers().isEmpty(), "Fixture debit needs exact BEFORE");
        int amount = Math.subtractExact(EscrowTestEndpoints.amount(persistent), op.payload().getInt("Amount"));
        EscrowTags.require(amount >= 0, "Fixture debit exceeds resources");
        persistent.getCompound("arce_escrow_fixture").putInt("amount", amount);
        EscrowTestEndpoints.putState(persistent, new EscrowPrefix.State(ledger, op.source().afterEpoch(), op.source().afterSignature(),
                java.util.List.of(new EscrowPrefix.Link(op, op.source(), "SOURCE").marker())), false);
        EscrowTags.require(EscrowTestEndpoints.state(persistent, ledger, false).signature().equals(op.source().afterSignature()),
                "Fixture debit differs from recorded frontier");
        return after;
    }
}
