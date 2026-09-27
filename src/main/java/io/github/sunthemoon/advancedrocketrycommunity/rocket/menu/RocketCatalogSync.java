package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

/** Menu-local retry state. A denied full-catalog send remains pending even if quotes do not change. */
public final class RocketCatalogSync {
    private long sentGeneration = -1;
    private long lastAttempt = -RocketNavigation.REFRESH_TICKS;
    private boolean pending = true;

    public void request() { pending = true; }

    public boolean attempt(long now, long generation) {
        if (generation <= 0 || !pending && sentGeneration == generation) { return false; }
        if (now >= lastAttempt && now - lastAttempt < RocketNavigation.REFRESH_TICKS) { return false; }
        lastAttempt = now;
        return true;
    }

    public void delivered(long generation) {
        sentGeneration = generation;
        pending = false;
    }
}
