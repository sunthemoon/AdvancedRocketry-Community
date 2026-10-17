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
    private java.util.function.BooleanSupplier serverActive = () -> false;

    private ResourceEscrowSavedData(EscrowLedger ledger) { this.ledger = ledger; }
    public static ResourceEscrowSavedData create() { return new ResourceEscrowSavedData(EscrowLedger.empty(UUID.randomUUID())); }
    public static ResourceEscrowSavedData load(CompoundTag payload) { return new ResourceEscrowSavedData(EscrowCodec.decode(payload)); }
    public boolean operational() { return coordinator == null || coordinator.operational(); }
    public EscrowLedger ledger() { return ledger; }
    /** Diagnostics retain queued reservations without lazily opening a writer. */
    EscrowFloors.Usage floorUsage() {
        return coordinator == null ? EscrowFloors.usage(ledger, java.util.List.of()) : coordinator.floorUsage();
    }
    @Override public CompoundTag save(CompoundTag target) { return EscrowCodec.encode(ledger); }
    @Override public void save(File file) {
        // The crash path reaches saveAllChunks without ServerStoppingEvent; no new generation starts during teardown.
        if (isDirty() && coordinator != null && serverActive.getAsBoolean()) { coordinator.autosave(); }
        // An unbound/closed root cannot use vanilla's unchecked writer or clear dirty state.
    }
    void attach(EscrowCoordinator coordinator, java.util.function.BooleanSupplier serverActive) {
        this.coordinator = coordinator; this.serverActive = serverActive;
    }
    void acknowledged(EscrowLedger next, boolean dirty) { ledger = next; setDirty(dirty); }
    public EscrowCoordinator bind(MinecraftServer server) {
        if (!server.isSameThread()) { throw new IllegalStateException("Escrow binding requires the server thread"); }
        if (coordinator == null) {
            var runtime = EscrowRuntime.of(server);
            var writer = new EscrowLedgerWriter(file(server), ledger.writerGeneration(),
                    runtime.ledgerCommitter(server),
                    runtime::checkpoint);
            writer.timings(runtime.timings());
            writer.baseline(ledger);
            // The coordinator attaches to the runtime's one PROPULSION delegate; it never registers an index.
            attach(new EscrowCoordinator(this, writer, runtime.propulsion(server)), () -> server.isRunning() && !server.isStopped());
            coordinator.tick(server.getTickCount()); // The first explicit write uses the real server clock, not tick zero.
        }
        return coordinator;
    }
    static java.nio.file.Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
    }
    /** Load-only lookup: it never creates a ledger or a file. */
    public static ResourceEscrowSavedData existing(MinecraftServer server) {
        return server.overworld().getDataStorage().get(ResourceEscrowSavedData::load,
                ManagedSavedDataType.RESOURCE_ESCROW.dataName());
    }
    /** Gated test creation goes through the runtime's retarget method, so a new ledger never registers a second index. */
    public static ResourceEscrowSavedData forTests(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        var current = existing(server);
        if (current != null) { return current; }
        var created = create();
        // No ledger is stored, so no attached coordinator holds work and the retarget cannot refuse.
        EscrowTags.require(EscrowRuntime.of(server).replaceLedgerForTests(server, created), "Escrow test ledger creation was refused");
        return created;
    }
    public void tick(long tick) { if (coordinator != null) { coordinator.tick(tick); } }
    public void stop() { if (coordinator != null) { coordinator.close(); } }
    public void abort() { if (coordinator != null) { coordinator.abort(); } }
}
