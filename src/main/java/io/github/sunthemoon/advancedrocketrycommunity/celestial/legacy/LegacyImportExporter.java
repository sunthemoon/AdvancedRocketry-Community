package io.github.sunthemoon.advancedrocketrycommunity.celestial.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Writes deterministic data-pack JSON and a machine-readable conversion report. */
public final class LegacyImportExporter {
    public static final String REPORT_FILE = "import-report.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public ExportSummary export(
            Path outputDirectory,
            String sourceFileName,
            byte[] sourceBytes,
            LegacyCelestialImporter.ImportResult result
    ) throws IOException {
        Path output = outputDirectory.toAbsolutePath().normalize();
        ensureEmptyOutput(output);
        Files.createDirectories(output);

        List<Path> written = new ArrayList<>();
        if (result.succeeded()) {
            for (CelestialBodyDefinition definition : result.definitions()) {
                Path target = output
                        .resolve("data")
                        .resolve(definition.id().getNamespace())
                        .resolve("celestial_bodies")
                        .resolve(definition.id().getPath() + ".json")
                        .normalize();
                if (!target.startsWith(output)) {
                    throw new IOException("Generated definition path escaped output directory");
                }
                Files.createDirectories(target.getParent());
                JsonElement encoded = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                        .getOrThrow(false, message -> {
                            throw new IllegalStateException(message);
                        });
                writeNew(target, GSON.toJson(encoded) + "\n");
                written.add(target);
            }
        }

        Path report = output.resolve(REPORT_FILE);
        writeNew(report, GSON.toJson(reportJson(sourceFileName, sourceBytes, result)) + "\n");
        written.add(report);
        return new ExportSummary(result.succeeded(), List.copyOf(written), report);
    }

    private static JsonObject reportJson(
            String sourceFileName,
            byte[] sourceBytes,
            LegacyCelestialImporter.ImportResult result
    ) {
        JsonObject report = new JsonObject();
        report.addProperty("schema_version", 1);
        report.addProperty("status", result.succeeded()
                ? result.issues().isEmpty() ? "SUCCESS" : "SUCCESS_WITH_WARNINGS"
                : "FAILED");
        report.addProperty("source_file", Path.of(sourceFileName).getFileName().toString());
        report.addProperty("source_sha256", sha256(sourceBytes));

        JsonArray definitions = new JsonArray();
        result.definitions().stream()
                .map(CelestialBodyDefinition::id)
                .map(Object::toString)
                .sorted()
                .forEach(definitions::add);
        report.add("definitions", definitions);

        JsonArray numericDimensions = new JsonArray();
        result.numericDimensions().forEach(metadata -> {
            JsonObject item = new JsonObject();
            item.addProperty("body_id", metadata.bodyId().toString());
            item.addProperty("legacy_dimension_id", metadata.numericDimensionId());
            item.addProperty("source_path", metadata.sourcePath());
            item.addProperty("runtime_identity", false);
            numericDimensions.add(item);
        });
        report.add("numeric_dimension_metadata", numericDimensions);

        JsonArray issues = new JsonArray();
        result.issues().stream().sorted().forEach(issue -> {
            JsonObject item = new JsonObject();
            item.addProperty("severity", issue.severity().name());
            item.addProperty("code", issue.code());
            item.addProperty("path", issue.path());
            item.addProperty("message", issue.message());
            issues.add(item);
        });
        report.add("issues", issues);
        return report;
    }

    private static void ensureEmptyOutput(Path output) throws IOException {
        if (!Files.exists(output)) {
            return;
        }
        try (var contents = Files.list(output)) {
            if (contents.findAny().isPresent()) {
                throw new IOException("Output directory must be empty: " + output);
            }
        }
    }

    private static void writeNew(Path target, String content) throws IOException {
        Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ExportSummary(
            boolean succeeded,
            List<Path> writtenFiles,
            Path reportFile
    ) {
    }
}
