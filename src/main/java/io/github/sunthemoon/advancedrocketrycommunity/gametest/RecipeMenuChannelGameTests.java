package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkRegistry;

/** Read-only registry observation after actual Forge setup, not a connected-peer test. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeMenuChannelGameTests {
    private RecipeMenuChannelGameTests() { }

    @GameTest(template = "rocket_test", batch = "machine_menu_channel", timeoutTicks = 20)
    public static void installedMenuChannelAdvertisesAndRejectsIncompatiblePeerVersions(GameTestHelper helper) {
        try {
            ResourceLocation menuId = ModIdentity.id("machine_menu");
            Map<ResourceLocation, String> advertised = advertised();
            helper.assertTrue("1".equals(advertised.get(menuId)),
                    "Common setup must install the required menu channel before this observation");
            for (String direction : new String[] {"validateClientChannels", "validateServerChannels"}) {
                helper.assertTrue(rejected(direction, advertised).isEmpty(),
                        "Matching installed channel versions must be accepted by " + direction);
                Map<ResourceLocation, String> missing = new HashMap<>(advertised);
                missing.remove(menuId);
                Map<ResourceLocation, String> missingRejected = rejected(direction, missing);
                helper.assertTrue(missingRejected.size() == 1 && missingRejected.containsKey(menuId),
                        "Only the omitted menu channel must be rejected by " + direction);
                for (String version : new String[] {"", "0", "2", "01", "1 ",
                        NetworkRegistry.ABSENT.version(), NetworkRegistry.ACCEPTVANILLA}) {
                    Map<ResourceLocation, String> incompatible = new HashMap<>(advertised);
                    incompatible.put(menuId, version);
                    Map<ResourceLocation, String> refused = rejected(direction, incompatible);
                    helper.assertTrue(refused.size() == 1 && refused.containsKey(menuId),
                            "Only the incompatible menu channel must be rejected by " + direction);
                    helper.assertTrue(version.equals(refused.get(menuId)),
                            "The rejection must report the supplied peer version without rewriting it");
                }
            }
            helper.assertTrue(advertised.equals(advertised()),
                    "Read-only channel validation must not change installed advertisements");
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Pinned Forge channel observation is unavailable", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, String> advertised() throws ReflectiveOperationException {
        Method method = NetworkRegistry.class.getDeclaredMethod("buildChannelVersions");
        method.setAccessible(true);
        return (Map<ResourceLocation, String>) method.invoke(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, String> rejected(String direction, Map<ResourceLocation, String> incoming)
            throws ReflectiveOperationException {
        Method method = NetworkRegistry.class.getDeclaredMethod(direction, Map.class);
        method.setAccessible(true);
        return (Map<ResourceLocation, String>) method.invoke(null, incoming);
    }
}
