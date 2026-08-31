package io.github.sunthemoon.advancedrocketrycommunity.celestial.legacy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Secure bounded StAX parser; no DOM object crosses this adapter boundary. */
public final class LegacyXmlParser {
    public static final int MAX_INPUT_BYTES = 1_048_576;
    public static final int MAX_ELEMENTS = 2_048;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_ATTRIBUTES_PER_ELEMENT = 16;
    public static final int MAX_ATTRIBUTE_CHARS = 512;
    public static final int MAX_TEXT_CHARS = 4_096;
    public static final int MAX_PLANETS = 128;
    public static final int MAX_ISSUES = 256;

    private static final Set<String> SUPPORTED_FIELDS = Set.of(
            "atmospheredensity",
            "gravitationalmultiplier",
            "orbitaldistance",
            "rotationalperiod",
            "hasoxygen"
    );

    public ParseResult parse(byte[] xml) {
        List<LegacyImportIssue> issues = new ArrayList<>();
        if (xml.length > MAX_INPUT_BYTES) {
            addIssue(issues, error("INPUT_TOO_LARGE", "/", "XML exceeds " + MAX_INPUT_BYTES + " bytes"));
            return new ParseResult(Optional.empty(), issues);
        }
        String defensiveScan = new String(xml, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        if (defensiveScan.contains("<!DOCTYPE") || defensiveScan.contains("<!ENTITY")) {
            addIssue(issues, error("DTD_FORBIDDEN", "/", "DTD and entity declarations are disabled"));
            return new ParseResult(Optional.empty(), issues);
        }

        List<PlanetDto> roots = new ArrayList<>();
        Set<Integer> dimensionIds = new HashSet<>();
        Deque<ElementFrame> stack = new ArrayDeque<>();
        int elementCount = 0;
        int planetCount = 0;
        int galaxyCount = 0;
        try {
            XMLInputFactory factory = secureFactory();
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        elementCount++;
                        if (elementCount > MAX_ELEMENTS) {
                            throw new ImportLimitException("XML exceeds " + MAX_ELEMENTS + " elements");
                        }
                        if (stack.size() + 1 > MAX_DEPTH) {
                            throw new ImportLimitException("XML exceeds depth " + MAX_DEPTH);
                        }
                        ElementFrame frame = startFrame(reader, stack, issues);
                        if ("galaxy".equals(frame.normalizedName)) {
                            galaxyCount++;
                        }
                        if ("planet".equals(frame.normalizedName)) {
                            planetCount++;
                            if (planetCount > MAX_PLANETS) {
                                throw new ImportLimitException("XML exceeds " + MAX_PLANETS + " planets");
                            }
                            frame.planet = createPlanet(frame, dimensionIds, issues, planetCount);
                        }
                        validatePlacement(frame, stack.peek(), issues);
                        stack.push(frame);
                    } else if (event == XMLStreamConstants.CHARACTERS
                            || event == XMLStreamConstants.CDATA
                            || event == XMLStreamConstants.SPACE) {
                        if (!stack.isEmpty()) {
                            ElementFrame frame = stack.peek();
                            if (frame.text.length() + reader.getTextLength() > MAX_TEXT_CHARS) {
                                throw new ImportLimitException("Text exceeds " + MAX_TEXT_CHARS + " characters at " + frame.path);
                            }
                            frame.text.append(reader.getText());
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        finishFrame(stack, roots, issues);
                    } else if (event == XMLStreamConstants.DTD
                            || event == XMLStreamConstants.ENTITY_DECLARATION
                            || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        addIssue(issues, error("ENTITY_FORBIDDEN", currentPath(stack), "Entity-related XML events are disabled"));
                    }
                }
            } finally {
                reader.close();
            }
        } catch (ImportLimitException exception) {
            addIssue(issues, error("LIMIT_EXCEEDED", currentPath(stack), exception.getMessage()));
        } catch (XMLStreamException | IllegalArgumentException exception) {
            addIssue(issues, error("MALFORMED_XML", currentPath(stack), safeMessage(exception)));
        }

        if (!stack.isEmpty()) {
            addIssue(issues, error("UNCLOSED_ELEMENT", currentPath(stack), "XML ended with unclosed elements"));
        }
        if (galaxyCount != 1) {
            addIssue(issues, error("GALAXY_ROOT_COUNT", "/", "Expected exactly one galaxy element"));
        }
        if (roots.isEmpty()) {
            addIssue(issues, error("NO_PLANETS", "/galaxy", "No legacy planets were found"));
        }
        if (hasErrors(issues)) {
            return new ParseResult(Optional.empty(), issues);
        }
        return new ParseResult(Optional.of(new GalaxyDto(roots)), issues);
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML resources are disabled");
        });
        return factory;
    }

    private static ElementFrame startFrame(
            XMLStreamReader reader,
            Deque<ElementFrame> stack,
            List<LegacyImportIssue> issues
    ) {
        String name = reader.getLocalName();
        String normalized = normalize(name);
        ElementFrame parent = stack.peek();
        int sibling = parent == null
                ? 1
                : parent.childCounts.merge(normalized, 1, Integer::sum);
        String path = parent == null
                ? "/" + normalized + "[" + sibling + "]"
                : parent.path + "/" + normalized + "[" + sibling + "]";
        if (reader.getAttributeCount() > MAX_ATTRIBUTES_PER_ELEMENT) {
            throw new ImportLimitException("Too many attributes at " + path);
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            String value = reader.getAttributeValue(index);
            if (value.length() > MAX_ATTRIBUTE_CHARS) {
                throw new ImportLimitException("Attribute exceeds " + MAX_ATTRIBUTE_CHARS + " characters at " + path);
            }
            String key = normalize(reader.getAttributeLocalName(index));
            if (attributes.putIfAbsent(key, value) != null) {
                addIssue(issues, error("DUPLICATE_ATTRIBUTE", path + "/@" + key, "Duplicate attribute"));
            }
        }
        return new ElementFrame(normalized, path, attributes);
    }

    private static PlanetBuilder createPlanet(
            ElementFrame frame,
            Set<Integer> dimensionIds,
            List<LegacyImportIssue> issues,
            int planetIndex
    ) {
        String name = frame.attributes.get("name");
        if (name == null || name.isBlank()) {
            name = "unnamed_planet_" + planetIndex;
            addIssue(issues, warning("DEFAULTED_NAME", frame.path + "/@name", "Generated deterministic name " + name));
        } else if (name.length() > 64) {
            addIssue(issues, error("NAME_TOO_LONG", frame.path + "/@name", "Planet name exceeds 64 characters"));
        }

        Integer dimensionId = null;
        String rawDimensionId = frame.attributes.get("dimid");
        if (rawDimensionId != null) {
            try {
                dimensionId = Integer.valueOf(rawDimensionId.trim());
                if (!dimensionIds.add(dimensionId)) {
                    addIssue(issues, error("DUPLICATE_DIMENSION_ID", frame.path + "/@DIMID", "Duplicate legacy dimension id " + dimensionId));
                }
            } catch (NumberFormatException exception) {
                addIssue(issues, error("INVALID_NUMBER", frame.path + "/@DIMID", "Expected a 32-bit integer"));
            }
        }

        for (String attribute : frame.attributes.keySet()) {
            if (!Set.of("name", "dimid", "customicon").contains(attribute)) {
                addIssue(issues, warning("UNSUPPORTED_ATTRIBUTE", frame.path + "/@" + attribute, "Legacy planet attribute is not imported"));
            }
        }
        return new PlanetBuilder(
                frame.path,
                name,
                dimensionId,
                frame.attributes.get("customicon")
        );
    }

    private static void validatePlacement(
            ElementFrame frame,
            ElementFrame parent,
            List<LegacyImportIssue> issues
    ) {
        if (parent == null) {
            if (!"galaxy".equals(frame.normalizedName)) {
                addIssue(issues, error("INVALID_ROOT", frame.path, "Root element must be galaxy"));
            }
            return;
        }
        if ("star".equals(frame.normalizedName)) {
            if (!"galaxy".equals(parent.normalizedName) && !"star".equals(parent.normalizedName)) {
                addIssue(issues, error("INVALID_STAR_PLACEMENT", frame.path, "star must be under galaxy or star"));
            }
            for (String attribute : frame.attributes.keySet()) {
                addIssue(issues, warning("UNSUPPORTED_STAR_ATTRIBUTE", frame.path + "/@" + attribute, "Star metadata is report-only"));
            }
        } else if ("planet".equals(frame.normalizedName)
                && !"star".equals(parent.normalizedName)
                && !"planet".equals(parent.normalizedName)) {
            addIssue(issues, error("INVALID_PLANET_PLACEMENT", frame.path, "planet must be under star or planet"));
        }
    }

    private static void finishFrame(
            Deque<ElementFrame> stack,
            List<PlanetDto> roots,
            List<LegacyImportIssue> issues
    ) {
        if (stack.isEmpty()) {
            addIssue(issues, error("UNBALANCED_XML", "/", "Unexpected closing element"));
            return;
        }
        ElementFrame frame = stack.pop();
        ElementFrame parent = stack.peek();
        if (frame.planet != null) {
            PlanetDto planet = frame.planet.build();
            if (parent != null && parent.planet != null) {
                parent.planet.children.add(planet);
            } else {
                roots.add(planet);
            }
            return;
        }
        if (parent != null && parent.planet != null) {
            applyPlanetField(parent.planet, frame, issues);
        } else if (!Set.of("galaxy", "star").contains(frame.normalizedName)) {
            addIssue(issues, warning("UNSUPPORTED_ELEMENT", frame.path, "Element is not imported"));
        }
    }

    private static void applyPlanetField(
            PlanetBuilder planet,
            ElementFrame frame,
            List<LegacyImportIssue> issues
    ) {
        String field = frame.normalizedName;
        String value = frame.text.toString().trim();
        if (!SUPPORTED_FIELDS.contains(field)) {
            addIssue(issues, warning("UNSUPPORTED_FIELD", frame.path, "Legacy field is not imported"));
            return;
        }
        if (!frame.attributes.isEmpty()) {
            frame.attributes.keySet().forEach(attribute -> addIssue(
                    issues,
                    warning("UNSUPPORTED_FIELD_ATTRIBUTE", frame.path + "/@" + attribute, "Field attribute is not imported")
            ));
        }
        if (!planet.seenFields.add(field)) {
            addIssue(issues, error("DUPLICATE_FIELD", frame.path, "Field appears more than once"));
            return;
        }
        switch (field) {
            case "atmospheredensity" -> planet.atmosphereDensity = parseInt(
                    value, 0, 1_000, frame.path, issues
            );
            case "gravitationalmultiplier" -> planet.gravityPercent = parseInt(
                    value, 0, 400, frame.path, issues
            );
            case "orbitaldistance" -> planet.orbitalDistance = parseLong(
                    value, 0L, 1_000_000_000L, frame.path, issues
            );
            case "rotationalperiod" -> planet.rotationalPeriod = parseLong(
                    value, 1L, 10_000_000_000L, frame.path, issues
            );
            case "hasoxygen" -> planet.hasOxygen = parseBoolean(value, frame.path, issues);
            default -> throw new IllegalStateException("Unhandled supported field: " + field);
        }
    }

    private static Integer parseInt(
            String value,
            int minimum,
            int maximum,
            String path,
            List<LegacyImportIssue> issues
    ) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                addIssue(issues, error("NUMBER_OUT_OF_RANGE", path, "Expected " + minimum + ".." + maximum));
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            addIssue(issues, error("INVALID_NUMBER", path, "Expected an integer"));
            return null;
        }
    }

    private static Long parseLong(
            String value,
            long minimum,
            long maximum,
            String path,
            List<LegacyImportIssue> issues
    ) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < minimum || parsed > maximum) {
                addIssue(issues, error("NUMBER_OUT_OF_RANGE", path, "Expected " + minimum + ".." + maximum));
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            addIssue(issues, error("INVALID_NUMBER", path, "Expected an integer"));
            return null;
        }
    }

    private static Boolean parseBoolean(
            String value,
            String path,
            List<LegacyImportIssue> issues
    ) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        addIssue(issues, error("INVALID_BOOLEAN", path, "Expected true or false"));
        return null;
    }

    private static void addIssue(List<LegacyImportIssue> issues, LegacyImportIssue issue) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(issue);
        } else if (issues.size() == MAX_ISSUES) {
            issues.add(error("ISSUE_LIMIT_REACHED", "/", "Additional diagnostics were suppressed"));
        }
    }

    private static LegacyImportIssue error(String code, String path, String message) {
        return new LegacyImportIssue(LegacyImportIssue.Severity.ERROR, code, path, message);
    }

    private static LegacyImportIssue warning(String code, String path, String message) {
        return new LegacyImportIssue(LegacyImportIssue.Severity.WARNING, code, path, message);
    }

    private static boolean hasErrors(List<LegacyImportIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == LegacyImportIssue.Severity.ERROR);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String currentPath(Deque<ElementFrame> stack) {
        return stack.isEmpty() ? "/" : stack.peek().path;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    public record ParseResult(Optional<GalaxyDto> galaxy, List<LegacyImportIssue> issues) {
        public ParseResult {
            issues = List.copyOf(issues);
        }

        public boolean hasErrors() {
            return LegacyXmlParser.hasErrors(issues);
        }
    }

    public record GalaxyDto(List<PlanetDto> planets) {
        public GalaxyDto {
            planets = List.copyOf(planets);
        }
    }

    public record PlanetDto(
            String sourcePath,
            String name,
            OptionalInt numericDimensionId,
            Optional<String> customIcon,
            OptionalInt atmosphereDensity,
            OptionalInt gravityPercent,
            OptionalLong orbitalDistance,
            OptionalLong rotationalPeriod,
            Optional<Boolean> hasOxygen,
            List<PlanetDto> children
    ) {
        public PlanetDto {
            children = List.copyOf(children);
        }
    }

    private static final class ElementFrame {
        private final String normalizedName;
        private final String path;
        private final Map<String, String> attributes;
        private final Map<String, Integer> childCounts = new HashMap<>();
        private final StringBuilder text = new StringBuilder();
        private PlanetBuilder planet;

        private ElementFrame(String normalizedName, String path, Map<String, String> attributes) {
            this.normalizedName = normalizedName;
            this.path = path;
            this.attributes = attributes;
        }
    }

    private static final class PlanetBuilder {
        private final String sourcePath;
        private final String name;
        private final Integer numericDimensionId;
        private final String customIcon;
        private final Set<String> seenFields = new HashSet<>();
        private final List<PlanetDto> children = new ArrayList<>();
        private Integer atmosphereDensity;
        private Integer gravityPercent;
        private Long orbitalDistance;
        private Long rotationalPeriod;
        private Boolean hasOxygen;

        private PlanetBuilder(
                String sourcePath,
                String name,
                Integer numericDimensionId,
                String customIcon
        ) {
            this.sourcePath = sourcePath;
            this.name = name;
            this.numericDimensionId = numericDimensionId;
            this.customIcon = customIcon;
        }

        private PlanetDto build() {
            return new PlanetDto(
                    sourcePath,
                    name,
                    numericDimensionId == null ? OptionalInt.empty() : OptionalInt.of(numericDimensionId),
                    Optional.ofNullable(customIcon),
                    atmosphereDensity == null ? OptionalInt.empty() : OptionalInt.of(atmosphereDensity),
                    gravityPercent == null ? OptionalInt.empty() : OptionalInt.of(gravityPercent),
                    orbitalDistance == null ? OptionalLong.empty() : OptionalLong.of(orbitalDistance),
                    rotationalPeriod == null ? OptionalLong.empty() : OptionalLong.of(rotationalPeriod),
                    Optional.ofNullable(hasOxygen),
                    children
            );
        }
    }

    private static final class ImportLimitException extends RuntimeException {
        private ImportLimitException(String message) {
            super(message);
        }
    }
}
