package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Native passive-content checks, not survival crafting, restart or visual evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermiteTorchGameTests {
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private ThermiteTorchGameTests() { }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 20)
    public static void registeredIdentitiesArePlainMaterialAndNativePairedTorches(GameTestHelper helper) {
        Item dust = item(helper, "thermite"), torch = item(helper, "thermite_torch");
        Block standing = block(helper, "thermite_torch"), wall = block(helper, "thermite_wall_torch");
        helper.assertTrue(dust.getClass() == Item.class && torch.getClass() == StandingAndWallBlockItem.class,
                "Thermite material/item acquired custom behavior");
        helper.assertTrue(standing.getClass() == TorchBlock.class && wall.getClass() == WallTorchBlock.class
                && ((StandingAndWallBlockItem) torch).getBlock() == standing
                && standing.asItem() == torch && wall.asItem() == torch,
                "Native standing/wall item pairing differs");
        helper.assertTrue(!ForgeRegistries.ITEMS.containsKey(id("thermite_wall_torch")), "Wall torch gained an item ID");
        BlockPos pos = centre(helper);
        for (Item value : List.of(dust, torch)) {
            ItemStack stack = new ItemStack(value);
            helper.assertTrue(stack.getMaxStackSize() == 64 && !stack.isDamageableItem() && !stack.hasTag(),
                    "Plain item defaults or payload differ");
        }
        for (Block value : List.of(standing, wall)) {
            BlockState state = value.defaultBlockState();
            helper.assertTrue(!state.hasBlockEntity() && !state.isRandomlyTicking() && !state.isSignalSource()
                    && state.getLightEmission(helper.getLevel(), pos) == 14 && state.getCollisionShape(helper.getLevel(), pos).isEmpty()
                    && state.getDestroySpeed(helper.getLevel(), pos) == 0.0F
                    && state.getSoundType() == SoundType.WOOD && state.getPistonPushReaction() == PushReaction.DESTROY,
                    "Passive torch properties differ");
        }
        helper.assertTrue(standing.defaultBlockState().getProperties().isEmpty()
                && wall.defaultBlockState().getProperties().size() == 1
                && wall.defaultBlockState().hasProperty(WallTorchBlock.FACING), "Native state properties differ");
        helper.assertTrue(standing.getLootTable().equals(id("blocks/thermite_torch"))
                && wall.getLootTable().equals(id("blocks/thermite_wall_torch")), "Distinct native loot IDs differ");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 20)
    public static void survivalUseOnPlacesFloorAndAllFourWalls(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, helper.getLevel(), centre(helper))) {
            FakePlayer player = survival(fixture.level, fixture.pos);
            reset(fixture); fixture.set(fixture.pos.below(), Blocks.IRON_BLOCK.defaultBlockState(), QUIET);
            place(helper, fixture, player, fixture.pos.below(), Direction.UP);
            helper.assertTrue(fixture.level.getBlockState(fixture.pos).is(block(helper, "thermite_torch")), "Floor useOn did not stand");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                reset(fixture); BlockPos support = fixture.pos.relative(facing.getOpposite());
                fixture.set(support, Blocks.IRON_BLOCK.defaultBlockState(), QUIET);
                player.setYRot(facing.getOpposite().toYRot()); player.setXRot(0);
                place(helper, fixture, player, support, facing);
                BlockState state = fixture.level.getBlockState(fixture.pos);
                helper.assertTrue(state.is(block(helper, "thermite_wall_torch")) && state.getValue(WallTorchBlock.FACING) == facing,
                        "Wall useOn facing differs: " + facing);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 20)
    public static void nearestDirectionFallbackAndUnsupportedRefusalsPreserveStacks(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, helper.getLevel(), centre(helper))) {
            FakePlayer player = survival(fixture.level, fixture.pos);
            for (boolean floor : new boolean[] {true, false}) {
                reset(fixture); fixture.set(fixture.pos.above(), Blocks.IRON_BLOCK.defaultBlockState(), QUIET);
                fixture.set(floor ? fixture.pos.below() : fixture.pos.west(), Blocks.IRON_BLOCK.defaultBlockState(), QUIET);
                place(helper, fixture, player, fixture.pos.above(), Direction.DOWN);
                BlockState state = fixture.level.getBlockState(fixture.pos);
                helper.assertTrue(state.is(block(helper, floor ? "thermite_torch" : "thermite_wall_torch")),
                        "Ceiling click did not use an available native fallback");
                if (!floor) { helper.assertTrue(state.getValue(WallTorchBlock.FACING) == Direction.EAST, "Fallback wall facing differs"); }
            }
            for (boolean ceiling : new boolean[] {false, true}) {
                reset(fixture);
                if (ceiling) { fixture.set(fixture.pos.above(), Blocks.IRON_BLOCK.defaultBlockState(), QUIET); }
                ItemStack source = source(helper); player.setItemInHand(InteractionHand.MAIN_HAND, source);
                ItemStack before = source.copy();
                var result = source.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        hit(ceiling ? fixture.pos.above() : fixture.pos, ceiling ? Direction.DOWN : Direction.UP)));
                helper.assertTrue(!result.consumesAction() && fixture.level.isEmptyBlock(fixture.pos)
                        && ItemStack.matches(before, player.getMainHandItem()), "Unsupported useOn wrote a block or debited input");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 20)
    public static void canceledForgePlacementRestoresSurvivalAndCreativeSnapshots(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, helper.getLevel(), centre(helper))) {
            FakePlayer player = survival(fixture.level, fixture.pos);
            reset(fixture); fixture.set(fixture.pos.below(), Blocks.IRON_BLOCK.defaultBlockState(), QUIET);
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
                player.setGameMode(mode); ItemStack source = source(helper), before = source.copy();
                player.setItemInHand(InteractionHand.MAIN_HAND, source);
                var cells = fixture.before.keySet().stream().collect(Collectors.toMap(p -> p, fixture.level::getBlockState));
                AtomicInteger calls = new AtomicInteger();
                Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
                    if (event.getEntity() == player && event.getLevel() == fixture.level && event.getPos().equals(fixture.pos)) {
                        calls.incrementAndGet(); event.setCanceled(true);
                    }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, cancel);
                try { source.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(fixture.pos.below(), Direction.UP))); }
                finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
                helper.assertTrue(calls.get() == 1 && ItemStack.matches(before, player.getMainHandItem())
                        && cells.entrySet().stream().allMatch(e -> fixture.level.getBlockState(e.getKey()).equals(e.getValue()))
                        && fixture.newDrops().isEmpty(), "Canceled native placement failed to restore snapshots: " + mode);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 20)
    public static void nativeRemovalCreativeRemovalAndSupportLossUsePairedLoot(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, helper.getLevel(), centre(helper))) {
            FakePlayer player = survival(fixture.level, fixture.pos);
            for (boolean wall : new boolean[] {false, true}) {
                for (int removal = 0; removal < 3; removal++) {
                    reset(fixture); BlockPos support = wall ? fixture.pos.west() : fixture.pos.below();
                    fixture.set(support, Blocks.IRON_BLOCK.defaultBlockState(), QUIET);
                    player.setGameMode(GameType.SURVIVAL);
                    place(helper, fixture, player, support, wall ? Direction.EAST : Direction.UP);
                    if (removal == 2) { fixture.level.removeBlock(support, false); }
                    else {
                        player.setGameMode(removal == 1 ? GameType.CREATIVE : GameType.SURVIVAL);
                        helper.assertTrue(player.gameMode.destroyBlock(fixture.pos), "Native player removal failed");
                    }
                    helper.assertTrue(fixture.level.isEmptyBlock(fixture.pos), "Native removal left the torch");
                    List<ItemEntity> drops = fixture.newDrops();
                    helper.assertTrue(drops.stream().allMatch(e -> e.getItem().is(item(helper, "thermite_torch")) && !e.getItem().hasTag())
                            && drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == (removal == 1 ? 0 : 1),
                            "Normal/creative/support-loss native drop semantics differ");
                    drops.forEach(ItemEntity::discard);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 20)
    public static void bothNativeLootTablesHaveBoundedSeededExplosionSurvival(GameTestHelper helper) {
        for (String name : List.of("thermite_torch", "thermite_wall_torch")) {
            BlockState state = block(helper, name).defaultBlockState();
            var table = helper.getLevel().getServer().getLootData().getLootTable(id("blocks/" + name));
            int survivors = 0, lost = 0;
            for (int sample = 1; sample <= 64; sample++) {
                long seed = 0x9E3779B97F4A7C15L * sample;
                for (float radius : new float[] {0, 1, 4}) {
                    var builder = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.BLOCK_STATE, state)
                            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(centre(helper)));
                    if (radius > 0) { builder.withParameter(LootContextParams.EXPLOSION_RADIUS, radius); }
                    var params = builder.create(LootContextParamSets.BLOCK);
                    List<ItemStack> stacks = table.getRandomItems(params, seed);
                    helper.assertTrue(stacks.size() <= 1 && stacks.stream().allMatch(s -> s.is(item(helper, "thermite_torch"))
                            && s.getCount() == 1 && !s.hasTag()), "Native torch loot identity/count/payload differs");
                    int count = stacks.stream().mapToInt(ItemStack::getCount).sum();
                    helper.assertTrue(radius == 4 || count == 1, "Ordinary/unit-radius torch loot was lost");
                    helper.assertTrue(sameLoot(stacks, table.getRandomItems(params, seed)),
                            "Seeded native loot was not reproducible");
                    if (radius == 4) { if (count == 1) { survivors++; } else { lost++; } }
                }
            }
            helper.assertTrue(survivors > 0 && lost > 0, "Finite native explosion controls missed an outcome");
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 40)
    public static void darkLoadedFixturePublishesFourteenAndReturnsToBaseline(GameTestHelper helper) {
        startLight(helper, false, false);
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch_disabled", timeoutTicks = 40)
    public static void disabledClassicDevicesStillAllowPassiveNativePlacementAndLight(GameTestHelper helper) {
        startLight(helper, true, false);
    }

    @GameTest(template = "rocket_test", batch = "thermite_torch", timeoutTicks = 40)
    public static void installedMoonAnalyzerRemainsUnsuppliedVacuumWhileTorchIsLit(GameTestHelper helper) {
        startLight(helper, false, true);
    }

    private static void startLight(GameTestHelper helper, boolean disabled, boolean moon) {
        ServerLevel level = moon ? helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL) : helper.getLevel();
        helper.assertTrue(level != null, "Configured Moon Level is absent");
        BlockPos pos = centre(helper);
        if (moon) { ChunkPos chunk = new ChunkPos(pos); pos = new BlockPos(chunk.getMinBlockX() + 8, 180, chunk.getMinBlockZ() + 8); }
        Fixture fixture = new Fixture(helper, level, pos);
        try {
            if (moon) { fixture.pin(); }
            if (disabled) {
                fixture.disabled = true; SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
                helper.assertTrue(!CommonConfig.classicDevicesEnabled(), "Classic-device override was not disabled");
            }
            fixture.darkRoom();
            if (moon) {
                fixture.observer = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), "ThermiteObserver", level, pos.east().below(), new ArrayList<>());
                fixture.observer.setNoGravity(true); fixture.holdObserver();
                fixture.reading = AtmosphereAnalyzerRuntime.read(fixture.observer);
                helper.assertTrue(fixture.reading.state() == AnalyzerReading.State.NON_BREATHABLE && !fixture.reading.supplied()
                        && fixture.reading.bodyId().orElse("").equals(CelestialIds.MOON_ID.toString())
                        && fixture.reading.ambientBodyId().orElse("").equals(CelestialIds.MOON_ID.toString())
                        && fixture.reading.locus().orElse(null) == AnalyzerReading.Locus.SURFACE
                        && fixture.reading.ambient().isPresent() && fixture.reading.ambient().orElseThrow().pressure() == 0.0D,
                        "Installed analyzer did not report present, unsupplied Moon vacuum: " + fixture.reading);
            }
            int[] phase = {0};
            helper.onEachTick(() -> {
                if (fixture.closed) { return; }
                try {
                    fixture.holdObserver(); fixture.assertVacuum();
                    helper.assertTrue(level.hasChunkAt(fixture.pos) && level.hasChunkAt(fixture.pos.east()), "Light fixture unloaded");
                    int centreLight = level.getBrightness(LightLayer.BLOCK, fixture.pos), neighbourLight = level.getBrightness(LightLayer.BLOCK, fixture.pos.east());
                    if (phase[0] == 0 && centreLight == 0 && neighbourLight == 0) {
                        fixture.baseline = List.of(centreLight, neighbourLight);
                        if (disabled || moon) { place(helper, fixture, survival(level, fixture.pos), fixture.pos.below(), Direction.UP); }
                        else { fixture.set(fixture.pos, block(helper, "thermite_torch").defaultBlockState(), Block.UPDATE_ALL); }
                        helper.assertTrue(level.getBlockState(fixture.pos).getLightEmission(level, fixture.pos) == 14 && level.getBlockEntity(fixture.pos) == null,
                                "Loaded torch did not emit fourteen or acquired a BlockEntity");
                        fixture.set(fixture.pos.west(), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        fixture.set(fixture.pos.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        fixture.assertVacuum(); phase[0] = 1;
                    } else if (phase[0] == 1 && centreLight == 14 && neighbourLight == 13) {
                        fixture.assertVacuum(); fixture.set(fixture.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); phase[0] = 2;
                    } else if (phase[0] == 2 && centreLight == fixture.baseline.get(0) && neighbourLight == fixture.baseline.get(1)) {
                        fixture.assertVacuum(); fixture.close(); helper.succeed(); return;
                    }
                    helper.assertTrue(helper.getTick() < 39, "Light publication/removal exceeded unchanged 40 ticks; phase=" + phase[0]
                            + " centre=" + centreLight + " neighbour=" + neighbourLight);
                } catch (RuntimeException | Error failure) { fixture.close(failure); throw failure; }
            });
        } catch (RuntimeException | Error failure) { fixture.close(failure); throw failure; }
    }

    private static boolean sameLoot(List<ItemStack> first, List<ItemStack> second) {
        return first.size() == second.size() && (first.isEmpty() || ItemStack.matches(first.get(0), second.get(0)));
    }
    private static BlockPos centre(GameTestHelper helper) { return helper.absolutePos(new BlockPos(8, 8, 8)); }
    private static ResourceLocation id(String name) { return ResourceLocation.tryParse(ModIdentity.MOD_ID + ":" + name); }
    private static Item item(GameTestHelper helper, String name) {
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(name)), "Literal item ID absent: " + name);
        Item value = ForgeRegistries.ITEMS.getValue(id(name));
        helper.assertTrue(value != null && value != Items.AIR, "Literal item resolved to AIR: " + name); return value;
    }
    private static Block block(GameTestHelper helper, String name) {
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(id(name)), "Literal block ID absent: " + name);
        Block value = ForgeRegistries.BLOCKS.getValue(id(name));
        helper.assertTrue(value != null && value != Blocks.AIR, "Literal block resolved to AIR: " + name); return value;
    }
    private static FakePlayer survival(ServerLevel level, BlockPos pos) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ThermiteFixture"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5); return player;
    }
    private static ItemStack source(GameTestHelper helper) {
        ItemStack source = new ItemStack(item(helper, "thermite_torch"), 4);
        source.getOrCreateTag().putString("thermite_probe", "retained"); return source;
    }
    private static BlockHitResult hit(BlockPos pos, Direction face) { return new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false); }
    private static void place(GameTestHelper helper, Fixture fixture, FakePlayer player, BlockPos support, Direction face) {
        ItemStack source = source(helper), before = source.copy(); player.setItemInHand(InteractionHand.MAIN_HAND, source);
        helper.assertTrue(source.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(support, face))).consumesAction(), "Survival ItemStack.useOn refused supported placement");
        helper.assertTrue(player.getMainHandItem().getCount() == 3 && before.getTag().equals(player.getMainHandItem().getTag())
                && fixture.level.getBlockEntity(fixture.pos) == null, "Survival placement debit/payload/no-BE differs");
    }
    private static void reset(Fixture fixture) {
        fixture.set(fixture.pos, Blocks.AIR.defaultBlockState(), QUIET);
        for (Direction direction : Direction.values()) { fixture.set(fixture.pos.relative(direction), Blocks.AIR.defaultBlockState(), QUIET); }
    }

    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos pos;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        private final AABB bounds;
        private Set<UUID> oldDrops;
        private boolean closed, ownsForce, disabled;
        private ServerPlayer observer;
        private AnalyzerReading reading;
        private List<Integer> baseline;

        private Fixture(GameTestHelper helper, ServerLevel level, BlockPos pos) {
            this.helper = helper; this.level = level; this.pos = pos; bounds = new AABB(pos).inflate(3);
            oldDrops = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
            try {
                Field field = GameTestHelper.class.getDeclaredField("testInfo"); field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(this);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot install fixture timeout cleanup", failure); }
        }
        private void pin() {
            ChunkPos chunk = new ChunkPos(pos);
            if (!level.getForcedChunks().contains(chunk.toLong())) { ownsForce = true; level.setChunkForced(chunk.x, chunk.z, true); }
            level.getChunk(chunk.x, chunk.z); // One explicitly forced fixture chunk; a joined observer also has player tickets.
            oldDrops = level.getEntitiesOfClass(ItemEntity.class, bounds).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        }
        private void set(BlockPos position, BlockState state, int flags) {
            BlockPos key = position.immutable();
            helper.assertTrue(bounds.contains(Vec3.atCenterOf(key)) && level.isInWorldBounds(key)
                    && level.hasChunkAt(key) && level.getBlockEntity(key) == null, "Fixture cell is unloaded, unowned or contains a BlockEntity");
            if (!before.containsKey(key)) { helper.assertTrue(before.size() < 160, "Fixture exceeded 160-cell budget"); before.put(key, level.getBlockState(key)); }
            level.setBlock(key, state, flags);
        }
        private void darkRoom() {
            for (int x = -2; x <= 2; x++) { for (int y = -2; y <= 2; y++) { for (int z = -2; z <= 2; z++) {
                set(pos.offset(x, y, z), Math.abs(x) == 2 || Math.abs(y) == 2 || Math.abs(z) == 2
                        ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } } }
            set(pos.below(), Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
        private List<ItemEntity> newDrops() {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> !oldDrops.contains(entity.getUUID()));
            helper.assertTrue(drops.size() <= 32, "Fixture exceeded 32-new-drop budget"); return drops;
        }
        private void holdObserver() {
            if (observer != null) { observer.teleportTo(level, pos.getX() + 1.5, pos.getY() - 1, pos.getZ() + 0.5, 0, 0); }
        }
        private void assertVacuum() {
            if (observer != null) {
                helper.assertTrue(observer.blockPosition().above().equals(BlockPos.containing(observer.getEyePosition()))
                        && BlockPos.containing(observer.getEyePosition()).equals(pos.east())
                        && AtmosphereAnalyzerRuntime.read(observer).equals(reading), "Installed eye-cell vacuum changed during passive placement/neighbor invalidation");
            }
        }
        @Override public void close() { close(null); }
        private void close(Throwable primary) {
            if (closed) { return; } closed = true; Throwable failure = primary;
            try { if (observer != null) { level.getServer().getPlayerList().remove(observer); observer = null; } }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
            for (var entry : before.entrySet()) {
                try { level.setBlock(entry.getKey(), entry.getValue(), QUIET); }
                catch (RuntimeException | Error next) { failure = combine(failure, next); }
            }
            try { newDrops().forEach(ItemEntity::discard); }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
            try { if (ownsForce) { ChunkPos chunk = new ChunkPos(pos); level.setChunkForced(chunk.x, chunk.z, false); ownsForce = false; } }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
            try { if (disabled) { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); disabled = false; } }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
            if (primary == null && failure instanceof RuntimeException next) { throw next; }
            if (primary == null && failure instanceof Error next) { throw next; }
        }
        private static Throwable combine(Throwable first, Throwable next) { if (first == null) { return next; } if (first != next) { first.addSuppressed(next); } return first; }
        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info) { close(); }
        @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
    }
}
