package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * W179: how the {@link MenuAuthorityMatrix} builds one menu type's holder. {@code checks} are the cells this probe
 * runs; {@code acts} says that the fixture implements {@link MenuAuthorityFixture#act}, which {@code RATE} needs and
 * which {@code FOREIGN_OWNER} uses when another player may open the menu. {@code template} is the mod's GameTest
 * structure the test runs in.
 */
public record MenuAuthorityProbe(ResourceLocation menu, Set<MenuAuthorityMatrix.Check> checks, boolean acts,
                                 int timeoutTicks, String template,
                                 Function<GameTestHelper, MenuAuthorityFixture> fixture) {
    /** Sixteen blocks of air on every side, so neighbouring holders of the batch stay apart. */
    public static final String DEFAULT_TEMPLATE = "rocket_test";

    public MenuAuthorityProbe {
        Objects.requireNonNull(menu, "menu");
        checks = Set.copyOf(checks);
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(fixture, "fixture");
        if (checks.contains(MenuAuthorityMatrix.Check.RATE) && !acts) {
            throw new IllegalArgumentException(menu + " runs RATE without a menu action");
        }
        if (timeoutTicks < 20) {
            throw new IllegalArgumentException(menu + " needs a timeout of at least 20 ticks");
        }
    }

    /** A probe of the mod's menu {@code path} for {@code checks}, in {@link #DEFAULT_TEMPLATE}. */
    public static MenuAuthorityProbe of(String path, boolean acts, int timeoutTicks,
                                        Function<GameTestHelper, MenuAuthorityFixture> fixture,
                                        MenuAuthorityMatrix.Check... checks) {
        Set<MenuAuthorityMatrix.Check> set = EnumSet.noneOf(MenuAuthorityMatrix.Check.class);
        set.addAll(List.of(checks));
        return new MenuAuthorityProbe(ModIdentity.id(path), set, acts, timeoutTicks, DEFAULT_TEMPLATE, fixture);
    }

    /** This probe in another of the mod's GameTest structures. */
    public MenuAuthorityProbe in(String structure) {
        return new MenuAuthorityProbe(menu, checks, acts, timeoutTicks, structure, fixture);
    }
}
