package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * W44 section 6.1: every gated release-test seam of the world-first cuts. With the cut property absent, or
 * {@code advancedrocketrycommunity.releaseTestHooks} false, every seam returns its production value. With both set,
 * each seam first requires the empty dedicated release-test server ({@link EscrowTestEndpoints#requireEnabled}), so a
 * connected player makes it fail closed. An unknown ID fails at startup. V and X rows use no seam.
 */
public final class FirstEventCutSeams {
    public static final String PROPERTY = "arce.releaseTest.firstEventCut";

    private FirstEventCutSeams() {
    }

    /** The headless (H) rows of W44 sections 6.2 to 6.4. */
    public enum Cut {
        FM1, FM2A, FM2B, FM3, FM4A, FM4B, FM5, FM6A, FM6B, FM7, FM8A, FM8B, FM9, FM12A, FM12B,
        FW1, FW2, FW3, FW4A, FW4B, FW5, FW6A, FW6B, FW7, FW8A, FW8B, FW9, FW11,
        FP2, FP3, FP4, FP5, FP6, FP7A, FP7B, FP8, FP9, FP10, FP11;

        /** The design's spelling, for example {@code FM2a}. */
        public String id() {
            String name = name();
            char last = name.charAt(name.length() - 1);
            return Character.isLetter(last) && name.length() > 3 && Character.isDigit(name.charAt(name.length() - 2))
                    ? name.substring(0, name.length() - 1) + Character.toLowerCase(last) : name;
        }

        static Optional<Cut> byId(String id) {
            return Arrays.stream(values()).filter(cut -> cut.id().equals(id)).findFirst();
        }

        /** The managed root whose runtime load the load-fault seam fails, if any. */
        Optional<ManagedSavedDataType> loadFault() {
            return switch (this) {
                case FP7A, FP7B, FP9 -> Optional.of(ManagedSavedDataType.CLASSIC_PROGRESSION);
                case FP10 -> Optional.of(ManagedSavedDataType.ROCKET_TRANSFERS);
                case FP11 -> Optional.of(ManagedSavedDataType.STATIONS);
                default -> Optional.empty();
            };
        }
    }

    /**
     * The configured cut; empty in production. A set property with hooks disabled is ignored, as every release-test
     * property is; a set property with hooks enabled and an unknown ID throws.
     */
    public static Optional<Cut> configured() {
        String id = System.getProperty(PROPERTY, "");
        if (id.isEmpty() || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) {
            return Optional.empty();
        }
        return Optional.of(Cut.byId(id).orElseThrow(() -> new IllegalStateException(
                "Unknown first-event release-test cut " + id)));
    }

    /** Startup validation of the property; an unknown ID refuses the session. */
    public static void validateAtStartup(MinecraftServer server) {
        configured().ifPresent(cut -> {
            EscrowTestEndpoints.requireEnabled(server);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_cut id={} armed", cut.id());
        });
    }

    /** The active cut after the gate; empty in production. */
    static Optional<Cut> active(MinecraftServer server) {
        Optional<Cut> cut = configured();
        cut.ifPresent(ignored -> EscrowTestEndpoints.requireEnabled(server));
        return cut;
    }

    /**
     * Load fault (FP7a, FP7b, FP9, FP10, FP11): throws inside vanilla's read, which logs {@code Error loading saved
     * data} and returns null exactly as for an IO failure; the supplier then builds the UNREADABLE root. The static
     * {@code load} used by the pre-start validator is unchanged, so validation of the same bytes still passes.
     */
    public static void beforeLoad(MinecraftServer server, ManagedSavedDataType type) {
        Optional<Cut> cut = configured();
        if (cut.isEmpty() || cut.orElseThrow().loadFault().filter(type::equals).isEmpty()) {
            return;
        }
        EscrowTestEndpoints.requireEnabled(server);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_cut id={} boundary=load_fault type={}",
                cut.orElseThrow().id(), type.dataName());
        throw new IllegalStateException("Release-test load fault for " + type.dataName());
    }
}
