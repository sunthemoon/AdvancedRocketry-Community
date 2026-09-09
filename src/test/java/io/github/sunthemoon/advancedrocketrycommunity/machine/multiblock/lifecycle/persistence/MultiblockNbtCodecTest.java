package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
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
}
