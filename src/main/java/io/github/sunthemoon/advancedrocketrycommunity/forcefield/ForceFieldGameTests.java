package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native event and BE adapters. Fixtures load only their own explicitly allocated cells. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldGameTests {
    private static final UUID OWNER = new UUID(0x4649454c44L, 4);
    static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final ForceFieldProjectorBlockEntity source;
        final FieldIdentity identity;
        final FieldCleanupSavedData data = new FieldCleanupSavedData();
        final List<BlockPos> extras = new ArrayList<>();
        Fixture(GameTestHelper helper, Direction direction) {
            level = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            BlockPos position = new BlockPos(1_000_000 + base.getX() * 64, 220, 1_000_000 + base.getZ() * 64);
            for (int i = -1; i <= 32; i++) { load(position.relative(direction, i)); }
            for (Direction side : Direction.values()) { load(position.relative(side)); }
            level.setBlock(position, ModBlocks.FORCE_FIELD_PROJECTOR.get().defaultBlockState()
                    .setValue(ForceFieldProjectorBlock.FACING, direction), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            source = (ForceFieldProjectorBlockEntity) level.getBlockEntity(position);
            CompoundTag tag = new CompoundTag();
            tag.put(ForceFieldCodecs.PROJECTOR_ROOT,
                    ForceFieldCodecs.projector(new ProjectorState(UUID.randomUUID(), Optional.of(OWNER), 1, direction, 0)));
            source.load(tag);
            identity = source.identity();
            data.reserve(identity, 0);
        }
        void load(BlockPos pos) { level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); }
        void power(boolean powered) {
            BlockPos pos = identity.source().relative(identity.direction().getOpposite());
            level.setBlock(pos, powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (!extras.contains(pos)) { extras.add(pos); }
        }
        void work() { ForceFieldService.work(level.getServer(), data, data.view().get(identity.key())); }
        ForceFieldBlockEntity field(int offset, FieldIdentity owner) {
            BlockPos pos = identity.target(offset);
            load(pos);
            ForceFieldProtection.physical(level, pos, () -> level.setBlock(pos, ModBlocks.FORCE_FIELD.get().defaultBlockState(), Block.UPDATE_ALL));
            var field = (ForceFieldBlockEntity) level.getBlockEntity(pos);
            field.initialize(owner);
            return field;
        }
        @Override public void close() {
            for (int i = 1; i <= 32; i++) {
                BlockPos pos = identity.target(i);
                if (ForceFieldService.loaded(level, pos)) {
                    ForceFieldProtection.physical(level, pos, () -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                }
            }
            ForceFieldProtection.physical(level, identity.source(), () -> level.setBlock(identity.source(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            for (BlockPos pos : extras) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
            FieldCleanupSavedData.get(level.getServer()).forget(identity.key());
        }
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void sixDirectionsOnlyEmptyAirAndExactThirtyTwoOffsets(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ModIdentity.id("force_field_projector"));
        helper.assertTrue(recipe.isPresent() && recipe.orElseThrow().getResultItem(helper.getLevel().registryAccess())
                .is(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.FORCE_FIELD_PROJECTOR.get()),
                "Generated projector recipe did not load into the native recipe manager");
        for (Direction direction : Direction.values()) {
            try (Fixture f = new Fixture(helper, direction)) {
                f.power(true);
                for (int i = 1; i <= 32; i++) { f.work(); }
                helper.assertTrue(f.source.state().claims() == -1, "Not all 32 offsets were claimed");
                helper.assertTrue(!f.level.getBlockState(f.identity.source().relative(direction, 33)).is(ModBlocks.FORCE_FIELD.get()), "Line exceeded 32 cells");
                f.power(false);
                for (int i = 1; i <= 32; i++) { f.work(); }
                helper.assertTrue(f.source.state().claims() == 0, "Retraction retained claims");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void plantsFluidsSnowAndBlockEntitiesAreNotReplaceable(GameTestHelper helper) {
        for (var obstacle : List.of(Blocks.WATER.defaultBlockState(), Blocks.SNOW.defaultBlockState(),
                Blocks.GRASS.defaultBlockState(), Blocks.CHEST.defaultBlockState())) {
            try (Fixture f = new Fixture(helper, Direction.EAST)) {
                f.level.setBlock(f.identity.target(1), obstacle, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                f.power(true); f.work();
                helper.assertTrue(f.level.getBlockState(f.identity.target(1)).equals(obstacle) && f.source.state().claims() == 0,
                        "Projection replaced a non-AIR block");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void staleGenerationsAndCrossedClaimsNeverDeleteOtherFields(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            f.source.claims(3); f.data.reserve(f.identity, 3);
            var stale = new FieldIdentity(f.identity.projector(), OWNER, 2, f.identity.level(), f.identity.source(), Direction.EAST);
            var crossed = new FieldIdentity(UUID.randomUUID(), OWNER, 1, f.identity.level(), f.identity.source(), Direction.EAST);
            f.field(1, stale); f.field(2, crossed);
            f.work(); f.work();
            helper.assertTrue(f.source.state().claims() == 0, "Foreign claims were not relinquished");
            helper.assertTrue(stale.equals(((ForceFieldBlockEntity) f.level.getBlockEntity(f.identity.target(1))).identity())
                    && crossed.equals(((ForceFieldBlockEntity) f.level.getBlockEntity(f.identity.target(2))).identity()), "Retraction deleted another generation/projector");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void cancelledApiAndStandardPlacementEventsCreateNoClaims(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            f.power(true);
            AtomicInteger observed = new AtomicInteger();
            Consumer<EndgameEffectEvent> api = event -> {
                if (event.ownerId().equals(OWNER) && event.min().equals(f.identity.target(1))) {
                    helper.assertTrue(event.effect() == EndgameEffect.FIELD_PROJECTION && event.actorId().isEmpty()
                            && event.systemId().equals(ModIdentity.id("force_field_projector")), "Wrong projection event payload");
                    observed.incrementAndGet(); event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EndgameEffectEvent.class, api);
            try { f.work(); } finally { MinecraftForge.EVENT_BUS.unregister(api); }
            helper.assertTrue(observed.get() == 1 && f.source.state().claims() == 0 && f.level.getBlockState(f.identity.target(1)).is(Blocks.AIR), "API cancellation mutated a claim");
            Consumer<BlockEvent.EntityPlaceEvent> placement = event -> {
                if (event.getPos().equals(f.identity.target(1)) && event.getLevel() == f.level) {
                    helper.assertTrue(event.getPlacedBlock().is(ModBlocks.FORCE_FIELD.get()), "Placement event did not identify the proposed field");
                    helper.assertTrue(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                            && !player.hasPermissions(2) && !player.createCommandSourceStack().hasPermission(2), "Offline owner became operator authority");
                    event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, placement);
            try { f.work(); } finally { MinecraftForge.EVENT_BUS.unregister(placement); }
            helper.assertTrue(f.source.state().claims() == 0 && f.data.view().get(f.identity.key()).claims() == 0,
                    "Standard placement cancellation changed bookkeeping");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void protectedZonesStopBothGrowthAndCleanup(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            var root = EndgameSavedData.get(f.level.getServer());
            var pos = f.identity.target(1);
            String name = "field_" + UUID.randomUUID().toString().substring(0, 8);
            root.update(r -> r.addZone(ProtectedZone.of(name, f.level.dimension().location(), pos.getX(), pos.getZ(), pos.getX(), pos.getZ(), List.of()), 256));
            try {
                f.power(true); f.work();
                helper.assertTrue(f.source.state().claims() == 0 && f.level.getBlockState(pos).is(Blocks.AIR), "Zone allowed projection");
                f.field(1, f.identity); f.source.claims(1); f.data.reserve(f.identity, 1); f.power(false); f.work();
                helper.assertTrue(f.level.getBlockState(pos).is(ModBlocks.FORCE_FIELD.get()) && f.source.state().claims() == 1, "Zone allowed retraction");
            } finally { root.update(r -> r.removeZone(name)); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void cancelledBreakAndWorldRemovalKeepSourceFieldAndClaims(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            f.field(1, f.identity); f.source.claims(1); f.data.reserve(f.identity, 1);
            Consumer<BlockEvent.BreakEvent> veto = event -> { if (event.getLevel() == f.level && (event.getPos().equals(f.identity.target(1))
                    || event.getPos().equals(f.identity.source()))) { event.setCanceled(true); } };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, veto);
            try {
                f.work();
                f.level.setBlock(f.identity.source(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                helper.assertTrue(f.level.getBlockEntity(f.identity.source()) == f.source, "World removal lost the refused source BE");
                helper.assertTrue(f.level.getBlockState(f.identity.target(1)).is(ModBlocks.FORCE_FIELD.get()) && f.source.state().claims() == 1,
                        "Cancellation removed field or claim");
            } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void explosionUsesTheSameCancelledAdmission(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            f.field(1, f.identity); f.source.claims(1); f.data.reserve(f.identity, 1);
            Consumer<EndgameEffectEvent> veto = event -> { if (event.ownerId().equals(OWNER) && event.effect() == EndgameEffect.BLOCK_BREAK) { event.setCanceled(true); } };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EndgameEffectEvent.class, veto);
            try {
                Explosion explosion = new Explosion(f.level, null, f.identity.source().getX(), 220, f.identity.source().getZ(), 4, false, Explosion.BlockInteraction.DESTROY);
                explosion.getToBlow().add(f.identity.source()); explosion.getToBlow().add(f.identity.target(1));
                MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(f.level, explosion, new ArrayList<>()));
                helper.assertTrue(explosion.getToBlow().isEmpty(), "Cancelled explosion retained a destructive cell");
                f.source.getBlockState().getBlock().onBlockExploded(f.source.getBlockState(), f.level, f.identity.source(), explosion);
                helper.assertTrue(f.level.getBlockEntity(f.identity.source()) == f.source, "Explosion override lost source");
            } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void missingTargetClaimsAreRetainedWithoutChunkTickets(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            FieldIdentity far = new FieldIdentity(UUID.randomUUID(), OWNER, 1, f.level.dimension(), new BlockPos(25_000_015, 220, 25_000_000), Direction.EAST);
            var entry = new FieldCleanupRoot.Entry(far, FieldIdentity.bit(32), true);
            f.data.put(entry);
            var tickets = f.level.getForcedChunks();
            helper.assertTrue(!ForceFieldService.loaded(f.level, far.target(32)), "Unavailable fixture was already loaded");
            ForceFieldService.work(f.level.getServer(), f.data, entry);
            helper.assertTrue(f.data.view().get(far.key()).claims() == FieldIdentity.bit(32)
                    && !ForceFieldService.loaded(f.level, far.target(32)) && tickets.equals(f.level.getForcedChunks()), "Cleanup loaded a chunk or discarded unavailable claims");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void nativeRootsAndCleanupDiskReadbackRecoverAnOrphan(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            var field = f.field(1, f.identity); f.source.claims(1); f.data.reserve(f.identity, 1);
            CompoundTag fieldTag = field.saveWithFullMetadata();
            f.level.setBlock(f.identity.source(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            var global = FieldCleanupSavedData.get(f.level.getServer());
            helper.assertTrue(global.view().get(f.identity.key()).orphan(), "Removal did not retain a tombstone");
            java.nio.file.Path directory = null;
            try {
                directory = java.nio.file.Files.createTempDirectory("arce-w04-restart-");
                var file = directory.resolve("cleanup.dat").toFile();
                global.setDirty(); global.save(file);
                var recovered = FieldCleanupSavedData.load(NbtIo.readCompressed(file).getCompound("data"));
                f.level.removeBlockEntity(f.identity.target(1));
                BlockEntity restored = BlockEntity.loadStatic(f.identity.target(1), ModBlocks.FORCE_FIELD.get().defaultBlockState(), fieldTag);
                f.level.setBlockEntity(restored);
                helper.assertTrue(restored instanceof ForceFieldBlockEntity && f.identity.equals(((ForceFieldBlockEntity) restored).identity()), "Native field identity changed across reload");
                ForceFieldService.work(f.level.getServer(), recovered, recovered.view().get(f.identity.key()));
                helper.assertTrue(f.level.getBlockState(f.identity.target(1)).is(Blocks.AIR) && recovered.view().get(f.identity.key()) == null,
                        "Disk-recovered orphan did not settle its exact claim");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            finally {
                if (directory != null) {
                    try { java.nio.file.Files.deleteIfExists(directory.resolve("cleanup.dat")); java.nio.file.Files.delete(directory); }
                    catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                }
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void fullCleanupRootRefusesPlacementRemovalAndExplosionBeforeEvents(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            var original = FieldCleanupSavedData.get(f.level.getServer());
            var full = new FieldCleanupSavedData();
            for (int i = 0; i < 4096; i++) {
                var identity = new FieldIdentity(new UUID(99, i), OWNER, 1, f.level.dimension(), new BlockPos(i, 220, 0), Direction.EAST);
                helper.assertTrue(full.reserve(identity, 1), "Failed to fill cleanup root");
            }
            f.level.getServer().overworld().getDataStorage().set(FieldCleanupSavedData.NAME, full);
            AtomicInteger events = new AtomicInteger();
            Consumer<EndgameEffectEvent> observed = event -> { if (event.ownerId().equals(OWNER)) { events.incrementAndGet(); } };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EndgameEffectEvent.class, observed);
            try {
                f.level.setBlock(f.identity.source(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                helper.assertTrue(f.level.getBlockEntity(f.identity.source()) == f.source && events.get() == 0,
                        "Full root admitted source removal or posted an effect");
                BlockPos another = f.identity.source().above(3);
                f.level.setBlock(another, ModBlocks.FORCE_FIELD_PROJECTOR.get().defaultBlockState(), Block.UPDATE_ALL);
                helper.assertTrue(f.level.getBlockState(another).is(Blocks.AIR), "Full root admitted another projector");
                Explosion explosion = new Explosion(f.level, null, another.getX(), another.getY(), another.getZ(), 4, false, Explosion.BlockInteraction.DESTROY);
                f.source.getBlockState().getBlock().onBlockExploded(f.source.getBlockState(), f.level, f.identity.source(), explosion);
                helper.assertTrue(f.level.getBlockEntity(f.identity.source()) == f.source && events.get() == 0 && full.view().size() == 4096,
                        "Full root explosion discarded source/claims");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(observed);
                f.level.getServer().overworld().getDataStorage().set(FieldCleanupSavedData.NAME, original);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void futureNativeRootsRemainSealedAndRejectNormalization(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            var field = f.field(1, f.identity);
            CompoundTag raw = ForceFieldCodecs.field(f.identity); raw.putInt("schema", 99); raw.putString("future", "retain");
            CompoundTag outer = new CompoundTag(); outer.put(ForceFieldCodecs.FIELD_ROOT, raw); field.load(outer);
            f.level.setBlock(f.identity.target(1), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(f.level.getBlockEntity(f.identity.target(1)) == field && field.quarantined()
                    && field.saveWithoutMetadata().get(ForceFieldCodecs.FIELD_ROOT).equals(raw), "Future root was removed or normalized");
            helper.assertTrue(Block.isShapeFullBlock(field.getBlockState().getCollisionShape(f.level, field.getBlockPos())), "Quarantine changed collision");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void foreignNativeLocationRemainsInertAndOwnerNbtRequiresOperator(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            var field = f.field(1, f.identity);
            helper.assertTrue(field.onlyOpCanSetNbt() && f.source.onlyOpCanSetNbt(), "Owner roots accept non-operator item NBT");
            var misplaced = new FieldIdentity(f.identity.projector(), f.identity.owner(), 1,
                    f.identity.level(), f.identity.source().above(10), Direction.EAST);
            CompoundTag raw = ForceFieldCodecs.field(misplaced);
            CompoundTag tag = new CompoundTag(); tag.put(ForceFieldCodecs.FIELD_ROOT, raw); field.load(tag);
            f.level.setBlock(field.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(field.quarantined() && field.identity() == null && f.level.getBlockEntity(field.getBlockPos()) == field
                    && field.saveWithoutMetadata().get(ForceFieldCodecs.FIELD_ROOT).equals(raw), "Misplaced root was removed or normalized");
            var crossLevel = new FieldIdentity(f.identity.projector(), f.identity.owner(), 1,
                    net.minecraft.world.level.Level.OVERWORLD, f.identity.source(), Direction.EAST);
            raw = ForceFieldCodecs.field(crossLevel); tag.put(ForceFieldCodecs.FIELD_ROOT, raw); field.load(tag);
            helper.assertTrue(field.quarantined() && field.saveWithoutMetadata().get(ForceFieldCodecs.FIELD_ROOT).equals(raw),
                    "Cross-level native identity was accepted");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void quarantinedSourceAlsoRefusesDestructiveRemovalOfItsReadableFields(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Direction.EAST)) {
            var field = f.field(1, f.identity); f.source.claims(1); f.data.reserve(f.identity, 1);
            var raw = ForceFieldCodecs.projector(f.source.state()); raw.putInt("schema", 99);
            CompoundTag tag = new CompoundTag(); tag.put(ForceFieldCodecs.PROJECTOR_ROOT, raw); f.source.load(tag);
            f.work();
            f.level.setBlock(field.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(f.source.quarantined() && f.level.getBlockEntity(field.getBlockPos()) == field
                    && f.data.view().get(f.identity.key()).claims() == 1, "Unreadable source allowed field removal");
            helper.assertTrue(f.source.saveWithoutMetadata().get(ForceFieldCodecs.PROJECTOR_ROOT).equals(raw), "Source root changed");
        }
        helper.succeed();
    }
    private ForceFieldGameTests() { }
}
