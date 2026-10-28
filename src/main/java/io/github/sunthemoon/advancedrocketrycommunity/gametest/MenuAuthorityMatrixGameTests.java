package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Exemption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * W179 (VDOC 12.1): the menu-authority matrix (see {@link MenuAuthorityMatrix}). One GameTest per probed menu type,
 * generated from {@link MenuAuthorityProbes#all()}, runs that menu's cells; one more GameTest fails when a registered
 * menu type has a check that is neither probed nor exempt, or when a probe or an exemption names no registered menu.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MenuAuthorityMatrixGameTests {
    static final String BATCH = "menu_authority";
    /** Ticks between two probes' fixture builds. */
    private static final int STAGGER_TICKS = 3;

    private MenuAuthorityMatrixGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void everyRegisteredMenuTypeIsProbedOrExemptForEveryCheck(GameTestHelper helper) {
        List<String> defects = new ArrayList<>();
        List<Exemption> exemptions = MenuAuthorityMatrix.exemptions(defects);
        Map<ResourceLocation, MenuType<?>> registered = MenuAuthorityMatrix.registered();
        if (registered.isEmpty()) {
            defects.add("no menu type of the mod is registered");
        }
        Map<ResourceLocation, MenuAuthorityProbe> probes = new TreeMap<>();
        for (MenuAuthorityProbe probe : MenuAuthorityProbes.all()) {
            if (probes.put(probe.menu(), probe) != null) {
                defects.add("two probes for " + probe.menu());
            }
            if (!registered.containsKey(probe.menu())) {
                defects.add("a probe names the unregistered menu " + probe.menu());
            }
        }
        Set<String> keys = new HashSet<>();
        for (Exemption exemption : exemptions) {
            if (!registered.containsKey(exemption.menu())) {
                defects.add(MenuAuthorityMatrix.EXEMPTIONS + " line " + exemption.line() + " names the unregistered menu "
                        + exemption.menu());
            }
            if (!keys.add(exemption.menu() + " " + exemption.check().map(Enum::name).orElse("ALL"))) {
                defects.add(MenuAuthorityMatrix.EXEMPTIONS + " line " + exemption.line() + " repeats an entry");
            }
        }
        int gaps = 0;
        for (ResourceLocation menu : registered.keySet()) {
            MenuAuthorityProbe probe = probes.get(menu);
            for (Check check : Check.values()) {
                List<Exemption> matching = exemptions.stream()
                        .filter(exemption -> exemption.menu().equals(menu) && exemption.covers(check)).toList();
                boolean probed = probe != null && probe.checks().contains(check);
                if (matching.size() > 1) {
                    defects.add(menu + " " + check + " is exempt more than once");
                } else if (matching.isEmpty() && !probed) {
                    defects.add(menu + " " + check + " has no probe and no exemption");
                } else if (!matching.isEmpty() && probed && !matching.get(0).gap()) {
                    defects.add(menu + " " + check + " is both probed and exempt (" + matching.get(0).kind() + ")");
                } else if (!matching.isEmpty() && !probed && matching.get(0).gap()) {
                    defects.add(menu + " " + check + " is a gap that no probe shows");
                } else if (!matching.isEmpty() && matching.get(0).gap()) {
                    gaps++;
                }
            }
        }
        helper.assertTrue(defects.isEmpty(), "Menu-authority coverage defects: " + defects);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W179_MENU_MATRIX menus={} probes={} exemptions={} gaps={}",
                registered.size(), probes.size(), exemptions.size(), gaps);
        helper.succeed();
    }

    /** One required GameTest per probe, so the matrix cannot claim a cell that no test runs. */
    @GameTestGenerator
    public static Collection<TestFunction> everyProbedMenuRefusesItsCells() {
        List<TestFunction> tests = new ArrayList<>();
        List<MenuAuthorityProbe> probes = MenuAuthorityProbes.all();
        for (int index = 0; index < probes.size(); index++) {
            MenuAuthorityProbe probe = probes.get(index);
            int stagger = 1 + STAGGER_TICKS * index;
            tests.add(new TestFunction(BATCH, "menuauthoritymatrixgametests." + probe.menu().getPath(),
                    ModIdentity.MOD_ID + ":" + probe.template(), stagger + probe.timeoutTicks(), 0L, true,
                    helper -> run(helper, probe, stagger)));
        }
        return tests;
    }

    /**
     * Builds the holder {@code stagger} ticks after the batch starts, so the batch's fixtures (stations, multiblocks
     * and a rocket scan among them) do not all load in one tick, then runs the cells once it is ready.
     */
    private static void run(GameTestHelper helper, MenuAuthorityProbe probe, int stagger) {
        MinecraftServer server = helper.getLevel().getServer();
        MenuType<?> type = ForgeRegistries.MENU_TYPES.getValue(probe.menu());
        MenuAuthorityFixture[] fixture = new MenuAuthorityFixture[1];
        long[] built = new long[1];
        List<ServerPlayer> joined = new ArrayList<>();
        boolean[] closed = new boolean[1];
        Runnable cleanup = () -> {
            if (!closed[0]) {
                closed[0] = true;
                joined.forEach(player -> server.getPlayerList().remove(player));
                if (fixture[0] != null) {
                    fixture[0].close();
                }
            }
        };
        onEnd(helper, cleanup);
        helper.startSequence()
                .thenIdle(stagger)
                .thenExecute(() -> guarded(probe, () -> {
                    long started = System.nanoTime();
                    fixture[0] = probe.fixture().apply(helper);
                    built[0] = (System.nanoTime() - started) / 1_000_000L;
                }))
                .thenWaitUntil(() -> helper.assertTrue(fixture[0].ready(), "The holder is not ready: "
                        + fixture[0].describe()))
                .thenExecute(() -> {
                    try {
                        guarded(probe, () -> check(helper, probe, fixture[0], type, joined, built[0]));
                    } finally {
                        cleanup.run();
                    }
                })
                .thenSucceed();
    }

    /** An exception other than an assertion, thrown in a sequence step, would stop the whole GameTest server. */
    private static void guarded(MenuAuthorityProbe probe, Runnable step) {
        try {
            step.run();
        } catch (GameTestAssertException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            AdvancedRocketryCommunity.LOGGER.error("ARCE_W179_MENU_PROBE_FAILED menu={}", probe.menu(), failure);
            throw new GameTestAssertException(probe.menu() + " probe failed: " + failure);
        }
    }

    private static void check(GameTestHelper helper, MenuAuthorityProbe probe, MenuAuthorityFixture fixture,
                              MenuType<?> type, List<ServerPlayer> joined, long built) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> defects = new ArrayList<>();
        List<Exemption> exemptions = MenuAuthorityMatrix.exemptions(defects);
        String tag = Integer.toHexString(probe.menu().getPath().hashCode() & 0xFFFFF);
        ServerPlayer owner = MenuAuthorityMatrix.join(server, fixture.owner(), "w179o" + tag, fixture.level(),
                fixture.stand());
        joined.add(owner);
        ServerPlayer stranger = MenuAuthorityMatrix.join(server, UUID.randomUUID(), "w179s" + tag, fixture.level(),
                fixture.stand());
        joined.add(stranger);
        fixture.joined(owner);
        List<String> notes = new ArrayList<>();
        Map<Check, Boolean> refused = MenuAuthorityMatrix.run(probe, fixture, type, owner, stranger, notes, defects);
        for (Check check : probe.checks()) {
            boolean gap = exemptions.stream().anyMatch(exemption -> exemption.menu().equals(probe.menu())
                    && exemption.covers(check) && exemption.gap());
            Boolean result = refused.get(check);
            if (result == null) {
                continue;
            }
            if (gap && result) {
                defects.add(check + " is refused now; remove its gap exemption");
            } else if (!gap && !result) {
                defects.add(check + " was not refused");
            }
        }
        helper.assertTrue(defects.isEmpty(), probe.menu() + " defects " + defects + "; notes " + notes);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W179_MENU_CELLS menu={} build_ms={} refused={} notes={}",
                probe.menu(), built, refused, notes);
    }

    /** Runs {@code cleanup} when the test ends either way, also on a timeout before the sequence reaches it. */
    private static void onEnd(GameTestHelper helper, Runnable cleanup) {
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                @Override
                public void testStructureLoaded(GameTestInfo info) {
                }

                @Override
                public void testPassed(GameTestInfo info) {
                    cleanup.run();
                }

                @Override
                public void testFailed(GameTestInfo info) {
                    cleanup.run();
                }
            });
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not attach the matrix fixture cleanup", failure);
        }
    }
}
