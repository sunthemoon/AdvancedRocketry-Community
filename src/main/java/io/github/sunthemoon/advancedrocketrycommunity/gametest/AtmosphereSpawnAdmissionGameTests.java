package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.NonPlayerAtmosphere;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.NonPlayerAtmosphere.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.NonPlayerAtmosphereService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.NonPlayerAtmosphereGameTests.Fixture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 section 3.3 W34 spawn admission: natural, chunk-generation and spawner position checks, and the spawn-egg
 * and command exceptions, which never post a position check and are not natural-spawn acceptance.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereSpawnAdmissionGameTests {
    private static final String TEMPLATE = "atmosphere_test";
    private static final double DAMAGE = NonPlayerAtmosphereGameTests.DAMAGE;
    private static final BlockPos FAR = new BlockPos(25_000_000, 80, 25_000_000);

    private AtmosphereSpawnAdmissionGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void naturalAndSpawnerChecksDenyVacuumButAdmitBypassAndSuppliedRooms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos open = helper.absolutePos(new BlockPos(12, 1, 3));
        BlockPos inside = helper.absolutePos(new BlockPos(3, 2, 3));
        AtmosphereGameTests.buildShell(helper, new BlockPos(1, 1, 1), new BlockPos(5, 5, 5));
        OxygenVentBlockEntity vent = AtmosphereGameTests.placePreparedVent(helper, new BlockPos(3, 1, 3));
        Predicate<EntityType<?>> pigs = type -> type == EntityType.PIG;
        try (Fixture vacuum = new Fixture(NonPlayerAtmosphereGameTests.vacuumEarth())) {
            NonPlayerAtmosphereService service = vacuum.service(pigs, DAMAGE);
            for (MobSpawnType type : List.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER, MobSpawnType.CHUNK_GENERATION)) {
                helper.assertTrue(check(service, EntityType.ZOMBIE, level, open, type) == Event.Result.DENY,
                        "Vacuum admitted a " + type + " spawn");
                helper.assertTrue(check(service, EntityType.PIG, level, open, type) == Event.Result.DEFAULT,
                        "A bypass type was denied a " + type + " spawn");
            }
            for (MobSpawnType type : List.of(MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND, MobSpawnType.BREEDING,
                    MobSpawnType.MOB_SUMMONED)) {
                helper.assertTrue(check(service, EntityType.ZOMBIE, level, open, type) == Event.Result.DEFAULT,
                        "The natural policy judged a " + type + " check");
            }
            BlockPos high = new BlockPos(open.getX(), level.getMaxBuildHeight() + 4, open.getZ());
            helper.assertTrue(!level.hasChunkAt(FAR), "The far column was loaded before the check");
            helper.assertTrue(check(service, EntityType.ZOMBIE, level, high, MobSpawnType.NATURAL) == Event.Result.DENY
                            && check(service, EntityType.ZOMBIE, level, FAR, MobSpawnType.NATURAL) == Event.Result.DENY,
                    "An unavailable eye cell admitted a spawn");
            helper.assertTrue(!level.hasChunkAt(FAR), "Spawn admission loaded a far chunk");

            seal(helper, vacuum.atmosphere, vent);
            helper.assertTrue(vacuum.atmosphere.breathabilityAt(level, inside.above()) == BreathabilityState.BREATHABLE,
                    "The supplied room did not become breathable");
            helper.assertTrue(check(service, EntityType.ZOMBIE, level, inside, MobSpawnType.NATURAL) == Event.Result.DEFAULT
                            && check(service, EntityType.ZOMBIE, level, inside, MobSpawnType.SPAWNER) == Event.Result.DEFAULT,
                    "A supplied sealed room denied a spawn");
            helper.assertTrue(check(service, EntityType.ZOMBIE, level, inside, MobSpawnType.CHUNK_GENERATION)
                    == Event.Result.DENY, "Chunk generation used live room state");
        }
        try (Fixture hostile = new Fixture(NonPlayerAtmosphereGameTests.hostileEarth())) {
            NonPlayerAtmosphereService service = hostile.service(pigs, DAMAGE);
            helper.assertTrue(check(service, EntityType.ZOMBIE, level, open, MobSpawnType.NATURAL) == Event.Result.DENY,
                    "An opted-in hostile profile admitted an unprotected spawn");
            helper.assertTrue(check(service, EntityType.PIG, level, open, MobSpawnType.NATURAL) == Event.Result.DEFAULT,
                    "A bypass type was denied by the hostile profile");
            seal(helper, hostile.atmosphere, vent);
            helper.assertTrue(hostile.atmosphere.controlledAt(level, inside.above()), "The supplied room is not climate controlled");
            helper.assertTrue(check(service, EntityType.ZOMBIE, level, inside, MobSpawnType.NATURAL) == Event.Result.DEFAULT,
                    "A supplied sealed room did not protect a spawn from the hostile profile");
        }
        try (Fixture ordinary = new Fixture(NonPlayerAtmosphereGameTests.ordinaryEarth())) {
            NonPlayerAtmosphereService service = ordinary.service(type -> false, DAMAGE);
            for (MobSpawnType type : List.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER, MobSpawnType.CHUNK_GENERATION)) {
                helper.assertTrue(check(service, EntityType.ZOMBIE, level, open, type) == Event.Result.DEFAULT
                                && check(service, EntityType.ZOMBIE, level, FAR, type) == Event.Result.DEFAULT,
                        "A breathable Level denied a " + type + " spawn");
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void spawnEggAndCommandAreExplicitExceptionsNotNaturalAdmission(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // The registered listener denies a natural check in a production vacuum Level, with no chunk loaded for it.
        ServerLevel moon = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.MOON_LEVEL);
        Zombie candidate = EntityType.ZOMBIE.create(moon);
        BlockPos column = helper.absolutePos(new BlockPos(3, 1, 3));
        candidate.moveTo(column.getX() + 0.5D, 64.0D, column.getZ() + 0.5D, 0.0F, 0.0F);
        MobSpawnEvent.PositionCheck natural = new MobSpawnEvent.PositionCheck(candidate, moon, MobSpawnType.NATURAL, null);
        MinecraftForge.EVENT_BUS.post(natural);
        helper.assertTrue(natural.getResult() == Event.Result.DENY, "The registered listener admitted a natural vacuum spawn");

        List<MobSpawnEvent.PositionCheck> asked = new ArrayList<>();
        Consumer<MobSpawnEvent.PositionCheck> probe = asked::add;
        BlockPos eggFeet = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos summonFeet = helper.absolutePos(new BlockPos(9, 1, 3));
        AABB area = new AABB(eggFeet).minmax(new AABB(summonFeet)).inflate(2.0D);
        try (Fixture fixture = new Fixture(NonPlayerAtmosphereGameTests.vacuumEarth())) {
            NonPlayerAtmosphereService service = fixture.service(type -> false, DAMAGE);
            helper.assertTrue(check(service, EntityType.ZOMBIE, level, eggFeet, MobSpawnType.NATURAL) == Event.Result.DENY
                            && check(service, EntityType.ZOMBIE, level, summonFeet, MobSpawnType.NATURAL) == Event.Result.DENY,
                    "The vacuum fixture admitted a natural spawn at the exception cells");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MobSpawnEvent.PositionCheck.class, probe);
            BlockPos clicked = eggFeet.below();
            level.setBlockAndUpdate(clicked, Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(summonFeet.below(), Blocks.IRON_BLOCK.defaultBlockState());
            FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ARCEW34SpawnEgg"));
            ItemStack egg = new ItemStack(Items.ZOMBIE_SPAWN_EGG);
            player.setItemInHand(InteractionHand.MAIN_HAND, egg);
            InteractionResult used = egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked).add(0.0D, 0.5D, 0.0D), Direction.UP, clicked, false)));
            List<Zombie> eggs = level.getEntitiesOfClass(Zombie.class, new AABB(eggFeet).inflate(1.0D));
            helper.assertTrue(used.consumesAction() && eggs.size() == 1 && egg.isEmpty(),
                    "The spawn egg did not place exactly one zombie in vacuum: " + used + " " + eggs.size());
            int summoned = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level)
                    .withPermission(4).withSuppressedOutput(), "summon minecraft:zombie " + summonFeet.getX() + " "
                    + summonFeet.getY() + " " + summonFeet.getZ());
            List<Zombie> commands = level.getEntitiesOfClass(Zombie.class, new AABB(summonFeet).inflate(1.0D));
            helper.assertTrue(summoned == 1 && commands.size() == 1, "The command did not summon exactly one zombie in vacuum");
            helper.assertTrue(asked.isEmpty(), "A spawn egg or command consulted the natural position check: " + asked.size());
            // Neither is an atmosphere acceptance: both take the vacuum attempt on their next scheduled tick.
            for (Zombie zombie : List.of(eggs.get(0), commands.get(0))) {
                zombie.tickCount = Math.floorMod(-zombie.getId(), NonPlayerAtmosphere.INTERVAL);
                helper.assertTrue(service.tick(zombie).kind() == Kind.VACUUM, "An egg or command spawn was exempt from vacuum");
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            level.getEntitiesOfClass(Mob.class, area).forEach(Entity::discard);
        }
        helper.succeed();
    }

    private static Event.Result check(NonPlayerAtmosphereService service, EntityType<? extends Mob> type,
                                      ServerLevel level, BlockPos feet, MobSpawnType spawnType) {
        Mob mob = type.create(level);
        if (mob == null) {
            throw new IllegalStateException("Could not create " + type);
        }
        mob.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0.0F, 0.0F);
        MobSpawnEvent.PositionCheck event = new MobSpawnEvent.PositionCheck(mob, level, spawnType, null);
        service.onPositionCheck(event);
        return event.getResult();
    }

    /** Observe and scan the room in the same tick, like AtmosphereGameTests, so no other manager re-elects it first. */
    private static void seal(GameTestHelper helper, AtmosphereManager atmosphere, OxygenVentBlockEntity vent) {
        atmosphere.observeVent(helper.getLevel(), vent);
        atmosphere.tick(helper.getLevel().getServer());
        helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE, "The supplied room did not seal: " + vent.status());
    }
}
