package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.EnumSet;
import java.util.Set;

/**
 * The ADR-046 UI-03 expected-outcome table (revision 1 of the table), one cell per action and actor.
 * Tokens are {@code StationManagementCode} names or the observations below; {@code N/A} cells are not
 * reachable by that actor (for example, building needs a player entity) and are not executed.
 */
final class StationPermissionMatrix {
    static final int TABLE_REVISION = 1;
    static final String NOT_PLAYER = "NOT_PLAYER";
    static final String UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    static final String REACHED = "REACHED";
    static final String ALLOWED = "ALLOWED";
    static final String REJECTED = "REJECTED";
    static final String NOT_APPLICABLE = "N/A";

    enum Actor {
        OWNER("connected mock player, the station's owner, standing on its platform"),
        MEMBER("connected mock player, a member"),
        INVITEE("connected mock player with a pending invitation"),
        OUTSIDER("connected mock player with no relation to the station"),
        OPERATOR("connected mock player with a level-4 ops entry, standing on the platform"),
        OPERATOR_OUTSIDE("connected mock player with a level-4 ops entry, standing in Space outside every station"),
        OTHER_STATION_OWNER("connected mock player owning a second station, standing in this one"),
        REMOVED_MEMBER("connected mock player added as a member and then removed"),
        PREVIOUS_OWNER("connected mock player who created the station and transferred it to the owner"),
        DEOPPED_OPERATOR("connected mock player whose level-4 ops entry was removed"),
        CONSOLE("server command source"),
        EXECUTE_AS_OWNER("/execute as <owner> at <owner> run ... from the console"),
        SILENT_OWNER("the owner's own source with suppressed output (as /function or a reward)"),
        FAKE_PLAYER_OWNER("FakePlayer with the owner's UUID on the platform"),
        STALE_OWNER("the owner's previous player object after it logged out");

        final String construction;

        Actor(String construction) {
            this.construction = construction;
        }
    }

    enum Kind { LOCAL, STATUS, ENVIRONMENT, TEAM, ANSWER, LIST, ADMIN, BUILD, VISIT }

    /** Actions in execution order: confirmations run before the requests that would create one. */
    enum Action {
        EXPAND_CONFIRM(Kind.LOCAL, "NO_CONFIRMATION", "no confirmation pending"),
        EXPAND(Kind.LOCAL, "ISSUED", "station not expanded"),
        GRAVITY(Kind.LOCAL, "GRAVITY_UNCHANGED", "the current gravity is requested"),
        WARP_CONFIRM(Kind.LOCAL, "WARP_NO_CONFIRMATION", "no warp confirmation pending"),
        WARP_CANCEL(Kind.LOCAL, "WARP_NO_COUNTDOWN", "no countdown running"),
        WARP_REQUEST(Kind.LOCAL, "WARP_INSUFFICIENT_ENERGY", "looking at a warp core; balance 0"),
        WARP_STATUS(Kind.STATUS, "WARP_STATUS", "none"),
        ENVIRONMENT(Kind.ENVIRONMENT, "IDENTIFIED", "none"),
        INVITE(Kind.TEAM, ALLOWED, "target online and not invited; reset after an allowed cell"),
        REMOVE(Kind.TEAM, ALLOWED, "target an online member; reset after an allowed cell"),
        REMOVE_UUID(Kind.TEAM, ALLOWED, "target an offline member by UUID; reset after an allowed cell"),
        ACCEPT(Kind.ANSWER, ALLOWED, "one pending invitation (the invitee's); reset after an allowed cell"),
        DECLINE(Kind.ANSWER, ALLOWED, "one pending invitation (the invitee's); reset after an allowed cell"),
        LIST(Kind.LIST, "SEES", "none"),
        ADMIN_INSPECT(Kind.ADMIN, REACHED, "none"),
        ADMIN_DUMP(Kind.ADMIN, REACHED, "none"),
        ADMIN_RECOVER_RESERVATIONS(Kind.ADMIN, REACHED, "no stale reservation"),
        ADMIN_WARP(Kind.ADMIN, REACHED, "none"),
        ADMIN_ELEVATOR_CHECK(Kind.ADMIN, REACHED, "none"),
        ADMIN_TRANSFER(Kind.ADMIN, REACHED, "transfer to the current owner (no change)"),
        ADMIN_CREATE_DELETE(Kind.ADMIN, REACHED, "create then delete a station; executed for OPERATOR and CONSOLE"),
        BUILD(Kind.BUILD, ALLOWED, "block break event on the platform"),
        VISIT(Kind.VISIT, ALLOWED, "the flight service's VISIT predicate and inputs");

        final Kind kind;
        final String allowed;
        final String precondition;

        Action(Kind kind, String allowed, String precondition) {
            this.kind = kind;
            this.allowed = allowed;
            this.precondition = precondition;
        }
    }

    /** Sources acting for the owner that are not the owner's own connected, non-silent source. */
    private static final Set<Actor> OWNER_NOT_LOCAL = EnumSet.of(Actor.EXECUTE_AS_OWNER, Actor.SILENT_OWNER,
            Actor.FAKE_PLAYER_OWNER, Actor.STALE_OWNER);
    private static final Set<Actor> VISITORS = EnumSet.of(Actor.OWNER, Actor.MEMBER, Actor.PREVIOUS_OWNER,
            Actor.OPERATOR, Actor.OPERATOR_OUTSIDE);
    private static final Set<Actor> OPERATOR_SOURCES = EnumSet.of(Actor.OPERATOR, Actor.OPERATOR_OUTSIDE,
            Actor.CONSOLE, Actor.EXECUTE_AS_OWNER);
    /** No replies reach these sources; only the return value and audit lines are observable. */
    static final Set<Actor> SUPPRESSED = EnumSet.of(Actor.SILENT_OWNER, Actor.FAKE_PLAYER_OWNER);
    private static final Set<Actor> NO_RELATION = EnumSet.of(Actor.OUTSIDER, Actor.OTHER_STATION_OWNER,
            Actor.REMOVED_MEMBER, Actor.DEOPPED_OPERATOR);

    private StationPermissionMatrix() {
    }

    static String expected(Action action, Actor actor) {
        return switch (action.kind) {
            case LOCAL -> actor == Actor.CONSOLE ? NOT_PLAYER
                    : OWNER_NOT_LOCAL.contains(actor) ? "NOT_LOCAL_PLAYER"
                    : actor == Actor.OPERATOR_OUTSIDE ? "NOT_IN_STATION"
                    : actor == Actor.OWNER || actor == Actor.OPERATOR ? action.allowed
                    : "UNAUTHORIZED";
            case STATUS -> actor == Actor.CONSOLE ? NOT_PLAYER
                    : actor == Actor.OPERATOR_OUTSIDE ? "NOT_IN_STATION"
                    : VISITORS.contains(actor) || OWNER_NOT_LOCAL.contains(actor) ? action.allowed
                    : "UNAUTHORIZED";
            case ENVIRONMENT -> actor == Actor.CONSOLE ? NOT_PLAYER
                    : actor == Actor.OPERATOR_OUTSIDE ? "NOT_IN_STATION"
                    : SUPPRESSED.contains(actor) ? ALLOWED
                    : VISITORS.contains(actor) || OWNER_NOT_LOCAL.contains(actor) ? action.allowed
                    : "ANONYMOUS";
            case TEAM -> actor == Actor.CONSOLE ? NOT_PLAYER
                    : actor == Actor.OWNER || actor == Actor.OPERATOR || actor == Actor.OPERATOR_OUTSIDE
                    || OWNER_NOT_LOCAL.contains(actor) ? ALLOWED
                    : "UNAUTHORIZED";
            case ANSWER -> actor == Actor.CONSOLE ? NOT_PLAYER
                    : actor == Actor.INVITEE ? ALLOWED
                    : SUPPRESSED.contains(actor) ? REJECTED
                    : "INVITATION_MISSING";
            case LIST -> NO_RELATION.contains(actor) ? "HIDDEN" : action.allowed;
            case ADMIN -> OPERATOR_SOURCES.contains(actor) ? REACHED : UNKNOWN_COMMAND;
            case BUILD -> actor == Actor.CONSOLE || actor == Actor.EXECUTE_AS_OWNER || actor == Actor.SILENT_OWNER
                    || actor == Actor.STALE_OWNER ? NOT_APPLICABLE
                    : VISITORS.contains(actor) || actor == Actor.FAKE_PLAYER_OWNER ? ALLOWED
                    : "CANCELLED";
            case VISIT -> actor == Actor.CONSOLE || OWNER_NOT_LOCAL.contains(actor) ? NOT_APPLICABLE
                    : VISITORS.contains(actor) ? ALLOWED
                    : "UNAUTHORIZED";
        };
    }
}
