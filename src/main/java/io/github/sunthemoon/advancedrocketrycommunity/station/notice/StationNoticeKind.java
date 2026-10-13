package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

/**
 * The station warp notices of ADR-065 section 5.3, each a stable translatable key in the v1.8 language namespace with
 * a fixed list of argument roles. The first argument always names the station; an outcome's second argument is the
 * existing station code key. Only commit and abort outcomes, and the brief outcome that always fits the envelope, are
 * stored for offline members.
 */
public enum StationNoticeKind {
    WARP_STARTED("warp_started", false, Role.TEXT, Role.CODE, Role.BODY, Role.BODY, Role.NUMBER, Role.COST_CLASS,
            Role.NUMBER),
    WARP_COUNTDOWN("warp_countdown", false, Role.TEXT, Role.BODY, Role.NUMBER),
    WARP_COMMITTED("warp_committed", true, Role.TEXT, Role.CODE, Role.BODY, Role.BODY, Role.COST_CLASS, Role.NUMBER,
            Role.NUMBER),
    WARP_ABORTED("warp_aborted", true, Role.TEXT, Role.CODE, Role.BODY),
    WARP_OUTCOME("warp_outcome", true, Role.TEXT, Role.CODE);

    public static final String PREFIX = "station.advancedrocketrycommunity.notice.";
    /** The login wrapper around one stored outcome. */
    public static final String WHILE_AWAY = PREFIX + "while_away";
    /** The cost classes of ADR-044 section 2, as {@code WarpCostClass} names in lowercase. */
    public static final List<String> COST_CLASSES = List.of("in_system", "interstellar");
    public static final int MAX_ARGUMENTS = Arrays.stream(values()).mapToInt(kind -> kind.roles.size()).max()
            .orElseThrow();

    private final String id;
    private final boolean stored;
    private final List<Role> roles;

    StationNoticeKind(String id, boolean stored, Role... roles) {
        this.id = id;
        this.stored = stored;
        this.roles = List.of(roles);
    }

    public String key() {
        return PREFIX + id;
    }

    /** Whether this outcome is kept as a station's offline summary. */
    public boolean stored() {
        return stored;
    }

    public List<Role> roles() {
        return roles;
    }

    /** The code this kind may carry: its own success code, or for an abort a cancel or any refusal. */
    public boolean allows(StationManagementCode code) {
        boolean abort = code == StationManagementCode.WARP_CANCELLED || !code.success();
        return switch (this) {
            case WARP_STARTED -> code == StationManagementCode.WARP_STARTED;
            case WARP_COUNTDOWN -> false;
            case WARP_COMMITTED -> code == StationManagementCode.WARP_COMMITTED;
            case WARP_ABORTED -> abort;
            case WARP_OUTCOME -> abort || code == StationManagementCode.WARP_COMMITTED;
        };
    }

    public static Optional<StationNoticeKind> byKey(String key) {
        return Arrays.stream(values()).filter(kind -> kind.key().equals(key)).findFirst();
    }

    /** The translatable key of a cost class argument. */
    public static String costClassKey(String costClass) {
        return "station.advancedrocketrycommunity.warp_class." + costClass;
    }

    /** How one argument is checked and shown. */
    public enum Role {
        /** The station name, under the station record's own rule; shown as literal text. */
        TEXT,
        /** A canonical body ResourceLocation; shown as literal text. */
        BODY,
        /** A canonical non-negative int; shown as literal text. */
        NUMBER,
        /** A {@link StationManagementCode} name; shown through its code key. */
        CODE,
        /** One of {@link #COST_CLASSES}; shown through {@link #costClassKey}. */
        COST_CLASS;

        private static final Set<String> CODES = Arrays.stream(StationManagementCode.values()).map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());

        boolean accepts(StationNoticeKind kind, String value) {
            if (value == null) {
                return false;
            }
            return switch (this) {
                case TEXT -> validName(value);
                case BODY -> {
                    ResourceLocation body = ResourceLocation.tryParse(value);
                    yield body != null && body.toString().equals(value);
                }
                case NUMBER -> value.matches("0|[1-9][0-9]{0,9}") && Long.parseLong(value) <= Integer.MAX_VALUE;
                case CODE -> CODES.contains(value) && kind.allows(StationManagementCode.valueOf(value));
                case COST_CLASS -> COST_CLASSES.contains(value);
            };
        }

        /** The station record's name rule: stripped, 1 to 48 characters, no control character. */
        private static boolean validName(String value) {
            return !value.isEmpty() && value.equals(value.strip()) && value.length() <= StationLimits.MAX_NAME_LENGTH
                    && value.chars().noneMatch(Character::isISOControl);
        }
    }
}
