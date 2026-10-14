package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Connected-player visits mutate discovery even when the test is about beds rather than exploration. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NaturalBedDiscoveryGameTests {
    private NaturalBedDiscoveryGameTests() { }

    @GameTest(template = "empty", batch = "natural_beds_discovery", timeoutTicks = 30)
    public static void nativeMarsVisitIsDiscoveredButFixtureRestoresMemoryAndDisk(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var original = CelestialSavedData.get(server);
        CompoundTag before = original.save(new CompoundTag());
        try (var discovery = new NaturalBedFixtures.DiscoveryScope(server, true)) {
            var copy = CelestialSavedData.get(server);
            helper.assertTrue(copy != original && copy.get(PlanetaryContent.MARS).isEmpty(),
                    "The controlled visit did not start with an independent, undiscovered Mars");
            var player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "W37BDiscovery", helper.getLevel(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>());
            try {
                var mars = NonPlayerAtmosphereGameTests.level(helper, PlanetaryContent.level(PlanetaryContent.MARS));
                player.teleportTo(mars, 0.5D, 200, 0.5D, 0, 0);
                helper.assertTrue(copy.get(PlanetaryContent.MARS).filter(progress -> progress.firstVisitAt().isPresent())
                                .isPresent(), "The actual connected-player visit did not discover Mars");
                copy.flush(server);
                var persisted = read(server);
                helper.assertTrue(CelestialSavedData.load(persisted).get(PlanetaryContent.MARS).isPresent(),
                        "The native visit's discovery was not persisted in the fixture ledger");
                helper.assertTrue(before.equals(original.save(new CompoundTag())),
                        "The visit mutated the original discovery authority");
            } finally { server.getPlayerList().remove(player); }
        }
        helper.assertTrue(CelestialSavedData.get(server) == original
                        && before.equals(original.save(new CompoundTag())) && before.equals(read(server)),
                "The fixture leaked discovery in memory or on disk into the next batch");
        helper.succeed();
    }

    private static CompoundTag read(net.minecraft.server.MinecraftServer server) {
        return CheckedSavedDataFile.readPayload(server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(ManagedSavedDataType.CELESTIAL.fileName()), ManagedSavedDataType.CELESTIAL).orElseThrow();
    }
}
