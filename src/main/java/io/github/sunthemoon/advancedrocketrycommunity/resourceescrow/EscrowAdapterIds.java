package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;

/** Stable adapter identity shared by prefix keys and typed resource/invariant projections. */
final class EscrowAdapterIds {
    static final String ROCKET = ModIdentity.id("rocket").toString();
    /** The fluid fuel loader's block-entity type: the one registered production fill source (W11B3-DESIGN section 3). */
    static final String LOADER = ModIdentity.id("fluid_fuel_loader").toString();
    private EscrowAdapterIds() { }
    static boolean nativeRocket(EscrowEndpoint endpoint) {
        return endpoint.kind() == EscrowEndpoint.Kind.ENTITY && endpoint.type().equals(ROCKET);
    }
    /** The loader's one native tank; it is only ever the source of a propulsion fill. */
    static boolean nativeLoader(EscrowEndpoint endpoint) {
        return endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY && endpoint.type().equals(LOADER)
                && endpoint.unit() == EscrowEndpoint.Unit.TANK;
    }
    /** Reserved C-A identities; recognizing a projection does not register a device or enable admission. */
    static boolean fluidCargoDevice(EscrowEndpoint endpoint) {
        return endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY && endpoint.unit() == EscrowEndpoint.Unit.TANK
                && (endpoint.type().equals(ModIdentity.id("rocket_fluid_loader").toString())
                || endpoint.type().equals(ModIdentity.id("rocket_fluid_unloader").toString()));
    }
    /** Reserved D-A identities; recognizing them grants no runtime admission. */
    static boolean itemCargoDevice(EscrowEndpoint endpoint) {
        return endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY && endpoint.unit() == EscrowEndpoint.Unit.CONTAINER
                && (endpoint.type().equals(ModIdentity.id("rocket_item_loader").toString())
                || endpoint.type().equals(ModIdentity.id("rocket_item_unloader").toString()));
    }
}
