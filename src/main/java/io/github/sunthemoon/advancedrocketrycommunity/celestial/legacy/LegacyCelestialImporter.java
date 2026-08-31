package io.github.sunthemoon.advancedrocketrycommunity.celestial.legacy;

import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Converts isolated legacy DTOs into canonical fixed-Level definitions and diagnostics. */
public final class LegacyCelestialImporter {
    private static final int MAX_IMPORTED_BODIES = CelestialCatalog.MAX_BODIES - 3;
    private static final int MAX_GENERATED_PATH_CHARS = 96;

    private final LegacyXmlParser parser = new LegacyXmlParser();

    public ImportResult importXml(byte[] xml) {
        LegacyXmlParser.ParseResult parsed = parser.parse(xml);
        List<LegacyImportIssue> issues = new ArrayList<>(parsed.issues());
        if (parsed.hasErrors() || parsed.galaxy().isEmpty()) {
            return new ImportResult(List.of(), issues, List.of());
        }

        List<CelestialBodyDefinition> definitions = new ArrayList<>();
        List<NumericDimensionMetadata> numericDimensions = new ArrayList<>();
        Set<ResourceLocation> generatedIds = new HashSet<>();
        for (LegacyXmlParser.PlanetDto root : parsed.galaxy().orElseThrow().planets()) {
            convertPlanet(
                    root,
                    "imported",
                    CelestialIds.EARTH_ID,
                    0,
                    definitions,
                    numericDimensions,
                    generatedIds,
                    issues
            );
        }
        if (definitions.size() > MAX_IMPORTED_BODIES) {
            issues.add(error(
                    "IMPORT_CAPACITY_EXCEEDED",
                    "/galaxy",
                    "At most " + MAX_IMPORTED_BODIES + " imported bodies fit beside the fixed baseline"
            ));
        }

        if (!hasErrors(issues)) {
            for (CelestialBodyDefinition definition : definitions) {
                var encoded = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition);
                if (encoded.error().isPresent()) {
                    issues.add(error(
                            "CANONICAL_DEFINITION_INVALID",
                            "/galaxy",
                            definition.id() + ": " + encoded.error().orElseThrow().message()
                    ));
                }
            }
        }

        if (!hasErrors(issues)) {
            List<CelestialBodyDefinition> merged = new ArrayList<>(CelestialDefaults.definitions());
            merged.addAll(definitions);
            var validated = CelestialCatalog.create(merged).flatMap(CelestialCatalog::requireFixedBaseline);
            if (validated.error().isPresent()) {
                issues.add(error(
                        "CANONICAL_VALIDATION_FAILED",
                        "/galaxy",
                        validated.error().orElseThrow().message()
                ));
            }
        }

        definitions.sort((left, right) -> left.id().compareTo(right.id()));
        numericDimensions.sort((left, right) -> left.bodyId().compareTo(right.bodyId()));
        issues.sort(LegacyImportIssue::compareTo);
        return new ImportResult(definitions, issues, numericDimensions);
    }

    private static void convertPlanet(
            LegacyXmlParser.PlanetDto legacy,
            String parentPath,
            ResourceLocation parentId,
            int depth,
            List<CelestialBodyDefinition> definitions,
            List<NumericDimensionMetadata> numericDimensions,
            Set<ResourceLocation> generatedIds,
            List<LegacyImportIssue> issues
    ) {
        String segment = slug(legacy.name());
        String path = parentPath + "/" + segment;
        if (path.length() > MAX_GENERATED_PATH_CHARS) {
            issues.add(error("GENERATED_ID_TOO_LONG", legacy.sourcePath(), "Generated canonical path exceeds 96 characters"));
            return;
        }
        ResourceLocation bodyId = ModIdentity.id(path);
        if (!generatedIds.add(bodyId)) {
            issues.add(error("DUPLICATE_CANONICAL_ID", legacy.sourcePath(), "Duplicate canonical id " + bodyId));
            return;
        }

        int density = legacy.atmosphereDensity().orElseGet(() -> {
            issues.add(warning("DEFAULTED_ATMOSPHERE", legacy.sourcePath(), "Missing atmosphereDensity defaulted to vacuum"));
            return 0;
        });
        int gravityPercent = legacy.gravityPercent().orElseGet(() -> {
            issues.add(warning("DEFAULTED_GRAVITY", legacy.sourcePath(), "Missing gravitationalMultiplier defaulted to 100"));
            return 100;
        });
        long distance = legacy.orbitalDistance().orElseGet(() -> {
            issues.add(warning("DEFAULTED_ORBIT_DISTANCE", legacy.sourcePath(), "Missing orbitalDistance defaulted to 1"));
            return 1L;
        });
        if (distance <= 0L) {
            issues.add(error("INVALID_ORBIT_DISTANCE", legacy.sourcePath(), "Imported bodies orbit Earth or a parent and require positive distance"));
            distance = 1L;
        }
        long period;
        if (legacy.rotationalPeriod().isPresent()) {
            period = legacy.rotationalPeriod().getAsLong();
        } else {
            issues.add(warning("DEFAULTED_ORBIT_PERIOD", legacy.sourcePath(), "Missing rotationalPeriod defaulted to orbitalDistance"));
            period = distance;
        }

        boolean oxygen = legacy.hasOxygen().orElseGet(() -> {
            issues.add(warning("DEFAULTED_OXYGEN", legacy.sourcePath(), "Missing hasOxygen defaulted to false"));
            return false;
        });
        double pressure = density / 100.0D;
        if (oxygen && pressure == 0.0D) {
            issues.add(error("OXYGEN_WITHOUT_PRESSURE", legacy.sourcePath(), "hasOxygen=true requires positive atmosphereDensity"));
            oxygen = false;
        }

        ResourceKey<Level> level = depth == 0 ? CelestialIds.SPACE_LEVEL : CelestialIds.MOON_LEVEL;
        ResourceLocation visualProfile = legacy.customIcon()
                .map(LegacyCelestialImporter::slug)
                .map(icon -> ModIdentity.id("legacy_icon/" + icon))
                .orElseGet(() -> ModIdentity.id(depth == 0 ? "legacy/planet" : "legacy/moon"));
        AtmosphereDefinition atmosphere = new AtmosphereDefinition(
                pressure,
                oxygen,
                pressure == 0.0D ? 3.0D : 288.0D,
                ModIdentity.id(pressure == 0.0D ? "vacuum" : "legacy_atmosphere")
        );
        CelestialBodyDefinition definition = new CelestialBodyDefinition(
                bodyId,
                Optional.of(parentId),
                level,
                gravityPercent / 100.0D,
                atmosphere,
                new OrbitDefinition(distance, period, 0.0D),
                visualProfile
        );
        definitions.add(definition);

        issues.add(warning(
                "FIXED_LEVEL_MAPPING",
                legacy.sourcePath(),
                "Mapped to fixed " + level.location() + "; importer never creates runtime dimensions"
        ));
        issues.add(warning(
                "ROTATION_PERIOD_AS_DISPLAY_PERIOD",
                legacy.sourcePath(),
                "Legacy rotationalPeriod is preserved as non-physical display period metadata"
        ));
        if (depth == 0) {
            issues.add(warning(
                    "TOP_LEVEL_PARENT_EARTH",
                    legacy.sourcePath(),
                    "Top-level legacy planet is attached to the fixed Earth baseline"
            ));
        }
        if (legacy.numericDimensionId().isPresent()) {
            int numericId = legacy.numericDimensionId().getAsInt();
            numericDimensions.add(new NumericDimensionMetadata(bodyId, numericId, legacy.sourcePath()));
            issues.add(warning(
                    "NUMERIC_DIMENSION_ID_IGNORED",
                    legacy.sourcePath() + "/@DIMID",
                    "Legacy numeric id " + numericId + " is report metadata only"
            ));
        }

        for (LegacyXmlParser.PlanetDto child : legacy.children()) {
            convertPlanet(
                    child,
                    path,
                    bodyId,
                    depth + 1,
                    definitions,
                    numericDimensions,
                    generatedIds,
                    issues
            );
        }
    }

    private static String slug(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT);
        StringBuilder slug = new StringBuilder();
        boolean separator = false;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                if (separator && !slug.isEmpty()) {
                    slug.append('_');
                }
                slug.append(character);
                separator = false;
            } else {
                separator = true;
            }
        }
        if (slug.isEmpty()) {
            return "planet";
        }
        return slug.length() <= 48 ? slug.toString() : slug.substring(0, 48);
    }

    private static boolean hasErrors(List<LegacyImportIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == LegacyImportIssue.Severity.ERROR);
    }

    private static LegacyImportIssue error(String code, String path, String message) {
        return new LegacyImportIssue(LegacyImportIssue.Severity.ERROR, code, path, message);
    }

    private static LegacyImportIssue warning(String code, String path, String message) {
        return new LegacyImportIssue(LegacyImportIssue.Severity.WARNING, code, path, message);
    }

    public record ImportResult(
            List<CelestialBodyDefinition> definitions,
            List<LegacyImportIssue> issues,
            List<NumericDimensionMetadata> numericDimensions
    ) {
        public ImportResult {
            definitions = List.copyOf(definitions);
            issues = List.copyOf(issues);
            numericDimensions = List.copyOf(numericDimensions);
        }

        public boolean succeeded() {
            return issues.stream().noneMatch(issue -> issue.severity() == LegacyImportIssue.Severity.ERROR);
        }
    }

    public record NumericDimensionMetadata(
            ResourceLocation bodyId,
            int numericDimensionId,
            String sourcePath
    ) {
    }
}
