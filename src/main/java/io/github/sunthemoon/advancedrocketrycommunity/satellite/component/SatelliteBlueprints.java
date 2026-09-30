package io.github.sunthemoon.advancedrocketrycommunity.satellite.component;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-049 §4 blueprint rules: slot layout, 64-bit stat sums, caps and host-fixed requirements, reported in the
 * frozen refusal order (layout, stat limit, requirement). Mirrors {@code check_examples.py} {@code blueprint()}.
 */
public final class SatelliteBlueprints {
    private SatelliteBlueprints() {
    }

    /** Evaluates one layout; {@code primary} is empty only for the legacy {@code data} label. */
    public static Evaluation evaluate(
            Optional<ResourceLocation> chassis,
            Optional<ResourceLocation> primary,
            List<ResourceLocation> modules,
            SatelliteKind kind,
            int scanEnergy,
            SatelliteComponentCatalog catalog
    ) {
        Objects.requireNonNull(chassis, "chassis");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(modules, "modules");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(catalog, "catalog");
        Optional<SatelliteComponentDefinition> chassisDefinition = chassis.flatMap(catalog::get);
        if (chassisDefinition.isEmpty() || chassisDefinition.get().role() != SatelliteComponentRole.CHASSIS
                || modules.size() > SatelliteLimits.MAX_MODULES) {
            return Evaluation.refused(SatelliteOperationCode.INVALID_COMPONENTS);
        }
        List<SatelliteComponentDefinition> moduleDefinitions = new ArrayList<>(modules.size());
        for (ResourceLocation module : modules) {
            Optional<SatelliteComponentDefinition> definition = catalog.get(module);
            if (definition.isEmpty() || !definition.get().role().module()) {
                return Evaluation.refused(SatelliteOperationCode.INVALID_COMPONENTS);
            }
            moduleDefinitions.add(definition.get());
        }
        Optional<SatelliteComponentDefinition> primaryDefinition = primary.flatMap(catalog::get);
        if (primary.isPresent() && primaryDefinition.isEmpty()) {
            return Evaluation.refused(SatelliteOperationCode.INVALID_COMPONENTS);
        }
        if (kind == SatelliteKind.DATA) {
            if (primaryDefinition.isPresent()) {
                return Evaluation.refused(SatelliteOperationCode.INVALID_COMPONENTS);
            }
        } else if (primaryDefinition.isEmpty()
                || primaryDefinition.get().role() != SatelliteComponentRole.PRIMARY
                || primaryDefinition.get().kind().orElseThrow() != kind) {
            return Evaluation.refused(SatelliteOperationCode.INVALID_COMPONENTS);
        }
        long power = 0;
        long battery = SatelliteLimits.BASE_BATTERY;
        long data = 0;
        long cargo = 0;
        for (SatelliteComponentDefinition module : moduleDefinitions) {
            power += module.powerGeneration();
            battery += module.batteryCapacity();
            data += module.dataCapacity();
            cargo += module.cargoStacks();
        }
        long rating = primaryDefinition.map(SatelliteComponentDefinition::primaryRating).orElse(0);
        if (power > SatelliteLimits.MAX_POWER || battery > SatelliteLimits.MAX_BATTERY
                || data > SatelliteLimits.MAX_DATA || cargo > SatelliteLimits.MAX_CARGO
                || rating > SatelliteLimits.MAX_RATING) {
            return Evaluation.refused(SatelliteOperationCode.STAT_LIMIT);
        }
        SatelliteStats stats = new SatelliteStats((int) power, (int) battery, (int) data, (int) cargo, (int) rating);
        boolean met = stats.power() >= 1
                && ((kind != SatelliteKind.DATA && kind != SatelliteKind.SURVEY) || stats.data() >= 1)
                && (kind != SatelliteKind.SURVEY || stats.battery() >= scanEnergy)
                && ((kind != SatelliteKind.ASTEROID_MINER && kind != SatelliteKind.GAS_HARVESTER) || stats.cargo() >= 1);
        return met ? Evaluation.accepted(stats) : Evaluation.refused(SatelliteOperationCode.REQUIREMENT_UNMET);
    }

    /** The component list stored in an identity: chassis, primary, then modules, in slot order. */
    public static List<ResourceLocation> components(
            ResourceLocation chassis,
            ResourceLocation primary,
            List<ResourceLocation> modules
    ) {
        List<ResourceLocation> components = new ArrayList<>(2 + modules.size());
        components.add(chassis);
        components.add(primary);
        components.addAll(modules);
        return List.copyOf(components);
    }

    /** Re-derives stats from an identity's component list against the current catalog (ADR-049 §6). */
    public static Evaluation evaluateIdentity(
            List<ResourceLocation> components,
            SatelliteKind kind,
            int scanEnergy,
            SatelliteComponentCatalog catalog
    ) {
        if (components.size() < 2 || components.size() > SatelliteLimits.MAX_BLUEPRINT_COMPONENTS) {
            return Evaluation.refused(SatelliteOperationCode.INVALID_COMPONENTS);
        }
        for (ResourceLocation component : components) {
            if (catalog.get(component).isEmpty()) {
                return Evaluation.refused(SatelliteOperationCode.COMPONENT_UNAVAILABLE);
            }
        }
        return evaluate(
                Optional.of(components.get(0)),
                Optional.of(components.get(1)),
                components.subList(2, components.size()),
                kind,
                scanEnergy,
                catalog
        );
    }

    public record Evaluation(SatelliteOperationCode code, Optional<SatelliteStats> stats) {
        public Evaluation {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(stats, "stats");
            if ((code == SatelliteOperationCode.SUCCESS) != stats.isPresent()) {
                throw new IllegalArgumentException("Only an accepted blueprint has stats");
            }
        }

        static Evaluation accepted(SatelliteStats stats) {
            return new Evaluation(SatelliteOperationCode.SUCCESS, Optional.of(stats));
        }

        static Evaluation refused(SatelliteOperationCode code) {
            return new Evaluation(code, Optional.empty());
        }

        public boolean accepted() {
            return code == SatelliteOperationCode.SUCCESS;
        }
    }
}
