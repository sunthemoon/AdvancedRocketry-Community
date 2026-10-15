package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import java.util.Locale;
import java.util.Optional;

/**
 * The bay's two menu status slots and its protection reasons. Deployment arrives with W27d; until then the phase is
 * NONE and the reason the fixed DEPLOYMENT_UNAVAILABLE. Codes are unsigned-short safe for {@code ContainerData}.
 */
public final class SatelliteBayStatus {
    private static final String PREFIX = "screen.advancedrocketrycommunity.rocket_satellite_bay.";

    private SatelliteBayStatus() {
    }

    public enum Phase {
        NONE(0);

        private final int code;

        Phase(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public String translationKey() {
            return PREFIX + "phase." + name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Phase> fromCode(int code) {
            for (Phase phase : values()) {
                if (phase.code == code) {
                    return Optional.of(phase);
                }
            }
            return Optional.empty();
        }
    }

    public enum Reason {
        DEPLOYMENT_UNAVAILABLE(1);

        private final int code;

        Reason(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public String translationKey() {
            return PREFIX + "reason." + name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Reason> fromCode(int code) {
            for (Reason reason : values()) {
                if (reason.code == code) {
                    return Optional.of(reason);
                }
            }
            return Optional.empty();
        }
    }

    /** Why a bay refuses its menu, insertion, extraction, player break, explosions and drops. */
    public enum Guard {
        NONE,
        /** A root with unknown fields, both entries, wrong types or outside its bound: kept unchanged. */
        QUARANTINED,
        /** A root of a newer schema. */
        UNSUPPORTED_ROOT,
        /** A package whose identity is of a newer schema. */
        UNSUPPORTED_DATA,
        /** A package whose item ID is not registered. */
        PACKAGE_UNAVAILABLE,
        /** A package that is not a valid satellite package or would not load unchanged. */
        INVALID_PACKAGE,
        /** An assembly or disassembly transaction claims the position. */
        CLAIMED;

        public String translationKey() {
            return "message.advancedrocketrycommunity.rocket_satellite_bay.guard." + name().toLowerCase(Locale.ROOT);
        }
    }
}
