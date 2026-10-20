package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SatelliteLaunchServiceTest {
    private static final ResourceLocation EARTH = ModIdentity.id("earth"), MOON = ModIdentity.id("moon");
    private final SatelliteCatalogManager satellites = new SatelliteCatalogManager();
    private final CelestialCatalogManager celestial = new CelestialCatalogManager();
    private final SatelliteLaunchService service = new SatelliteLaunchService(satellites, celestial);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void matchingIdentityOfEveryKindReplaysBeforeAllNewLaunchDependencies() throws Exception {
        for (SatelliteKind kind : SatelliteKind.values()) {
            for (String changed : List.of("catalog", "definition", "primary", "component", "stats", "research", "body", "quotas")) {
                catalogs(changed);
                var data = SatelliteMissionSavedData.create(0);
                var identity = identity(kind);
                seed(data, identity);
                data.applyLimits(new RegistryLimits(1, 1, 1, 1, 1, 1, 1, 1, 10, 2));
                CompoundTag before = data.save(new CompoundTag());
                // Even a removed/disallowed/null selected target is irrelevant to replay.
                for (ResourceLocation target : Arrays.asList(null, ModIdentity.id("removed"), MOON)) {
                    var validation = service.validate(data, identity.ownerId(), identity, target,
                            body -> { fail("Replay consulted discovery: " + kind + "/" + changed); return false; });
                    assertEquals(SatelliteOperationCode.IDEMPOTENT, validation.result().code(), kind + "/" + changed);
                    assertEquals(SatelliteLaunchService.Branch.REPLAY, validation.request().orElseThrow().branch());
                    var replay = service.register(data, validation.request().orElseThrow(), 999,
                            body -> { fail("Replay registration consulted discovery"); return false; });
                    assertEquals(SatelliteOperationCode.IDEMPOTENT, replay.code());
                    assertFalse(replay.changed()); assertTrue(replay.mission().isEmpty());
                    assertEquals(before, data.save(new CompoundTag()), "Replay changed clock, mission, account or satellite");
                }
            }
        }
    }

    @Test void ownershipIdentityAndOperationalChecksPrecedeReplay() {
        for (SatelliteKind kind : SatelliteKind.values()) {
            var data = SatelliteMissionSavedData.create(0);
            var identity = identity(kind); seed(data, identity);
            assertEquals(SatelliteOperationCode.UNAUTHORIZED,
                    service.validate(data, UUID.randomUUID(), identity, EARTH, b -> false).result().code());
            var foreignOwner = new SatelliteIdentity(identity.satelliteId(), UUID.randomUUID(), identity.definitionId(), kind, identity.components());
            var otherDefinition = new SatelliteIdentity(identity.satelliteId(), identity.ownerId(), ModIdentity.id("changed"), kind, identity.components());
            SatelliteKind otherKind = kind == SatelliteKind.DATA ? SatelliteKind.SOLAR : SatelliteKind.DATA;
            var changedKind = new SatelliteIdentity(identity.satelliteId(), identity.ownerId(), identity.definitionId(), otherKind,
                    otherKind == SatelliteKind.DATA ? List.of() : List.of(ModIdentity.id("other")));
            for (var conflict : List.of(foreignOwner, otherDefinition, changedKind)) {
                assertEquals(SatelliteOperationCode.IDENTITY_CONFLICT,
                        service.validate(data, conflict.ownerId(), conflict, EARTH, b -> false).result().code());
            }
            CompoundTag root = data.save(new CompoundTag()); root.putInt("schema_version", 999);
            var blocked = SatelliteMissionSavedData.load(root);
            assertEquals(SatelliteOperationCode.REGISTRY_BLOCKED,
                    service.validate(blocked, identity.ownerId(), identity, EARTH, b -> false).result().code());
        }
    }

    @Test void freshDataStartsAtomicDeterministicDiscoveryMissionAndRegistrationRevalidates() throws Exception {
        catalogs("normal");
        var data = SatelliteMissionSavedData.create(0);
        var identity = identity(SatelliteKind.DATA);
        var validation = service.validate(data, identity.ownerId(), identity, EARTH, body -> false);
        assertEquals(SatelliteOperationCode.SUCCESS, validation.result().code());
        assertEquals(SatelliteLaunchService.Branch.FRESH, validation.request().orElseThrow().branch());
        assertTrue(data.satellites().isEmpty()); assertTrue(data.missions().isEmpty()); assertFalse(data.isDirty());
        var result = service.register(data, validation.request().orElseThrow(), 42, body -> false);
        assertEquals(SatelliteOperationCode.SUCCESS, result.code());
        var mission = result.mission().orElseThrow();
        assertEquals(SatelliteManager.initialMissionId(identity.satelliteId()), mission.missionId());
        assertEquals(identity.satelliteId(), mission.satelliteId()); assertEquals(identity.ownerId(), mission.ownerId());
        assertEquals(identity.definitionId(), mission.definitionId()); assertEquals(EARTH, mission.targetBodyId());
        assertTrue(mission.discoveryRequired());
        satellites.clear(); celestial.clear();
        assertEquals(SatelliteOperationCode.IDEMPOTENT, service.register(data, validation.request().orElseThrow(), 43,
                body -> { fail("D2 revalidation took the fresh branch over a matching identity"); return false; }).code());
        assertEquals(1, data.missions().size());
        assertEquals(SatelliteOperationCode.SERVER_ERROR, service.register(SatelliteMissionSavedData.create(0),
                validation.request().orElseThrow(), 44, b -> false).code());
    }

    @Test void absentDataStillRequiresCatalogAllowedKnownTargetAndAdmission() throws Exception {
        var data = SatelliteMissionSavedData.create(0); var identity = identity(SatelliteKind.DATA);
        assertEquals(SatelliteOperationCode.CATALOG_UNAVAILABLE, service.validate(data, identity.ownerId(), identity, EARTH, b -> false).result().code());
        catalogs("normal");
        assertEquals(SatelliteOperationCode.TARGET_NOT_ALLOWED, service.validate(data, identity.ownerId(), identity, null, b -> false).result().code());
        var accepted = service.validate(data, identity.ownerId(), identity, EARTH, b -> false).request().orElseThrow();
        satellites.clear();
        assertEquals(SatelliteOperationCode.CATALOG_UNAVAILABLE, service.register(data, accepted, 1, b -> false).code());
        assertTrue(data.satellites().isEmpty());
        catalogs("normal"); seed(data, identity(SatelliteKind.DATA));
        data.applyLimits(new RegistryLimits(1, 1, 1, 1, 1, 1, 1, 1, 10, 2));
        assertEquals(SatelliteOperationCode.CAPACITY_REACHED,
                service.validate(data, identity.ownerId(), identity, EARTH, b -> false).result().code());
    }

    @Test void freshNonDataKeepsDefinitionComponentResearchAndOrbitValidationAndFixedOrbit() throws Exception {
        catalogs("normal");
        for (var definition : satellites.current().orElseThrow().kindDefinitions()) {
            var data = SatelliteMissionSavedData.create(0);
            var identity = new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), definition.id(), definition.kind(),
                    List.of(ModIdentity.id("satellite_chassis"), definition.primaryComponent(), ModIdentity.id("satellite_solar_module"),
                            ModIdentity.id("data_storage_unit"), ModIdentity.id("satellite_battery"), ModIdentity.id("satellite_cargo_hold")));
            ResourceLocation target = definition.launchTargets().get(0);
            assertEquals(SatelliteOperationCode.TARGET_NOT_ALLOWED,
                    service.validate(data, identity.ownerId(), identity, ModIdentity.id("removed"), b -> true).result().code());
            var validation = service.validate(data, identity.ownerId(), identity, target, b -> true);
            assertEquals(SatelliteOperationCode.SUCCESS, validation.result().code(), definition.id().toString());
            assertTrue(data.satellites().isEmpty());
            var registered = service.register(data, validation.request().orElseThrow(), 3, b -> true);
            assertEquals(SatelliteOperationCode.SUCCESS, registered.code());
            assertEquals(Optional.of(target), registered.satellite().orElseThrow().orbitBody());
            assertTrue(registered.mission().isEmpty()); assertTrue(data.missions().isEmpty());
            var fresh = new SatelliteIdentity(UUID.randomUUID(), identity.ownerId(), definition.id(), identity.kind(), identity.components());
            catalogs("research");
            assertEquals(SatelliteOperationCode.RESEARCH_LOCKED, service.validate(data, fresh.ownerId(), fresh, target, b -> true).result().code());
            catalogs("component");
            assertEquals(SatelliteOperationCode.INVALID_COMPONENTS, service.validate(data, fresh.ownerId(), fresh, target, b -> true).result().code());
            catalogs("primary");
            assertEquals(SatelliteOperationCode.DEFINITION_NOT_FOUND, service.validate(data, fresh.ownerId(), fresh, target, b -> true).result().code());
            catalogs("normal");
        }
    }

    private void catalogs(String change) throws Exception {
        satellites.clear(); celestial.clear();
        if (change.equals("catalog")) { return; }
        var bodies = new ArrayList<CelestialBodyDefinition>(PlanetaryContent.definitions());
        Path v03 = Path.of("src/generated/v0.3/resources/data/advancedrocketrycommunity/celestial_bodies");
        for (String name : List.of("earth", "moon", "space")) {
            bodies.add(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(v03.resolve(name + ".json"))))
                    .getOrThrow(false, message -> { throw new AssertionError(message); }));
        }
        assertTrue(celestial.applyCandidate(CelestialCatalog.create(bodies)));
        var components = new ArrayList<SatelliteComponentDefinition>();
        Path v16 = Path.of("src/generated/v1.6/resources/data/advancedrocketrycommunity");
        try (var paths = Files.list(v16.resolve("satellite_components"))) {
            for (Path path : paths.toList()) {
                var component = SatelliteComponentDefinition.decode(JsonParser.parseString(Files.readString(path)));
                if (change.equals("component") && component.role() == SatelliteComponentRole.BATTERY) { continue; }
                if (change.equals("primary") && component.role() == SatelliteComponentRole.PRIMARY) { continue; }
                if (change.equals("stats") && component.role() == SatelliteComponentRole.POWER) {
                    component = new SatelliteComponentDefinition(component.item(), component.role(), component.kind(), 1);
                }
                components.add(component);
            }
        }
        var kinds = new ArrayList<SatelliteKindDefinition>();
        if (!change.equals("primary") && !change.equals("definition")) {
            try (var paths = Files.list(v16.resolve("satellite_definitions"))) {
                for (Path path : paths.toList()) {
                    var kind = SatelliteKindDefinition.decode(JsonParser.parseString(Files.readString(path)));
                    if (change.equals("research")) {
                        kind = new SatelliteKindDefinition(kind.id(), kind.kind(), kind.primaryComponent(), 100000, kind.launchTargets(), kind.parameters());
                    }
                    kinds.add(kind);
                }
            }
        }
        var definition = new SatelliteDefinition(1, ModIdentity.id("data_satellite"), 200, 120, 100,
                change.equals("definition") ? List.of(MOON) : List.of(EARTH, MOON));
        var componentCatalog = SatelliteComponentCatalog.create(components, id -> true).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertTrue(satellites.applyCandidate(SatelliteCatalog.create(List.of(definition), kinds,
                bodies.stream().map(CelestialBodyDefinition::id).toList(), componentCatalog)));
        if (change.equals("body")) { celestial.clear(); }
    }

    private static SatelliteIdentity identity(SatelliteKind kind) {
        ResourceLocation definition = ModIdentity.id(kind == SatelliteKind.DATA ? "data_satellite" : kind.id() + "_satellite");
        return new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), definition, kind,
                kind == SatelliteKind.DATA ? List.of() : List.of(ModIdentity.id("removed_chassis"), ModIdentity.id("removed_primary")));
    }
    private static void seed(SatelliteMissionSavedData data, SatelliteIdentity identity) {
        if (identity.kind() == SatelliteKind.DATA) {
            var definition = new SatelliteDefinition(1, identity.definitionId(), 200, 120, 100, List.of(EARTH));
            assertTrue(data.launch(identity.satelliteId(), SatelliteManager.initialMissionId(identity.satelliteId()), identity.ownerId(), definition, EARTH, 0, false).success());
        } else {
            SatelliteKindState state = switch (identity.kind()) {
                case SOLAR -> new SatelliteKindState.Solar(100, Optional.empty());
                case SURVEY -> new SatelliteKindState.Survey(0, 0, 1000, 32, 8);
                default -> new SatelliteKindState.Plain(identity.kind());
            };
            assertTrue(data.launchIdle(time -> SatelliteState.launchIdle(identity.satelliteId(), identity.definitionId(),
                    identity.ownerId(), time, EARTH, new SatelliteBlueprint(identity.components(), false,
                            new SatelliteStats(4, 10720, 1000, 1, 10)), state), 0).success());
        }
    }
}
