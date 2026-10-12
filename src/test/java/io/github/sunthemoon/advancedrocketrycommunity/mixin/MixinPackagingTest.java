package io.github.sunthemoon.advancedrocketrycommunity.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** ADR-073: the reobfuscated jar declares the one Mixin configuration and ships a refmap with its SRG targets. */
class MixinPackagingTest {
    private static final String CONFIG = "advancedrocketrycommunity.mixins.json";
    private static final String REFMAP = "advancedrocketrycommunity.refmap.json";
    private static final String MIXIN = "io/github/sunthemoon/advancedrocketrycommunity/mixin/IOWorkerPendingReadMixin";
    // SRG names read from forge-1.20.1-47.4.10-srg.jar: the loadAsync lambda and IOWorker$PendingStore.data.
    private static final Map<String, String> SRG_TARGETS = Map.of(
            "lambda$loadAsync$4(Lnet/minecraft/world/level/ChunkPos;)Lcom/mojang/datafixers/util/Either;",
            "Lnet/minecraft/world/level/chunk/storage/IOWorker;m_223494_"
                    + "(Lnet/minecraft/world/level/ChunkPos;)Lcom/mojang/datafixers/util/Either;",
            "Lnet/minecraft/world/level/chunk/storage/IOWorker$PendingStore;data:Lnet/minecraft/nbt/CompoundTag;",
            "Lnet/minecraft/world/level/chunk/storage/IOWorker$PendingStore;f_63565_:Lnet/minecraft/nbt/CompoundTag;");

    @Test
    void manifestNamesTheConfiguration() throws IOException {
        try (ZipFile jar = runtimeJar(); var input = jar.getInputStream(entry(jar, "META-INF/MANIFEST.MF"))) {
            assertEquals(CONFIG, new Manifest(input).getMainAttributes().getValue("MixinConfigs"));
        }
    }

    @Test
    void configurationIsRequiredCommonAndListsOnlyThePendingReadMixin() throws IOException {
        try (ZipFile jar = runtimeJar()) {
            JsonObject config = json(jar, CONFIG);
            assertTrue(config.get("required").getAsBoolean());
            assertEquals("0.8", config.get("minVersion").getAsString());
            assertEquals("io.github.sunthemoon.advancedrocketrycommunity.mixin", config.get("package").getAsString());
            assertEquals("JAVA_17", config.get("compatibilityLevel").getAsString());
            assertEquals(REFMAP, config.get("refmap").getAsString());
            JsonArray mixins = new JsonArray();
            mixins.add("IOWorkerPendingReadMixin");
            assertEquals(mixins, config.get("mixins"));
            assertFalse(config.has("client"));
            assertFalse(config.has("server"));
            assertNotNull(jar.getEntry(MIXIN + ".class"));
        }
    }

    @Test
    void refmapResolvesBothTargetsToTheirSrgNames() throws IOException {
        try (ZipFile jar = runtimeJar()) {
            JsonObject refmap = json(jar, REFMAP);
            for (JsonObject mappings : new JsonObject[] {
                    refmap.getAsJsonObject("mappings").getAsJsonObject(MIXIN),
                    refmap.getAsJsonObject("data").getAsJsonObject("searge").getAsJsonObject(MIXIN)}) {
                assertEquals(SRG_TARGETS.size(), mappings.size());
                SRG_TARGETS.forEach((mapped, srg) -> assertEquals(srg, mappings.get(mapped).getAsString(), mapped));
            }
        }
    }

    private static ZipFile runtimeJar() throws IOException {
        return new ZipFile(System.getProperty("arce.runtimeJar"));
    }

    private static ZipEntry entry(ZipFile jar, String name) {
        ZipEntry entry = jar.getEntry(name);
        assertNotNull(entry, name);
        return entry;
    }

    private static JsonObject json(ZipFile jar, String name) throws IOException {
        try (var input = jar.getInputStream(entry(jar, name))) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
