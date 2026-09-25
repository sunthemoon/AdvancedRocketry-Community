package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiblockNbtCodecTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void controllerAndPartBindingRoundTrip() {
        UUID instanceId = UUID.randomUUID();
        MultiblockControllerState controller = new MultiblockControllerState(
                1,
                instanceId,
                7,
                new PatternTransform(PatternRotation.CLOCKWISE_270, true),
                MultiblockFormationState.WAITING_UNLOADED,
                Set.of(new BlockPos(2, 64, 1), new BlockPos(1, 64, 1))
        );
        CompoundTag controllerParent = new CompoundTag();
        controllerParent.put(MultiblockControllerNbtCodec.ROOT, MultiblockControllerNbtCodec.encode(controller));

        MultiblockNbtLoadResult<MultiblockControllerState> decodedController =
                MultiblockControllerNbtCodec.decode(controllerParent);
        assertEquals(MultiblockNbtStatus.SUPPORTED, decodedController.status());
        assertEquals(controller, decodedController.value().orElseThrow());

        MultiblockPartBinding binding = new MultiblockPartBinding(
                1,
                Level.OVERWORLD,
                new BlockPos(10, 70, -4),
                instanceId,
                7
        );
        CompoundTag bindingParent = new CompoundTag();
        bindingParent.put(MultiblockPartBindingNbtCodec.ROOT, MultiblockPartBindingNbtCodec.encode(binding));

        MultiblockNbtLoadResult<MultiblockPartBinding> decodedBinding =
                MultiblockPartBindingNbtCodec.decode(bindingParent);
        assertEquals(MultiblockNbtStatus.SUPPORTED, decodedBinding.status());
        assertEquals(binding, decodedBinding.value().orElseThrow());
    }

    @Test
    void futureSchemasArePreservedByteForByteAtTheNbtTreeLevel() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        future.putString("future_payload", "keep-me");
        CompoundTag parent = new CompoundTag();
        parent.put(MultiblockControllerNbtCodec.ROOT, future);

        MultiblockNbtLoadResult<MultiblockControllerState> result =
                MultiblockControllerNbtCodec.decode(parent);
        CompoundTag rewritten = new CompoundTag();
        result.writeRoot(rewritten, MultiblockControllerNbtCodec.ROOT, MultiblockControllerNbtCodec::encode);

        assertEquals(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, result.status());
        assertEquals(parent.get(MultiblockControllerNbtCodec.ROOT), rewritten.get(MultiblockControllerNbtCodec.ROOT));
    }

    @Test
    void invalidPrimitiveRootIsPreservedWithoutCoercion() {
        CompoundTag parent = new CompoundTag();
        parent.put(MultiblockPartBindingNbtCodec.ROOT, IntTag.valueOf(42));

        MultiblockNbtLoadResult<MultiblockPartBinding> result =
                MultiblockPartBindingNbtCodec.decode(parent);
        CompoundTag rewritten = new CompoundTag();
        result.writeRoot(rewritten, MultiblockPartBindingNbtCodec.ROOT, MultiblockPartBindingNbtCodec::encode);

        assertEquals(MultiblockNbtStatus.INVALID_DATA, result.status());
        assertEquals(IntTag.valueOf(42), rewritten.get(MultiblockPartBindingNbtCodec.ROOT));
    }

    @Test
    void oversizedDuplicateAndMalformedDataFailClosed() {
        MultiblockControllerState initial = MultiblockControllerState.initial(
                UUID.randomUUID(),
                new PatternTransform(PatternRotation.ZERO, false)
        );
        CompoundTag root = MultiblockControllerNbtCodec.encode(initial);
        ListTag oversized = new ListTag();
        CompoundTag position = new CompoundTag();
        position.putInt("x", 1);
        position.putInt("y", 2);
        position.putInt("z", 3);
        for (int index = 0; index <= MultiblockControllerState.MAX_PARTS; index++) {
            oversized.add(position.copy());
        }
        root.put("parts", oversized);
        CompoundTag parent = new CompoundTag();
        parent.put(MultiblockControllerNbtCodec.ROOT, root);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, MultiblockControllerNbtCodec.decode(parent).status());

        ListTag duplicate = new ListTag();
        duplicate.add(position.copy());
        duplicate.add(position.copy());
        root.put("parts", duplicate);
        root.putString("formation_state", MultiblockFormationState.WAITING_UNLOADED.name());
        assertEquals(MultiblockNbtStatus.INVALID_DATA, MultiblockControllerNbtCodec.decode(parent).status());

        CompoundTag malformedBinding = new CompoundTag();
        malformedBinding.putInt("schema_version", 1);
        malformedBinding.putString("controller_level", "not a level id");
        parent = new CompoundTag();
        parent.put(MultiblockPartBindingNbtCodec.ROOT, malformedBinding);
        MultiblockNbtLoadResult<MultiblockPartBinding> malformed =
                MultiblockPartBindingNbtCodec.decode(parent);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, malformed.status());
        assertTrue(malformed.preservedRoot().isPresent());
    }

    @Test
    void malformedControllerPartsAndExtraFieldsPreserveTheOriginalRoot() {
        MultiblockControllerState initial = MultiblockControllerState.initial(
                UUID.randomUUID(), new PatternTransform(PatternRotation.ZERO, false));
        CompoundTag root = MultiblockControllerNbtCodec.encode(initial);
        ListTag wrongElementType = new ListTag();
        wrongElementType.add(IntTag.valueOf(42));
        root.put("parts", wrongElementType);
        assertRejectedControllerPreserved(root);

        root = MultiblockControllerNbtCodec.encode(initial);
        root.putString("unrecognized_state", "must-not-disappear");
        assertRejectedControllerPreserved(root);

        root = MultiblockControllerNbtCodec.encode(initial);
        root.putString("machine_instance_id", "1-1-1-1-1");
        assertRejectedControllerPreserved(root);

        MultiblockControllerState formed = initial.formed(1, Set.of(new BlockPos(1, 64, 2)));
        root = MultiblockControllerNbtCodec.encode(formed);
        root.getList("parts", Tag.TAG_COMPOUND).getCompound(0).putString("unknown_position_data", "keep");
        assertRejectedControllerPreserved(root);
    }

    @Test
    void malformedPartBindingAndFutureSchemaPreserveTheOriginalRoot() {
        MultiblockPartBinding binding = new MultiblockPartBinding(
                1, Level.OVERWORLD, new BlockPos(10, 70, -4), UUID.randomUUID(), 7);
        CompoundTag root = MultiblockPartBindingNbtCodec.encode(binding);
        root.getCompound("controller").putString("unknown_position_data", "keep");
        assertRejectedBindingPreserved(root, MultiblockNbtStatus.INVALID_DATA);

        root = MultiblockPartBindingNbtCodec.encode(binding);
        root.putString("unrecognized_state", "must-not-disappear");
        assertRejectedBindingPreserved(root, MultiblockNbtStatus.INVALID_DATA);

        root = MultiblockPartBindingNbtCodec.encode(binding);
        root.putString("machine_instance_id", "1-1-1-1-1");
        assertRejectedBindingPreserved(root, MultiblockNbtStatus.INVALID_DATA);

        root = MultiblockPartBindingNbtCodec.encode(binding);
        root.putInt("schema_version", 99);
        root.putString("future_payload", "keep");
        assertRejectedBindingPreserved(root, MultiblockNbtStatus.UNSUPPORTED_SCHEMA);
    }

    @Test
    void oversizedRootsAreRejectedAndMaximumPartCountStillRoundTrips() {
        MultiblockControllerState initial = MultiblockControllerState.initial(
                UUID.randomUUID(), new PatternTransform(PatternRotation.ZERO, false));
        Set<BlockPos> positions = new HashSet<>();
        for (int index = 0; index < MultiblockControllerState.MAX_PARTS; index++) {
            positions.add(new BlockPos(index, 64, 0));
        }
        MultiblockControllerState maximum = initial.formed(1, positions);
        CompoundTag valid = MultiblockControllerNbtCodec.encode(maximum);
        assertTrue(valid.sizeInBytes() <= MultiblockControllerNbtCodec.MAX_ROOT_BYTES);
        CompoundTag parent = new CompoundTag();
        parent.put(MultiblockControllerNbtCodec.ROOT, valid);
        assertEquals(maximum, MultiblockControllerNbtCodec.decode(parent).value().orElseThrow());

        CompoundTag oversizedController = MultiblockControllerNbtCodec.encode(initial);
        oversizedController.putByteArray(
                "oversized_payload", new byte[MultiblockControllerNbtCodec.MAX_ROOT_BYTES]);
        assertTrue(oversizedController.sizeInBytes() > MultiblockControllerNbtCodec.MAX_ROOT_BYTES);
        assertRejectedControllerPreserved(oversizedController);

        MultiblockPartBinding binding = new MultiblockPartBinding(
                1, Level.OVERWORLD, new BlockPos(10, 70, -4), UUID.randomUUID(), 7);
        CompoundTag oversizedBinding = MultiblockPartBindingNbtCodec.encode(binding);
        oversizedBinding.putByteArray(
                "oversized_payload", new byte[MultiblockPartBindingNbtCodec.MAX_ROOT_BYTES]);
        assertTrue(oversizedBinding.sizeInBytes() > MultiblockPartBindingNbtCodec.MAX_ROOT_BYTES);
        assertRejectedBindingPreserved(oversizedBinding, MultiblockNbtStatus.INVALID_DATA);
    }

    private static void assertRejectedControllerPreserved(CompoundTag root) {
        CompoundTag parent = new CompoundTag();
        parent.put(MultiblockControllerNbtCodec.ROOT, root);
        MultiblockNbtLoadResult<MultiblockControllerState> result =
                MultiblockControllerNbtCodec.decode(parent);
        assertEquals(MultiblockNbtStatus.INVALID_DATA, result.status());
        CompoundTag rewritten = new CompoundTag();
        result.writeRoot(rewritten, MultiblockControllerNbtCodec.ROOT, MultiblockControllerNbtCodec::encode);
        assertEquals(root, rewritten.get(MultiblockControllerNbtCodec.ROOT));
    }

    private static void assertRejectedBindingPreserved(CompoundTag root, MultiblockNbtStatus expected) {
        CompoundTag parent = new CompoundTag();
        parent.put(MultiblockPartBindingNbtCodec.ROOT, root);
        MultiblockNbtLoadResult<MultiblockPartBinding> result = MultiblockPartBindingNbtCodec.decode(parent);
        assertEquals(expected, result.status());
        CompoundTag rewritten = new CompoundTag();
        result.writeRoot(rewritten, MultiblockPartBindingNbtCodec.ROOT, MultiblockPartBindingNbtCodec::encode);
        assertEquals(root, rewritten.get(MultiblockPartBindingNbtCodec.ROOT));
    }
}
