package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;

/** Stable adapter identity shared by prefix keys and typed resource/invariant projections. */
final class EscrowAdapterIds {
    static final String ROCKET = ModIdentity.id("rocket").toString();
    private EscrowAdapterIds() { }
    static boolean nativeRocket(EscrowEndpoint endpoint) {
        return endpoint.kind() == EscrowEndpoint.Kind.ENTITY && endpoint.type().equals(ROCKET);
    }
}
