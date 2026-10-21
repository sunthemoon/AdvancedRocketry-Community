package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/** Session-local, independently gated cargo kinds. No switch enables the fuel loader. */
public final class EscrowCargoSwitches {
    private final EnumMap<EscrowOperation.Kind, Set<UUID>> devices = new EnumMap<>(EscrowOperation.Kind.class);
    static boolean production(EscrowOperation.Kind kind) {
        return switch (kind) {
            case FLUID_CARGO_LOAD -> false;
            case FLUID_CARGO_UNLOAD -> false;
            case ITEM_CARGO_LOAD -> false;
            case ITEM_CARGO_UNLOAD -> false;
            default -> false;
        };
    }
    public static boolean enabled(MinecraftServer server, EscrowOperation.Kind kind, UUID device) {
        return open(kind, server.isSameThread(), () -> EscrowTestEndpoints.enabled(server),
                () -> EscrowRuntime.of(server).cargoSwitches.allowed(kind, device));
    }
    static boolean open(EscrowOperation.Kind kind, boolean ownerThread, BooleanSupplier gate, BooleanSupplier named) {
        return kind.cargo() && ownerThread && (production(kind) || gate.getAsBoolean() && named.getAsBoolean());
    }
    boolean allowed(EscrowOperation.Kind kind, UUID device) { return devices.getOrDefault(kind, Set.of()).contains(device); }
    void set(EscrowOperation.Kind kind, UUID device, boolean enabled) {
        EscrowTags.require(kind.cargo(), "Only cargo kinds have cargo switches");
        var named = devices.computeIfAbsent(kind, ignored -> new HashSet<>());
        if (!enabled) { named.remove(device); return; }
        EscrowTags.require(named.contains(device) || named.size() < EscrowLimits.OPERATIONS, "Too many gated cargo devices");
        named.add(device);
    }
    /** Only the existing gated test environment may name a device and kind. */
    public static void forTests(MinecraftServer server, EscrowOperation.Kind kind, UUID device, boolean enabled) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(server.isSameThread(), "Cargo switches require the server thread");
        EscrowRuntime.of(server).cargoSwitches.set(kind, device, enabled);
    }
    void clear() { devices.clear(); }
}
