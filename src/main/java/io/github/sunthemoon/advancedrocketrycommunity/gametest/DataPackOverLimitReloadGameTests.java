package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.Level;

/**
 * W179 (VDOC 12.4, docs/17 section 5): a generated world data pack with many oversized or invalid classic recipes,
 * item tags and multiblock patterns is selected and then deselected through the server's own data-pack reload, as
 * {@code /datapack enable} does. The reload completes; every bad recipe, tag and pattern is refused while the valid
 * content stays; each refusal logs exactly one bounded entry; and deselecting the pack restores the server.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataPackOverLimitReloadGameTests {
    private static final String BATCH = "datapack_overlimit";
    /** Spaces the pack scan and the two reloads, so their work never shares one stretch of ticks. */
    private static final int CATCH_UP_TICKS = 60;
    /** No logged refusal, nor any cause it carries, may be longer than this. */
    static final int MAX_LOGGED_CHARS = 512;
    private static final ResourceLocation KNOWN_GOOD = ModIdentity.id("lathe_iron_rod");
    private static final Pattern PARSING = Pattern.compile("Parsing error loading recipe (\\S+)");
    private static final Pattern UNREADABLE_FILE = Pattern.compile("Couldn't parse data file (\\S+) from \\S+");
    private static final Pattern MISSING_REFERENCE = Pattern.compile("Couldn't load tag "
            + Pattern.quote(OverLimitDataPack.MISSING_REFERENCE_TAG.toString())
            + " as it is missing following references: " + Pattern.quote(OverLimitDataPack.MISSING_ITEM.toString())
            + " \\(from " + Pattern.quote(OverLimitDataPack.ID) + "\\)");
    private static final Pattern UNREADABLE_TAG = Pattern.compile("Couldn't read tag list "
            + Pattern.quote(OverLimitDataPack.UNREADABLE_TAG.toString()) + " from \\S+ in data pack "
            + Pattern.quote(OverLimitDataPack.ID));
    private static final String REJECTED = "Rejected multiblock pattern catalog; last valid generation remains "
            + "active: pattern catalog: exceeds " + MultiblockPatternCatalog.MAX_DEFINITIONS + " definitions";
    private static final String FORGOTTEN_TAG = "Not all defined tags for registry ResourceKey[minecraft:root / "
            + "minecraft:item] are present in data pack: " + OverLimitDataPack.OVERSIZED_TAG;
    private static final Pattern ACCEPTED = Pattern.compile(
            "Accepted multiblock pattern catalog generation \\d+ with (\\d+) definitions");
    /** The two pattern catalogs: the mod's and the classic machines' ({@code ClassicRuntime}). */
    private static final int PATTERN_CATALOGS = 2;

    private DataPackOverLimitReloadGameTests() {
    }

    /**
     * What the server held before the pack: its selected packs, its classic recipes by type, its patterns and its
     * command set.
     */
    private record Before(List<String> selected, Map<String, Integer> classic, int patterns, Commands commands) {
        static Before of(MinecraftServer server) {
            return new Before(List.copyOf(server.getPackRepository().getSelectedIds()), classicCounts(server),
                    server.getResourceManager().listResources(MultiblockPatternReloadListener.DIRECTORY,
                            location -> location.getPath().endsWith(".json")).size(), server.getCommands());
        }
    }

    /** The command set to reinstate if the test ends before its restoring reload. */
    private static Commands original;

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void anOverLimitPackIsRefusedEntryByEntryWithBoundedLoggingAndThenRemoved(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        OverLimitDataPack pack = new OverLimitDataPack();
        Before before = Before.of(server);
        original = before.commands();
        helper.assertTrue(!before.selected().contains(OverLimitDataPack.ID), "The fixture pack is already selected");
        helper.assertTrue(server.getRecipeManager().byKey(KNOWN_GOOD).isPresent(), "No known classic recipe");
        List<String> enabled = new ArrayList<>(before.selected());
        enabled.add(OverLimitDataPack.ID);
        Reload[] reload = new Reload[1];
        helper.startSequence()
                .thenExecute(() -> write(helper, server, pack))
                .thenIdle(CATCH_UP_TICKS)
                .thenExecute(() -> reload[0] = new Reload(server, enabled))
                .thenWaitUntil(() -> helper.assertTrue(reload[0].done(), "The over-limit reload is still running"))
                .thenExecute(() -> enabled(helper, server, pack, before, reload[0]))
                .thenIdle(CATCH_UP_TICKS)
                .thenExecute(() -> reload[0] = new Reload(server, before.selected()))
                .thenWaitUntil(() -> helper.assertTrue(reload[0].done(), "The restoring reload is still running"))
                .thenExecute(() -> disabled(helper, server, pack, before, reload[0]))
                .thenSucceed();
    }

    /**
     * One reload with the given selection, started from a worker thread. The server's reload chain is unchanged: it
     * selects the packs and applies every listener on the server thread and prepares them on worker threads. Started
     * from the server thread, as {@code /reload} does, it would block a single tick for the whole reload and cost the
     * run a deterministic "Can't keep up" warning (W179's first trial: 2,131 ms behind); here the server keeps ticking.
     * What every logger writes meanwhile is captured until {@link #finish}.
     */
    private static final class Reload {
        private final ReloadLogCapture capture;
        private final long started = System.nanoTime();
        private final CompletableFuture<Void> future;

        Reload(MinecraftServer server, List<String> selected) {
            capture = new ReloadLogCapture();
            open = capture;
            future = CompletableFuture.supplyAsync(() -> server.reloadResources(selected), Util.backgroundExecutor())
                    .thenCompose(Function.identity());
        }

        boolean done() {
            return future.isDone();
        }

        long millis() {
            return (System.nanoTime() - started) / 1_000_000L;
        }

        List<ReloadLogCapture.Event> finish(GameTestHelper helper) {
            capture.close();
            open = null;
            helper.assertTrue(!future.isCompletedExceptionally(), "The reload failed: " + future);
            return capture.events();
        }
    }

    /** The capture of a reload that a failed test left open. */
    private static ReloadLogCapture open;

    /** A failed run must not leave the over-limit pack selected for the batches after it. */
    @AfterBatch(batch = BATCH)
    public static void removeTheFixturePack(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (open != null) {
            open.close();
            open = null;
        }
        if (server.getPackRepository().getSelectedIds().contains(OverLimitDataPack.ID)) {
            List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
            selected.remove(OverLimitDataPack.ID);
            server.reloadResources(selected);
        }
        if (original != null && server.getCommands() != original) {
            reinstate(server, original);
        }
        original = null;
        try {
            OverLimitDataPack.delete(OverLimitDataPack.directory(server));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        server.getPackRepository().reload();
    }

    private static void write(GameTestHelper helper, MinecraftServer server, OverLimitDataPack pack) {
        long started = System.nanoTime();
        try {
            pack.write(OverLimitDataPack.directory(server));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        long written = System.nanoTime();
        server.getPackRepository().reload();
        helper.assertTrue(server.getPackRepository().getAvailableIds().contains(OverLimitDataPack.ID),
                "The generated pack is not available");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W179_RELOAD stage=write write_ms={} scan_ms={}",
                (written - started) / 1_000_000L, (System.nanoTime() - written) / 1_000_000L);
    }

    private static void enabled(GameTestHelper helper, MinecraftServer server, OverLimitDataPack pack, Before before,
                                Reload reload) {
        long millis = reload.millis();
        List<ReloadLogCapture.Event> logged = reload.finish(helper);
        helper.assertTrue(server.getPackRepository().getSelectedIds().contains(OverLimitDataPack.ID),
                "The reload did not select the pack");

        List<String> defects = new ArrayList<>();
        var recipes = server.getRecipeManager();
        for (ResourceLocation id : pack.refusedAtLoad.keySet()) {
            if (recipes.byKey(id).isPresent()) {
                defects.add(id + " loaded");
            }
        }
        for (ResourceLocation id : pack.unreadable) {
            if (recipes.byKey(id).isPresent()) {
                defects.add(id + " loaded");
            }
        }
        for (Map.Entry<ResourceLocation, String> entry : pack.refusedAtResolution.entrySet()) {
            Optional<? extends Recipe<?>> recipe = recipes.byKey(entry.getKey());
            if (!(recipe.orElse(null) instanceof ClassicMachineRecipe classic)) {
                defects.add(entry.getKey() + " did not load as a classic recipe");
                continue;
            }
            // ClassicMachineManager.resolve disables a recipe whose definition throws this.
            try {
                classic.definition();
                defects.add(entry.getKey() + " resolved");
            } catch (IllegalArgumentException refused) {
                if (!entry.getValue().equals(refused.getMessage())) {
                    defects.add(entry.getKey() + " was refused for " + refused.getMessage());
                }
            }
        }
        Map<String, Integer> classic = classicCounts(server);
        for (String type : OverLimitDataPack.CLASSIC_TYPES) {
            if (classic.get(type) != before.classic().get(type) + pack.loadable(type)) {
                defects.add(type + " holds " + classic.get(type) + " recipes, not " + before.classic().get(type)
                        + " + " + pack.loadable(type));
            }
        }
        if (recipes.byKey(KNOWN_GOOD).isEmpty()) {
            defects.add(KNOWN_GOOD + " was lost");
        }
        int oversized = tag(OverLimitDataPack.OVERSIZED_TAG).map(HolderSet.Named::size).orElse(-1);
        if (oversized != ProcessInput.MAX_VARIANTS + 1) {
            defects.add("the oversized tag holds " + oversized + " items");
        }
        if (tag(OverLimitDataPack.MISSING_REFERENCE_TAG).isPresent() || tag(OverLimitDataPack.UNREADABLE_TAG).isPresent()) {
            defects.add("a refused tag was bound");
        }
        defects.addAll(enabledLog(pack, logged));
        helper.assertTrue(defects.isEmpty(), "Over-limit reload defects: " + defects);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W179_RELOAD stage=enable ms={} refused_at_load={} unreadable={} "
                        + "refused_at_resolution={} problems_logged={} longest={}", millis, pack.refusedAtLoad.size(),
                pack.unreadable.size(), pack.refusedAtResolution.size(),
                logged.stream().filter(ReloadLogCapture.Event::problem).count(), longest(logged));
    }

    private static void disabled(GameTestHelper helper, MinecraftServer server, OverLimitDataPack pack, Before before,
                                 Reload reload) {
        long millis = reload.millis();
        List<ReloadLogCapture.Event> logged = reload.finish(helper);
        reinstate(server, before.commands());
        original = null;
        try {
            OverLimitDataPack.delete(OverLimitDataPack.directory(server));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        server.getPackRepository().reload();
        List<String> defects = new ArrayList<>();
        if (!List.copyOf(server.getPackRepository().getSelectedIds()).equals(before.selected())
                || server.getPackRepository().getAvailableIds().contains(OverLimitDataPack.ID)) {
            defects.add("the pack selection was not restored: " + server.getPackRepository().getSelectedIds());
        }
        for (ResourceLocation id : pack.recipes()) {
            if (server.getRecipeManager().byKey(id).isPresent()) {
                defects.add(id + " outlived its pack");
            }
        }
        if (!classicCounts(server).equals(before.classic()) || server.getRecipeManager().byKey(KNOWN_GOOD).isEmpty()) {
            defects.add("classic recipes " + classicCounts(server) + " differ from " + before.classic());
        }
        // Vanilla's MappedRegistry.bindTags warns once that the data no longer defines the tag and keeps its named set
        // with the old contents, but it rebinds every item without the tag: no item is a member any more.
        TagKey<Item> oversized = TagKey.create(Registries.ITEM, OverLimitDataPack.OVERSIZED_TAG);
        long members = BuiltInRegistries.ITEM.holders().filter(holder -> holder.is(oversized)).count();
        if (members != 0) {
            defects.add(members + " items are still members of the oversized tag");
        }
        int accepted = 0;
        int forgotten = 0;
        for (ReloadLogCapture.Event event : logged) {
            if (event.level() == Level.WARN && event.message().equals(FORGOTTEN_TAG)) {
                forgotten++;
            } else if (event.problem()) {
                defects.add("restoring logged " + describe(event));
            }
            Matcher matcher = ACCEPTED.matcher(event.message());
            if (matcher.matches()) {
                accepted++;
                if (Integer.parseInt(matcher.group(1)) != before.patterns()) {
                    defects.add("a restored pattern catalog holds " + matcher.group(1) + ", not " + before.patterns());
                }
            }
        }
        if (accepted != PATTERN_CATALOGS) {
            defects.add(accepted + " pattern catalogs were accepted again, not " + PATTERN_CATALOGS);
        }
        if (forgotten != 1) {
            defects.add("Forge warned " + forgotten + " times, not once, that the oversized tag is gone");
        }
        if (server.getCommands() != before.commands()
                || server.getCommands().getDispatcher().getRoot().getChild("save-all") == null) {
            defects.add("the server's own command set, with save-all, is not back");
        }
        helper.assertTrue(defects.isEmpty(), "Restoring reload defects: " + defects);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W179_RELOAD stage=disable ms={} patterns={}", millis,
                before.patterns());
    }


    /**
     * {@code GameTestServer} is not a {@code DedicatedServer}, so {@code MinecraftServer.reloadResources} builds the
     * INTEGRATED command set, without {@code save-all}, {@code stop} or {@code op}, which later batches use. The
     * server's own set, built at start with the DEDICATED selection, goes back into the reloaded resources.
     */
    private static void reinstate(MinecraftServer server, Commands commands) {
        ReloadableServerResources managers = server.getServerResources().managers();
        for (Field field : ReloadableServerResources.class.getDeclaredFields()) {
            if (field.getType() == Commands.class && !Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                    field.set(managers, commands);
                    return;
                } catch (IllegalAccessException failure) {
                    throw new IllegalStateException("The reloaded command set cannot be replaced", failure);
                }
            }
        }
        throw new IllegalStateException("The reloaded resources hold no command set");
    }

    /** Each refusal of the pack logs exactly once, within {@link #MAX_LOGGED_CHARS}; nothing else warns. */
    private static List<String> enabledLog(OverLimitDataPack pack, List<ReloadLogCapture.Event> logged) {
        List<String> defects = new ArrayList<>();
        Map<ResourceLocation, Integer> parsed = new TreeMap<>();
        Map<ResourceLocation, Integer> unreadable = new TreeMap<>();
        Map<String, Integer> others = new LinkedHashMap<>();
        for (ReloadLogCapture.Event event : logged) {
            if (ACCEPTED.matcher(event.message()).matches()) {
                defects.add("a pattern catalog was accepted: " + event.message());
            }
            if (!event.problem()) {
                continue;
            }
            if (event.longest() > MAX_LOGGED_CHARS) {
                defects.add("an entry is longer than " + MAX_LOGGED_CHARS + " characters: " + describe(event));
            }
            Matcher parsing = PARSING.matcher(event.message());
            Matcher file = UNREADABLE_FILE.matcher(event.message());
            if (event.level() != Level.ERROR) {
                defects.add("unexpected " + describe(event));
            } else if (parsing.matches()) {
                ResourceLocation id = new ResourceLocation(parsing.group(1));
                parsed.merge(id, 1, Integer::sum);
                String reason = pack.refusedAtLoad.get(id);
                if (reason != null && (event.causes().isEmpty() || !event.causes().get(0).equals(reason))) {
                    defects.add(id + " was refused for " + event.causes() + ", not " + reason);
                }
            } else if (file.matches()) {
                unreadable.merge(new ResourceLocation(file.group(1)), 1, Integer::sum);
            } else if (MISSING_REFERENCE.matcher(event.message()).matches()) {
                others.merge("missing reference", 1, Integer::sum);
            } else if (UNREADABLE_TAG.matcher(event.message()).matches()) {
                others.merge("unreadable tag", 1, Integer::sum);
            } else if (event.message().equals(REJECTED)) {
                others.merge("rejected pattern catalog", 1, Integer::sum);
            } else {
                defects.add("unexpected " + describe(event));
            }
        }
        Map<ResourceLocation, Integer> once = new TreeMap<>();
        pack.refusedAtLoad.keySet().forEach(id -> once.put(id, 1));
        if (!parsed.equals(once)) {
            defects.add("recipe refusals logged " + parsed + ", not once each for " + once.keySet());
        }
        Map<ResourceLocation, Integer> files = new TreeMap<>();
        pack.unreadable.forEach(id -> files.put(id, 1));
        if (!unreadable.equals(files)) {
            defects.add("unreadable files logged " + unreadable + ", not once each for " + files.keySet());
        }
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("missing reference", 1);
        expected.put("unreadable tag", 1);
        expected.put("rejected pattern catalog", PATTERN_CATALOGS);
        if (!others.equals(expected)) {
            defects.add("tag and pattern refusals logged " + others + ", not " + expected);
        }
        return defects;
    }

    private static Map<String, Integer> classicCounts(MinecraftServer server) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String type : OverLimitDataPack.CLASSIC_TYPES) {
            @SuppressWarnings("unchecked")
            RecipeType<ClassicMachineRecipe> recipeType = (RecipeType<ClassicMachineRecipe>) ForgeRegistries.RECIPE_TYPES
                    .getValue(ModIdentity.id(type));
            counts.put(type, server.getRecipeManager().getAllRecipesFor(recipeType).size());
        }
        return counts;
    }

    private static Optional<HolderSet.Named<Item>> tag(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id));
    }

    private static int longest(List<ReloadLogCapture.Event> logged) {
        return logged.stream().filter(ReloadLogCapture.Event::problem).mapToInt(ReloadLogCapture.Event::longest)
                .max().orElse(0);
    }

    private static String describe(ReloadLogCapture.Event event) {
        String message = event.message().length() <= 200 ? event.message() : event.message().substring(0, 200) + "...";
        return event.level() + " [" + event.logger() + "] " + message;
    }
}
