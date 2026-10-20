package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketItemCargoSelectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final Function<EscrowItemCargoPayload, Status> ANY = delta -> Status.ELIGIBLE;

    @Test void loadTakesTheFirstNonemptyBufferSlotAndTheFirstMatchingSlotWithRoom() {
        var before = snapshot(CHEST, entry(0, stack(Items.STONE, 64)), entry(1, stack(Items.DIRT, 2)), entry(2, stack(Items.STONE, 60)));
        var buffer = buffer(new CompoundTag(), stack(Items.STONE, 5), stack(Items.DIRT, 7));
        var delta = select(before, buffer, true, ANY);
        assertEquals(1, delta.from()); assertEquals(2, delta.to()); assertEquals(4, count(delta.stack()));
        assertEquals(64, count(slots(RocketItemCargoMutation.apply(before, delta, true), 0).get(2)));
        assertEquals(1, count(RocketItemCargoMutation.buffer(buffer, delta, true).slots().get(1)), "The remainder stays in its slot");
    }

    @Test void aFullTargetFallsBackToTheFirstEmptySlotThenTheNextContainer() {
        var entries = new ArrayList<CompoundTag>();
        for (int slot = 0; slot < 27; slot++) { entries.add(entry(slot, stack(Items.STONE, 64))); }
        var full = block(0, state(CHEST), container(null, null, entries.toArray(CompoundTag[]::new)));
        var barrel = block(1, state(BARREL), container(null, null, entry(0, stack(Items.DIRT, 1)), entry(2, stack(Items.STONE, 64))));
        var before = snapshot(full, barrel);
        var delta = select(before, buffer(stack(Items.STONE, 5)), true, ANY);
        assertEquals(1, delta.cursor().x()); assertEquals(1, delta.to()); assertEquals(5, count(delta.stack()));
        var onlyFull = snapshot(full);
        assertEquals(Status.NO_CARGO_STORAGE, RocketItemCargoSelection.select(onlyFull, buffer(stack(Items.STONE, 5)), true, ANY).status());
    }

    @Test void sixteenStacksAndTheLastItemOfABufferSlot() {
        var pearls = snapshot(CHEST, entry(0, stack(Items.ENDER_PEARL, 10)));
        var buffer = buffer(stack(Items.ENDER_PEARL, 16));
        var delta = select(pearls, buffer, true, ANY);
        assertEquals(0, delta.to()); assertEquals(6, count(delta.stack()));
        assertEquals(10, count(RocketItemCargoMutation.buffer(buffer, delta, true).slots().get(0)));
        var last = select(snapshot(CHEST), buffer(new CompoundTag(), new CompoundTag(), stack(Items.STONE, 1)), true, ANY);
        assertEquals(2, last.from()); assertEquals(0, last.to()); assertEquals(1, count(last.stack()));
        var emptied = RocketItemCargoMutation.buffer(buffer(new CompoundTag(), new CompoundTag(), stack(Items.STONE, 1)), last, true);
        assertTrue(emptied.slots().stream().allMatch(CompoundTag::isEmpty));
    }

    @Test void unloadTakesTheFirstAcceptedContainerSlotAndPrefersAMatchingBufferSlot() {
        var before = snapshot(CHEST, entry(0, stack(Items.ENDER_PEARL, 12)), entry(1, stack(Items.DIRT, 5)));
        var mixed = buffer(stack(Items.STONE, 64), stack(Items.ENDER_PEARL, 14), new CompoundTag(), stack(Items.DIRT, 3));
        var delta = select(before, mixed, false, ANY);
        assertEquals(0, delta.from()); assertEquals(1, delta.to()); assertEquals(2, count(delta.stack()));
        assertEquals(10, count(slots(RocketItemCargoMutation.apply(before, delta, false), 0).get(0)), "The remainder stays in its slot");
        var full = buffer(stack(Items.STONE, 64), stack(Items.ENDER_PEARL, 16), stack(Items.DIRT, 60), stack(Items.GLASS, 1));
        var skip = select(before, full, false, ANY);
        assertEquals(1, skip.from()); assertEquals(2, skip.to()); assertEquals(4, count(skip.stack()));
        var whole = select(snapshot(BARREL, entry(3, stack(Items.STONE, 64))), buffer(), false, ANY);
        assertEquals(3, whole.from()); assertEquals(0, whole.to()); assertEquals(64, count(whole.stack()));
        var last = select(snapshot(BARREL, entry(26, stack(Items.STONE, 1))), buffer(), false, ANY);
        assertEquals(26, last.from()); assertEquals(1, count(last.stack()));
        var nowhere = buffer(stack(Items.STONE, 64), stack(Items.ENDER_PEARL, 16), stack(Items.DIRT, 64), stack(Items.GLASS, 1));
        assertEquals(Status.NO_CARGO_STORAGE, RocketItemCargoSelection.select(before, nowhere, false, ANY).status());
    }

    @Test void displayNamesNeverDecideAMergeAndInvalidContainerStacksStay() {
        var named = new ItemStack(Items.STONE, 3); named.setHoverName(Component.literal("Stone"));
        var before = snapshot(CHEST, entry(0, named.save(new CompoundTag())));
        var delta = select(before, buffer(stack(Items.STONE, 2)), true, ANY);
        assertEquals(1, delta.to(), "A renamed stone is not the same stack as stone");
        var caps = stack(Items.STONE, 3); caps.put("ForgeCaps", new CompoundTag());
        var mixed = snapshot(CHEST, entry(0, caps), entry(1, stack(Items.DIRT, 2)));
        var unload = select(mixed, buffer(), false, ANY);
        assertEquals(1, unload.from(), "A stack that fails the codec stays where it is");
        assertEquals(2, select(mixed, buffer(stack(Items.STONE, 1)), true, ANY).to(), "A capability stack is not a merge target");
        assertEquals(Status.INVALID_DATA, RocketItemCargoSelection.select(snapshot(CHEST, entry(0, caps)), buffer(), false, ANY).status());
    }

    @Test void lootOtherSubclassesAndUnreadableContainersAreSkippedWithTheirStatus() {
        var lootData = new CompoundTag(); lootData.putString("LootTable", "minecraft:chests/simple_dungeon");
        var loot = block(0, state(CHEST), new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, lootData));
        var other = block(1, new RocketBlockState(ResourceLocation.tryParse("fixture:copper_chest"), Map.of()),
                container(null, null, entry(0, stack(Items.STONE, 5))));
        var badData = new CompoundTag(); badData.putInt("Items", 3);
        var bad = block(2, state(BARREL), new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, badData));
        // R44: a double chest half is a 27-slot cargo target, so it is the first container offered here.
        var half = block(3, new RocketBlockState(CHEST, Map.of("facing", "north", "type", "left", "waterlogged", "false")),
                container(null, null, entry(0, stack(Items.STONE, 3))));
        var good = block(4, state(TRAPPED), container(null, null));
        var buffer = buffer(stack(Items.STONE, 1));
        assertEquals(3, select(snapshot(loot, other, bad, half, good), buffer, true, ANY).cursor().x());
        assertEquals(4, select(snapshot(loot, other, bad, good), buffer, true, ANY).cursor().x());
        assertEquals(Status.UNRESOLVED_LOOT, RocketItemCargoSelection.select(snapshot(loot), buffer, true, ANY).status());
        assertEquals(Status.NO_CARGO_STORAGE, RocketItemCargoSelection.select(snapshot(other), buffer, false, ANY).status(),
                "A nonempty subclass refuses as storage, not as an empty container");
        var unload = select(snapshot(other, half), buffer, false, ANY);
        assertEquals(3, unload.cursor().x()); assertEquals(0, unload.from()); assertEquals(3, count(unload.stack()));
        assertEquals(Status.UNSUPPORTED_DATA, RocketItemCargoSelection.select(snapshot(bad), buffer, true, ANY).status());
        assertEquals(Status.NO_CARGO_STORAGE, RocketItemCargoSelection.select(snapshot(), buffer, true, ANY).status());
        var called = new boolean[1];
        assertEquals(Status.NO_CARGO_STORAGE, RocketItemCargoSelection.select(snapshot(good), buffer(), true, d -> {
            called[0] = true; return Status.ELIGIBLE;
        }).status());
        assertFalse(called[0], "An empty buffer never reaches the eligibility check");
    }

    @Test void anEligibilityRefusalMovesToTheNextContainerAndInputsNeverChange() {
        var before = snapshot(block(0, state(CHEST), container(null, null)), block(1, state(BARREL), container(null, null)));
        var buffer = buffer(stack(Items.STONE, 3));
        var encoded = RocketSnapshotNbtCodec.encode(before); var savedBuffer = buffer.encode();
        var seen = new ArrayList<Integer>();
        var result = RocketItemCargoSelection.select(before, buffer, true, delta -> {
            seen.add(delta.cursor().x()); return delta.cursor().x() == 0 ? Status.CARGO_TOO_LARGE : Status.ELIGIBLE;
        });
        assertEquals(List.of(0, 1), seen); assertEquals(1, result.delta().cursor().x());
        assertEquals(Status.CARGO_TOO_LARGE, RocketItemCargoSelection.select(before, buffer, true, d -> Status.CARGO_TOO_LARGE).status());
        assertEquals(encoded, RocketSnapshotNbtCodec.encode(before)); assertEquals(savedBuffer, buffer.encode());
        assertEquals(result, RocketItemCargoSelection.select(before, buffer, true, delta -> delta.cursor().x() == 0
                ? Status.CARGO_TOO_LARGE : Status.ELIGIBLE), "Selection is deterministic");
    }

    @Test void aLocallyValidStackThatFailsTheFramedPayloadNeverReachesEligibility() {
        for (String kind : new String[] {"depth", "nodes"}) {
            var saved = stack(Items.STONE, 2); var tag = new CompoundTag(); saved.put("tag", tag);
            if (kind.equals("depth")) {
                for (int i = 2; i < 12; i++) { var next = new CompoundTag(); tag.put("c", next); tag = next; }
            } else { for (int i = 0; i < 380; i++) { tag.putByte("n" + i, (byte) 1); } }
            assertTrue(BoundedNbt.fits(saved, 4_096, 16, 512), kind + " must pass the local stack limits");
            var called = new boolean[1];
            Function<EscrowItemCargoPayload, Status> check = d -> { called[0] = true; return Status.ELIGIBLE; };
            var load = RocketItemCargoSelection.select(snapshot(CHEST), new EscrowItemBuffer(List.of(saved, new CompoundTag(),
                    new CompoundTag(), new CompoundTag())), true, check);
            assertEquals(Status.CARGO_TOO_LARGE, load.status(), kind);
            var unload = RocketItemCargoSelection.select(snapshot(CHEST, entry(0, saved)), buffer(), false, check);
            assertEquals(Status.CARGO_TOO_LARGE, unload.status(), kind);
            assertFalse(called[0], "A framed refusal must precede complete capture or reservation");
        }
    }

    private static EscrowItemCargoPayload select(RocketStructureSnapshot before, EscrowItemBuffer buffer, boolean load,
                                                 Function<EscrowItemCargoPayload, Status> eligibility) {
        var result = RocketItemCargoSelection.select(before, buffer, load, eligibility);
        assertEquals(Status.ELIGIBLE, result.status());
        return result.delta();
    }
}
