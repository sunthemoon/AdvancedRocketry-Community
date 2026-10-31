package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ADR-066 W14 implementation note and R-027; preservation uses native chunk admission. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class DeviceProtection {
    private static final Map<String, String> ROOTS = Map.of("advancedrocketrycommunity:oxygen_vent", OxygenVentPersistence.DATA_KEY,
            "advancedrocketrycommunity:co2_scrubber", ScrubberBlockEntity.KEY,
            "advancedrocketrycommunity:atmosphere_detector", AtmosphereDetectorBlockEntity.KEY,
            "advancedrocketrycommunity:gas_charge_pad", GasChargePadBlockEntity.KEY);
    public static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, 65_536, 16, 1_024); }
    private static boolean protectedAt(LevelAccessor level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) { return false; }
        var entity = level.getBlockEntity(pos);
        return entity instanceof OxygenVentBlockEntity vent && vent.protectedDeviceData()
                || entity instanceof ScrubberBlockEntity scrubber && scrubber.protectedData()
                || entity instanceof AtmosphereDetectorBlockEntity detector && detector.protectedData()
                || entity instanceof GasChargePadBlockEntity pad && pad.protectedData();
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (protectedAt(event.getLevel(), event.getPos())) { event.setCanceled(true); }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> protectedAt(event.getLevel(), pos));
    }
    @SubscribeEvent public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) { GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds its physical bound"); }
        for (int i = 0; i < entities.size(); i++) {
            var entity = entities.getCompound(i); String key = ROOTS.get(entity.getString("id"));
            if (key != null && entity.contains(key) && !bounded(entity.get(key))) {
                GuardedChunkSaves.refuse(event, "Refusing non-lossless life-support device root; back up and repair offline");
            }
        }
    }
    private DeviceProtection() { }
}
