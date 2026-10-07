package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicRawPermitTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static ClassicOwnerState state() { return new ClassicOwnerState(new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState())); }

    @Test void rawStorageIsExclusiveAndClosedPermitCannotEmit() {
        var state = state(); var permit = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow();
        assertTrue(permit.storageStillCurrent());
        assertTrue(ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).isEmpty());
        assertTrue(ClassicRawPermit.acquire(state, ClassicRawPurpose.EMIT).isEmpty());
        permit.close(); permit.close(); assertFalse(permit.storageStillCurrent());
        assertThrows(IllegalStateException.class, permit::requireCurrent);
        try (var next = ClassicRawPermit.acquire(state, ClassicRawPurpose.EMIT).orElseThrow()) { assertTrue(next.storageStillCurrent()); }
    }

    @Test void absentCapturePurposeCannotAcquireOrLeakTheOwnerGuard() {
        var state = state();
        assertThrows(NullPointerException.class, () -> ClassicRawPermit.acquire(state, null));
        assertFalse(state.busy()); assertTrue(state.pending().isEmpty()); assertFalse(state.loaded());
        try (var permit = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            assertTrue(permit.storageStillCurrent());
        }
    }

    @Test void retirementInvalidatesCaptureButDoesNotEraseFinalRawEmission() {
        var state = state();
        try (var capture = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            state.retire(); assertFalse(capture.storageStillCurrent());
        }
        try (var emit = ClassicRawPermit.acquire(state, ClassicRawPurpose.EMIT).orElseThrow()) {
            state.retire(); assertTrue(emit.storageStillCurrent());
        }
    }

    @Test void boundedPendingHandoffOwnsAllTwelveRootsAndDoesNotBecomeWorldAuthority() {
        var state = state(); CompoundTag parent = new CompoundTag(); CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 99); parent.put(ClassicRootBundle.HATCH, root);
        parent.putString("arce_machine", "legacy");
        try (var capture = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            var pending = ClassicPendingLoad.capture(parent, ClassicRootBundle.OwnerType.HATCH, capture);
            state.captured(pending, capture); root.putInt("schema_version", 0);
            assertTrue(capture.storageStillCurrent()); assertFalse(state.available()); assertTrue(state.loaded());
        }
        CompoundTag out = new CompoundTag(); out.putString("unowned", "keep");
        try (var emit = ClassicRawPermit.emission(state)) { state.pending().orElseThrow().emit(out, emit); }
        assertEquals(99, out.getCompound(ClassicRootBundle.HATCH).getInt("schema_version"));
        assertEquals("legacy", out.getString("arce_machine")); assertEquals("keep", out.getString("unowned"));
    }

    @Test void missingAndUnsafeBundlesStayPendingAndCannotEmitPartialData() {
        var state = state(); CompoundTag parent = new CompoundTag(); parent.putByteArray(ClassicRootBundle.HATCH, new byte[4_097]);
        try (var capture = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            var pending = ClassicPendingLoad.capture(parent, ClassicRootBundle.OwnerType.HATCH, capture);
            assertTrue(pending.requiresSaveRefusal()); state.captured(pending, capture);
        }
        try (var emit = ClassicRawPermit.emission(state)) {
            CompoundTag out = new CompoundTag(); out.putString("unchanged", "value");
            assertThrows(IllegalStateException.class, () -> state.pending().orElseThrow().emit(out, emit));
            assertEquals(1, out.size()); assertTrue(out.contains("unchanged"));
        }
    }

    @Test void foreignRawPermitCannotPublishAndBusyNonserverEmissionRefuses() {
        var first = state(); var second = state();
        try (var capture = ClassicRawPermit.acquire(first, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            var pending = ClassicPendingLoad.capture(new CompoundTag(), ClassicRootBundle.OwnerType.HATCH, capture);
            assertThrows(IllegalStateException.class, () -> second.captured(pending, capture));
            assertThrows(IllegalStateException.class, () -> ClassicRawPermit.emission(first));
        }
    }

    @Test void retirementAfterRawHandoffPreservesTheCheckpointButInvalidatesTheCaptureLease() {
        var state = state(); CompoundTag input = new CompoundTag(); CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 99); input.put(ClassicRootBundle.HATCH, root);
        input.putString("arce_machine", "legacy");
        try (var capture = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            var pending = ClassicPendingLoad.capture(input, ClassicRootBundle.OwnerType.HATCH, capture);
            state.captured(pending, capture); state.retire();
            assertThrows(IllegalStateException.class, capture::requireCurrent);
            assertSame(pending, state.pending().orElseThrow()); assertFalse(state.available());
        }
        CompoundTag output = new CompoundTag();
        try (var emit = ClassicRawPermit.emission(state)) { state.pending().orElseThrow().emit(output, emit); }
        assertEquals(input, output); assertFalse(state.busy());
        // This is storage retention only, not a native disk/save-consumer proof.
    }

    @Test void pendingRootsCannotMoveToAnotherOwnersPublicationOrEmissionLease() {
        var first = state(); var second = state(); ClassicPendingLoad pending;
        CompoundTag input = new CompoundTag(); input.put(ClassicRootBundle.HATCH, new CompoundTag());
        try (var capture = ClassicRawPermit.acquire(first, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            pending = ClassicPendingLoad.capture(input, ClassicRootBundle.OwnerType.HATCH, capture);
            first.captured(pending, capture);
        }
        try (var foreign = ClassicRawPermit.acquire(second, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            assertThrows(IllegalStateException.class, () -> second.captured(pending, foreign));
            assertTrue(second.pending().isEmpty()); assertFalse(second.loaded());
        }
        try (var foreign = ClassicRawPermit.emission(second)) {
            CompoundTag output = new CompoundTag(); output.putString("unchanged", "value");
            assertThrows(IllegalStateException.class, () -> pending.emit(output, foreign));
            assertEquals(1, output.size()); assertEquals("value", output.getString("unchanged"));
        }
    }
}
