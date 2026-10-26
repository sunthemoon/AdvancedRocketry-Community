package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Wiring checks complement SealedReleaseGuardGameTests' actual same-world interlocks. */
class SealedReleaseGuardTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/" + name + ".java"));
    }
    private static void before(String source, String guard, String action) {
        int at = source.indexOf(guard);
        assertTrue(at >= 0 && at < source.indexOf(action), guard + " must precede " + action);
    }
    @Test void releasePlanningChecksSealedClaimsBeforeConstructingAPlan() throws Exception {
        var body = source("RocketReleaseWorld").split("RocketReleasePlan plan\\(", 2)[1].split("@Override public long now", 2)[0];
        before(body, "RocketRuntime.sealedClaimed(authority)", "RocketReleasePlan.create(");
    }
    @Test void releaseBoundaryRechecksClaimsBeforeAcceptingAnAuthority() throws Exception {
        var body = source("RocketReleaseWorld").split("boolean boundary\\(", 2)[1].split("@Override public boolean applySeats", 2)[0];
        before(body, "RocketRuntime.sealedClaimed(rocket)", "return true;");
        assertTrue(source("RocketRuntime").contains("manager.sealedClaimed(rocket)"));
        assertTrue(source("RocketManager").contains("return sealed.claimed(rocket)"));
    }
    @Test void sealedAdmissionChecksReleaseFenceBeforeReservingOrBuildingCarriers() throws Exception {
        var body = source("SealedDisassemblyCoordinator").split("UUID begin\\(", 2)[1].split("private boolean exclusive", 2)[0];
        before(body, "RocketReleaseAccess.fenced(rocket)", "transactions.reserveSealed(");
        before(body, "RocketReleaseAccess.fenced(rocket)", "new SealedTerrain(");
        before(body, "RocketReleaseAccess.fenced(rocket)", "work.put(");
    }
    @Test void sealedWorldBoundaryRechecksReleaseFenceBeforeAnyProtocolStep() throws Exception {
        var body = source("SealedDisassemblyWorld").split("SealedDisassembly.Gate gate\\(\\)", 2)[1]
                .split("@Override public boolean requestCurrent", 2)[0];
        before(body, "RocketReleaseAccess.fenced(level.getServer(), entities.logical())", "SealedDisassembly.Gate.READY");
    }
}
