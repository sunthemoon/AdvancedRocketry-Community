package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The built-in satellite bay adapter (W27A-DESIGN section 6). Capture validates the schema-1 root and copies it
 * exactly; an empty unowned bay is an empty payload. A quarantined root refuses capture, so assembly keeps it in place.
 * Restore loads the copy into a fresh bay, and the adapter set then recaptures and compares it.
 */
public final class SatelliteBayRocketAdapter implements RocketBlockEntityAdapter {
    public static final ResourceLocation ID = ModIdentity.id("satellite_bay_v1");
    /** The bay's block entity type, which no external adapter may claim. */
    public static final ResourceLocation TYPE = ModIdentity.id("rocket_satellite_bay");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supports(BlockEntity blockEntity) {
        return blockEntity instanceof SatelliteBayBlockEntity;
    }

    @Override
    public RocketBlockEntityPayload capture(BlockEntity blockEntity) {
        if (!(blockEntity instanceof SatelliteBayBlockEntity bay)) {
            throw new IllegalArgumentException("Adapter supports only satellite bays");
        }
        return new RocketBlockEntityPayload(ID, bay.captureRoot());
    }

    @Override
    public boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload) {
        return blockEntity instanceof SatelliteBayBlockEntity bay && ID.equals(payload.adapterId())
                && restorable(payload.data()) && bay.restoreRoot(payload.data());
    }

    @Override
    public boolean canRestorePayload(RocketBlockEntityPayload payload) {
        return ID.equals(payload.adapterId()) && restorable(payload.data());
    }

    private static boolean restorable(CompoundTag data) {
        return data.isEmpty() || SatelliteBayRoot.decode(data).valid();
    }
}
