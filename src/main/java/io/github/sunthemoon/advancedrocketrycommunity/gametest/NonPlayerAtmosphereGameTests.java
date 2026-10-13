package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.NonPlayerAtmosphere;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.NonPlayerAtmosphere.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.NonPlayerAtmosphereService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModDamageTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 section 3.3 W34: non-player atmosphere effects, the bypass tag and vacuumDamage. Spawn admission is in
 * {@link AtmosphereSpawnAdmissionGameTests}, which shares this class's fixture.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NonPlayerAtmosphereGameTests {
    private static final String TEMPLATE = "atmosphere_test";
    static final double DAMAGE = 2.0D;
    private static final String VACUUM = ModIdentity.MOD_ID + ".vacuum";
    private static final String COLD = ModIdentity.MOD_ID + ".planetary_cold";
    private static final String PRESSURE = ModIdentity.MOD_ID + ".planetary_pressure";

    private NonPlayerAtmosphereGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void vacuumZombieTakesOneAttemptPerIntervalWithoutDoublingThePlayerPath(GameTestHelper helper) {
        Fixture fixture = new Fixture(vacuumEarth());
        NonPlayerAtmosphereService service = fixture.service(type -> false, DAMAGE);
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.IRON_BLOCK);
        Zombie zombie = sheltered(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3)));
        // Last observed entity tick, scheduled attempts and observed entity ticks; one callback, no nested scheduling.
        int[] state = {Integer.MIN_VALUE, 0, 0};
        helper.onEachTick(() -> {
            int tick = zombie.tickCount;
            if (tick == state[0]) {
                return;
            }
            state[0] = tick;
            state[2]++;
            float before = zombie.getHealth();
            NonPlayerAtmosphere.Attempt attempt = service.tick(zombie);
            boolean scheduled = NonPlayerAtmosphere.scheduled(tick, zombie.getId());
            helper.assertTrue(attempt.kind() == (scheduled ? Kind.VACUUM : Kind.NONE),
                    "Wrong attempt " + attempt + " at entity tick " + tick);
            helper.assertTrue(before - zombie.getHealth() == (scheduled ? 2.0F : 0.0F),
                    "Not exactly one 2-point vacuum attempt at entity tick " + tick + ": " + before + " -> "
                            + zombie.getHealth() + " source=" + zombie.getLastDamageSource());
            if (scheduled && ++state[1] == 3) {
                helper.assertTrue(state[2] <= 3 * NonPlayerAtmosphere.INTERVAL,
                        "Three attempts needed " + state[2] + " entity ticks");
                playersStayOnThePlayerPath(helper, fixture, service, zombie);
                zombie.discard();
                fixture.close();
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void bypassTypeIgnoresAtmosphereButStillBurnsAndDrowns(GameTestHelper helper) {
        Fixture fixture = new Fixture(vacuumEarth());
        NonPlayerAtmosphereService service = fixture.service(type -> type == EntityType.PIG, DAMAGE);
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.IRON_BLOCK);
        helper.setBlock(new BlockPos(6, 0, 3), Blocks.IRON_BLOCK);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 3));
        Zombie control = sheltered(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(6, 1, 3)));
        BlockPos pool = helper.absolutePos(buildPool(helper, new BlockPos(9, 0, 9)));
        float full = pig.getMaxHealth();
        // Phase (0 atmosphere, 1 fire, 2 drowning), last pig tick, last control tick, control attempts.
        int[] state = {0, Integer.MIN_VALUE, Integer.MIN_VALUE, 0};
        helper.onEachTick(() -> {
            if (pig.tickCount != state[1]) {
                state[1] = pig.tickCount;
                helper.assertTrue(service.tick(pig) == NonPlayerAtmosphere.Attempt.NONE,
                        "A bypass type took an atmosphere attempt at entity tick " + pig.tickCount);
            }
            DamageSource source = pig.getLastDamageSource();
            helper.assertTrue(source == null || !source.is(ModDamageTypes.VACUUM), "A bypass type took vacuum damage");
            if (state[0] == 0) {
                if (control.tickCount != state[2]) {
                    state[2] = control.tickCount;
                    if (service.tick(control).kind() == Kind.VACUUM) {
                        state[3]++;
                    }
                }
                if (state[3] == 2) {
                    helper.assertTrue(pig.getHealth() == full && control.getHealth() == control.getMaxHealth() - 4.0F,
                            "Bypass/control health differs: pig=" + pig.getHealth() + " control=" + control.getHealth());
                    control.discard();
                    pig.setSecondsOnFire(4);
                    state[0] = 1;
                }
            } else if (state[0] == 1) {
                if (source != null && source.is(DamageTypes.ON_FIRE)) {
                    helper.assertTrue(pig.getHealth() < full, "Fire did not damage the bypass type");
                    pig.clearFire();
                    pig.teleportTo(pool.getX() + 0.5D, pool.getY(), pool.getZ() + 0.5D);
                    pig.setAirSupply(0);
                    state[0] = 2;
                }
            } else if (source != null && source.is(DamageTypes.DROWN)) {
                pig.discard();
                fixture.close();
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void bypassTagIsEmptyByDefaultAndTheDefaultServiceUsesIt(GameTestHelper helper) {
        helper.assertTrue(NonPlayerAtmosphereService.ATMOSPHERE_BYPASS.location().equals(ModIdentity.id("atmosphere_bypass")),
                "Unexpected bypass tag identity");
        var tag = BuiltInRegistries.ENTITY_TYPE.getTag(NonPlayerAtmosphereService.ATMOSPHERE_BYPASS);
        helper.assertTrue(tag.isPresent() && tag.orElseThrow().size() == 0, "The generated bypass tag is missing or not empty");
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.stream().noneMatch(type -> type.is(NonPlayerAtmosphereService.ATMOSPHERE_BYPASS)),
                "An entity type is tagged by default");
        try (Fixture fixture = new Fixture(ordinaryEarth())) {
            ProbeZombie zombie = probe(level(helper, CelestialIds.MOON_LEVEL));
            helper.assertTrue(attempts(new NonPlayerAtmosphereService(fixture.atmosphere), zombie, 20).equals(List.of(VACUUM)),
                    "The default service did not apply vacuum to an untagged type: " + zombie.attempts);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void invulnerableDeadAndArmorStandEntitiesAreLeftAlone(GameTestHelper helper) {
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        try (Fixture fixture = new Fixture(ordinaryEarth())) {
            NonPlayerAtmosphereService service = fixture.service(type -> false, DAMAGE);
            ProbeZombie invulnerable = probe(moon);
            invulnerable.setInvulnerable(true);
            ProbeZombie dead = probe(moon);
            dead.setHealth(0.0F);
            helper.assertTrue(attempts(service, invulnerable, 40).isEmpty() && attempts(service, dead, 40).isEmpty(),
                    "An invulnerable or dead entity took an atmosphere attempt");
            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, moon);
            stand.setPos(0.5D, 250.0D, 0.5D);
            for (int tick = 0; tick < NonPlayerAtmosphere.INTERVAL; tick++) {
                stand.tickCount = tick;
                helper.assertTrue(service.tick(stand) == NonPlayerAtmosphere.Attempt.NONE, "An armor stand took an attempt");
            }
            helper.assertTrue(stand.isAlive() && !stand.isRemoved(), "Vacuum removed an armor stand");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void zeroVacuumDamageStopsDamageButNotOxygenUseOrExposure(GameTestHelper helper) {
        ServerLevel mars = level(helper, PlanetaryContent.level(PlanetaryContent.MARS));
        ServerLevel venus = level(helper, PlanetaryContent.level(PlanetaryContent.VENUS));
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        try (Fixture fixture = new Fixture(ordinaryEarth())) {
            NonPlayerAtmosphereService standard = fixture.service(type -> false, DAMAGE);
            NonPlayerAtmosphereService zero = fixture.service(type -> false, 0.0D);
            ProbeZombie suffocating = probe(mars);
            helper.assertTrue(attempts(standard, suffocating, 40).equals(List.of(VACUUM, VACUUM))
                            && suffocating.getHealth() == 16.0F,
                    "Mars did not take one vacuum attempt per interval: " + suffocating.attempts + " " + suffocating.getHealth());
            ProbeZombie exposed = probe(mars);
            helper.assertTrue(attempts(zero, exposed, 40).equals(List.of(COLD, COLD)),
                    "Zero vacuum damage stopped Mars exposure: " + exposed.attempts);
            ProbeZombie pressured = probe(venus);
            helper.assertTrue(attempts(zero, pressured, 20).equals(List.of(PRESSURE)),
                    "Zero vacuum damage changed the exposure priority: " + pressured.attempts);
            ProbeZombie moonZombie = probe(moon);
            helper.assertTrue(attempts(zero, moonZombie, 40).isEmpty(), "Zero vacuum damage still damaged on the Moon");

            List<PlayerLifeSupportSnapshot> snapshots = new ArrayList<>();
            PlayerLifeSupportService life = new PlayerLifeSupportService(fixture.atmosphere,
                    (ignored, snapshot) -> snapshots.add(snapshot), new SuitEquipmentService(SuitEquipmentCatalog.empty()),
                    () -> 0.0D);
            try {
                var suited = new PlanetaryExposureGameTests.ProbePlayer(mars);
                suited.setGameMode(GameType.SURVIVAL);
                suited.setPos(0.5D, 250.0D, 0.5D);
                PlanetaryExposureGameTests.suit(suited);
                ItemStack chest = suited.getItemBySlot(EquipmentSlot.CHEST);
                helper.assertTrue(SpaceSuitOxygen.set(chest, 5), "Could not seed suit oxygen");
                for (int tick = 0; tick < NonPlayerAtmosphere.INTERVAL; tick++) {
                    life.tickPlayer(suited);
                }
                helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 4 && suited.attempts == 0,
                        "Zero vacuum damage changed suit oxygen use");
                var bare = new PlanetaryExposureGameTests.ProbePlayer(mars);
                bare.setGameMode(GameType.SURVIVAL);
                bare.setPos(0.5D, 250.0D, 0.5D);
                for (int tick = 0; tick < NonPlayerAtmosphere.INTERVAL; tick++) {
                    life.tickPlayer(bare);
                }
                PlayerLifeSupportSnapshot last = snapshots.get(snapshots.size() - 1);
                helper.assertTrue(bare.attempts == 1 && bare.lastDamage.equals(COLD)
                                && last.status() == PlayerProtectionStatus.EXPOSED
                                && last.breathability() == BreathabilityState.VACUUM,
                        "Zero vacuum damage changed player breathability or exposure: " + bare.attempts + " "
                                + bare.lastDamage + " " + last);
            } finally {
                life.clear();
            }
        }
        helper.succeed();
    }

    private static void playersStayOnThePlayerPath(GameTestHelper helper, Fixture fixture,
                                                   NonPlayerAtmosphereService service, Zombie zombie) {
        var player = new PlanetaryExposureGameTests.ProbePlayer(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(zombie.getX(), zombie.getY(), zombie.getZ());
        PlayerLifeSupportService life = new PlayerLifeSupportService(fixture.atmosphere, (ignored, snapshot) -> { },
                new SuitEquipmentService(SuitEquipmentCatalog.empty()), () -> DAMAGE);
        try {
            for (int tick = 0; tick < NonPlayerAtmosphere.INTERVAL; tick++) {
                player.tickCount = tick;
                life.tickPlayer(player);
                service.onLivingTick(new LivingEvent.LivingTickEvent(player));
            }
            helper.assertTrue(player.attempts == 1 && player.lastDamage.equals(VACUUM),
                    "A player in vacuum did not take exactly one player-path attempt: " + player.attempts);
            boolean rejected = false;
            try {
                service.tick(player);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "The non-player service accepted a player");
        } finally {
            life.clear();
        }
    }

    /** A glass cup with two stacked water sources; returns the relative cell a submerged entity stands in. */
    private static BlockPos buildPool(GameTestHelper helper, BlockPos corner) {
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = 0; z <= 2; z++) {
                    boolean wall = x != 1 || z != 1 || y == 0;
                    BlockPos position = corner.offset(x, y, z);
                    helper.setBlock(position, wall ? Blocks.GLASS : y <= 2 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        return corner.offset(1, 1, 1);
    }

    private static Zombie sheltered(Zombie zombie) {
        // An undamageable head item stops sunburn without armor or a fire source.
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        zombie.setPersistenceRequired();
        return zombie;
    }

    private static ProbeZombie probe(ServerLevel level) {
        ProbeZombie zombie = new ProbeZombie(level);
        zombie.setPos(0.5D, 250.0D, 0.5D);
        return zombie;
    }

    /** Native, unenrolled entity ticks: only the lifecycle tick counter advances, one service call per tick. */
    private static List<String> attempts(NonPlayerAtmosphereService service, ProbeZombie zombie, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            zombie.tickCount++;
            zombie.invulnerableTime = 0;
            service.tick(zombie);
        }
        return zombie.attempts;
    }

    static ServerLevel level(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        helper.assertTrue(level != null, "Missing Level " + key);
        return level;
    }

    static CelestialBodyDefinition ordinaryEarth() {
        return CelestialDefaults.definitions().get(0);
    }

    static CelestialBodyDefinition vacuumEarth() {
        return earth(new AtmosphereDefinition(0.0D, false, 288.0D, ModIdentity.id("vacuum")), false);
    }

    /** Breathable but above 330 K, with ADR-034 effects opted in: a heat hazard outside supplied rooms. */
    static CelestialBodyDefinition hostileEarth() {
        return earth(new AtmosphereDefinition(1.0D, true, 400.0D, ModIdentity.id("earth")), true);
    }

    private static CelestialBodyDefinition earth(AtmosphereDefinition atmosphere, boolean effects) {
        CelestialBodyDefinition earth = ordinaryEarth();
        return new CelestialBodyDefinition(earth.id(), earth.parentId(), earth.levelKey(), earth.gravityMultiplier(),
                atmosphere, earth.orbit(), earth.visualProfile(), earth.capabilities(), earth.solarIntensity(),
                earth.radiation(), effects, earth.discoveryRequired());
    }

    /** A private atmosphere authority over a test catalog; it is never installed into the runtime. */
    static final class Fixture implements AutoCloseable {
        private final CelestialCatalogManager catalogs = new CelestialCatalogManager();
        final AtmosphereManager atmosphere = new AtmosphereManager(new CelestialEnvironmentService(catalogs));

        Fixture(CelestialBodyDefinition earth) {
            List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
            bodies.set(0, earth);
            bodies.addAll(PlanetaryContent.definitions());
            if (!catalogs.applyCandidate(CelestialCatalog.create(bodies))) {
                throw new IllegalStateException("Bad W34 fixture catalog");
            }
        }

        NonPlayerAtmosphereService service(Predicate<EntityType<?>> bypass, double damage) {
            return new NonPlayerAtmosphereService(atmosphere, bypass, () -> damage);
        }

        @Override
        public void close() {
            atmosphere.clear();
            catalogs.clear();
        }
    }

    private static final class ProbeZombie extends Zombie {
        private final List<String> attempts = new ArrayList<>();

        private ProbeZombie(Level level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            attempts.add(source.getMsgId());
            return super.hurt(source, amount);
        }
    }
}
