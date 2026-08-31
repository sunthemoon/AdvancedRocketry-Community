package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Fixed-field schema that never persists arbitrary item NBT or volume caches. */
public final class OxygenVentPersistence {
    public static final String DATA_KEY = "arce_oxygen_vent";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_STACK_COUNT = 16;

    private static final String SCHEMA_KEY = "schema_version";
    private static final String INPUT_KEY = "oxygen_canisters";
    private static final String OUTPUT_KEY = "empty_canisters";
    private static final String OXYGEN_KEY = "oxygen_units";
    private static final String ENERGY_KEY = "energy";
    private static final String PHASE_KEY = "oxygen_phase";

    private OxygenVentPersistence() {
    }

    public static CompoundTag encode(
            int inputCount,
            int outputCount,
            int oxygenUnits,
            int energy,
            int oxygenPhase
    ) {
        validate(inputCount, outputCount, oxygenUnits, energy, oxygenPhase);
        CompoundTag data = new CompoundTag();
        data.putInt(SCHEMA_KEY, SCHEMA_VERSION);
        data.putInt(INPUT_KEY, inputCount);
        data.putInt(OUTPUT_KEY, outputCount);
        data.putInt(OXYGEN_KEY, oxygenUnits);
        data.putInt(ENERGY_KEY, energy);
        data.putInt(PHASE_KEY, oxygenPhase);
        return data;
    }

    public static DecodeResult decode(CompoundTag parent) {
        Objects.requireNonNull(parent, "parent");
        if (!parent.contains(DATA_KEY)) {
            return DecodeResult.valid(0, 0, 0, 0, 0);
        }
        if (!parent.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return DecodeResult.invalid(invalidSentinel());
        }
        CompoundTag data = parent.getCompound(DATA_KEY);
        if (!data.contains(SCHEMA_KEY, Tag.TAG_INT)) {
            return DecodeResult.invalid(data);
        }
        int schema = data.getInt(SCHEMA_KEY);
        if (schema > SCHEMA_VERSION) {
            return DecodeResult.future(data.copy());
        }
        if (schema != SCHEMA_VERSION
                || !hasInt(data, INPUT_KEY)
                || !hasInt(data, OUTPUT_KEY)
                || !hasInt(data, OXYGEN_KEY)
                || !hasInt(data, ENERGY_KEY)
                || !hasInt(data, PHASE_KEY)) {
            return DecodeResult.invalid(data);
        }
        int input = data.getInt(INPUT_KEY);
        int output = data.getInt(OUTPUT_KEY);
        int oxygen = data.getInt(OXYGEN_KEY);
        int energy = data.getInt(ENERGY_KEY);
        int phase = data.getInt(PHASE_KEY);
        try {
            validate(input, output, oxygen, energy, phase);
        } catch (IllegalArgumentException exception) {
            return DecodeResult.invalid(data);
        }
        return DecodeResult.valid(input, output, oxygen, energy, phase);
    }

    private static boolean hasInt(CompoundTag data, String key) {
        return data.contains(key, Tag.TAG_INT);
    }

    private static CompoundTag invalidSentinel() {
        CompoundTag data = new CompoundTag();
        data.putInt(SCHEMA_KEY, 0);
        return data;
    }

    private static void validate(
            int inputCount,
            int outputCount,
            int oxygenUnits,
            int energy,
            int oxygenPhase
    ) {
        if (inputCount < 0 || inputCount > MAX_STACK_COUNT
                || outputCount < 0 || outputCount > MAX_STACK_COUNT
                || oxygenUnits < 0 || oxygenUnits > AtmosphereLimits.VENT_OXYGEN_CAPACITY
                || energy < 0 || energy > AtmosphereLimits.VENT_ENERGY_CAPACITY
                || oxygenPhase < 0
                || oxygenPhase >= io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life
                        .VentSupplyInput.TICKS_PER_OXYGEN) {
            throw new IllegalArgumentException("Oxygen Vent state is outside fixed bounds");
        }
    }

    public record DecodeResult(
            DecodeStatus status,
            int inputCount,
            int outputCount,
            int oxygenUnits,
            int energy,
            int oxygenPhase,
            CompoundTag preservedFutureData
    ) {
        public DecodeResult {
            Objects.requireNonNull(status, "status");
            preservedFutureData = preservedFutureData == null ? null : preservedFutureData.copy();
        }

        public static DecodeResult valid(int input, int output, int oxygen, int energy, int phase) {
            return new DecodeResult(DecodeStatus.VALID, input, output, oxygen, energy, phase, null);
        }

        public static DecodeResult future(CompoundTag data) {
            return new DecodeResult(DecodeStatus.FUTURE, 0, 0, 0, 0, 0, data);
        }

        public static DecodeResult invalid(CompoundTag data) {
            return new DecodeResult(DecodeStatus.INVALID, 0, 0, 0, 0, 0, data.copy());
        }

        @Override
        public CompoundTag preservedFutureData() {
            return preservedFutureData == null ? null : preservedFutureData.copy();
        }
    }

    public enum DecodeStatus {
        VALID,
        FUTURE,
        INVALID
    }
}
