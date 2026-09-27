package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentSnapshot;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Independent consumer tests of the actual ready-event service, not an internal test instance. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnvironmentQueryGameTests {
    private static final String HOST = "advancedrocketrycommunity";

    private EnvironmentQueryGameTests() { }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void eventHandleReadsActualEarthAndMoonDefinitions(GameTestHelper helper) {
        var queries = EnvironmentQueryFixture.queries();
        var earth = queries.at(Level.OVERWORLD, helper.absolutePos(BlockPos.ZERO)).orElseThrow();
        helper.assertTrue(earth.bodyId().toString().equals(HOST + ":earth")
                        && earth.locus() == EnvironmentSnapshot.Locus.SURFACE && earth.instanceId().isEmpty()
                        && earth.gravityMultiplier() == 1 && !earth.vacuum()
                        && earth.atmosphere().orElseThrow().breathable(), "Earth environment projection differs");
        var moon = queries.at(dimension("moon"), BlockPos.ZERO).orElseThrow();
        helper.assertTrue(moon.bodyId().toString().equals(HOST + ":moon") && moon.gravityMultiplier() == 0.165
                        && moon.vacuum() && !moon.atmosphere().orElseThrow().breathable(), "Moon environment differs");
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void unloadedPositionsAndSpaceGapsNeverRequestChunks(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        BlockPos far = new BlockPos(28_000_000, 0, -28_000_000);
        var queries = EnvironmentQueryFixture.queries();
        for (var dimension : java.util.List.of(Level.OVERWORLD, dimension("moon"), dimension("space"))) {
            var level = server.getLevel(dimension);
            helper.assertTrue(level != null && level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                    "Unloaded query precondition differs");
            var result = queries.at(dimension, far);
            helper.assertTrue(result.isEmpty() == dimension.equals(dimension("space")), "Far-location result differs");
            helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                    "Read-only query loaded a chunk");
        }
        helper.assertTrue(queries.at(dimension("missing_fixture"), far).isEmpty(), "Unknown dimension resolved");
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void actualHandleRejectsCallsFromAnotherThread(GameTestHelper helper) throws InterruptedException {
        var queries = EnvironmentQueryFixture.queries();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { queries.at(Level.OVERWORLD, BlockPos.ZERO); }
            catch (Throwable caught) { failure.set(caught); }
        }, "environment-api-fixture");
        worker.start();
        worker.join(1_000);
        helper.assertTrue(!worker.isAlive() && failure.get() instanceof IllegalStateException,
                "Off-thread environment query was not rejected");
        helper.assertTrue(queries.at(Level.OVERWORLD, BlockPos.ZERO).isPresent(), "Owner-thread handle was damaged");
        helper.succeed();
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(HOST + ":" + path));
    }
}
