package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6PadProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void onlyTheSevenExactHookedSelectorsAreAdmitted() {
        for (var suffix : List.of("PREPARED", "REFUSED", "STOPPED", "BEFORE", "MOVED", "UNKNOWN", "RELEASED")) {
            var cut = "LF_S6_PAD_" + suffix;
            assertTrue(EscrowLoaderS6PadProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6PadProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6PadProbe.selected(true, cut + "_OTHER"));
        }
        assertFalse(EscrowLoaderS6PadProbe.selected(true, null));
        assertFalse(EscrowLoaderS6PadProbe.selected(true, "LF_M_R52_UNKNOWN"));
    }
    @Test void refusalComparesEveryFieldOfAllThreeObjectsByValue() {
        var before = new CompoundTag();
        for (var key : List.of("source", "journal", "pads")) {
            var nested = new CompoundTag(); nested.putString("arbitrary", "original"); before.put(key, nested);
        }
        assertTrue(EscrowLoaderS6PadProbe.sameRefusal(before, before.copy()));
        for (var key : List.of("source", "journal", "pads")) {
            var after = before.copy(); after.getCompound(key).putString("arbitrary", "changed");
            assertFalse(EscrowLoaderS6PadProbe.sameRefusal(before, after));
        }
    }
    @Test void liveObservationDoesNotInvokeEntitySaveSerialization() throws Exception {
        try (var stream = EscrowLoaderS6PadProbe.class.getResourceAsStream("EscrowLoaderS6PadProbe.class")) {
            var type = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(stream).accept(type, 0);
            var method = type.methods.stream().filter(value -> value.name.equals("tag")).findFirst().orElseThrow();
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    assertFalse(List.of("save", "saveWithoutId", "saveAsPassenger", "addAdditionalSaveData").contains(call.name),
                            "Native save serialization invalidates the governed observation token: " + call.name);
                }
            }
        }
    }
}
