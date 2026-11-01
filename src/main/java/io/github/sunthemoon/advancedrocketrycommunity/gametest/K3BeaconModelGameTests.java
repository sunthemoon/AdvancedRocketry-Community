package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(ModIdentity.MOD_ID) @PrefixGameTestTemplate(false)
public final class K3BeaconModelGameTests {
    private K3BeaconModelGameTests() { }
    private record Fixture(K3ModelTestRegion region, PlanetaryBeaconBlockEntity beacon, BlockPos cap) { }

    @GameTest(template = "empty", batch = "w236_beacon_model", timeoutTicks = 120)
    public static void beaconProjectsFormationNotOwnershipOrEnabledStateInEveryFacing(GameTestHelper helper) {
        var layout = K3ModelTestLayout.BEACON;
        var region = new K3ModelTestRegion(helper, layout.center(), layout.radius());
        var pattern = BeaconRuntime.services().orElseThrow().pattern().orElseThrow();
        layout.assertIsolated(helper, region, pattern.cells().keySet(), pattern.controllerAnchor());
        var fixtures = new ArrayList<Fixture>(); int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var origin = layout.origin(index++);
            var transform = ClassicModelProjection.transform(facing);
            for (int x = -1; x <= 1; x++) { for (int z = 0; z < 3; z++) { for (int y = 0; y < 5; y++) {
                var offset = transform.applyOffset(new io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition(x, y, z));
                var state = y == 0 || x == 0 && z == 1 && y < 4 ? ModBlocks.MACHINE_CASING.get().defaultBlockState()
                        : x == 0 && z == 1 && y == 4 ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                region.set(origin.offset(offset.x(), offset.y(), offset.z()), state);
            } } }
            region.set(origin, ModBlocks.PLANETARY_BEACON.get().defaultBlockState().setValue(PlanetaryBeaconBlock.FACING, facing));
            var capOffset = transform.applyOffset(new io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition(0, 4, 1));
            var beacon = (PlanetaryBeaconBlockEntity) helper.getLevel().getBlockEntity(origin);
            helper.assertTrue(beacon != null && beacon.owner().isEmpty(), "K3 beacon fixture gained an owner/player");
            fixtures.add(new Fixture(region, beacon, origin.offset(capOffset.x(), capOffset.y(), capOffset.z())));
        }
        int[] stage = {0};
        // Exactly one vanilla schedule registration, at test start. Continuations only change stage.
        helper.onEachTick(() -> {
            for (var f : fixtures) {
                PlanetaryBeaconBlockEntity.serverTick(helper.getLevel(), f.beacon.getBlockPos(), f.beacon.getBlockState(), f.beacon);
            }
            if (stage[0] == 0 && fixtures.stream().allMatch(f -> f.beacon.formed())) {
                for (var f : fixtures) {
                    K3ModelTestRegion.assertProjection(helper, f.beacon, true);
                    var original = FormedModelRotationProbe.rotate(helper, f.beacon);
                    FormedModelRotationProbe.assertPresentation(helper, f.beacon, false);
                    FormedModelRotationProbe.set(helper, f.beacon, original);
                    FormedModelRotationProbe.assertPresentation(helper, f.beacon, true);
                    helper.assertTrue(!f.beacon.live(), "Unowned K3 beacon became live");
                    helper.assertTrue(f.beacon.getRenderBoundingBox().equals(ClassicModelProjection.bounds(ModIdentity.id("planetary_beacon"),
                            f.beacon.getBlockState().getValue(PlanetaryBeaconBlock.FACING), f.beacon.getBlockPos())), "Beacon render bounds differ");
                    f.region.set(f.cap, Blocks.AIR.defaultBlockState());
                    BeaconRuntime.services().orElseThrow().structures().changed(helper.getLevel().dimension(), f.cap);
                }
                stage[0] = 1;
            } else if (stage[0] == 1 && fixtures.stream().noneMatch(f -> f.beacon.formed())) {
                for (var f : fixtures) {
                    K3ModelTestRegion.assertProjection(helper, f.beacon, false);
                    f.region.set(f.cap, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    BeaconRuntime.services().orElseThrow().structures().changed(helper.getLevel().dimension(), f.cap);
                }
                stage[0] = 2;
            } else if (stage[0] == 2 && fixtures.stream().allMatch(f -> f.beacon.formed())) {
                for (var f : fixtures) { K3ModelTestRegion.assertProjection(helper, f.beacon, true); }
                region.close(); helper.succeed();
            }
        });
    }
}
