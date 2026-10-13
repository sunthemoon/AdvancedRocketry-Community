package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.Objects;
import java.util.function.LongSupplier;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * One tick callback, registered during test setup, with a mutable continuation and its due tick.
 * Advancing a step changes only these fields; it never registers work in vanilla's iterated schedule map.
 * Each driver owns one sequential chain. Independent test-start timers may still be registered at test start.
 */
public final class GameTestTickDriver {
    private final LongSupplier tick;
    private Runnable next;
    private long due;

    public GameTestTickDriver(GameTestHelper helper) {
        this(helper::getTick);
        helper.onEachTick(this::tick);
    }

    GameTestTickDriver(LongSupplier tick) {
        this.tick = tick;
    }

    public void after(long delay, Runnable step) {
        at(tick.getAsLong() + delay, step);
    }

    public void at(long dueTick, Runnable step) {
        if (next != null) {
            throw new IllegalStateException("A GameTest continuation is already pending");
        }
        next = Objects.requireNonNull(step);
        due = dueTick;
    }

    void tick() {
        if (next != null && tick.getAsLong() >= due) {
            Runnable step = next;
            next = null;
            step.run();
        }
    }
}
