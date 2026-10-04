package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicResourcesTest {
    private static final UUID OWNER = new UUID(17, 29);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aggregateReplacementAdvancesOnceSimulationAndNoOpDoNotAdvance() {
        ClassicResources empty = ClassicResources.empty(OWNER);
        List<ClassicResourceBank> changes = List.of(bank(0), bank(1), bank(2));
        assertSame(empty, empty.replace(changes, true));
        assertTrue(empty.banks().isEmpty()); assertEquals(0, empty.revision());
        ClassicResources populated = empty.replace(changes, false);
        assertEquals(1, populated.revision()); assertEquals(3, populated.banks().size());
        assertSame(populated, populated.replace(changes, false));
        ClassicResourceBank first = populated.banks().get(0);
        ClassicResourceBank changed = first.withItem(0, ClassicResourceBankTest.taggedItem(Items.STONE, 12, "batch"));
        ClassicResources replacement = populated.replace(List.of(changed), false);
        assertEquals(2, replacement.revision()); assertTrue(populated.banks().get(0).item(0).isEmpty());
        assertEquals(populated.banks().get(1).key(), replacement.banks().get(1).key());
        assertThrows(UnsupportedOperationException.class, () -> replacement.banks().clear());
    }

    @Test void retained64BanksAndAll256SlotsAreRepresentableWithoutJournalEnlargement() {
        List<ClassicResourceBank> banks = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            var stack = ClassicResourceBankTest.taggedItem(Items.STONE, 64, "s" + i);
            banks.add(ClassicResourceBank.items(key(i), List.of(stack, stack, stack, stack)));
        }
        ClassicResources full = ClassicResources.empty(OWNER).replace(banks, false);
        assertEquals(64, full.banks().size());
        assertEquals(16_384, full.banks().stream().flatMap(bank -> bank.items().stream()).mapToInt(stack -> stack.getCount()).sum());
        assertEquals(ClassicResourcesCodec.encode(full), ClassicResourcesCodec.encode(
                ClassicResourcesCodec.decode(ClassicResourcesCodec.encode(full), OWNER).value().orElseThrow()));
        assertThrows(IllegalArgumentException.class, () -> full.replace(List.of(bank(64)), false));
        assertEquals(64, full.banks().size()); assertEquals(1, full.revision());
        assertSame(full, full.replace(List.of(), false));
    }

    @Test void duplicatesAndOversizedAggregateRefuseIntactRatherThanDropOtherBanks() {
        ClassicResources resources = ClassicResources.empty(OWNER).replace(List.of(bank(0)), false);
        CompoundTag original = ClassicResourcesCodec.encode(resources);
        assertThrows(IllegalArgumentException.class, () -> resources.replace(List.of(bank(1), bank(1)), false));
        List<ClassicResourceBank> large = new ArrayList<>();
        for (int i = 1; i < 4; i++) {
            var stack = ClassicResourceBankTest.taggedItem(Items.STONE, 1, "large");
            stack.getTag().putByteArray("bytes", new byte[12_000]);
            large.add(ClassicResourceBank.items(key(i), ClassicResourceBankTest.slots(stack)));
        }
        assertThrows(IllegalArgumentException.class, () -> resources.replace(large, false));
        assertThrows(IllegalArgumentException.class, () -> resources.replace(large, true));
        assertEquals(original, ClassicResourcesCodec.encode(resources));
    }

    @Test void revisionExhaustionRefusesChangedCandidatesButAllowsReadAndNoOp() {
        ClassicResources exhausted = ClassicResources.restore(OWNER, Long.MAX_VALUE, List.of(bank(0)));
        CompoundTag original = ClassicResourcesCodec.encode(exhausted);
        assertSame(exhausted, exhausted.replace(List.of(bank(0)), false));
        assertThrows(IllegalStateException.class, () -> exhausted.replace(List.of(bank(1)), false));
        assertThrows(IllegalStateException.class, () -> exhausted.replace(List.of(bank(1)), true));
        assertEquals(original, ClassicResourcesCodec.encode(exhausted));
        assertThrows(IllegalArgumentException.class, () -> ClassicResources.restore(OWNER, -1, List.of()));
    }

    @Test void orderingIsRetainedAndDistinctRolesAtSameCoordinatesDoNotMerge() {
        ClassicResourceBank first = bank(4);
        ClassicResourceBank second = ClassicResourceBank.empty(new ClassicBankKey(ClassicBankKind.ITEM_OUTPUT, 4, 64, -4));
        ClassicResources resources = ClassicResources.empty(OWNER).replace(List.of(first, second, bank(2)), false);
        assertEquals(List.of(first.key(), second.key(), key(2)), resources.banks().stream().map(ClassicResourceBank::key).toList());
        assertEquals(3, resources.banks().size());
        assertEquals(3, resources.replace(List.of(bank(4)), false).banks().size());
    }

    static ClassicBankKey key(int i) { return new ClassicBankKey(ClassicBankKind.ITEM_INPUT, i, 64, -i); }
    static ClassicResourceBank bank(int i) { return ClassicResourceBank.empty(key(i)); }
}
