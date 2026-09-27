package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkyRenderStateTest {
    @Test void nonDefaultStateIsRestoredOnSuccessAndOnUploadOrDrawFailure() {
        for (int failureAt : new int[] {-1, 0, 1}) {
            var original = new SkyRenderState.State(true, false, false, 1, 2, 3, 4, 9, 10, 17, 23, 31);
            var access = new Access(original);
            try {
                try (var ignored = SkyRenderState.capture(access)) {
                    if (failureAt == 0) { throw new IllegalStateException("fixture upload failure"); }
                    access.state = new SkyRenderState.State(false, true, true, 5, 6, 7, 8, 11, 12, 41, 43, 47);
                    if (failureAt == 1) { throw new IllegalStateException("fixture draw failure"); }
                }
                assertEquals(-1, failureAt);
            } catch (IllegalStateException expected) {
                assertNotEquals(-1, failureAt);
            }
            assertEquals(original, access.state);
            assertEquals(1, access.captures);
            assertEquals(1, access.restores);
        }
    }

    private static final class Access implements SkyRenderState.Access {
        private SkyRenderState.State state;
        private int captures;
        private int restores;
        private Access(SkyRenderState.State state) { this.state = state; }
        @Override public SkyRenderState.State capture() { captures++; return state; }
        @Override public void restore(SkyRenderState.State previous) { restores++; state = previous; }
    }
}
