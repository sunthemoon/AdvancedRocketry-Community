package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.io.File;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;

/** In-place ordinary save, observing IO success instead of SavedData.save(File)'s swallowed IOException. */
final class StationOrdinaryWriter {
    private StationOrdinaryWriter() { }

    static void save(StationRegistrySavedData data, StationRootPresence root, File file) {
        if (!data.isDirty() || !root.admitsOrdinarySave(file.toPath())) {
            if (root.unreadable()) { data.setDirty(false); }
            return;
        }
        CompoundTag outer = new CompoundTag();
        // The adapter retains its ordinary-save live-clock / quarantined-floor policy; this is not a barrier.
        outer.put("data", data.save(new CompoundTag()));
        NbtUtils.addCurrentDataVersion(outer);
        try {
            NbtIo.writeCompressed(outer, file);
            root.committed();
        } catch (IOException failure) {
            AdvancedRocketryCommunity.LOGGER.error("Could not save data {}", data, failure);
        }
        // Preserve vanilla ordinary-save dirty handling, including failed IO. A later mutation can retry.
        data.setDirty(false);
    }
}
