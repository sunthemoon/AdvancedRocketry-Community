package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** NBT loaded into a live projector leaves exactly the loaded identity's reservation (W04F review N5, W59). */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldReservationGameTests {
    private static final UUID OWNER = new UUID(0x5759L, 1);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static ProjectorState owned() { return new ProjectorState(UUID.randomUUID(), Optional.of(OWNER), 1, Direction.NORTH, 0); }
    private static ProjectorState unowned() { return new ProjectorState(UUID.randomUUID(), Optional.empty(), 1, Direction.NORTH, 0); }
    private static FieldIdentity identity(ForceFieldPlacementGameTests.Placement f, BlockPos pos, ProjectorState state) {
        return new FieldIdentity(state.id(), state.owner().orElse(FieldIdentity.UNBOUND_OWNER), state.generation(),
                f.level.dimension(), pos, state.direction());
    }
    /** A creative operator: the only placer whose item BlockEntityTag a projector accepts. */
    private static InteractionResult placeTagged(ForceFieldPlacementGameTests.Placement f, ProjectorState loaded) {
        f.stack.getOrCreateTagElement("BlockEntityTag").put(ForceFieldCodecs.PROJECTOR_ROOT, ForceFieldCodecs.projector(loaded));
        FakePlayer operator = new FakePlayer(f.level, new GameProfile(UUID.randomUUID(), "FieldOperator")) {
            @Override protected int getPermissionLevel() { return 2; }
        };
        operator.setGameMode(GameType.CREATIVE);
        operator.setPos(f.pos.getX() + 0.5, f.pos.getY(), f.pos.getZ() + 0.5);
        operator.setItemInHand(InteractionHand.MAIN_HAND, f.stack);
        return f.stack.useOn(new UseOnContext(operator, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(f.pos.below()), Direction.UP, f.pos.below(), false)));
    }
    /** Like a structure template or /setblock: the block first, then the NBT into its live block entity. */
    private static ForceFieldProjectorBlockEntity place(ForceFieldPlacementGameTests.Placement f, BlockPos pos) {
        f.level.setBlock(pos, ModBlocks.FORCE_FIELD_PROJECTOR.get().defaultBlockState()
                .setValue(ForceFieldProjectorBlock.FACING, Direction.NORTH), FLAGS);
        return (ForceFieldProjectorBlockEntity) f.level.getBlockEntity(pos);
    }
    private static ForceFieldProjectorBlockEntity placePermitted(ForceFieldPlacementGameTests.Placement f, BlockPos pos) {
        ForceFieldProtection.physical(f.level, pos, () -> place(f, pos));
        return (ForceFieldProjectorBlockEntity) f.level.getBlockEntity(pos);
    }
    private static void remove(ForceFieldPlacementGameTests.Placement f, BlockPos pos) {
        ForceFieldProtection.physical(f.level, pos, () -> f.level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS));
    }
    private static void load(ForceFieldProjectorBlockEntity source, CompoundTag root) {
        CompoundTag outer = new CompoundTag(); outer.put(ForceFieldCodecs.PROJECTOR_ROOT, root); source.load(outer);
    }
    private static void load(ForceFieldProjectorBlockEntity source, ProjectorState state) { load(source, ForceFieldCodecs.projector(state)); }
    private static Set<FieldIdentity.Key> keys(FieldCleanupSavedData data) {
        return data.view().entries().stream().map(entry -> entry.identity().key()).collect(Collectors.toSet());
    }
    /** Every entry whose source is this cell; a stray constructor reservation would be a second one. */
    private static List<FieldCleanupRoot.Entry> at(FieldCleanupSavedData data, ForceFieldPlacementGameTests.Placement f, BlockPos pos) {
        return data.view().entries().stream().filter(entry -> entry.identity().level().equals(f.level.dimension())
                && entry.identity().source().equals(pos)).toList();
    }

    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void operatorTagPlacementKeepsOnlyTheLoadedReservationAcrossARestart(GameTestHelper helper) {
        try (var f = new ForceFieldPlacementGameTests.Placement(helper)) {
            var data = FieldCleanupSavedData.get(f.level.getServer());
            var keys = new HashSet<>(keys(data));
            var loaded = owned();
            var identity = identity(f, f.pos, loaded);
            var expected = new FieldCleanupRoot.Entry(identity, 0, false);
            helper.assertTrue(placeTagged(f, loaded).consumesAction()
                    && f.level.getBlockEntity(f.pos) instanceof ForceFieldProjectorBlockEntity placed
                    && !placed.quarantined() && placed.identity().equals(identity) && placed.hasReservation(),
                    "Operator placement did not load and reserve the tagged identity");
            keys.add(identity.key());
            helper.assertTrue(keys(data).equals(keys) && at(data, f, f.pos).equals(List.of(expected)),
                    "Tagged placement kept the constructor's reservation or lost the loaded one: " + at(data, f, f.pos));
            var root = data.save(new CompoundTag());
            java.nio.file.Path directory = null;
            try {
                directory = java.nio.file.Files.createTempDirectory("arce-w59-restart-");
                var file = directory.resolve("cleanup.dat").toFile();
                // Round-trip a copy, so the live root keeps its dirty flag for the world save.
                var copy = FieldCleanupSavedData.load(root.copy());
                copy.setDirty(); copy.save(file);
                var recovered = FieldCleanupSavedData.load(NbtIo.readCompressed(file).getCompound("data"));
                helper.assertTrue(recovered.operational() && recovered.save(new CompoundTag()).equals(root)
                        && keys(recovered).equals(keys) && expected.equals(recovered.view().get(identity.key())),
                        "The restarted root did not keep exactly the loaded reservation");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            finally {
                if (directory != null) {
                    try { java.nio.file.Files.deleteIfExists(directory.resolve("cleanup.dat")); java.nio.file.Files.delete(directory); }
                    catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                }
            }
            // A chunk reload loads the root before the block entity has a Level, so it is not an in-place load.
            var placed = (ForceFieldProjectorBlockEntity) f.level.getBlockEntity(f.pos);
            CompoundTag saved = placed.saveWithFullMetadata();
            f.level.removeBlockEntity(f.pos);
            BlockEntity restored = BlockEntity.loadStatic(f.pos, f.level.getBlockState(f.pos), saved);
            f.level.setBlockEntity(restored);
            helper.assertTrue(restored != placed && f.level.getBlockEntity(f.pos) == restored
                    && restored instanceof ForceFieldProjectorBlockEntity reloaded && reloaded.identity().equals(identity)
                    && reloaded.hasReservation() && data.save(new CompoundTag()).equals(root),
                    "A chunk-reload equivalent of the projector changed the cleanup root");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void inPlaceLoadSwapsWithinTheLastSlotAndNeverGrowsAFullRoot(GameTestHelper helper) {
        try (var f = new ForceFieldPlacementGameTests.Placement(helper)) {
            var storage = f.level.getServer().overworld().getDataStorage();
            var original = FieldCleanupSavedData.get(f.level.getServer());
            var data = new FieldCleanupSavedData();
            for (int i = 0; i < FieldCleanupRoot.MAX_ENTRIES - 1; i++) {
                var filler = new FieldIdentity(new UUID(59, i), new UUID(59, 4096), 1, f.level.dimension(),
                        new BlockPos(27_000_000 + i, 100, 27_000_000), Direction.EAST);
                helper.assertTrue(data.reserve(filler, 0), "Capacity fixture admission failed");
            }
            BlockPos second = f.pos.east(2);
            storage.set(FieldCleanupSavedData.NAME, data);
            try {
                var loaded = owned();
                helper.assertTrue(placeTagged(f, loaded).consumesAction() && f.level.getBlockState(f.pos).is(ModBlocks.FORCE_FIELD_PROJECTOR.get())
                        && data.view().size() == FieldCleanupRoot.MAX_ENTRIES
                        && at(data, f, f.pos).equals(List.of(new FieldCleanupRoot.Entry(identity(f, f.pos, loaded), 0, false))),
                        "Tagged placement at the last free slot did not swap the constructor's entry for the loaded one");
                // The root is full, so onLoad could not reserve this projector; its in-place load is not recognised.
                var unreserved = placePermitted(f, second);
                var full = data.save(new CompoundTag());
                helper.assertTrue(unreserved != null && !unreserved.hasReservation() && at(data, f, second).isEmpty(),
                        "A full root admitted another projector's reservation");
                load(unreserved, owned());
                helper.assertTrue(!unreserved.quarantined() && !unreserved.hasReservation()
                        && data.save(new CompoundTag()).equals(full), "An in-place load changed a full root");
            } finally {
                remove(f, second);
                storage.set(FieldCleanupSavedData.NAME, original);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void inPlaceLoadLeavesARefusedRootUntouched(GameTestHelper helper) {
        try (var f = new ForceFieldPlacementGameTests.Placement(helper)) {
            var storage = f.level.getServer().overworld().getDataStorage();
            var original = FieldCleanupSavedData.get(f.level.getServer());
            CompoundTag raw = new CompoundTag(); raw.putInt("schema", 2); raw.put("entries", new ListTag());
            var refused = FieldCleanupSavedData.load(raw.copy());
            helper.assertTrue(!refused.operational() && !refused.isDirty(), "Refused-root fixture is operational");
            storage.set(FieldCleanupSavedData.NAME, refused);
            try {
                var source = placePermitted(f, f.pos);
                load(source, unowned());
                helper.assertTrue(!source.quarantined() && !source.hasReservation() && !refused.isDirty()
                        && refused.view().size() == 0 && refused.save(new CompoundTag()).equals(raw),
                        "An in-place load wrote to a refused cleanup root");
            } finally { storage.set(FieldCleanupSavedData.NAME, original); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void inPlaceLoadCollisionsKeepTheOtherEntryAndReleaseTheEmptyOne(GameTestHelper helper) {
        try (var f = new ForceFieldPlacementGameTests.Placement(helper)) {
            var data = FieldCleanupSavedData.get(f.level.getServer());
            BlockPos second = f.pos.east(2);
            // Another projector's claimed entry under the loaded key, and a tombstone of the loaded identity itself.
            var copied = owned();
            var other = new FieldCleanupRoot.Entry(identity(f, f.pos.south(5), copied), FieldIdentity.bit(1), false);
            var buried = owned();
            var tombstone = new FieldCleanupRoot.Entry(identity(f, second, buried), FieldIdentity.bit(1), true);
            helper.assertTrue(data.put(other) && data.put(tombstone), "Collision fixture admission failed");
            try {
                var keys = keys(data);
                var copy = place(f, f.pos);
                var reburied = place(f, second);
                helper.assertTrue(copy.hasReservation() && reburied.hasReservation() && keys(data).size() == keys.size() + 2,
                        "Collision fixture did not reserve its constructor identities");
                load(copy, copied);
                load(reburied, buried);
                helper.assertTrue(keys(data).equals(keys) && other.equals(data.view().get(other.identity().key()))
                        && tombstone.equals(data.view().get(tombstone.identity().key()))
                        && at(data, f, f.pos).isEmpty() && at(data, f, second).equals(List.of(tombstone)),
                        "A colliding in-place load changed the other entry or kept the empty one");
                helper.assertTrue(!copy.quarantined() && !copy.hasReservation() && !reburied.quarantined() && !reburied.hasReservation(),
                        "A colliding loaded identity was reserved");
            } finally {
                remove(f, second);
                data.forget(other.identity().key());
                data.forget(tombstone.identity().key());
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void quarantiningInPlaceLoadReleasesOnlyTheEmptyReservation(GameTestHelper helper) {
        try (var f = new ForceFieldPlacementGameTests.Placement(helper)) {
            var data = FieldCleanupSavedData.get(f.level.getServer());
            BlockPos second = f.pos.east(2);
            try {
                var keys = keys(data);
                var future = place(f, f.pos);
                var turned = place(f, second);
                var futureRoot = ForceFieldCodecs.projector(owned()); futureRoot.putInt("schema", 2);
                var turnedRoot = ForceFieldCodecs.projector(new ProjectorState(UUID.randomUUID(), Optional.of(OWNER), 1, Direction.SOUTH, 0));
                load(future, futureRoot);
                load(turned, turnedRoot);
                helper.assertTrue(future.quarantined() && turned.quarantined(), "Quarantine fixtures were accepted");
                helper.assertTrue(keys(data).equals(keys) && at(data, f, f.pos).isEmpty() && at(data, f, second).isEmpty(),
                        "A quarantining in-place load kept the empty reservation or reserved the unreadable root");
            } finally { remove(f, second); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void inPlaceLoadKeepsBoundClaimedForeignAndUnchangedReservations(GameTestHelper helper) {
        try (var f = new ForceFieldPlacementGameTests.Placement(helper)) {
            var data = FieldCleanupSavedData.get(f.level.getServer());
            List<BlockPos> extras = List.of(f.pos.east(2), f.pos.east(4), f.pos.east(6));
            List<FieldIdentity.Key> kept = new ArrayList<>();
            try {
                var bound = place(f, f.pos);
                var claimed = place(f, extras.get(0));
                var foreign = place(f, extras.get(1));
                var same = place(f, extras.get(2));
                // A bound reservation: the projector and its entry are owned.
                var boundState = bound.state().bind(OWNER);
                var boundIdentity = identity(f, f.pos, boundState);
                helper.assertTrue(data.bindReservation(bound.reservationIdentity(), boundIdentity), "Bound fixture failed");
                kept.add(boundIdentity.key());
                load(bound, boundState);
                // Claims on the empty sentinel identity's entry (an unowned ProjectorState cannot hold claims itself).
                helper.assertTrue(data.reserve(claimed.reservationIdentity(), FieldIdentity.bit(1)), "Claimed fixture failed");
                kept.add(claimed.reservationIdentity().key());
                // A different owner: the entry is bound while the block entity still holds the UNBOUND identity.
                var foreignBound = identity(f, extras.get(1), foreign.state().bind(OWNER));
                helper.assertTrue(data.bindReservation(foreign.reservationIdentity(), foreignBound), "Foreign fixture failed");
                kept.add(foreignBound.key());
                kept.add(same.reservationIdentity().key());
                var root = data.save(new CompoundTag());
                var sources = List.of(bound, claimed, foreign);
                var replacements = List.of(owned(), unowned(), unowned());
                for (int i = 0; i < sources.size(); i++) { load(sources.get(i), replacements.get(i)); }
                load(same, same.state());
                helper.assertTrue(data.save(new CompoundTag()).equals(root),
                        "An in-place load changed a bound, claimed, foreign or unchanged reservation");
                for (int i = 0; i < sources.size(); i++) {
                    var source = sources.get(i);
                    helper.assertTrue(!source.quarantined() && source.state().equals(replacements.get(i))
                            && data.view().get(source.reservationIdentity().key()) == null,
                            "A replaced identity was reserved at once although its previous entry was kept");
                }
            } finally {
                extras.forEach(pos -> remove(f, pos));
                kept.forEach(data::forget);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void nativeFixturesNoLongerLeakTheirConstructorReservations(GameTestHelper helper) {
        var data = FieldCleanupSavedData.get(helper.getLevel().getServer());
        for (Direction direction : Direction.values()) {
            var before = keys(data);
            try (var f = new ForceFieldGameTests.Fixture(helper, direction)) {
                var expected = new HashSet<>(before); expected.add(f.identity.key());
                var source = f.identity.source();
                helper.assertTrue(keys(data).equals(expected)
                        && new FieldCleanupRoot.Entry(f.identity, 0, false).equals(data.view().get(f.identity.key()))
                        && data.view().entries().stream().filter(entry -> entry.identity().source().equals(source)).count() == 1,
                        "Fixture facing " + direction + " did not hold exactly its loaded reservation");
            }
            helper.assertTrue(keys(data).equals(before), "Fixture facing " + direction + " left a cleanup entry behind");
        }
        helper.succeed();
    }
    private ForceFieldReservationGameTests() { }
}
