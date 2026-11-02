package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayPassengerExitLandingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final List<BayPassengerExitLanding.Signature> REQUIRED = List.of(
            new BayPassengerExitLanding.Signature(String.class, "cause", "(FF)Z"),
            new BayPassengerExitLanding.Signature(Integer.class, "fall", "()V"),
            new BayPassengerExitLanding.Signature(Double.class, "check", "()V"),
            new BayPassengerExitLanding.Signature(Float.class, "player", "(DDDZ)V"));
    @Test void exactOrderedClassesNamesAndDescriptorsAreRequired() {
        assertTrue(BayPassengerExitLanding.matches(REQUIRED, REQUIRED));
        for (int i = 0; i < 4; i++) {
            var missing = new ArrayList<>(REQUIRED); missing.remove(i);
            assertFalse(BayPassengerExitLanding.matches(missing, REQUIRED));
            var descriptor = new ArrayList<>(REQUIRED); var row = descriptor.get(i);
            descriptor.set(i, new BayPassengerExitLanding.Signature(row.owner(), row.name(), "()Z"));
            assertFalse(BayPassengerExitLanding.matches(descriptor, REQUIRED));
        }
    }
    @Test void reentrantAmbiguousAndTruncatedStacksRefuse() {
        var duplicate = new ArrayList<>(REQUIRED); duplicate.add(REQUIRED.get(0));
        assertFalse(BayPassengerExitLanding.matches(duplicate, REQUIRED));
        var reversed = new ArrayList<>(REQUIRED); java.util.Collections.reverse(reversed);
        assertFalse(BayPassengerExitLanding.matches(reversed, REQUIRED));
        var frames = new ArrayList<>(REQUIRED);
        var unrelated = new BayPassengerExitLanding.Signature(Object.class, "plugin", "()V");
        while (frames.size() < 64) { frames.add(unrelated); }
        assertTrue(BayPassengerExitLanding.matches(frames, REQUIRED));
        frames.add(unrelated); assertFalse(BayPassengerExitLanding.matches(frames, REQUIRED));
    }
}
