package io.github.sunthemoon.advancedrocketrycommunity.api.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiArtifactTest {
    private static final String PACKAGE = "io/github/sunthemoon/advancedrocketrycommunity/api/version/";
    private static final Set<String> CLASSES = Set.of(PACKAGE + "ApiVersion.class",
            PACKAGE + "ApiCompatibility.class", PACKAGE + "ApiVersions.class");
    private static final Set<String> METADATA = Set.of("META-INF/MANIFEST.MF", "META-INF/LICENSE",
            "META-INF/NOTICE.md", "META-INF/THIRD-PARTY-NOTICES.md",
            "META-INF/licenses/GRADLE-8.1.1-LICENSE.txt",
            "META-INF/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt");

    @TempDir
    Path temporary;

    @Test
    void classifierContainsOnlyTheFrozenSurfaceAndLicensing() throws IOException {
        try (ZipFile api = new ZipFile(apiJar().toFile())) {
            Set<String> entries = api.stream().filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName).collect(Collectors.toSet());
            assertEquals(CLASSES, entries.stream().filter(name -> name.endsWith(".class"))
                    .collect(Collectors.toSet()));
            assertEquals(METADATA, entries.stream().filter(name -> !name.endsWith(".class"))
                    .collect(Collectors.toSet()));
            assertEquals(entries.size(), api.stream().filter(entry -> !entry.isDirectory()).count(),
                    "Duplicate archive entries are not allowed");
        }
    }

    @Test
    void runtimeJarContainsIdenticalApiClassesAfterReobfuscation() throws IOException {
        try (ZipFile api = new ZipFile(apiJar().toFile());
             ZipFile runtime = new ZipFile(Path.of(System.getProperty("arce.runtimeJar")).toFile())) {
            Set<String> runtimeApi = runtime.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith(PACKAGE) && name.endsWith(".class"))
                    .collect(Collectors.toSet());
            assertEquals(CLASSES, runtimeApi);
            for (String name : CLASSES) {
                assertArrayEquals(api.getInputStream(api.getEntry(name)).readAllBytes(),
                        runtime.getInputStream(runtime.getEntry(name)).readAllBytes(), name);
            }
        }
    }

    @Test
    void independentConsumerCompilesAndRunsWithOnlyTheClassifier() throws Exception {
        Compilation result = compile("ApiConsumer");
        assertTrue(result.success(), result.diagnostics().toString());
        // The platform parent cannot see the JUnit process's main/Forge classpath.
        try (URLClassLoader loader = new URLClassLoader(new URL[]{result.output().toUri().toURL(),
                apiJar().toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            assertEquals("compatible", loader.loadClass("consumer.ApiConsumer")
                    .getMethod("verify").invoke(null));
        }
    }

    @Test
    void independentConsumerCannotImportAnExistingInternalType() throws IOException {
        try (ZipFile runtime = new ZipFile(Path.of(System.getProperty("arce.runtimeJar")).toFile())) {
            assertNotNull(runtime.getEntry(
                    "io/github/sunthemoon/advancedrocketrycommunity/atmosphere/AtmosphereLimits.class"));
        }
        Compilation result = compile("InternalConsumer");
        assertFalse(result.success(), "Internal dependencies must not compile against the classifier");
        List<Diagnostic<? extends JavaFileObject>> errors = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
        assertTrue(errors.stream().anyMatch(diagnostic -> diagnostic.getCode().equals(
                "compiler.err.doesnt.exist") && diagnostic.getMessage(Locale.ROOT).contains(
                "io.github.sunthemoon.advancedrocketrycommunity.atmosphere")), errors.toString());
        assertTrue(errors.stream().allMatch(diagnostic ->
                diagnostic.getCode().equals("compiler.err.doesnt.exist")
                        || diagnostic.getCode().equals("compiler.err.cant.resolve.location")), errors.toString());
    }

    private Compilation compile(String name) throws IOException {
        assertEquals(17, Runtime.version().feature(), "Boundary checks use the Java 17 toolchain");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required");
        Path source = temporary.resolve(name + ".java");
        try (var fixture = getClass().getResourceAsStream("/api-consumer/" + name + ".java")) {
            assertNotNull(fixture);
            Files.copy(fixture, source);
        }
        Path output = Files.createDirectory(temporary.resolve(name + "-classes"));
        Path emptySourcePath = Files.createDirectory(temporary.resolve(name + "-empty-source"));
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<String> options = List.of("--release", "17", "-encoding", "UTF-8", "-proc:none",
                "-classpath", apiJar().toString(), "-sourcepath", emptySourcePath.toString(),
                "-d", output.toString());
        boolean success;
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            success = compiler.getTask(null, manager, diagnostics, options, null,
                    manager.getJavaFileObjects(source.toFile())).call();
        }
        System.out.println(name + ": JDK " + Runtime.version() + ", options=" + options
                + ", success=" + success + ", diagnostics=" + diagnostics.getDiagnostics());
        return new Compilation(success, output, diagnostics.getDiagnostics());
    }

    private static Path apiJar() {
        return Path.of(System.getProperty("arce.apiJar"));
    }

    private record Compilation(boolean success, Path output,
                               List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    }
}
