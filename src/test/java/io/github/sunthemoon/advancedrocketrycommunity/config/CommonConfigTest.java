package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class CommonConfigTest {
    @Test
    void atmosphereSettingsExposeExactSafeDefaultsAndBounds() {
        assertRange(
                "atmosphere.maxVolumeCells",
                CommonConfig.MAX_ATMOSPHERE_VOLUME,
                1,
                AtmosphereLimits.MAX_VOLUME_CELLS
        );
        assertRange(
                "atmosphere.maxInspectionsPerLevelTick",
                CommonConfig.MAX_ATMOSPHERE_INSPECTIONS_PER_TICK,
                1,
                AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
        );
    }

    @Test
    void obsoleteUnconsumedLifecycleToggleIsNotExposed() {
        assertFalse(CommonConfig.SPEC.getValues().contains("logLifecycleEvents"));
        assertEquals(2, countValues(CommonConfig.SPEC.getValues()));
    }

    private static void assertRange(
            String path,
            ForgeConfigSpec.IntValue value,
            int minimum,
            int maximum
    ) {
        ForgeConfigSpec.ValueSpec spec = assertInstanceOf(
                ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get(path)
        );
        ForgeConfigSpec.Range<Integer> range = spec.getRange();
        assertEquals(minimum, range.getMin());
        assertEquals(maximum, range.getMax());
        assertEquals(maximum, value.getDefault());
        assertTrue(spec.test(minimum));
        assertTrue(spec.test(maximum));
        assertFalse(spec.test(minimum - 1));
        assertFalse(spec.test(maximum + 1));
    }

    private static int countValues(com.electronwill.nightconfig.core.UnmodifiableConfig config) {
        int count = 0;
        for (Object value : config.valueMap().values()) {
            if (value instanceof ForgeConfigSpec.ConfigValue<?>) {
                count++;
            } else if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested) {
                count += countValues(nested);
            }
        }
        return count;
    }
}
