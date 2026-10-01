package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The active, immutable singularity profiles and fuel tables (ADR-057 sections 1 and 2), built only from a
 * complete, cross-checked set: at most 16 of each, one profile per body, and every profile's fuel table present.
 * Whether a profile applies is decided live against the catalog, so an unknown or changed body fails closed.
 */
public final class BlackHoleData {
    public static final BlackHoleData EMPTY = new BlackHoleData(Map.of(), Map.of());

    private final Map<ResourceLocation, SingularityProfile> byBody;
    private final Map<ResourceLocation, BlackHoleFuelTable> fuels;

    private BlackHoleData(Map<ResourceLocation, SingularityProfile> byBody, Map<ResourceLocation, BlackHoleFuelTable> fuels) {
        this.byBody = Map.copyOf(byBody);
        this.fuels = Map.copyOf(fuels);
    }

    public static DataResult<BlackHoleData> create(Collection<SingularityProfile> profiles,
                                                   Collection<BlackHoleFuelTable> tables) {
        List<String> errors = new ArrayList<>();
        if (profiles.size() > BlackHoleCodec.MAX_FILES || tables.size() > BlackHoleCodec.MAX_FILES) {
            errors.add("More than " + BlackHoleCodec.MAX_FILES + " singularity or fuel files");
        }
        Map<ResourceLocation, BlackHoleFuelTable> fuels = new LinkedHashMap<>();
        tables.forEach(table -> fuels.put(table.id(), table));
        Map<ResourceLocation, SingularityProfile> byBody = new LinkedHashMap<>();
        for (SingularityProfile profile : profiles.stream()
                .sorted(Comparator.comparing(profile -> profile.id().toString())).toList()) {
            SingularityProfile previous = byBody.putIfAbsent(profile.body(), profile);
            if (previous != null) {
                errors.add("Body " + profile.body() + " has singularities " + previous.id() + " and " + profile.id());
            }
            if (!fuels.containsKey(profile.fuelTable())) {
                errors.add("Singularity " + profile.id() + " names the missing fuel table " + profile.fuelTable());
            }
        }
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors.subList(0, Math.min(32, errors.size()))));
        }
        return DataResult.success(new BlackHoleData(byBody, fuels));
    }

    /** The profile of an orbit body that exists in the live catalog, is orbitable and is not landable. */
    public Optional<SingularityProfile> at(ResourceLocation orbitBody, CelestialCatalog catalog) {
        SingularityProfile profile = byBody.get(orbitBody);
        if (profile == null) {
            return Optional.empty();
        }
        Optional<CelestialBodyDefinition> body = catalog.get(orbitBody);
        return body.filter(definition -> definition.capabilities().orbitable() && !definition.capabilities().landable())
                .map(definition -> profile);
    }

    public Optional<BlackHoleFuelTable> fuelTable(ResourceLocation id) {
        return Optional.ofNullable(fuels.get(id));
    }

    public int profileCount() {
        return byBody.size();
    }
}
