package io.github.sunthemoon.advancedrocketrycommunity.api.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiArtifactTest {
    private static final String HOST_PACKAGE = "io/github/sunthemoon/advancedrocketrycommunity/";
    private static final String API_PACKAGE = HOST_PACKAGE + "api/";
    private static final Set<String> VERSION_CLASSES = Set.of(API_PACKAGE + "version/ApiVersion.class",
            API_PACKAGE + "version/ApiCompatibility.class", API_PACKAGE + "version/ApiVersions.class");
    private static final Set<String> CLASSES = Stream.concat(VERSION_CLASSES.stream(), Stream.of(
            API_PACKAGE + "rocket/RocketBlockEntityAdapter.class",
            API_PACKAGE + "rocket/RocketAdapterRegistrar.class",
            API_PACKAGE + "rocket/RegisterRocketAdaptersEvent.class",
            API_PACKAGE + "rocket/RocketComponentDefinition.class",
            API_PACKAGE + "rocket/RocketComponentRegistrar.class",
            API_PACKAGE + "rocket/RegisterRocketComponentsEvent.class",
            API_PACKAGE + "atmosphere/AtmosphereBoundary.class",
            API_PACKAGE + "atmosphere/AtmosphereBoundaryProvider.class",
            API_PACKAGE + "atmosphere/AtmosphereBoundaryRegistrar.class",
            API_PACKAGE + "atmosphere/RegisterAtmosphereBoundariesEvent.class",
            API_PACKAGE + "atmosphere/SuitOxygenProvider.class",
            API_PACKAGE + "atmosphere/SuitEquipmentRegistrar.class",
            API_PACKAGE + "atmosphere/RegisterSuitEquipmentEvent.class")).collect(Collectors.toUnmodifiableSet());
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
                    .filter(name -> name.startsWith(API_PACKAGE) && name.endsWith(".class"))
                    .collect(Collectors.toSet());
            assertEquals(CLASSES, runtimeApi);
            for (String name : CLASSES) {
                assertArrayEquals(api.getInputStream(api.getEntry(name)).readAllBytes(),
                        runtime.getInputStream(runtime.getEntry(name)).readAllBytes(), name);
            }
        }
    }

    @Test
    void exportedClassesReferenceNeitherClientOnlyNorInternalHostTypes() throws IOException {
        Pattern internalReference = Pattern.compile(Pattern.quote(HOST_PACKAGE) + "(?!api/)");
        try (ZipFile api = new ZipFile(apiJar().toFile())) {
            for (String name : CLASSES) {
                ZipEntry entry = api.getEntry(name);
                assertNotNull(entry, name);
                String constantPoolBytes = new String(api.getInputStream(entry).readAllBytes(),
                        StandardCharsets.ISO_8859_1);
                assertFalse(internalReference.matcher(constantPoolBytes).find(), name);
                assertFalse(constantPoolBytes.contains("net/minecraft/client/"), name);
                assertFalse(constantPoolBytes.contains("net/minecraftforge/client/"), name);
                assertFalse(constantPoolBytes.contains("com/mojang/blaze3d/"), name);
                if (VERSION_CLASSES.contains(name)) {
                    assertFalse(constantPoolBytes.contains("net/minecraft/"), name);
                    assertFalse(constantPoolBytes.contains("net/minecraftforge/"), name);
                }
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

    @Test
    void rocketConsumerCompilesWithOnlyTheClassifierAndPlatformDependencies() throws IOException {
        Compilation result = compile("RocketApiConsumer", platformConsumerClasspath());
        assertTrue(result.success(), result.diagnostics().toString());
    }

    @Test
    void atmosphereConsumerCompilesWithOnlyTheClassifierAndPlatformDependencies() throws IOException {
        Compilation result = compile("AtmosphereApiConsumer", platformConsumerClasspath());
        assertTrue(result.success(), result.diagnostics().toString());
    }

    @Test
    void suitConsumerCompilesWithOnlyTheClassifierAndPlatformDependencies() throws IOException {
        Compilation result = compile("SuitApiConsumer", platformConsumerClasspath());
        assertTrue(result.success(), result.diagnostics().toString());
    }

    @Test
    void componentConsumerCompilesWithOnlyTheClassifierAndPlatformDependencies() throws IOException {
        Compilation result = compile("ComponentApiConsumer", platformConsumerClasspath());
        assertTrue(result.success(), result.diagnostics().toString());
    }

    @Test
    void platformConsumerCannotImportTheAtmosphereRegistry() throws IOException {
        try (ZipFile runtime = new ZipFile(Path.of(System.getProperty("arce.runtimeJar")).toFile())) {
            assertNotNull(runtime.getEntry(HOST_PACKAGE + "compat/atmosphere/AtmosphereBoundaryRegistry.class"));
        }
        Compilation result = compile("AtmosphereInternalConsumer", platformConsumerClasspath());
        assertFalse(result.success(), "The platform must not expose the host's mutable registry");
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.getKind() == Diagnostic.Kind.ERROR
                        && diagnostic.getCode().equals("compiler.err.doesnt.exist")
                        && diagnostic.getMessage(Locale.ROOT).contains("compat.atmosphere")),
                result.diagnostics().toString());
    }

    @Test
    void platformConsumerCannotImportAnExistingInternalRocketType() throws IOException {
        try (ZipFile runtime = new ZipFile(Path.of(System.getProperty("arce.runtimeJar")).toFile())) {
            assertNotNull(runtime.getEntry(HOST_PACKAGE + "rocket/forge/RocketBlockEntityAdapter.class"));
        }
        Compilation result = compile("RocketInternalConsumer", platformConsumerClasspath());
        assertFalse(result.success(), "Platform dependencies must not expose host internals");
        List<Diagnostic<? extends JavaFileObject>> errors = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
        assertTrue(errors.stream().anyMatch(diagnostic -> diagnostic.getCode().equals(
                "compiler.err.doesnt.exist") && diagnostic.getMessage(Locale.ROOT).contains(
                "io.github.sunthemoon.advancedrocketrycommunity.rocket.forge")), errors.toString());
        assertTrue(errors.stream().allMatch(diagnostic ->
                diagnostic.getCode().equals("compiler.err.doesnt.exist")
                        || diagnostic.getCode().equals("compiler.err.cant.resolve.location")), errors.toString());
    }

    private List<Path> platformConsumerClasspath() throws IOException {
        String property = System.getProperty("arce.apiPlatformClasspath");
        assertNotNull(property, "Gradle must supply only the platform compile dependencies");
        assertFalse(property.isBlank(), "The Forge-facing consumer requires platform dependencies");
        List<Path> dependencies = Stream.of(property.split(Pattern.quote(File.pathSeparator), -1))
                .map(value -> {
                    assertFalse(value.isBlank(), "Empty classpath entries would expose the working directory");
                    return Path.of(value).toAbsolutePath().normalize();
                }).toList();
        for (Path dependency : dependencies) {
            assertTrue(Files.isRegularFile(dependency), "Source/output directories are forbidden: " + dependency);
            assertTrue(dependency.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"),
                    "Platform dependencies must be JARs: " + dependency);
            try (ZipFile jar = new ZipFile(dependency.toFile())) {
                List<String> hostClasses = jar.stream().map(ZipEntry::getName)
                        .filter(name -> name.endsWith(".class") && name.contains(HOST_PACKAGE)).toList();
                assertTrue(hostClasses.isEmpty(), "Host classes leaked through " + dependency + ": " + hostClasses);
            }
        }
        return Stream.concat(Stream.of(apiJar()), dependencies.stream()).toList();
    }

    private Compilation compile(String name) throws IOException {
        return compile(name, List.of(apiJar()));
    }

    private Compilation compile(String name, List<Path> classpath) throws IOException {
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
                "-classpath", classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)),
                "-sourcepath", emptySourcePath.toString(),
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
