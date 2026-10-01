package io.github.sunthemoon.advancedrocketrycommunity.celestial.content;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-057 section 1 example content: Cygnus X-1, a public root body with no Level, orbitable and not landable,
 * that stations reach only by interstellar warp. Its singularity profile is separate data; the celestial schema is
 * unchanged. The values are placeholders inside the schema bounds.
 */
public final class SingularityContent {
    public static final ResourceLocation CYGNUS_X1 = ModIdentity.id("cygnus_x1");
    private static final ResourceLocation VACUUM = ModIdentity.id("vacuum");

    private SingularityContent() {
    }

    public static List<CelestialBodyDefinition> definitions() {
        return List.of(new CelestialBodyDefinition(CYGNUS_X1, Optional.empty(), Optional.empty(), 4.0D,
                new AtmosphereDefinition(0.0D, false, 3.0D, VACUUM), new OrbitDefinition(0L, 0L, 0.0D),
                CelestialIds.SPACE_ID, new CelestialCapabilities(false, true, false), 0.0D, 0.0D, false, false));
    }
}
