package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Leaf B3-E's loader endpoint adapter (W11B3-DESIGN sections 3 and 4), the registered production fill source. It reads
 * only the loader's own bounded root, live or from a disk record, projects the one fixed C2 debit, and supplies the C8
 * marker projection. Unlike the gated chest fixture it needs no release-test hooks; unsupported roots are refused,
 * never projected, so recovery waits for repair instead of reading foreign or newer data.
 */
final class EscrowLoaderEndpoint {
    private EscrowLoaderEndpoint() { }

    static boolean is(EscrowEndpoint endpoint) { return EscrowAdapterIds.nativeLoader(endpoint); }

    static BlockPos position(EscrowEndpoint endpoint) { return new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()); }

    /** The supported root inside a loader's saved envelope ({@code saveWithFullMetadata} or a disk entry). */
    static LoaderRoot root(CompoundTag envelope) {
        EscrowTags.require(EscrowAdapterIds.LOADER.equals(envelope.getString("id")) && envelope.contains(LoaderRoot.KEY),
                "Not a fluid fuel loader envelope");
        return LoaderRoot.decode(envelope.get(LoaderRoot.KEY));
    }

    /** The device a disk entry names, or null when it is not a readable loader; used only for the relocation census. */
    static UUID device(CompoundTag entry) {
        if (!EscrowAdapterIds.LOADER.equals(entry.getString("id")) || !entry.contains(LoaderRoot.KEY)) { return null; }
        try { return LoaderRoot.decode(entry.get(LoaderRoot.KEY)).device(); }
        catch (IllegalArgumentException unreadable) { return null; }
    }

    /** One observation of a supported root: a pristine section reads as epoch 0 with no markers, for this ledger. */
    static EscrowSettlementIntent.Observation observation(EscrowEndpoint endpoint, LoaderRoot root, UUID ledger) {
        EscrowTags.require(is(endpoint) && endpoint.identity().equals(root.device()), "Loader device identity changed");
        var section = root.escrow();
        EscrowTags.require(section.ledger() == null || section.ledger().equals(ledger), "Foreign loader ledger");
        return new EscrowSettlementIntent.Observation(endpoint,
                new EscrowPrefix.State(ledger, section.epoch(), root.signature(), section.markers()), section.ledger() != null,
                root.tank().amount(), EscrowSignatureKind.FLUID_TANK_V1, EscrowInvariant.reference(endpoint));
    }

    /** Loaded-only live observation; the caller has already proved the slot FULL, entity-loaded and ticking. */
    static EscrowSettlementIntent.Observation observe(BlockEntity source, EscrowEndpoint endpoint, UUID ledger) {
        if (source == null) { return new EscrowSettlementIntent.Observation(endpoint, null, false, 0); }
        EscrowTags.require(source instanceof FluidFuelLoaderBlockEntity, "Source identity/type changed");
        var loader = (FluidFuelLoaderBlockEntity) source;
        EscrowTags.require(loader.dataStatus() == LoaderRoot.Status.VALID && !loader.duplicate(), "Unsupported or duplicated loader root");
        return observation(endpoint, loader.root().orElseThrow(), ledger);
    }

    /**
     * The fixed C2 debit: exactly the payload leaves the tank, and the section binds this ledger with the operation's
     * SOURCE marker at its after epoch. The owner, device, binding, Fluid and native tag never change.
     */
    static CompoundTag change(UUID ledger, EscrowOperation op, CompoundTag before) {
        var root = root(before); var source = op.source();
        EscrowTags.require(is(source) && root.device().equals(source.identity()), "Debit names another loader");
        var section = root.escrow();
        EscrowTags.require((section.ledger() == null || section.ledger().equals(ledger)) && section.markers().isEmpty()
                && section.epoch() == source.beforeEpoch() && root.signature().equals(source.beforeSignature()),
                "Loader debit needs exact BEFORE");
        var payload = op.payload(); var tank = root.tank();
        EscrowTags.require(!tank.isEmpty() && tank.fluid().toString().equals(payload.getString("FluidName"))
                && Objects.equals(tank.tag(), payload.contains("Tag") ? payload.getCompound("Tag") : null),
                "Loader debit changes its native Fluid");
        var debited = tank.withAmount(Math.subtractExact(tank.amount(), payload.getInt("Amount")));
        var next = new LoaderRoot(root.owner(), root.device(), root.binding(), debited,
                new LoaderEscrowSection(ledger, source.afterEpoch(), List.of(new EscrowPrefix.Link(op, source, "SOURCE").marker())));
        EscrowTags.require(next.signature().equals(source.afterSignature()), "Loader debit differs from recorded frontier");
        var after = before.copy(); after.put(LoaderRoot.KEY, next.encode());
        return after;
    }

    /** C8 and O4 pruning: only markers leave; the tank, identity, ledger and epoch stay exactly as observed. */
    static LoaderEscrowSection pruned(LoaderRoot root, EscrowPrefix.State state) {
        var section = root.escrow();
        EscrowTags.require(state.ledger().equals(section.ledger()) && state.epoch() == section.epoch()
                && state.signature().equals(root.signature()) && section.markers().containsAll(state.markers()),
                "Loader pruning changes more than its markers");
        return new LoaderEscrowSection(state.ledger(), state.epoch(), state.markers());
    }
}
