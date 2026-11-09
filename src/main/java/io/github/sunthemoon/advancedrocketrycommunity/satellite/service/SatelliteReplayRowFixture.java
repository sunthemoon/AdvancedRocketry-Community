package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.*;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryTestAwait;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;

/** W333's synchronous adapter fixture. All temporary runtime/SavedData substitutions end in the same server tick. */
final class SatelliteReplayRowFixture implements AutoCloseable {
    static final ResourceLocation EARTH = ModIdentity.id("earth"), MOON = ModIdentity.id("moon"), MARS = ModIdentity.id("mars");
    final GameTestHelper h;
    final MinecraftServer server;
    final SatelliteCatalog baseline;
    final CelestialCatalog bodies;
    final SatelliteCatalogManager satellites = new SatelliteCatalogManager();
    final CelestialCatalogManager celestial = new CelestialCatalogManager();
    final SatelliteLaunchService service = new SatelliteLaunchService(satellites, celestial);
    final SatelliteMissionSavedData original;
    final CelestialSavedData originalKnowledge;
    final SatelliteManager manager;
    final java.lang.reflect.Field bridge;
    SatelliteMissionSavedData data;
    SatelliteTerminalBlockEntity terminal;
    FakePlayer player;

    SatelliteReplayRowFixture(GameTestHelper h) throws Exception {
        this.h = h; server = h.getLevel().getServer();
        h.assertTrue(SatelliteRegistryTestAwait.allowed(server), "Fixture requires GameTest server");
        baseline = SatelliteRuntime.catalog().orElseThrow(); bodies = SatelliteRuntime.celestialCatalog().orElseThrow();
        original = SatelliteMissionSavedData.get(server); original.awaitForTests(server);
        originalKnowledge = CelestialSavedData.get(server);
        bridge = SatelliteRuntime.class.getDeclaredField("manager"); bridge.setAccessible(true);
        manager = (SatelliteManager) bridge.get(null);
        SatelliteRuntime.install(new SatelliteManager(satellites, celestial, ignored -> { }));
        catalogs("normal");
        server.overworld().getDataStorage().set(CelestialSavedData.DATA_NAME, CelestialSavedData.create());
        install(SatelliteMissionSavedData.create(0));
    }

    void install(SatelliteMissionSavedData next) {
        if (data != null) { data.close(); }
        data = next;
        server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME, next);
        SatelliteMissionSavedData.get(server);
    }

    SatelliteIdentity identity(SatelliteKind kind) {
        var definition = kind == SatelliteKind.DATA ? ModIdentity.id("data_satellite") : baseline.kindDefinitions().stream()
                .filter(d -> d.kind() == kind).findFirst().orElseThrow().id();
        var primary = kind == SatelliteKind.DATA ? null : baseline.kindDefinition(definition).orElseThrow().primaryComponent();
        return new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), definition, kind, kind == SatelliteKind.DATA ? List.of()
                : List.of(ModIdentity.id("satellite_chassis"), primary, ModIdentity.id("satellite_solar_module"),
                ModIdentity.id("data_storage_unit"), ModIdentity.id("satellite_battery"), ModIdentity.id("satellite_cargo_hold")));
    }

    void seed(SatelliteIdentity identity) {
        if (identity.kind() == SatelliteKind.DATA) {
            h.assertTrue(data.launch(identity.satelliteId(), SatelliteManager.initialMissionId(identity.satelliteId()), identity.ownerId(),
                    new SatelliteDefinition(1, identity.definitionId(), 200, 120, 100, List.of(EARTH)), EARTH, 0, false).success(), "DATA seed failed");
        } else {
            SatelliteKindState state = switch (identity.kind()) {
                case SOLAR -> new SatelliteKindState.Solar(100, Optional.empty());
                case SURVEY -> new SatelliteKindState.Survey(0, 0, 1000, 32, 8);
                default -> new SatelliteKindState.Plain(identity.kind());
            };
            h.assertTrue(data.launchIdle(time -> SatelliteState.launchIdle(identity.satelliteId(), identity.definitionId(), identity.ownerId(),
                    time, EARTH, new SatelliteBlueprint(identity.components(), false, new SatelliteStats(4, 10720, 1000, 1, 10)), state), 0).success(),
                    "Non-DATA seed failed");
        }
    }

    void catalogs(String change) {
        satellites.clear(); celestial.clear();
        if (change.equals("definition")) { return; }
        h.assertTrue(celestial.applyCandidate(CelestialCatalog.create(bodies.definitions())), "Body fixture failed");
        var components = new ArrayList<SatelliteComponentDefinition>();
        for (var component : baseline.components().definitions()) {
            if (change.equals("component") && component.role() == SatelliteComponentRole.BATTERY
                    || change.equals("primary") && component.role() == SatelliteComponentRole.PRIMARY) { continue; }
            components.add(change.equals("stats") && component.role() == SatelliteComponentRole.POWER
                    ? new SatelliteComponentDefinition(component.item(), component.role(), component.kind(), 1) : component);
        }
        var kinds = new ArrayList<SatelliteKindDefinition>();
        if (!change.equals("primary")) {
            for (var definition : baseline.kindDefinitions()) {
                kinds.add(change.equals("research") ? new SatelliteKindDefinition(definition.id(), definition.kind(), definition.primaryComponent(),
                        100000, definition.launchTargets(), definition.parameters()) : change.equals("target")
                        ? new SatelliteKindDefinition(definition.id(), definition.kind(), definition.primaryComponent(), definition.requiredLifetimeResearch(),
                        List.of(MOON), definition.parameters()) : definition);
            }
        }
        var componentCatalog = SatelliteComponentCatalog.create(components, id -> true).getOrThrow(false, message -> { throw new AssertionError(message); });
        var definitions = change.equals("target") ? List.of(new SatelliteDefinition(1, ModIdentity.id("data_satellite"), 200, 120, 100, List.of(MOON)))
                : baseline.definitions();
        h.assertTrue(satellites.applyCandidate(SatelliteCatalog.create(definitions, kinds,
                bodies.definitions().stream().map(d -> d.id()).toList(), componentCatalog)), "Satellite fixture failed: " + satellites.status());
        if (change.equals("body")) { celestial.clear(); }
    }

    void terminal(SatelliteIdentity identity) {
        if (terminal != null) { h.getLevel().removeBlock(terminal.getBlockPos(), false); }
        var pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, ModBlocks.SATELLITE_TERMINAL.get());
        terminal = (SatelliteTerminalBlockEntity) h.getBlockEntity(pos);
        terminal.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
        player = new FakePlayer(h.getLevel(), new GameProfile(identity.ownerId(), "W333Replay"));
        player.setPos(terminal.getBlockPos().getX() + .5, terminal.getBlockPos().getY() + .5, terminal.getBlockPos().getZ() + .5);
        var chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()); SatelliteItemData.write(chip, identity);
        var pkg = new ItemStack(identity.kind() == SatelliteKind.DATA ? ModItems.DATA_SATELLITE_PACKAGE.get() : ModItems.SATELLITE_PACKAGE.get());
        SatelliteItemData.write(pkg, identity);
        h.assertTrue(terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, chip, false).isEmpty()
                && terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE, pkg, false).isEmpty(), "Terminal fixture insertion failed");
    }

    ItemStack pkg() { return terminal.menuInventory().getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE); }
    io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode result() {
        return io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode.values()[
                terminal.saveWithoutMetadata().getCompound("SatelliteTerminal").getInt("last_result")];
    }

    @Override public void close() throws Exception {
        try {
            if (data != null) { data.close(); }
            if (terminal != null) { h.getLevel().removeBlock(terminal.getBlockPos(), false); }
        } finally {
            bridge.set(null, manager);
            var storage = server.overworld().getDataStorage();
            storage.set(SatelliteMissionSavedData.DATA_NAME, original);
            storage.set(CelestialSavedData.DATA_NAME, originalKnowledge);
            original.setDirty(); originalKnowledge.setDirty();
            original.flush(server);
        }
    }
}
