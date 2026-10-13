package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AtmosphereDetectorBlockEntity extends BlockEntity {
    public static final String KEY = "arce_atmosphere_detector";
    private DetectorMode mode = DetectorMode.BREATHABLE;
    private Tag refused;
    public enum Reason { AVAILABLE, PENDING, UNAVAILABLE, DISABLED }
    private Reason reason = Reason.UNAVAILABLE;
    public AtmosphereDetectorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ATMOSPHERE_DETECTOR.get(), pos, state); }
    public boolean protectedData() { return refused != null; }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    public DetectorMode mode() { return mode; }
    public Reason reason() { return reason; }
    public boolean signal() { return !protectedData() && reason == Reason.AVAILABLE && getBlockState().getValue(AtmosphereDetectorBlock.POWERED); }
    public void cycle() {
        if (!protectedData() && DeviceAccess.loaded(this) && CommonConfig.classicDevicesEnabled() && CommonConfig.oxygenDetection()) {
            mode = DetectorMode.values()[(mode.ordinal() + 1) % DetectorMode.values().length]; setChanged(); measure();
        }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, AtmosphereDetectorBlockEntity detector) {
        if (level.getGameTime() % 10 == 0) { detector.measure(); }
    }
    void measure() {
        if (!DeviceAccess.loaded(this)) { return; }
        boolean signal = false; boolean known = false; boolean pending = false;
        if (!protectedData() && CommonConfig.classicDevicesEnabled() && CommonConfig.oxygenDetection()) {
            for (Direction side : Direction.values()) {
                BlockPos cell = worldPosition.relative(side);
                if (!level.hasChunkAt(cell) || level.getBlockState(cell).isCollisionShapeFullBlock(level, cell)) { continue; }
                AnalyzerReading reading = AtmosphereAnalyzerRuntime.readAt((ServerLevel) level, cell);
                pending |= reading.state() == AnalyzerReading.State.PENDING;
                known |= reading.state() == AnalyzerReading.State.BREATHABLE || reading.state() == AnalyzerReading.State.NON_BREATHABLE;
                signal |= mode.matches(reading);
            }
        }
        reason = !CommonConfig.classicDevicesEnabled() || !CommonConfig.oxygenDetection() ? Reason.DISABLED
                : known ? Reason.AVAILABLE : pending ? Reason.PENDING : Reason.UNAVAILABLE;
        boolean old = getBlockState().getValue(AtmosphereDetectorBlock.POWERED);
        if (old != signal) {
            level.setBlock(worldPosition, getBlockState().setValue(AtmosphereDetectorBlock.POWERED, signal), Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }
    public static DetectorMode decode(Tag raw) {
        if (!(raw instanceof CompoundTag root) || !DeviceProtection.bounded(root)
                || !root.getAllKeys().equals(Set.of("schema_version", "mode")) || !root.contains("schema_version", Tag.TAG_INT)
                || root.getInt("schema_version") != 1 || !root.contains("mode", Tag.TAG_STRING)) { return null; }
        try { return DetectorMode.valueOf(root.getString("mode")); } catch (IllegalArgumentException invalid) { return null; }
    }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (refused != null) { outer.put(KEY, DeviceProtection.bounded(refused) ? refused.copy() : refused); return; }
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("mode", mode.name()); outer.put(KEY, root);
    }
    @Override public void load(CompoundTag outer) {
        super.load(outer); mode = DetectorMode.BREATHABLE; refused = null; reason = Reason.UNAVAILABLE;
        Tag raw = outer.get(KEY); if (raw == null) { return; }
        DetectorMode decoded = decode(raw);
        if (decoded == null) { refused = DeviceProtection.bounded(raw) ? raw.copy() : raw; } else { mode = decoded; }
    }
}
