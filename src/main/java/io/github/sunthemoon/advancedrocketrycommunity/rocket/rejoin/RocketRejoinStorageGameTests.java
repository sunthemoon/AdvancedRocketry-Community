package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import java.nio.file.Files;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinStorageGameTests {
    private RocketRejoinStorageGameTests() { }
    @GameTest(template = "empty", batch = "rocket_rejoin_storage", timeoutTicks = 100)
    public static void startupAndOrdinarySaveKeepAnUnusedSidecarAbsent(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var data = RocketRejoinEvents.existing(server);
        helper.assertTrue(data != null, "Rejoin state was not installed before recovery and login");
        helper.assertTrue(data.root().equals(RocketRejoinRoot.empty()) && !data.isDirty(), "Unused sidecar has state");
        var file = ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE);
        data.save(file.toFile());
        helper.assertTrue(!Files.exists(file), "Startup or ordinary save created an unused sidecar");
        helper.succeed();
    }
}
