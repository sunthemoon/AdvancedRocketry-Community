package io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persists only the last bounded diagnostic; active scans remain manager-owned. */
public final class RocketAssemblerBlockEntity extends BlockEntity {
    private static final String DATA_KEY = "RocketAssemblerData";
    private static final int SCHEMA_VERSION = 1;

    private RocketAssemblerReport report = RocketAssemblerReport.idle();
    private CompoundTag preservedFutureData;

    public RocketAssemblerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ROCKET_ASSEMBLER.get(), position, state);
    }

    public RocketAssemblerReport report() {
        return report;
    }

    public void setReport(RocketAssemblerReport report) {
        this.report = report;
        preservedFutureData = null;
        setChanged();
    }

    public boolean blockedByFutureData() {
        return preservedFutureData != null;
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if (preservedFutureData != null) {
            parent.put(DATA_KEY, preservedFutureData.copy());
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", SCHEMA_VERSION);
        data.putString("code", report.code().name());
        data.putString("detail", report.detail());
        data.putLong("updated_at_game_time", report.updatedAtGameTime());
        report.optionalStats().ifPresent(stats -> data.put("stats", saveStats(stats)));
        parent.put(DATA_KEY, data);
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        report = RocketAssemblerReport.idle();
        preservedFutureData = null;
        if (!parent.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag data = parent.getCompound(DATA_KEY);
        if (!data.contains("schema_version", Tag.TAG_INT)) {
            return;
        }
        int schema = data.getInt("schema_version");
        if (schema > SCHEMA_VERSION) {
            preservedFutureData = data.copy();
            return;
        }
        if (schema != SCHEMA_VERSION
                || !data.contains("code", Tag.TAG_STRING)
                || !data.contains("detail", Tag.TAG_STRING)
                || !data.contains("updated_at_game_time", Tag.TAG_LONG)) {
            return;
        }
        try {
            RocketValidationCode code = RocketValidationCode.valueOf(data.getString("code"));
            RocketStats stats = data.contains("stats", Tag.TAG_COMPOUND)
                    ? loadStats(data.getCompound("stats"))
                    : null;
            report = new RocketAssemblerReport(
                    code,
                    stats,
                    data.getString("detail"),
                    data.getLong("updated_at_game_time")
            );
        } catch (RuntimeException ignored) {
            report = RocketAssemblerReport.idle();
        }
    }

    private static CompoundTag saveStats(RocketStats stats) {
        CompoundTag data = new CompoundTag();
        data.putInt("blocks", stats.blockCount());
        data.putLong("mass", stats.mass());
        data.putLong("thrust", stats.thrust());
        data.putLong("fuel_capacity", stats.fuelCapacity());
        data.putInt("engines", stats.engineCount());
        data.putInt("seats", stats.seatCount());
        data.putInt("guidance", stats.guidanceCount());
        data.putInt("block_entities", stats.blockEntityCount());
        return data;
    }

    private static RocketStats loadStats(CompoundTag data) {
        return new RocketStats(
                requireInt(data, "blocks"),
                requireLong(data, "mass"),
                requireLong(data, "thrust"),
                requireLong(data, "fuel_capacity"),
                requireInt(data, "engines"),
                requireInt(data, "seats"),
                requireInt(data, "guidance"),
                requireInt(data, "block_entities")
        );
    }

    private static int requireInt(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing assembler stat " + key);
        }
        return data.getInt(key);
    }

    private static long requireLong(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing assembler stat " + key);
        }
        return data.getLong(key);
    }
}
