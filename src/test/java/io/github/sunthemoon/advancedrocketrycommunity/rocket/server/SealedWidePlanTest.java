package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SealedWidePlanTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @ParameterizedTest @CsvSource({"160,23", "1008,129"})
    void supportedWideAssembliesReceiveTheirOwnRefusalAndSlotDiagnostic(int length, int slots) throws Exception {
        var snapshot = scan(length); var type = type();
        var ctor = type.getDeclaredConstructor(RocketStructureSnapshot.class); ctor.setAccessible(true);
        var policy = ctor.newInstance(snapshot); var lines = new ArrayList<String>();
        assertEquals(slots, call(policy, "slots", new Class<?>[0]));
        var result = call(policy, "refuseNew", new Class<?>[] {UUID.class, Consumer.class}, UUID.randomUUID(), (Consumer<String>) lines::add);
        assertEquals("PROOF_PLAN_LIMIT", result.toString());
        assertEquals(1, lines.size()); assertTrue(lines.get(0).contains("slots=" + slots) && lines.get(0).contains("limit=21"));
    }
    @Test void oldWideUHasOneDistinctWarningPerRecordLoadAndNeverBecomesAdmissible() throws Exception {
        var lines = new ArrayList<String>(); var id = UUID.randomUUID();
        for (int load = 0; load < 2; load++) {
            var ctor = type().getDeclaredConstructor(int.class); ctor.setAccessible(true); var policy = ctor.newInstance(129);
            for (int retry = 0; retry < 300; retry++) {
                call(policy, "reportRetained", new Class<?>[] {UUID.class, Consumer.class}, id, (Consumer<String>) lines::add);
                assertEquals(false, call(policy, "allowed", new Class<?>[0]));
            }
            assertEquals(load + 1, lines.size());
        }
        assertTrue(lines.stream().allMatch(s -> s.contains(id.toString()) && s.contains("slots=129")
                && s.contains("limit=21") && s.contains("offline-whole-world") && !s.contains("BUSY")));
    }
    @Test void exactlyTwentyOneHasNoRefusalOrWarning() throws Exception {
        var ctor = type().getDeclaredConstructor(int.class); ctor.setAccessible(true); var policy = ctor.newInstance(21);
        Consumer<String> unexpected = line -> fail(line);
        assertTrue((boolean) call(policy, "allowed", new Class<?>[0]));
        assertNull(call(policy, "refuseNew", new Class<?>[] {UUID.class, Consumer.class}, UUID.randomUUID(), unexpected));
        call(policy, "reportRetained", new Class<?>[] {UUID.class, Consumer.class}, UUID.randomUUID(), unexpected);
    }
    @Test void refusalPrecedesClaimCarrierAndJournalWorkAndBusyDoesNotHideTheReason() throws Exception {
        var base = Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server");
        var source = Files.readString(base.resolve("SealedDisassemblyCoordinator.java"));
        var begin = source.split("UUID begin\\(", 2)[1].split("private boolean exclusive", 2)[0];
        int refusal = begin.indexOf(".refuseNew(");
        assertTrue(refusal >= 0 && refusal < begin.indexOf("transactions.reserveSealed(")
                && refusal < begin.indexOf("new SealedTerrain(") && refusal < begin.indexOf("work.put("));
        var world = Files.readString(base.resolve("SealedDisassemblyWorld.java"));
        assertTrue(world.contains("PROOF_PLAN_LIMIT"));
        assertTrue(world.contains("admission.allowed()"));
    }
    @Test void twentyOneIsNotTheAbsoluteNativeWindowLimit() throws Exception {
        var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrierBudget");
        var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); var quota = ctor.newInstance();
        assertEquals(true, call(quota, "reserve", new Class<?>[] {String.class, boolean.class, long.class}, "level", true, 0L));
        call(quota, "captured", new Class<?>[] {int.class}, 0); call(quota, "release", new Class<?>[] {int.class}, 0);
        int captures = 0; long last = -1;
        for (long tick = 4; tick <= 104; tick++) {
            if ((boolean) call(quota, "reserveOrdinary", new Class<?>[] {String.class, boolean.class, long.class}, "level", true, tick)) {
                captures++; last = tick;
                call(quota, "captured", new Class<?>[] {int.class}, 0); call(quota, "releaseOrdinary", new Class<?>[] {int.class}, 0);
            }
        }
        assertEquals(24, captures); assertEquals(99, last - 4);
        assertFalse(SealedProofAdmission.fitsNativeWindow(24), "Policy stays conservative despite this aligned window");
    }
    private static Class<?> type() throws Exception {
        return Class.forName("io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedPlanAdmission");
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }
    static RocketStructureSnapshot scan(int length) {
        var origin = new RocketPosition(15, 64, 3);
        RocketScanWorld world = new RocketScanWorld() {
            public RocketPropulsion.Settings propulsionSettings() { return RocketPropulsion.Settings.DEFAULTS; }
            public RocketScanObservation observe(RocketPosition absolute) {
                int x = absolute.x() - origin.x();
                if (absolute.y() != origin.y() || absolute.z() != origin.z() || x < 0 || x >= length) { return RocketScanObservation.empty(); }
                String name; RocketBlockMetrics metrics; RocketPropulsionComponent component;
                if (x == 0) { name = "rocket_seat"; metrics = new RocketBlockMetrics(20, 0, 0, false, true, false); component = RocketPropulsionComponent.EXTERNAL; }
                else if (x == 1) { name = "guidance_computer"; metrics = new RocketBlockMetrics(30, 0, 0, false, false, true); component = RocketPropulsionComponent.EXTERNAL; }
                else if (x == 2) { name = "rocket_fuel_tank"; component = RocketPropulsionComponent.MONO_TANK; metrics = component.metrics(); }
                else { name = "rocket_motor"; component = RocketPropulsionComponent.BASIC_MONO; metrics = component.metrics(); }
                return RocketScanObservation.movable(new RocketBlockState(ResourceLocation.tryParse("advancedrocketrycommunity:" + name), Map.of()), metrics, null, component);
            }
        };
        var task = new RocketStructureScanTask(world, ResourceLocation.tryParse("minecraft:overworld"), origin, UUID.randomUUID(), 0);
        RocketScanResult result;
        do { result = task.step(256); } while (result.status() == RocketScanResult.Status.RUNNING);
        assertEquals(RocketScanResult.Status.SUCCESS, result.status(), result.issues().toString());
        return result.snapshot().orElseThrow();
    }
}
