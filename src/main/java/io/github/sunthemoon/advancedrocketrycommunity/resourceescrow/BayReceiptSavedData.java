package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The Overworld-owned receipt root, bound to the server-lifetime escrow runtime. The file is created lazily by the
 * first admission; an existing file is loaded at startup. Vanilla's unchecked writer never runs: every generation goes
 * through the checked {@link BayReceiptWriter}, and autosave publishes nothing, so it can never publish a queued intent.
 */
public final class BayReceiptSavedData extends SavedData {
    private final BayReceiptRoot loaded;
    private BayReceiptStore store;

    private BayReceiptSavedData(BayReceiptRoot loaded) { this.loaded = loaded; }
    public static BayReceiptSavedData load(CompoundTag payload) { return new BayReceiptSavedData(BayReceiptCodec.decode(payload)); }
    static BayReceiptSavedData create(UUID storeId) { return new BayReceiptSavedData(BayReceiptRoot.empty(storeId)); }

    /** The acknowledged root: the bound store's durable generation, or the loaded root before binding. */
    public BayReceiptRoot root() { return store == null ? loaded : store.durable(); }
    public boolean operational() { return store == null || !store.unavailable(); }
    BayReceiptStore store() { return store; }
    void attach(BayReceiptStore bound) {
        EscrowTags.require(store == null && bound != null, "Bay receipt root is already bound"); store = bound;
    }

    /** The durable root only; queued, captured and unknown receipts are never encoded here. */
    @Override public CompoundTag save(CompoundTag target) { return BayReceiptCodec.encode(root()); }
    @Override public void save(File file) { /* Never: the checked writer owns every generation of this file. */ }

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName());
    }
    /** Load-only lookup: it never creates a root or a file. */
    public static BayReceiptSavedData existing(MinecraftServer server) {
        return server.overworld().getDataStorage().get(BayReceiptSavedData::load, ManagedSavedDataType.BAY_RECEIPTS.dataName());
    }
}
