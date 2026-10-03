package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR-063 section 6, revision 6: the CLIENT config holds only the electric mushroom flash setting, default on. */
class ClientConfigTest {
    @Test
    void theOnlyClientValueIsTheElectricMushroomFlashSetting() {
        assertEquals(List.of("effects"), List.copyOf(ClientConfig.SPEC.getValues().valueMap().keySet()));
        assertEquals(Boolean.TRUE, ClientConfig.ELECTRIC_MUSHROOM_FLASHES.getDefault());
        assertEquals(List.of("effects", "electricMushroomFlashes"), ClientConfig.ELECTRIC_MUSHROOM_FLASHES.getPath());
        assertTrue(ClientConfig.electricMushroomFlashes(), "the default applies before the config is loaded");
    }
}
