package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.EquipmentSummary;
import java.util.Objects;

/**
 * One online player's transient jetpack state (ADR-066 section 5.2). It is never persisted, so a jetpack is off at
 * login. A held-thrust heartbeat at tick {@code h} keeps the intent active on ticks {@code h} to {@code h + 4}.
 */
public final class JetpackSession {
    public static final int INTENT_TICKS = 5;
    private static final long NONE = Long.MIN_VALUE;
    private boolean enabled;
    private JetpackMode mode = JetpackMode.NORMAL;
    private long heartbeat = NONE;
    private long corrected = NONE;
    private double reference = Double.NaN;
    private EquipmentSummary summary = EquipmentSummary.EMPTY;
    private String level = "";
    private Object head;
    private Object chest;

    public boolean enabled() { return enabled; }
    public JetpackMode mode() { return mode; }
    public EquipmentSummary summary() { return summary; }
    public String level() { return level; }
    public boolean hasReference() { return !Double.isNaN(reference); }

    public void enable(EquipmentSummary worn, String levelId) {
        enabled = true;
        observe(worn, levelId);
        end();
    }

    public void disable() {
        enabled = false;
        end();
    }

    /** NORMAL to HOVER needs the hover upgrade; HOVER to NORMAL is always allowed. */
    public boolean cycle(boolean hoverInstalled) {
        if (mode == JetpackMode.NORMAL && !hoverInstalled) { return false; }
        mode = mode == JetpackMode.NORMAL ? JetpackMode.HOVER : JetpackMode.NORMAL;
        reference = Double.NaN;
        return true;
    }

    /** A disabled jetpack ignores heartbeats; a released key ends the intent at once. */
    public void thrust(boolean held, long now) {
        if (!enabled) { return; }
        if (held) { heartbeat = now; } else { end(); }
    }

    public boolean active(long now) {
        return enabled && heartbeat != NONE && now >= heartbeat && now - heartbeat < INTENT_TICKS;
    }

    /** Ends an expired or clock-inconsistent intent, which also drops the HOVER reference. */
    public void expire(long now) {
        if (heartbeat != NONE && !active(now)) { end(); }
    }

    /** At most one correction per player per tick. */
    public boolean claim(long now) {
        if (corrected == now) { return false; }
        corrected = now;
        return true;
    }

    /** Records the worn-module summary and Level; any change drops the HOVER reference. */
    public boolean observe(EquipmentSummary worn, String levelId) {
        Objects.requireNonNull(worn, "worn");
        Objects.requireNonNull(levelId, "levelId");
        boolean changed = !worn.equals(summary) || !levelId.equals(level);
        if (!levelId.equals(level)) { heartbeat = NONE; }
        if (changed) { reference = Double.NaN; }
        summary = worn;
        level = levelId;
        return changed;
    }

    /** Opaque worn-stack identities: replacement ends the intent, but an in-place gas debit does not. */
    public boolean observeEquipment(Object wornHead, Object wornChest) {
        Objects.requireNonNull(wornHead, "wornHead");
        Objects.requireNonNull(wornChest, "wornChest");
        boolean changed = head != wornHead || chest != wornChest;
        if (changed) { end(); }
        head = wornHead;
        chest = wornChest;
        return changed;
    }

    /** The transient HOVER reference, fixed at the first HOVER correction after any reset. */
    public double reference(double currentY) {
        if (Double.isNaN(reference)) { reference = currentY; }
        return reference;
    }

    private void end() {
        heartbeat = NONE;
        reference = Double.NaN;
    }
}
