package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Versioned projections: equal digest strings never make unlike resource projections comparable. */
public enum EscrowSignatureKind {
    FUEL_VECTOR_V1, ROCKET_COMPOSITE_V1, FIXTURE_RESOURCE_V1,
    /** W11A section 1: {@code arce-escrow-fluid-tank-1:}, the device UUID and the native signature of the loader's tank. */
    FLUID_TANK_V1;
    static EscrowSignatureKind endpoint(EscrowEndpoint endpoint) {
        return EscrowAdapterIds.nativeRocket(endpoint) ? FUEL_VECTOR_V1
                : EscrowAdapterIds.nativeLoader(endpoint) ? FLUID_TANK_V1 : FIXTURE_RESOURCE_V1;
    }
}
