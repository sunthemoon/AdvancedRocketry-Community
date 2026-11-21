package io.github.sunthemoon.arceintenttest;

/** Local prerequisites prevent accidental positive actions; they are never a server refusal oracle. */
public final class CaseGuard {
    public record Observation(boolean sameMenu, boolean inventoryMenu, boolean entityPresent,
                              double distanceSquared, boolean spectator, boolean creative) { }

    public static void check(IntentCase which, int kind, Observation observation) {
        if (!Double.isFinite(observation.distanceSquared()) || observation.distanceSquared() < 0) {
            throw new IllegalStateException("Invalid local distance observation");
        }
        switch (which) {
            case FLIGHT_STALE -> require(!observation.entityPresent(), "Recorded entity must no longer be present locally");
            case CLASSIC_STALE -> require(!observation.sameMenu() && !observation.inventoryMenu(),
                    "Open a replacement menu with a different container ID");
            case FLIGHT_RANGE, CLASSIC_RANGE -> {
                require(observation.distanceSquared() > 64, "Move beyond 8 blocks from the recorded target");
                if (which.flight()) { require(observation.entityPresent(), "Range case needs the live recorded rocket"); }
            }
            case FLIGHT_LATE, CLASSIC_LATE -> require(observation.inventoryMenu(), "Close the menu before sending");
            case FLIGHT_NON_OWNER, CLASSIC_NON_OWNER -> {
                require(observation.sameMenu() && observation.distanceSquared() <= 64,
                        "Keep the recorded menu open and remain within 8 blocks");
                require(!observation.creative() && !observation.spectator(), "Use the non-creative, non-OP account");
                if (which.classic()) { require(kind != 4, "Monitor non-owner cannot open a valid menu: NOT_COVERED"); }
            }
            case FLIGHT_FORBIDDEN_SURFACE, FLIGHT_PRIVATE_STATION -> require(observation.sameMenu() && observation.distanceSquared() <= 64,
                    "Keep the flight menu open and choose the manifest's forbidden surface");
            case MOTION_SPECTATOR -> require(observation.spectator(), "Use the spectator variant");
            default -> require(!observation.spectator(), "Use a living non-spectator player for this case");
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) { throw new IllegalStateException(reason); }
    }

    private CaseGuard() { }
}
