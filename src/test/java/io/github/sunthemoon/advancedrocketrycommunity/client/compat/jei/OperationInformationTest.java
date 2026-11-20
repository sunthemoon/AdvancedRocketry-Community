package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasPadState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentSupplyEngine;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentSupplyInput;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityField;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunRoute;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchService;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionBurn;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeHooks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OperationInformationTest {
    private static final String PREFIX = "jei.advancedrocketrycommunity.operation.";
    private static final Path LANG = Path.of("src/main/resources/assets/advancedrocketrycommunity_jei_operations/lang");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
        // Forge normally fills this cache during startup; the deliberately minimal JUnit bootstrap does not.
        ForgeHooks.updateBurns();
    }

    @Test
    void operatingPagesRegisterInTheOrdinaryJvmWithoutJei() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("mezz.jei.api.IModPlugin"));
        List<Component> pages = new ArrayList<>();
        List<ResourceLocation> lookedUp = new ArrayList<>();
        OperationInformation.register(id -> { lookedUp.add(id); return Items.STONE; }, (stacks, text) -> {
            assertFalse(stacks.isEmpty());
            assertTrue(stacks.stream().allMatch(stack -> stack.is(Items.STONE) && stack.getCount() == 1));
            pages.add(text);
        });
        assertEquals(OperationInformation.entries().stream().flatMap(entry -> entry.items().stream()).toList(), lookedUp);
        assertEquals(OperationInformation.entries().stream().map(OperationInformation.Entry::key).toList(),
                pages.stream().map(page -> ((TranslatableContents) page.getContents()).getKey()).toList());
        assertEquals(29, pages.size());
    }

    @Test
    void realPluginBridgeCallsTheJeiApiWithoutInstallingItsRuntime() throws Exception {
        // Only compile-time API jars enter this child loader; no JEI implementation or build change is needed.
        List<URL> urls = new ArrayList<>();
        urls.add(Path.of("build/classes/java/main").toUri().toURL());
        for (String entry : System.getProperty("arce.apiPlatformClasspath").split(Pattern.quote(File.pathSeparator))) {
            String name = Path.of(entry).getFileName().toString();
            if (name.startsWith("jei-") && name.contains("-api-") && name.endsWith(".jar")) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        assertTrue(urls.size() >= 3, "Both declared JEI API artifacts must be available offline");
        String pluginName = getClass().getPackageName() + ".ArceJeiPlugin";
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(pluginName)) { return super.loadClass(name, resolve); }
                Class<?> type = findLoadedClass(name);
                if (type == null) { type = findClass(name); }
                if (resolve) { resolveClass(type); }
                return type;
            }
        }) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("mezz.jei.library.startup.JeiStarter"));
            Class<?> registrationType = loader.loadClass("mezz.jei.api.registration.IRecipeRegistration");
            List<String> keys = new ArrayList<>();
            Object registration = Proxy.newProxyInstance(loader, new Class<?>[]{registrationType}, (proxy, method, args) -> {
                assertEquals("addIngredientInfo", method.getName());
                assertEquals(3, args.length);
                assertFalse(((List<?>) args[0]).isEmpty());
                assertNotNull(args[1]);
                Component[] description = (Component[]) args[2];
                assertEquals(1, description.length);
                keys.add(((TranslatableContents) description[0].getContents()).getKey());
                return null;
            });
            var bridge = loader.loadClass(pluginName).getDeclaredMethod("registerInformation", registrationType, Function.class);
            bridge.setAccessible(true);
            bridge.invoke(null, registration, (Function<ResourceLocation, Item>) id -> Items.STONE);
            assertEquals(OperationInformation.entries().stream().map(OperationInformation.Entry::key).toList(), keys);
        }
        String source = Files.readString(Path.of("src/main/java", pluginName.replace('.', '/') + ".java"));
        assertTrue(source.contains("registerInformation(registration, BuiltInRegistries.ITEM::get);"));
    }

    @Test
    void bothPackagedLanguagesHaveExactlyTheNewKeysAndMatchingArguments() throws Exception {
        Set<String> expected = OperationInformation.entries().stream().map(OperationInformation.Entry::key)
                .filter(key -> key.startsWith(PREFIX)).collect(Collectors.toSet());
        assertEquals(28, expected.size());
        for (String language : List.of("en_us", "zh_cn")) {
            JsonObject translations = JsonParser.parseString(Files.readString(LANG.resolve(language + ".json"))).getAsJsonObject();
            assertEquals(expected, translations.keySet());
            String resource = "/assets/advancedrocketrycommunity_jei_operations/lang/" + language + ".json";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, "Language file must be packaged, not only present in the source tree");
            }
            for (OperationInformation.Entry entry : OperationInformation.entries()) {
                if (!expected.contains(entry.key())) { continue; }
                String template = translations.get(entry.key()).getAsString();
                assertFalse(template.isBlank());
                assertEquals(entry.arguments().size(), template.split("%s", -1).length - 1, entry.key());
                String rendered = String.format(Locale.ROOT, template, entry.arguments().toArray());
                assertFalse(rendered.contains("%s"), entry.key());
                for (Object value : entry.arguments()) { assertTrue(rendered.contains(value.toString()), entry.key()); }
            }
        }
    }

    @Test
    void combustionExampleMatchesTheExecutableFuelCredit() {
        List<Object> args = page("combustion").arguments();
        assertEquals(List.of(CombustionBurn.RATE, CombustionBurn.CAPACITY, CombustionBurn.OUTPUT_PER_TICK, 64_000), args);
        CombustionBurn.State state = new CombustionBurn.State(0, 0, 0);
        int credit = 0;
        for (int tick = 0; tick < 1_600; tick++) {
            var step = CombustionBurn.tick(state, 1_600);
            assertEquals(tick == 0, step.consumeFuel());
            credit += step.state().energy();
            state = step.state().withEnergy(0);
        }
        assertEquals(0, state.remaining());
        assertEquals(credit, args.get(3));
    }

    @Test
    void gasAndVentValuesMatchFiniteConversionsAndPaidSupply() {
        var pad = GasPadState.EMPTY.insert(0, GasPadState.Canister.OXYGEN).process();
        assertEquals(page("finite_canisters").arguments().get(0), pad.amount());
        assertEquals(page("gas_charge_pad").arguments().get(0), pad.amount());
        assertEquals(GasPadState.Canister.EMPTY, pad.input());
        var filled = pad.insert(1, GasPadState.Canister.EMPTY).process();
        assertEquals(0, filled.amount());
        assertEquals(GasPadState.Canister.OXYGEN, filled.output());
        assertEquals(List.of(AtmosphereLimits.OXYGEN_UNITS_PER_CANISTER, AtmosphereLimits.VENT_OXYGEN_CAPACITY,
                AtmosphereLimits.VENT_ENERGY_PER_TICK, VentSupplyInput.TICKS_PER_OXYGEN), page("oxygen_vent").arguments());
        var paid = VentSupplyEngine.tick(new VentSupplyInput(VolumeScanOutcome.SEALED, true,
                pad.amount(), 1_000, VentSupplyInput.TICKS_PER_OXYGEN - 1));
        assertEquals(1, paid.oxygenConsumed());
        assertEquals(page("oxygen_vent").arguments().get(2), paid.energyConsumed());
    }

    @Test
    void assemblyResearchAndEndgameQuotesUseProductionValues() {
        assertEquals(List.of(SatelliteBuilderBlockEntity.SLOT_MODULE_LAST - SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST + 1,
                SatelliteBuilderBlockEntity.ASSEMBLY_ENERGY, SatelliteBuilderBlockEntity.REDSTONE_ENERGY),
                page("satellite_builder").arguments());
        assertEquals(List.of(ResearchService.SURVEY_TICKS, ResearchService.ANALYSIS_TICKS,
                ResearchService.ENERGY_PER_TICK, ResearchService.CREDIT), page("ground_research").arguments());
        assertEquals(List.of(LaserDrillSettings.DEFAULTS.costFe(), LaserDrillSettings.DEFAULTS.operationIntervalTicks()),
                page("laser").arguments());
        assertEquals(List.of(GravityField.MIN_RADIUS, GravityField.MAX_RADIUS, GravityField.DEFAULT_RADIUS,
                GravityField.upkeep(GravityField.DEFAULT_RADIUS)), page("gravity").arguments());
        assertEquals(List.of(RailgunRoute.quote(true, 0, 0, 100).cost(), RailgunRoute.LOCAL_FE_PER_BLOCK,
                RailgunRoute.quote(true, 100_000, 0, 100).cost(), RailgunRoute.quote(false, 0, 0, 100).cost(),
                RailgunRoute.quote(false, 0, 0, 100).travel()), page("railgun").arguments());
        assertEquals(List.of(ElevatorRules.cargoCost(100), ElevatorRules.CARGO_TRAVEL_TICKS,
                ElevatorRules.rideCost(100), ElevatorRules.RIDE_COUNTDOWN_TICKS), page("elevator").arguments());
    }

    @Test
    void applicabilityCoversEveryCensusDeviceAndPartAndEachInfoPageIsRegistered() throws Exception {
        String census = Files.readString(Path.of("docs/releases/v1.8.0/MACHINE-CENSUS.md"));
        String table = Files.readString(Path.of("docs/releases/v1.8.0/JEI-APPLICABILITY.md"));
        Map<String, List<String>> rows = table.lines().filter(line -> line.startsWith("| `"))
                .map(line -> line.split("\\|"))
                .collect(Collectors.toMap(cells -> cells[1].trim().replace("`", ""),
                        cells -> List.of(cells[2].trim(), cells[3].trim().replace("`", ""))));
        Set<String> censusIds = new HashSet<>();
        for (String heading : List.of("## 3.", "## 6.")) {
            String section = census.substring(census.indexOf(heading));
            section = section.substring(0, section.indexOf("\n## ", 1));
            section.lines().filter(line -> line.startsWith("| `"))
                    .forEach(line -> censusIds.add(line.split("`", 3)[1]));
        }
        assertEquals(66, censusIds.size());
        assertEquals(censusIds, rows.keySet());
        Set<String> informed = OperationInformation.entries().stream().flatMap(entry -> entry.items().stream())
                .map(ResourceLocation::getPath).collect(Collectors.toSet());
        rows.forEach((id, cells) -> {
            if (cells.get(0).equals("Information")) {
                assertTrue(informed.contains(id), id);
                String key = cells.get(1).split(" ")[0];
                assertTrue(OperationInformation.entries().stream().anyMatch(entry -> entry.key().endsWith("." + key)
                        && entry.items().stream().anyMatch(item -> item.getPath().equals(id))), id);
            } else { assertFalse(informed.contains(id), id); }
        });
    }

    private static OperationInformation.Entry page(String name) {
        return OperationInformation.entries().stream().filter(entry -> entry.key().equals(PREFIX + name)).findFirst().orElseThrow();
    }
}
