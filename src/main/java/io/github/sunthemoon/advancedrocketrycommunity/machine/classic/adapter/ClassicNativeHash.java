package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicGuardedResourceAccess;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResources;

/** Whole resources K2 digest, including inactive banks, owner, order, revision and metadata. */
final class ClassicNativeHash {
    static String resources(ClassicResources resources, GuardTicket ticket) {
        ticket.requireValid();
        var root = ClassicGuardedResourceAccess.encode(resources, ticket::requireValid);
        String result = ClassicNbtCanonicalHash.sha256(root, ClassicNbtLimits.RESOURCES);
        ticket.requireValid(); return result;
    }

    private ClassicNativeHash() { }
}
