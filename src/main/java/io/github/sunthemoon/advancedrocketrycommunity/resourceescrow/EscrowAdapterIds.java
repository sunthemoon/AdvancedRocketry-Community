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
}
