package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

/** Connection authorization and transient visor effects, independent of rendering and client preferences. */
public final class SuitVisorState {
    private boolean enabled;
    private boolean antiFog;
    private boolean earthbright;

    public boolean enabled() { return enabled; }
    public boolean antiFog() { return antiFog; }
    public boolean earthbright() { return earthbright; }

    public void authorize(boolean allowed) {
        enabled = allowed;
        if (!allowed) { clear(); }
    }

    public void observe(boolean antiFogInstalled, boolean earthbrightInstalled) {
        antiFog = enabled && antiFogInstalled;
        earthbright = enabled && earthbrightInstalled;
    }

    /** Reload/Level changes clear effects, not the server's authorization for this connection. */
    public void clear() {
        antiFog = false;
        earthbright = false;
    }
}
