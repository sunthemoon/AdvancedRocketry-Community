package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only GameTest selection for repeating one batch in fresh worlds (W36). With the JVM system property
 * {@value #PROPERTY} set to comma-separated batch names, for example {@code escrow_core}, a GameTest server runs only
 * those batches. Without the property, and on every other server, nothing changes. Forge registers every test before
 * the server exists, so the server's own batch list is narrowed before its first tick starts the tests.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class GameTestBatchFilter {
    public static final String PROPERTY = "advancedrocketrycommunity.gameTestBatches";
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIdentity.MOD_ID);

    private GameTestBatchFilter() { }

    @SubscribeEvent
    public static void select(ServerAboutToStartEvent event) {
        Set<String> names = names(System.getProperty(PROPERTY, ""));
        if (names.isEmpty() || !(event.getServer() instanceof GameTestServer server)) {
            return;
        }
        // GameTestServer.testBatches, a mutable copy that startTests reads on the first tick.
        List<GameTestBatch> batches = ObfuscationReflectionHelper.getPrivateValue(GameTestServer.class, server, "f_177587_");
        int before = batches.size();
        batches.removeIf(batch -> !keeps(names, batch.getName()));
        LOGGER.info("GameTest batch filter {} kept {} of {} batches", names, batches.size(), before);
        if (batches.isEmpty()) {
            throw new IllegalStateException("GameTest batch filter " + names + " matched no batch");
        }
    }

    static Set<String> names(String property) {
        return Arrays.stream(property.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Vanilla names each batch {@code <batch>:<index>}, splitting a batch of more than 100 tests. */
    static boolean keeps(Set<String> names, String batch) {
        int index = batch.lastIndexOf(':');
        return names.contains(index < 0 ? batch : batch.substring(0, index));
    }
}
