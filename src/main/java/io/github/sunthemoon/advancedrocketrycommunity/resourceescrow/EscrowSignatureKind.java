package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Versioned projections: equal digest strings never make unlike resource projections comparable. */
public enum EscrowSignatureKind {
    FUEL_VECTOR_V1, ROCKET_COMPOSITE_V1, FIXTURE_RESOURCE_V1,
    /** W11A section 1: {@code arce-escrow-fluid-tank-1:}, the device UUID and the native signature of the loader's tank. */
    FLUID_TANK_V1, ITEM_BUFFER_V1;
    static EscrowSignatureKind endpoint(EscrowEndpoint endpoint) {
        return EscrowAdapterIds.nativeRocket(endpoint)
                ? endpoint.unit() == EscrowEndpoint.Unit.TANK || endpoint.unit() == EscrowEndpoint.Unit.CONTAINER
                    ? ROCKET_COMPOSITE_V1 : FUEL_VECTOR_V1
                : EscrowAdapterIds.itemCargoDevice(endpoint) ? ITEM_BUFFER_V1
                : EscrowAdapterIds.nativeLoader(endpoint) || EscrowAdapterIds.fluidCargoDevice(endpoint) ? FLUID_TANK_V1 : FIXTURE_RESOURCE_V1;
    }
    /** O1 keeps its fuel projection, even when the endpoint reference contains composite frontiers. */
    static EscrowSignatureKind settlement(EscrowEndpoint endpoint) {
        return EscrowAdapterIds.nativeRocket(endpoint) ? FUEL_VECTOR_V1 : endpoint(endpoint);
    }
    static String composite(java.util.UUID logical, String contentHash, String fuelVector) {
        java.util.Objects.requireNonNull(logical); EscrowTags.digest(contentHash); EscrowTags.digest(fuelVector);
        return io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.hash(
                "arce-escrow-rocket-1:" + logical + ":" + contentHash + ":" + fuelVector);
    }
}
