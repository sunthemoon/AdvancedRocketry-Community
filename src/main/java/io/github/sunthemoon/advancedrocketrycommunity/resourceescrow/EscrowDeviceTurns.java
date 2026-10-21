package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** A loaded device owns this RAM registration; rebind/unload closes it, and ledger replacement cannot retain a token. */
public final class EscrowDeviceTurns implements AutoCloseable {
    private EscrowCoordinator coordinator;
    private EscrowTransferTurns.Registration registration;
    private UUID device, rocket;
    public boolean refresh(MinecraftServer server, UUID device, UUID rocket) {
        if (!server.isSameThread()) { return false; }
        var data = ResourceEscrowSavedData.existing(server);
        return bind(data == null ? null : data.bind(server), device, rocket);
    }
    boolean bind(EscrowCoordinator next, UUID nextDevice, UUID nextRocket) {
        if (coordinator == next && java.util.Objects.equals(device, nextDevice)
                && java.util.Objects.equals(rocket, nextRocket) && (registration != null || next == null)) { return true; }
        close(); device = nextDevice; rocket = nextRocket; coordinator = next;
        if (next == null || device == null || rocket == null) { return true; }
        try { registration = next.transferTurns().register(device, rocket); return true; }
        catch (IllegalArgumentException full) { coordinator = null; return false; }
    }
    public void blocked() { if (registration != null) { coordinator.transferTurns().blocked(device, rocket); } }
    public boolean allows() { return registration == null || coordinator.transferTurns().allows(device, rocket); }
    public boolean claim() { return registration == null || coordinator.transferTurns().claim(device, rocket); }
    public void release() { if (registration != null) { coordinator.transferTurns().release(device, rocket); } }
    public void yieldNext() { if (registration != null) { coordinator.transferTurns().yieldNext(device, rocket); } }
    @Override public void close() {
        if (registration != null) { registration.close(); }
        registration = null; coordinator = null; device = null; rocket = null;
    }
}
