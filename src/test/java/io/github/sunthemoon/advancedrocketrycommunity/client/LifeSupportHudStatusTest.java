package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W504: an exposed player lacks breathable air, not necessarily ambient pressure (ADR-066 section 3.1). */
class LifeSupportHudStatusTest {
    private static final ResourceLocation VENUS = id("venus");
    private static final ResourceLocation MOON = id("moon");
    private static final ResourceLocation SPACE = id("space");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void venusHasANonBreathableAtmosphereInBothLanguages() throws IOException {
        Component label = exposed(VENUS, catalog(entry(VENUS, VENUS, 9.2)));
        assertBilingual(label, "NON-BREATHABLE ATMOSPHERE", "不可呼吸大气");
    }

    @Test
    void moonAndSpaceAreVacuumEvenWithVenusInTheCatalog() throws IOException {
        var catalog = catalog(entry(VENUS, VENUS, 9.2), entry(MOON, MOON, 0), entry(SPACE, SPACE, 0));
        assertBilingual(exposed(MOON, catalog), "VACUUM", "真空");
        // The actual Space Level's pressure wins; no station orbit body's surface air is used.
        assertBilingual(exposed(SPACE, catalog), "VACUUM", "真空");
    }

    @Test
    void anyPositivePressureIsAnAtmosphereRatherThanVacuum() throws IOException {
        assertBilingual(exposed(MOON, catalog(entry(MOON, MOON, Double.MIN_VALUE))),
                "NON-BREATHABLE ATMOSPHERE", "不可呼吸大气");
    }

    @Test
    void anAbsentCatalogOrUnmappedLevelUsesNeutralText() throws IOException {
        assertBilingual(exposed(VENUS, Optional.empty()), "NO AIR", "无可呼吸空气");
        assertBilingual(exposed(VENUS, catalog(entry(MOON, MOON, 0))), "NO AIR", "无可呼吸空气");
    }

    @Test
    void pressureIsSelectedByMappedLevelNotByBodyId() throws IOException {
        var catalog = catalog(entry(MOON, MOON, 0), entry(id("pack_body"), id("pack_level"), 1));
        assertBilingual(exposed(id("pack_level"), catalog), "NON-BREATHABLE ATMOSPHERE", "不可呼吸大气");
    }

    @Test
    void otherProtectionStatesKeepTheirExistingKeys() {
        for (PlayerProtectionStatus status : PlayerProtectionStatus.values()) {
            if (status == PlayerProtectionStatus.EXPOSED) {
                continue;
            }
            for (var catalog : List.of(Optional.<CelestialSnapshot>empty(), catalog(entry(VENUS, VENUS, 9.2)))) {
                var text = contents(LifeSupportHud.statusLabel(status, VENUS, catalog));
                assertEquals("hud.advancedrocketrycommunity.life_support.state." + status.diagnosticKey(), text.getKey());
                assertEquals(0, text.getArgs().length);
            }
        }
    }

    private static Component exposed(ResourceLocation level, Optional<CelestialSnapshot> catalog) {
        return LifeSupportHud.statusLabel(PlayerProtectionStatus.EXPOSED, level, catalog);
    }

    private static void assertBilingual(Component label, String english, String chinese) throws IOException {
        String key = contents(label).getKey();
        assertEquals(List.of(english), packagedTexts("en_us", key), "English definition of " + key);
        assertEquals(List.of(chinese), packagedTexts("zh_cn", key), "Chinese definition of " + key);
    }

    private static TranslatableContents contents(Component label) {
        return assertInstanceOf(TranslatableContents.class, label.getContents());
    }

    private static Optional<CelestialSnapshot> catalog(CelestialSnapshot.Entry... entries) {
        return Optional.of(new CelestialSnapshot(CelestialSnapshotCodec.SCHEMA_VERSION, List.of(entries)));
    }

    private static CelestialSnapshot.Entry entry(ResourceLocation body, ResourceLocation level, double pressure) {
        return new CelestialSnapshot.Entry(body, Optional.empty(), Optional.of(level), 1, pressure, false, 300,
                id("non_breathable"), id("space"), new CelestialCapabilities(true, true, false), 1, 0);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("advancedrocketrycommunity", path);
    }

    private static List<String> packagedTexts(String locale, String key) throws IOException {
        List<String> texts = new ArrayList<>();
        for (Path root : List.of(Path.of("src/generated"), Path.of("src/main/resources"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.getFileName().toString().equals(locale + ".json")
                        && path.getParent().getFileName().toString().equals("lang")).toList()) {
                    var language = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if (language.has(key)) {
                        texts.add(language.get(key).getAsString());
                    }
                }
            }
        }
        return texts;
    }
}
