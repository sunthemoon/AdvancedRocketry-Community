package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SatelliteTerminalLaunchParityTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest
    @EnumSource(SatelliteKind.class)
    void blockedRegistryResultMatchesTheServiceForEveryKind(SatelliteKind kind) {
        var server = UnstartedServer.owned();
        var level = UnstartedServer.allocate(RegistryLevel.class);
        level.storage = TestDataStorage.over(temp);
        UnstartedServer.set(ServerLevel.class, level, "server", server);
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        var owner = UUID.randomUUID();
        var player = UnstartedServer.allocate(ServerPlayer.class);
        UnstartedServer.set(Entity.class, player, "uuid", owner);
        UnstartedServer.set(Entity.class, player, "level", level);
        UnstartedServer.set(ServerPlayer.class, player, "server", server);
        var raw = new CompoundTag();
        raw.putInt("schema_version", 999);
        raw.putString("future_payload", "preserve-exactly");
        var blocked = SatelliteMissionSavedData.load(raw);
        level.storage.set(SatelliteMissionSavedData.DATA_NAME, blocked);
        var identity = new SatelliteIdentity(UUID.randomUUID(), owner, ModIdentity.id("parity_satellite"), kind,
                kind == SatelliteKind.DATA ? List.of() : List.of(ModIdentity.id("removed_component")));
        var satellites = new SatelliteCatalogManager();
        var celestial = new CelestialCatalogManager();
        var service = new SatelliteLaunchService(satellites, celestial);
        var terminalAdapter = new SatelliteManager(satellites, celestial, ignored -> { });

        var validation = service.validate(server, owner, identity, ModIdentity.id("earth"));
        assertEquals(SatelliteOperationCode.REGISTRY_BLOCKED, validation.result().code());
        assertTrue(validation.request().isEmpty());
        var result = terminalAdapter.launch(player, identity, ModIdentity.id("earth"));
        assertFalse(result.success());
        assertFalse(result.changed());
        assertEquals(raw, blocked.save(new CompoundTag()));
        assertEquals(validation.result().code(), result.code());
    }

    @Test void blockedRegistryHasEnglishAndChineseResultText() throws Exception {
        var expected = Map.of("en_us", "Satellite registry is blocked; check its saved data",
                "zh_cn", "卫星注册表不可用；请检查存档数据");
        for (var entry : expected.entrySet()) {
            Path language = Path.of("src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang",
                    entry.getKey() + ".json");
            try (var reader = Files.newBufferedReader(language)) {
                var translations = JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals(entry.getValue(), translations.get(SatelliteOperationCode.REGISTRY_BLOCKED.translationKey()).getAsString());
            }
        }
    }

    private static final class RegistryLevel extends ServerLevel {
        DimensionDataStorage storage;
        RegistryLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
    }
}
