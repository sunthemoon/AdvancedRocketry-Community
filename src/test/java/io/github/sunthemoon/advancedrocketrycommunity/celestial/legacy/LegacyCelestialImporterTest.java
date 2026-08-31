package io.github.sunthemoon.advancedrocketrycommunity.celestial.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyCelestialImporterTest {
    private static final String UPSTREAM_FIXTURE =
            "/io/github/sunthemoon/advancedrocketrycommunity/celestial/legacy/upstream/Template-c5cd5af6.xml";
    private static final String UPSTREAM_SHA256 =
            "40674cb8a730e5b6baf2baa3943d34d1712f6f24ffa07db79467627b9c0176e1";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void supportedFieldsBecomeBoundedFixedLevelDefinitions() {
        LegacyCelestialImporter.ImportResult result = importXml("""
                <galaxy><star name="Sol">
                  <planet name="Alpha" DIMID="7" customIcon="Blue Marble">
                    <atmosphereDensity>100</atmosphereDensity>
                    <gravitationalMultiplier>80</gravitationalMultiplier>
                    <orbitalDistance>1000</orbitalDistance>
                    <rotationalPeriod>24000</rotationalPeriod>
                    <hasOxygen>true</hasOxygen>
                    <planet name="Alpha Moon" DIMID="8">
                      <atmosphereDensity>0</atmosphereDensity>
                      <gravitationalMultiplier>16</gravitationalMultiplier>
                      <orbitalDistance>150</orbitalDistance>
                      <rotationalPeriod>2400</rotationalPeriod>
                      <hasOxygen>false</hasOxygen>
                    </planet>
                  </planet>
                </star></galaxy>
                """);

        assertTrue(result.succeeded());
        assertEquals(2, result.definitions().size());
        var alpha = result.definitions().stream()
                .filter(definition -> definition.id().getPath().equals("imported/alpha"))
                .findFirst()
                .orElseThrow();
        var moon = result.definitions().stream()
                .filter(definition -> definition.id().getPath().equals("imported/alpha/alpha_moon"))
                .findFirst()
                .orElseThrow();

        assertEquals(CelestialIds.EARTH_ID, alpha.parentId().orElseThrow());
        assertEquals(CelestialIds.SPACE_LEVEL, alpha.levelKey());
        assertEquals(0.8D, alpha.gravityMultiplier());
        assertTrue(alpha.atmosphere().breathable());
        assertEquals(alpha.id(), moon.parentId().orElseThrow());
        assertEquals(CelestialIds.MOON_LEVEL, moon.levelKey());
        assertEquals(2, result.numericDimensions().size());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("NUMERIC_DIMENSION_ID_IGNORED")));
    }

    @Test
    void dtdAndExternalEntityPayloadIsRejectedBeforeParsing() {
        LegacyXmlParser.ParseResult result = new LegacyXmlParser().parse(bytes("""
                <!DOCTYPE galaxy [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <galaxy><star><planet name="&xxe;"/></star></galaxy>
                """));

        assertTrue(result.hasErrors());
        assertTrue(result.galaxy().isEmpty());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("DTD_FORBIDDEN")));
    }

    @Test
    void numericAndIdentityFailuresUseSourcePaths() {
        LegacyCelestialImporter.ImportResult invalidNumber = importXml("""
                <galaxy><star><planet name="Alpha">
                  <atmosphereDensity>not-a-number</atmosphereDensity>
                </planet></star></galaxy>
                """);
        assertFalse(invalidNumber.succeeded());
        assertTrue(invalidNumber.issues().stream().anyMatch(issue ->
                issue.code().equals("INVALID_NUMBER")
                        && issue.path().equals("/galaxy[1]/star[1]/planet[1]/atmospheredensity[1]")));

        LegacyCelestialImporter.ImportResult duplicateId = importXml("""
                <galaxy><star>
                  <planet name="Planet-A" DIMID="4"/>
                  <planet name="Planet A" DIMID="5"/>
                </star></galaxy>
                """);
        assertFalse(duplicateId.succeeded());
        assertTrue(duplicateId.issues().stream().anyMatch(issue -> issue.code().equals("DUPLICATE_CANONICAL_ID")));

        LegacyCelestialImporter.ImportResult duplicateDimension = importXml("""
                <galaxy><star>
                  <planet name="Alpha" DIMID="4"/>
                  <planet name="Beta" DIMID="4"/>
                </star></galaxy>
                """);
        assertFalse(duplicateDimension.succeeded());
        assertTrue(duplicateDimension.issues().stream().anyMatch(issue ->
                issue.code().equals("DUPLICATE_DIMENSION_ID") && issue.path().endsWith("/@DIMID")));
    }

    @Test
    void unknownFieldsAreWarningsWithDeterministicPaths() {
        LegacyCelestialImporter.ImportResult result = importXml("""
                <galaxy><star><planet name="Alpha">
                  <skyColor>0,0,1</skyColor>
                </planet></star></galaxy>
                """);

        assertTrue(result.succeeded());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.severity() == LegacyImportIssue.Severity.WARNING
                        && issue.code().equals("UNSUPPORTED_FIELD")
                        && issue.path().equals("/galaxy[1]/star[1]/planet[1]/skycolor[1]")));
    }

    @Test
    void parserInputAndDepthBudgetsFailClosed() {
        byte[] tooLarge = new byte[LegacyXmlParser.MAX_INPUT_BYTES + 1];
        LegacyXmlParser.ParseResult sizeResult = new LegacyXmlParser().parse(tooLarge);
        assertTrue(sizeResult.issues().stream().anyMatch(issue -> issue.code().equals("INPUT_TOO_LARGE")));

        String nested = "<galaxy>" + "<star>".repeat(LegacyXmlParser.MAX_DEPTH)
                + "<planet name=\"Alpha\"/>"
                + "</star>".repeat(LegacyXmlParser.MAX_DEPTH) + "</galaxy>";
        LegacyXmlParser.ParseResult depthResult = new LegacyXmlParser().parse(bytes(nested));
        assertTrue(depthResult.hasErrors());
        assertTrue(depthResult.issues().stream().anyMatch(issue -> issue.code().equals("LIMIT_EXCEEDED")));
    }

    @Test
    void exactUpstreamFixtureImportsWithoutDomTypes() throws IOException {
        LegacyCelestialImporter.ImportResult result = new LegacyCelestialImporter().importXml(upstreamFixture());

        assertTrue(result.succeeded());
        assertEquals(2, result.definitions().size());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("UNSUPPORTED_FIELD")));
        assertFalse(Arrays.stream(LegacyXmlParser.class.getDeclaredMethods())
                .flatMap(LegacyCelestialImporterTest::methodTypes)
                .map(Class::getName)
                .anyMatch(name -> name.startsWith("org.w3c.dom.")));
    }

    @Test
    void exactUpstreamExportIsByteDeterministic(@TempDir Path temporaryDirectory) throws IOException {
        byte[] fixture = upstreamFixture();
        LegacyCelestialImporter.ImportResult result = new LegacyCelestialImporter().importXml(fixture);
        LegacyImportExporter exporter = new LegacyImportExporter();
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");

        LegacyImportExporter.ExportSummary firstSummary = exporter.export(first, "Template.xml", fixture, result);
        LegacyImportExporter.ExportSummary secondSummary = exporter.export(second, "Template.xml", fixture, result);

        assertTrue(firstSummary.succeeded());
        assertEquals(readTree(first), readTree(second));
        String report = Files.readString(first.resolve(LegacyImportExporter.REPORT_FILE));
        assertTrue(report.contains("\"source_sha256\": \"" + UPSTREAM_SHA256 + "\""));
        assertTrue(report.contains("\"status\": \"SUCCESS_WITH_WARNINGS\""));
        assertFalse(report.contains("\r"));
    }

    private static LegacyCelestialImporter.ImportResult importXml(String xml) {
        return new LegacyCelestialImporter().importXml(bytes(xml));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] upstreamFixture() throws IOException {
        try (InputStream stream = LegacyCelestialImporterTest.class.getResourceAsStream(UPSTREAM_FIXTURE)) {
            if (stream == null) {
                throw new IOException("Missing exact upstream XML fixture");
            }
            return stream.readAllBytes();
        }
    }

    private static Stream<Class<?>> methodTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Arrays.stream(method.getParameterTypes()));
    }

    private static Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path));
            }
        }
        return files;
    }
}
