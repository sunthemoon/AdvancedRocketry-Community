package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketMonitorRootTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static RocketMonitorRoot root() {
        var binding = new LoaderBinding(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"),
                UUID.randomUUID(), new BlockPos(3, 64, 5), UUID.randomUUID());
        return new RocketMonitorRoot(UUID.randomUUID(), UUID.randomUUID(), binding, 1, true, true,
                new TravelTarget.Station(UUID.randomUUID()), "a".repeat(64));
    }
    @Test void roundTripRetainsOnlyMetadataAndTheEdgeLatch() {
        var root = root(); var saved = root.encode();
        assertEquals(root, RocketMonitorRoot.decode(saved));
        assertEquals(1, saved.getInt("schema"));
        assertEquals(root.binding().encode(), saved.getCompound("binding"));
        assertFalse(saved.contains("inventory")); assertFalse(saved.contains("request"));
        assertEquals(root, RocketMonitorRoot.decode(saved).signal(true));
        assertFalse(RocketMonitorRules.rising(RocketMonitorRoot.decode(saved).powered(), true));
        var blank = new RocketMonitorRoot(null, UUID.randomUUID(), null, 0, false, false, null, "");
        assertEquals(blank, RocketMonitorRoot.decode(blank.encode()));
    }
    @Test void bindingAndDisarmClearTheConfigurationWithoutRearmingAnEdge() {
        var root = root();
        for (var reset : new RocketMonitorRoot[] {root.bind(null), root.bind(root.binding()), root.disarm()}) {
            assertFalse(reset.armed()); assertNull(reset.destination()); assertEquals("", reset.authority());
            assertEquals(0, reset.mode()); assertTrue(reset.powered());
            assertEquals(root.device(), reset.device()); assertEquals(root.owner(), reset.owner());
        }
        assertEquals(root.binding(), root.disarm().binding());
    }
    @Test void futureMissingMistypedUnknownAndInvalidAuthorityAreRefused() {
        var good = root().encode();
        for (String key : new String[] {"schema", "device", "mode", "powered", "armed", "authority"}) {
            var missing = good.copy(); missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(missing), key);
            var mistyped = good.copy(); mistyped.putString(key, "invalid");
            assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(mistyped), key);
        }
        for (int schema : new int[] {-1, 0, 2, Integer.MAX_VALUE}) {
            var future = good.copy(); future.putInt("schema", schema);
            assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(future));
        }
        var unknown = good.copy(); unknown.putInt("request_id", 1);
        assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(unknown));
        var booleanByte = good.copy(); booleanByte.putByte("powered", (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(booleanByte));
        var badMode = good.copy(); badMode.putInt("mode", 2);
        assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(badMode));
        var badHash = good.copy(); badHash.putString("authority", "");
        assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(badHash));
        var unowned = good.copy(); unowned.remove("owner");
        assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(unowned));
        var wrongBinding = good.copy(); wrongBinding.getCompound("binding").putInt("schema_version", 2);
        assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(wrongBinding));
    }
    @Test void envelopeLimitsAreCheckedBeforeNestedDecoding() {
        assertTrue(RocketMonitorRoot.bounded(root().encode()));
        assertFalse(RocketMonitorRoot.bounded(StringTag.valueOf("x".repeat(4097))));
        var nodes = new CompoundTag(); for (int i = 0; i < 129; i++) { nodes.putByte("k" + i, (byte) 0); }
        assertFalse(RocketMonitorRoot.bounded(nodes));
        var deep = new CompoundTag(); var cursor = deep;
        for (int i = 0; i < 13; i++) { var next = new CompoundTag(); cursor.put("n", next); cursor = next; }
        assertFalse(RocketMonitorRoot.bounded(deep));
        for (var malformed : new net.minecraft.nbt.Tag[] {nodes, deep, StringTag.valueOf("x"), null}) {
            assertThrows(IllegalArgumentException.class, () -> RocketMonitorRoot.decode(malformed));
        }
    }
}
