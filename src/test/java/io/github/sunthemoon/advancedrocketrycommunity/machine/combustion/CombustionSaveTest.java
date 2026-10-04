package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CombustionSaveTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final CombustionBurn.State STATE = new CombustionBurn.State(19_960, 1_000_000, 987_654);

    @Test void roundTripsFuelAndEmptyContainerWithoutChangingCredit() {
        for (ItemStack fuel : new ItemStack[]{ItemStack.EMPTY, new ItemStack(Items.COAL, 64), new ItemStack(Items.BUCKET, 7)}) {
            CompoundTag root = CombustionSave.encode(STATE, fuel);
            var saved = CombustionSave.decode(root);
            assertEquals(STATE, saved.burn());
            assertTrue(ItemStack.matches(fuel, saved.fuel()));
            assertEquals(root, CombustionSave.encode(saved.burn(), saved.fuel()));
        }
    }

    @Test void missingUnknownFutureAndMistypedFieldsAreNotDefaults() {
        CompoundTag valid = CombustionSave.encode(STATE, ItemStack.EMPTY);
        for (String key : valid.getAllKeys()) {
            CompoundTag missing = valid.copy();
            missing.remove(key);
            assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(missing));
            CompoundTag wrong = valid.copy();
            wrong.put(key, StringTag.valueOf("retain"));
            assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(wrong));
        }
        CompoundTag future = valid.copy();
        future.putInt("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(future));
        valid.putInt("unknown", 7);
        assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(valid));
        assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(IntTag.valueOf(8)));
    }

    @Test void countsIdsAndCreditMustBeRepresentableWithoutLoss() {
        CompoundTag valid = CombustionSave.encode(STATE, new ItemStack(Items.COAL));
        for (int count : new int[]{0, -1, 65, 127}) {
            CompoundTag bad = valid.copy();
            bad.getCompound("fuel").putByte("Count", (byte) count);
            assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(bad));
        }
        CompoundTag unknown = valid.copy();
        unknown.getCompound("fuel").putString("id", "missing_mod:unknown_fuel");
        assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(unknown));
        for (String key : new String[]{"energy", "duration", "remaining"}) {
            CompoundTag bad = valid.copy();
            bad.putInt(key, Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(bad));
        }
    }

    @Test void oversizedAndDeepPayloadsArePreflightedBeforeCopying() {
        CompoundTag huge = new CompoundTag();
        huge.putByteArray("payload", new byte[CombustionSave.MAX_BYTES]);
        assertFalse(CombustionSave.bounded(huge));
        assertThrows(IllegalArgumentException.class, () -> CombustionSave.decode(huge));
        CompoundTag root = new CompoundTag();
        CompoundTag cursor = root;
        for (int i = 0; i < 40; i++) {
            CompoundTag child = new CompoundTag();
            cursor.put("child", child);
            cursor = child;
        }
        ItemStack deep = new ItemStack(Items.COAL);
        deep.setTag(root);
        assertFalse(CombustionSave.safeStack(deep));
        assertFalse(CombustionSave.fits(STATE, deep));
    }

    @Test void aggregateLimitIncludesTheStateAndItemFraming() {
        ItemStack fuel = new ItemStack(Items.COAL);
        CompoundTag payload = new CompoundTag();
        payload.putByteArray("payload", new byte[8_090]);
        fuel.setTag(payload);
        assertTrue(CombustionSave.safeStack(fuel));
        assertFalse(CombustionSave.fits(STATE, fuel));
    }
}
