package io.github.sunthemoon.advancedrocketrycommunity.client.exoplanet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FlashCooldownTest {
    @Test void oneWorldCannotFlashMoreOftenThanEveryHundredTicks() {
        var cooldown = new FlashCooldown();
        Object world = new Object();
        assertTrue(cooldown.tryAcquire(world, 0));
        assertFalse(cooldown.tryAcquire(world, 0));
        assertFalse(cooldown.tryAcquire(world, 99));
        assertTrue(cooldown.tryAcquire(world, 100));
        assertFalse(cooldown.tryAcquire(world, 101));
    }

    @Test void anotherWorldDoesNotInheritAnOldWorldsClockOrCooldown() {
        var cooldown = new FlashCooldown();
        Object oldWorld = new Object();
        Object newWorld = new Object();
        assertTrue(cooldown.tryAcquire(oldWorld, 1_000_000));
        assertTrue(cooldown.tryAcquire(newWorld, 0));
        assertFalse(cooldown.tryAcquire(newWorld, 99));
        assertTrue(cooldown.tryAcquire(newWorld, 100));
        assertTrue(cooldown.tryAcquire(new Object(), 100));
    }

    @Test void rewoundOrExtremeClocksCannotPermanentlySuppressFlashes() {
        var cooldown = new FlashCooldown();
        Object world = new Object();
        assertTrue(cooldown.tryAcquire(world, 1_000_000));
        assertTrue(cooldown.tryAcquire(world, 0));
        assertFalse(cooldown.tryAcquire(world, 99));
        assertTrue(cooldown.tryAcquire(world, Long.MAX_VALUE - 50));
        assertFalse(cooldown.tryAcquire(world, Long.MAX_VALUE));
        assertTrue(cooldown.tryAcquire(world, Long.MIN_VALUE));
        assertTrue(cooldown.tryAcquire(world, Long.MAX_VALUE));
        assertThrows(NullPointerException.class, () -> cooldown.tryAcquire(null, 0));
    }
}
