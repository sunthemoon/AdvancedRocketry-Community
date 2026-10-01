package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The active, immutable laser drill tables (ADR-055 section 2), built only from a complete, cross-checked set: at
 * most 32 tables, at most one {@code default}, every named body exists in the celestial catalog and appears in at
 * most one table.
 */
public final class LaserDrillTables {
    public static final LaserDrillTables EMPTY = new LaserDrillTables(Map.of(), null);

    private final Map<ResourceLocation, LaserDrillTable> byBody;
    private final LaserDrillTable defaultTable;

    private LaserDrillTables(Map<ResourceLocation, LaserDrillTable> byBody, LaserDrillTable defaultTable) {
        this.byBody = Map.copyOf(byBody);
        this.defaultTable = defaultTable;
    }

    public static DataResult<LaserDrillTables> create(Collection<LaserDrillTable> tables, CelestialCatalog celestial) {
        Objects.requireNonNull(celestial, "celestial");
        List<String> errors = new ArrayList<>();
        if (tables.size() > LaserDrillTableCodec.MAX_TABLES) {
            errors.add("More than " + LaserDrillTableCodec.MAX_TABLES + " laser drill tables");
        }
        Map<ResourceLocation, LaserDrillTable> byBody = new LinkedHashMap<>();
        LaserDrillTable defaultTable = null;
        for (LaserDrillTable table : tables.stream().sorted(Comparator.comparing(t -> t.id().toString())).toList()) {
            if (table.isDefault()) {
                if (defaultTable != null) {
                    errors.add("Laser drill tables " + defaultTable.id() + " and " + table.id() + " are both default");
                } else {
                    defaultTable = table;
                }
            }
            for (ResourceLocation body : table.bodies()) {
                if (celestial.get(body).isEmpty()) {
                    errors.add("Laser drill table " + table.id() + " names " + body + ", which is not a known body");
                } else {
                    LaserDrillTable previous = byBody.putIfAbsent(body, table);
                    if (previous != null) {
                        errors.add("Body " + body + " appears in laser drill tables " + previous.id() + " and "
                                + table.id());
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            return DataResult.error(() -> String.join("; ", errors.subList(0, Math.min(32, errors.size()))));
        }
        return DataResult.success(new LaserDrillTables(byBody, defaultTable));
    }

    /**
     * Eligibility: the orbit body's own table, otherwise the default when the body supports surface arrival. Gas
     * giants, stars and unmapped bodies need an explicit table.
     */
    public Optional<LaserDrillTable> forBody(CelestialBodyDefinition body) {
        Objects.requireNonNull(body, "body");
        LaserDrillTable own = byBody.get(body.id());
        if (own != null) {
            return Optional.of(own);
        }
        return body.supportsSurfaceArrival() ? Optional.ofNullable(defaultTable) : Optional.empty();
    }

    public Optional<LaserDrillTable> defaultTable() {
        return Optional.ofNullable(defaultTable);
    }

    public int bodyCount() {
        return byBody.size();
    }
}
