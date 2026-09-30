package io.github.sunthemoon.advancedrocketrycommunity.station.elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointValidator.Request;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointValidator.Snapshot;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointValidator.StationView;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-045: each rule, in order, on the pure validator. */
final class ElevatorEndpointValidatorTest {
    private static final UUID STATION = new UUID(45, 1);
    private static final UUID OWNER = new UUID(45, 2);
    private static final UUID MEMBER = new UUID(45, 3);
    // Instance fields: CelestialIds must not be initialized before the bootstrap below.
    private final StationView earthStation = new StationView(STATION, OWNER, CelestialIds.EARTH_ID, 8, 8);
    private final Request earthColumn = new Request(STATION, CelestialIds.EARTH_ID, 100, -100);
    private static final Predicate<ResourceKey<Level>> ALL_LEVELS = level -> true;
    private static final ElevatorEndpointValidator.BorderCheck INSIDE = (level, x, z) -> true;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void aValidPairResolvesTheBodysOwnLevel() {
        var result = ElevatorEndpointValidator.validate(earthColumn, snapshot(true, OWNER, false, catalog(defaults())));
        assertTrue(result.valid());
        assertEquals(earth().levelKey(), result.level());
        assertEquals(Optional.of(earthStation), result.station());
        assertTrue(ElevatorEndpointValidator.validate(earthColumn,
                snapshot(true, MEMBER, true, catalog(defaults()))).valid(), "An operator may check any station");
    }

    @Test
    void rulesAreCheckedInOrderAndTheFirstFailureIsReported() {
        // Every later rule fails too, so each result proves its rule comes first.
        Snapshot allBad = new Snapshot(false, Optional.of(earthStation), MEMBER, false, Optional.empty(),
                level -> false, (level, x, z) -> false);
        Request wrongBody = new Request(STATION, CelestialIds.MOON_ID, 40_000_000, 0);
        assertEquals(ElevatorEndpointCode.REGISTRY_UNAVAILABLE, code(wrongBody, allBad));
        Snapshot available = with(allBad, true, Optional.empty(), MEMBER, false);
        assertEquals(ElevatorEndpointCode.STATION_MISSING, code(wrongBody, available));
        Snapshot unauthorized = with(allBad, true, Optional.of(earthStation), MEMBER, false);
        var hidden = ElevatorEndpointValidator.validate(wrongBody, unauthorized);
        assertEquals(ElevatorEndpointCode.UNAUTHORIZED, hidden.code());
        assertTrue(hidden.station().isEmpty(), "Rule 2 disclosed the station");
        Snapshot owner = with(allBad, true, Optional.of(earthStation), OWNER, false);
        assertEquals(ElevatorEndpointCode.NOT_CURRENT_ORBIT, code(wrongBody, owner));
        Request earthOutOfRange = new Request(STATION, CelestialIds.EARTH_ID, 40_000_000, 0);
        assertEquals(ElevatorEndpointCode.CATALOG_UNAVAILABLE, code(earthOutOfRange, owner));
        Snapshot catalog = new Snapshot(true, Optional.of(earthStation), OWNER, false,
                Optional.of(catalog(defaults())), level -> false, (level, x, z) -> false);
        assertEquals(ElevatorEndpointCode.LEVEL_ABSENT, code(earthOutOfRange, catalog));
        Snapshot present = new Snapshot(true, Optional.of(earthStation), OWNER, false,
                Optional.of(catalog(defaults())), ALL_LEVELS, (level, x, z) -> false);
        assertEquals(ElevatorEndpointCode.OUT_OF_RANGE, code(earthOutOfRange, present));
        assertEquals(ElevatorEndpointCode.OUTSIDE_WORLD_BORDER, code(earthColumn, present));
    }

    @Test
    void anUnknownStationIsMissingEvenForAnOperator() {
        Request unknown = new Request(new UUID(45, 99), CelestialIds.EARTH_ID, 0, 0);
        assertEquals(ElevatorEndpointCode.STATION_MISSING, code(unknown, snapshot(true, OWNER, true,
                catalog(defaults()))));
        assertEquals(ElevatorEndpointCode.STATION_MISSING, code(earthColumn, new Snapshot(true, Optional.empty(),
                OWNER, true, Optional.of(catalog(defaults())), ALL_LEVELS, INSIDE)));
    }

    @Test
    void aBodyThatIsMissingOrNotLandableHasNoSurface() {
        List<CelestialBodyDefinition> notLandable = defaults();
        notLandable.set(earthIndex(notLandable), capabilities(earth(), false));
        assertEquals(ElevatorEndpointCode.NO_SURFACE, code(earthColumn,
                snapshot(true, OWNER, false, catalog(notLandable))));
        ResourceLocation removed = ModIdentity.id("removed_body");
        StationView orphan = new StationView(STATION, OWNER, removed, 8, 8);
        assertEquals(ElevatorEndpointCode.NO_SURFACE, code(new Request(STATION, removed, 0, 0),
                new Snapshot(true, Optional.of(orphan), OWNER, false, Optional.of(catalog(defaults())), ALL_LEVELS,
                        INSIDE)));
    }

    @Test
    void aRemappedOrSharedLevelIsRejected() {
        ResourceKey<Level> remapped = ResourceKey.create(Registries.DIMENSION, ModIdentity.id("remapped_earth"));
        List<CelestialBodyDefinition> moved = defaults();
        CelestialBodyDefinition earth = earth();
        moved.set(earthIndex(moved), new CelestialBodyDefinition(earth.id(), earth.parentId(), Optional.of(remapped),
                earth.gravityMultiplier(), earth.atmosphere(), earth.orbit(), earth.visualProfile(),
                earth.capabilities(), 1, 0));
        Snapshot onlyOverworld = new Snapshot(true, Optional.of(earthStation), OWNER, false,
                Optional.of(catalog(moved)), level -> level.equals(Level.OVERWORLD), INSIDE);
        assertEquals(ElevatorEndpointCode.LEVEL_ABSENT, code(earthColumn, onlyOverworld));

        List<CelestialBodyDefinition> shared = defaults();
        shared.add(new CelestialBodyDefinition(ModIdentity.id("alias_earth"), earth.parentId(),
                earth.levelKey().orElseThrow(), earth.gravityMultiplier(), earth.atmosphere(), earth.orbit(),
                earth.visualProfile()));
        assertEquals(ElevatorEndpointCode.LEVEL_SHARED, code(earthColumn,
                snapshot(true, OWNER, false, catalog(shared))));
    }

    @Test
    void coordinatesAreBoundedInclusivelyAndTheBorderIsAskedForTheBodysLevel() {
        Snapshot valid = snapshot(true, OWNER, false, catalog(defaults()));
        int max = ElevatorEndpointValidator.MAX_COORDINATE;
        assertTrue(ElevatorEndpointValidator.validate(new Request(STATION, CelestialIds.EARTH_ID, max, -max), valid)
                .valid());
        for (int[] column : new int[][]{{max + 1, 0}, {0, -max - 1}, {Integer.MIN_VALUE, 0}, {0, Integer.MAX_VALUE}}) {
            assertEquals(ElevatorEndpointCode.OUT_OF_RANGE,
                    code(new Request(STATION, CelestialIds.EARTH_ID, column[0], column[1]), valid));
        }
        List<ResourceKey<Level>> asked = new ArrayList<>();
        Snapshot recording = new Snapshot(true, Optional.of(earthStation), OWNER, false,
                Optional.of(catalog(defaults())), ALL_LEVELS, (level, x, z) -> {
                    asked.add(level);
                    return x == 100 && z == -100;
                });
        assertTrue(ElevatorEndpointValidator.validate(earthColumn, recording).valid());
        assertEquals(List.of(earth().levelKey().orElseThrow()), asked);
        assertEquals(ElevatorEndpointCode.OUTSIDE_WORLD_BORDER,
                code(new Request(STATION, CelestialIds.EARTH_ID, 101, -100), recording));
    }

    @Test
    void aWarpToAnotherBodyInvalidatesAPreviouslyValidPair() {
        StationView warped = new StationView(STATION, OWNER, CelestialIds.MOON_ID, 8, 8);
        Snapshot afterWarp = new Snapshot(true, Optional.of(warped), OWNER, false, Optional.of(catalog(defaults())),
                ALL_LEVELS, INSIDE);
        assertEquals(ElevatorEndpointCode.NOT_CURRENT_ORBIT, code(earthColumn, afterWarp));
    }

    private static ElevatorEndpointCode code(Request request, Snapshot snapshot) {
        return ElevatorEndpointValidator.validate(request, snapshot).code();
    }

    private Snapshot snapshot(boolean available, UUID requester, boolean operator, CelestialCatalog catalog) {
        return new Snapshot(available, Optional.of(earthStation), requester, operator, Optional.of(catalog),
                ALL_LEVELS, INSIDE);
    }

    private static Snapshot with(Snapshot base, boolean available, Optional<StationView> station, UUID requester,
                                 boolean operator) {
        return new Snapshot(available, station, requester, operator, base.catalog(), base.levelPresent(),
                base.border());
    }

    private static List<CelestialBodyDefinition> defaults() {
        return new ArrayList<>(CelestialDefaults.definitions());
    }

    private static CelestialBodyDefinition earth() {
        return CelestialDefaults.definitions().stream().filter(body -> body.id().equals(CelestialIds.EARTH_ID))
                .findFirst().orElseThrow();
    }

    private static int earthIndex(List<CelestialBodyDefinition> bodies) {
        for (int index = 0; index < bodies.size(); index++) {
            if (bodies.get(index).id().equals(CelestialIds.EARTH_ID)) {
                return index;
            }
        }
        throw new AssertionError("Earth is missing from the defaults");
    }

    private static CelestialBodyDefinition capabilities(CelestialBodyDefinition body, boolean landable) {
        return new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(), body.gravityMultiplier(),
                body.atmosphere(), body.orbit(), body.visualProfile(),
                new CelestialCapabilities(landable, body.capabilities().orbitable(), false), 1, 0);
    }

    private static CelestialCatalog catalog(List<CelestialBodyDefinition> bodies) {
        return CelestialCatalog.create(bodies).result().orElseThrow();
    }
}
