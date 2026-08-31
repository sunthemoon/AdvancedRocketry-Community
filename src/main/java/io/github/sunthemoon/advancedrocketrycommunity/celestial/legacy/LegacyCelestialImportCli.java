package io.github.sunthemoon.advancedrocketrycommunity.celestial.legacy;

import java.nio.file.Files;
import java.nio.file.Path;

/** Gradle-invoked offline importer; it is never registered in the game runtime. */
public final class LegacyCelestialImportCli {
    private LegacyCelestialImportCli() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Usage: <planetDefs.xml|Template.xml> <empty-output-directory>");
        }
        LegacyImportBootstrap.initialize();
        Path input = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path output = Path.of(arguments[1]).toAbsolutePath().normalize();
        long size = Files.size(input);
        if (size > LegacyXmlParser.MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("Input exceeds " + LegacyXmlParser.MAX_INPUT_BYTES + " bytes");
        }
        byte[] source = Files.readAllBytes(input);
        LegacyCelestialImporter.ImportResult result = new LegacyCelestialImporter().importXml(source);
        LegacyImportExporter.ExportSummary exported = new LegacyImportExporter().export(
                output,
                input.getFileName().toString(),
                source,
                result
        );
        System.out.println("Legacy celestial import report: " + exported.reportFile());
        System.out.println("Written files: " + exported.writtenFiles().size());
        if (!exported.succeeded()) {
            throw new IllegalStateException("Legacy celestial import failed; inspect the report");
        }
    }
}
