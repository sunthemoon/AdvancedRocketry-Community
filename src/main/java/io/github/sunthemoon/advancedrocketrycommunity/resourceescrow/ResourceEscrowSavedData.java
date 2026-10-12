package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.File;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Lazy overworld root. Ordinary autosave uses the same writer and never publishes queued admissions. */
public final class ResourceEscrowSavedData extends SavedData {
    private EscrowLedger ledger;
    private EscrowCoordinator coordinator;

    private ResourceEscrowSavedData(EscrowLedger ledger) { this.ledger = ledger; }
    public static ResourceEscrowSavedData create() { return new ResourceEscrowSavedData(EscrowLedger.empty(UUID.randomUUID())); }
    public static ResourceEscrowSavedData load(CompoundTag payload) { return new ResourceEscrowSavedData(EscrowCodec.decode(payload)); }
    public boolean operational() { return true; }
    public EscrowLedger ledger() { return ledger; }
    @Override public CompoundTag save(CompoundTag target) { return EscrowCodec.encode(ledger); }
    @Override public void save(File file) {
        if (isDirty() && coordinator != null) { coordinator.autosave(); }
        // An unbound/closed root cannot use vanilla's unchecked writer or clear dirty state.
    }
    void acknowledged(EscrowLedger next, boolean dirty) { ledger = next; setDirty(dirty); }
    public EscrowCoordinator bind(MinecraftServer server) {
        if (!server.isSameThread()) { throw new IllegalStateException("Escrow binding requires the server thread"); }
        if (coordinator == null) {
            var file = server.getWorldPath(LevelResource.ROOT).resolve("data")
                    .resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            coordinator = new EscrowCoordinator(this, new EscrowLedgerWriter(file, ledger.writerGeneration(),
                    EscrowRuntime.of(server).ledgerCommitter(server),
                    EscrowRuntime.of(server)::checkpoint));
        }
        return coordinator;
    }
    public static ResourceEscrowSavedData existing(MinecraftServer server) {
        return server.overworld().getDataStorage().get(ResourceEscrowSavedData::load,
                ManagedSavedDataType.RESOURCE_ESCROW.dataName());
    }
    public static ResourceEscrowSavedData forTests(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        return server.overworld().getDataStorage().computeIfAbsent(ResourceEscrowSavedData::load,
                ResourceEscrowSavedData::create, ManagedSavedDataType.RESOURCE_ESCROW.dataName());
    }
    public void tick(long tick) { if (coordinator != null) { coordinator.tick(tick); } }
    public void stop() { if (coordinator != null) { coordinator.close(); } }
}
