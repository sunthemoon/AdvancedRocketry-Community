package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedTerrainImageTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void nativeProjectionPreservesUnrelatedFullChunkFieldsWithoutMutatingItsInput() throws Exception {
        var root = empty(); root.putString("unrelated", "retained");
        var before = root.copy(); var members = List.of(member(1, 1, 1, 0), member(2, 1, 1, 1));
        var projected = project(root, members).orElseThrow();
        assertEquals(before, root); assertEquals("retained", projected.getString("unrelated"));
        assertEquals(2, projected.getList("block_entities", Tag.TAG_COMPOUND).size());
        assertTrue(matches(projected, members, true)); assertFalse(matches(projected, members, false));
        assertTrue(matches(root, members, false)); assertFalse(matches(root, members, true));
        var states = codec().parse(NbtOps.INSTANCE, projected.getList("sections", Tag.TAG_COMPOUND)
                .getCompound(0).getCompound("block_states")).result().orElseThrow();
        assertEquals(Blocks.STONE.defaultBlockState(), states.get(1, 1, 1));
        assertEquals(Blocks.AIR.defaultBlockState(), states.get(0, 0, 0));
    }

    @Test void fullChunkExpansionCountsAll2048CarriersRatherThanTheOriginal128PayloadLimit() throws Exception {
        var members = new ArrayList<SealedRocketMember>();
        for (int index = 0; index < 2048; index++) { members.add(member(index & 15, index >> 8, index >> 4 & 15, index)); }
        var projected = project(empty(), members).orElseThrow();
        assertEquals(2048, projected.getList("block_entities", Tag.TAG_COMPOUND).size());
        assertTrue(matches(projected, members, true));
        var crowded = empty(); crowded.putByteArray("other_mod", new byte[3_600_000]);
        assertTrue(project(crowded, members).isEmpty(), "Ignored expanded carrier framing in full native chunk");
    }

    @Test void compressedAndDecodedBoundsBothRefuseBeforeAnyMutation() throws Exception {
        var members = List.of(member(1, 1, 1, 0));
        var incompressible = new byte[1_060_000]; new java.util.Random(152).nextBytes(incompressible);
        var root = empty(); root.putByteArray("other_mod", incompressible); var before = root.copy();
        assertTrue(project(root, members).isEmpty()); assertEquals(before, root);
        root = empty(); root.putByteArray("other_mod", new byte[4_194_304]);
        assertTrue(project(root, members).isEmpty());
    }

    @Test void foreignCellsDuplicateBlockEntitiesAndWrongSlotsAreNotAdoptedOrOverwritten() throws Exception {
        var member = member(1, 1, 1, 0); var members = List.of(member);
        var foreign = empty();
        var states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES);
        states.set(1, 1, 1, Blocks.DIAMOND_BLOCK.defaultBlockState());
        foreign.getList("sections", Tag.TAG_COMPOUND).getCompound(0).put("block_states",
                codec().encodeStart(NbtOps.INSTANCE, states).result().orElseThrow());
        assertTrue(project(foreign, members).isEmpty());
        var projected = project(empty(), members).orElseThrow();
        var bes = projected.getList("block_entities", Tag.TAG_COMPOUND); bes.add(bes.getCompound(0).copy());
        assertFalse(matches(projected, members, true));
        projected = project(empty(), members).orElseThrow(); projected.putInt("xPos", 1);
        assertFalse(matches(projected, members, true));
        assertTrue(project(empty(), List.of(member(16, 1, 1, 0))).isEmpty());
    }

    @Test void matchingRequiresEveryIdentityAndTheWholeMemberPayload() throws Exception {
        var members = List.of(member(1, 1, 1, 0)); var root = project(empty(), members).orElseThrow();
        root.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0).getCompound(SealedRocketMember.ROOT)
                .putUUID("transaction_id", UUID.randomUUID());
        assertFalse(matches(root, members, true));
        root = project(empty(), members).orElseThrow();
        root.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0).putString("id", "minecraft:chest");
        assertFalse(matches(root, members, true));
    }

    @Test void recoveryProjectsOnlyMissingMembersAndRetainsExactExistingCarriers() throws Exception {
        var first = member(1, 1, 1, 0); var second = member(2, 1, 1, 1);
        var partial = project(empty(), List.of(first)).orElseThrow(); var before = partial.copy();
        var members = List.of(first, second);
        @SuppressWarnings("unchecked")
        var full = (Optional<CompoundTag>) type().getMethod("projectRecovery", CompoundTag.class, List.class, BlockState.class)
                .invoke(null, partial, members, Blocks.STONE.defaultBlockState());
        assertTrue(full.isPresent()); assertEquals(before, partial);
        assertTrue(matches(full.orElseThrow(), members, true));
        assertEquals(2, full.orElseThrow().getList("block_entities", Tag.TAG_COMPOUND).size());
        assertEquals(before.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0),
                full.orElseThrow().getList("block_entities", Tag.TAG_COMPOUND).getCompound(0));
        assertTrue(project(partial, members).isEmpty(), "Live preflight must still require empty cells");
    }

    @SuppressWarnings("unchecked") private static Optional<CompoundTag> project(CompoundTag root, List<SealedRocketMember> members)
            throws Exception {
        return (Optional<CompoundTag>) type().getMethod("project", CompoundTag.class, List.class, BlockState.class)
                .invoke(null, root, members, Blocks.STONE.defaultBlockState());
    }
    private static boolean matches(CompoundTag root, List<SealedRocketMember> members, boolean sealed) throws Exception {
        return (boolean) type().getMethod("matches", CompoundTag.class, List.class, BlockState.class, boolean.class)
                .invoke(null, root, members, Blocks.STONE.defaultBlockState(), sealed);
    }
    private static Class<?> type() throws ClassNotFoundException {
        return Class.forName(SealedTerrainImageTest.class.getPackageName() + ".SealedTerrainImage");
    }
    private static com.mojang.serialization.Codec<PalettedContainer<BlockState>> codec() {
        return PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    }
    private static CompoundTag empty() {
        var root = new CompoundTag(); root.putInt("xPos", 0); root.putInt("zPos", 0);
        var section = new CompoundTag(); section.putByte("Y", (byte) 0);
        var states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES);
        section.put("block_states", codec().encodeStart(NbtOps.INSTANCE, states).result().orElseThrow());
        var sections = new ListTag(); sections.add(section); root.put("sections", sections);
        root.put("block_entities", new ListTag()); return root;
    }
    private static SealedRocketMember member(int x, int y, int z, int index) {
        return new SealedRocketMember(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), "a".repeat(64),
                new ResourceLocation("minecraft:overworld"), new RocketPosition(x, y, z), index,
                new RocketBlockState(new ResourceLocation("minecraft:iron_block"), java.util.Map.of()), null);
    }
}
