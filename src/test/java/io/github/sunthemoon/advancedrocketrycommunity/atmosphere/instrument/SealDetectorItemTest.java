package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealDetectorItemTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void disabledBranchNeverInvokesReaderAndHasFixedUnavailableFields() {
        AtomicInteger queries = new AtomicInteger();
        var result = SealDetectorItem.response(() -> false, context -> { queries.incrementAndGet(); throw new AssertionError("reader"); }, null);
        assertEquals(0, queries.get());
        assertEquals(SealDetectorReading.disabled(), result);
    }

    @Test void admittedResponseInvokesReaderOnceAndPreservesUnavailableOrPendingCodes() {
        for (var expected : new SealDetectorReading[] {SealDetectorReading.unavailable(),
                SealDetectorReading.measured(SealDetectorReading.Boundary.OPEN, SealDetectorReading.Supply.PENDING)}) {
            AtomicInteger queries = new AtomicInteger();
            assertSame(expected, SealDetectorItem.response(() -> true, context -> { queries.incrementAndGet(); return expected; }, null));
            assertEquals(1, queries.get());
        }
        assertThrows(NullPointerException.class, () -> SealDetectorItem.response(() -> true, context -> null, null));
    }

    @Test void nullContextCannotAuthorizeUseAndTwoTickCadenceIsSharedItemKey() {
        var item = new SealDetectorItem(new Item.Properties(), () -> true);
        assertFalse(SealDetectorItem.heldMatches(null, item));
        assertEquals(net.minecraft.world.InteractionResult.FAIL, item.useOn(null));
        assertEquals(2, SealDetectorItem.COOLDOWN_TICKS);
        assertEquals(1, item.getMaxStackSize());
        assertFalse(item.canBeDepleted());
    }

    @Test void tooltipIsFixedAndDoesNotInspectHeldData() {
        var item = new SealDetectorItem(new Item.Properties(), () -> true);
        // Plain JUnit has no mod registration; the tooltip must ignore any supplied stack.
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putString("player_note", "untouched");
        var before = stack.getTag().copy();
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        item.appendHoverText(stack, null, tooltip, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        assertEquals(1, tooltip.size());
        assertEquals("tooltip.advancedrocketrycommunity.seal_detector.measurement_only",
                net.minecraft.network.chat.Component.Serializer.toJsonTree(tooltip.get(0)).getAsJsonObject().get("translate").getAsString());
        assertEquals(before, stack.getTag());
        assertEquals(1, stack.getCount());
    }
}
