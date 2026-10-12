package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.EquipmentSummary;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JetpackSessionTest {
    private static final EquipmentSummary JETPACK = new EquipmentSummary(Set.of(ModuleKind.JETPACK));
    private static final String OVERWORLD = "minecraft:overworld";

    private static JetpackSession enabled() {
        JetpackSession session = new JetpackSession();
        session.enable(JETPACK, OVERWORLD);
        return session;
    }

    @Test void offByDefaultAndADisabledJetpackIgnoresHeartbeats() {
        JetpackSession session = new JetpackSession();
        assertFalse(session.enabled());
        assertEquals(JetpackMode.NORMAL, session.mode());
        session.thrust(true, 100);
        assertFalse(session.active(100));
    }

    @Test void aHeartbeatKeepsTheIntentForFiveTicksThenItExpires() {
        JetpackSession session = enabled();
        session.thrust(true, 100);
        for (long tick = 100; tick <= 104; tick++) { assertTrue(session.active(tick), "tick " + tick); }
        assertFalse(session.active(105));
        assertFalse(session.active(99), "a tick before the heartbeat");
        session.expire(105);
        assertFalse(session.active(104), "expiry ends the intent");
        session.thrust(true, 110);
        session.thrust(true, 113);
        assertTrue(session.active(117));
        assertFalse(session.active(118));
    }

    @Test void releaseDisableAndLevelChangeEndTheIntentAtOnce() {
        JetpackSession session = enabled();
        session.thrust(true, 10);
        session.thrust(false, 11);
        assertFalse(session.active(11));
        session.thrust(true, 20);
        session.disable();
        assertFalse(session.active(20));
        session.enable(JETPACK, OVERWORLD);
        session.thrust(true, 30);
        assertTrue(session.observe(JETPACK, "advancedrocketrycommunity:space"));
        assertFalse(session.active(30));
    }

    @Test void onlyOneCorrectionPerTick() {
        JetpackSession session = enabled();
        assertTrue(session.claim(5));
        assertFalse(session.claim(5));
        assertTrue(session.claim(6));
    }

    @Test void sameKindHeadAndChestReplacementsEndIntentButSameStackDebitsDoNot() {
        JetpackSession session = enabled();
        Object head = new Object();
        Object chest = new Object();
        assertTrue(session.observeEquipment(head, chest));
        session.cycle(true);
        session.thrust(true, 10);
        session.reference(64);
        assertTrue(session.claim(10));
        assertFalse(session.observeEquipment(head, chest), "Same stack with updated gas is not a replacement");
        assertTrue(session.active(10));
        assertEquals(64, session.reference(60));
        head = new Object();
        assertTrue(session.observeEquipment(head, chest));
        assertFalse(session.active(10));
        assertFalse(session.hasReference());
        assertFalse(session.claim(10), "A replacement must not permit two corrections in one tick");
        session.thrust(true, 11);
        assertEquals(60, session.reference(60));
        assertTrue(session.observeEquipment(head, new Object()));
        assertFalse(session.active(11));
        assertFalse(session.hasReference());
        assertEquals(JetpackMode.HOVER, session.mode(), "Replacement preserves the chosen mode, not stale intent");
    }

    @Test void hoverNeedsTheUpgradeAndTheReferenceResetsOnEveryChange() {
        JetpackSession session = enabled();
        assertFalse(session.cycle(false));
        assertEquals(JetpackMode.NORMAL, session.mode());
        assertTrue(session.cycle(true));
        assertEquals(JetpackMode.HOVER, session.mode());
        session.thrust(true, 1);
        assertEquals(64.0, session.reference(64.0));
        assertEquals(64.0, session.reference(60.0), "fixed until a reset");
        assertFalse(session.observe(JETPACK, OVERWORLD), "unchanged equipment keeps the reference");
        assertTrue(session.hasReference());
        assertTrue(session.observe(new EquipmentSummary(Set.of(ModuleKind.JETPACK, ModuleKind.HOVER)), OVERWORLD));
        assertFalse(session.hasReference(), "equipment change");
        session.reference(70.0);
        session.expire(10);
        assertFalse(session.hasReference(), "expired intent");
        session.reference(70.0);
        assertTrue(session.cycle(false), "HOVER back to NORMAL is always allowed");
        assertFalse(session.hasReference(), "mode change");
        assertEquals(JetpackMode.NORMAL, session.mode());
    }
}
