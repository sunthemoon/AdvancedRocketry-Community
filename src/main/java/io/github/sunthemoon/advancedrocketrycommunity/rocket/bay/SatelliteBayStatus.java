package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import java.util.Locale;
import java.util.Optional;

/**
 * The bay's two menu status slots and its protection reasons. A bay shows its package's receipt (phase and reason),
 * else its runtime rejection (phase NONE), else NONE with DEPLOYMENT_UNAVAILABLE (W27d1). Reason names are the receipt
 * reason grammar; codes are unsigned-short safe for {@code ContainerData}.
 */
public final class SatelliteBayStatus {
    private static final String PREFIX = "screen.advancedrocketrycommunity.rocket_satellite_bay.";

    private SatelliteBayStatus() {
    }

    public enum Phase {
        NONE(0),
        INTENT(1),
        REGISTERED(2),
        REJECTED(3),
        REPAIR(4);

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

        /** A receipt phase name; anything else shows NONE. */
        public static Phase fromName(String name) {
            for (Phase phase : values()) {
                if (phase.name().equals(name)) {
                    return phase;
                }
            }
            return NONE;
        }
    }

    /**
     * Every reason the coordinator records (W27A-DESIGN sections 4, 4.1 and 7; W128's one-page design). A validation
     * refusal keeps its satellite operation code as the receipt reason; codes without their own entry show
     * LAUNCH_REFUSED.
     */
    public enum Reason {
        NONE(0),
        DEPLOYMENT_UNAVAILABLE(1),
        BAY_SOURCE_PROTECTED(2),
        BAY_RECEIPTS_UNAVAILABLE(3),
        REGISTRY_BLOCKED(4),
        TARGET_NOT_ALLOWED(5),
        CATALOG_UNAVAILABLE(6),
        ASSEMBLY_JOURNAL_PENDING(7),
        ASSEMBLY_JOURNAL_UNPROVED(8),
        ROCKET_DEPLOYMENT_LIMIT(9),
        UNSUPPORTED_DATA(10),
        PACKAGE_UNAVAILABLE(11),
        INVALID_PACKAGE(12),
        BAY_OWNER_MISMATCH(13),
        BAY_CONFLICT(14),
        RECEIPT_PENDING(15),
        DEPLOYMENT_CAPACITY(16),
        ROCKET_PENDING_FULL(17),
        RECEIPTS_FULL(18),
        OWNER_RECEIPTS_FULL(19),
        RECEIPT_IDENTITY_CONFLICT(20),
        RECEIPT_WRITE_FAILED(21),
        INTERRUPTED(22),
        REGISTRY_SIGNATURE_CONFLICT(23),
        TRANSFER_AUTHORITY_CONFLICT(24),
        UNAUTHORIZED(25),
        DEPLOYMENT_PENDING(26),
        IDENTITY_CONFLICT(27),
        DEFINITION_NOT_FOUND(28),
        RESEARCH_LOCKED(29),
        CAPACITY_REACHED(30),
        OWNER_LIMIT(31),
        STORAGE_BUDGET(32),
        INVALID_COMPONENTS(33),
        LAUNCH_REFUSED(34);

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

        /** A recorded reason name; an operation code without its own entry shows LAUNCH_REFUSED. */
        public static Reason fromName(String name) {
            for (Reason reason : values()) {
                if (reason.name().equals(name)) {
                    return reason;
                }
            }
            return LAUNCH_REFUSED;
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
