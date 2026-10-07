package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClassicCheckpointChecksTest {
    @Test void directShorthandItemSelectionIsNormalizedOnlyForComparison() {
        var recipe = recipe("{\"item\":\"stone\"}", 3, false);
        assertDoesNotThrow(() -> ClassicCheckpointChecks.validateRows(plan(recipe, "minecraft:stone", 3), recipe));
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointChecks.validateRows(plan(recipe, "minecraft:dirt", 3), recipe));
        assertEquals("{\"item\":\"stone\"}", recipe.itemInputs().get(0).canonicalIngredientJson());
    }

    @Test void retainedTagSelectionDoesNotReResolveOrAssertTodaysTagMembership() {
        var recipe = recipe("{\"tag\":\"missing:historical\"}", 3, false);
        assertDoesNotThrow(() -> ClassicCheckpointChecks.validateRows(plan(recipe, "unregistered:recorded", 3), recipe));
        assertThrows(IllegalStateException.class, recipe::logicalDefinition);
        // Rows alone are not native admission; validate(frame,ticket) separately requires registered native data.
    }

    @Test void missingChangedRecipesAndAmountsCannotValidateARecordedPlan() {
        var recipe = recipe("{\"item\":\"stone\"}", 3, false); var plan = plan(recipe, "minecraft:stone", 3);
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointChecks.validateRows(plan, recipe("{\"item\":\"stone\"}", 2, false)));
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointChecks.validateRows(plan, recipe("{\"item\":\"stone\"}", 3, true)));
        var changed = ClassicRecipeCodec.decode(new ResourceLocation("test:changed"), recipe.canonicalJson());
        assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointChecks.validateRows(plan, changed));
    }

    @Test void selectedQuantitativeSnapshotsContainExactAmountsCapacitiesAndOneRevisionStep() {
        var recipe = recipe("{\"item\":\"stone\"}", 3, false); var plan = plan(recipe, "minecraft:stone", 3);
        var before = ClassicCheckpointChecks.quantitative(plan, true); var after = ClassicCheckpointChecks.quantitative(plan, false);
        assertEquals(7, before.revision()); assertEquals(8, after.revision());
        assertEquals(1, before.balances().size()); assertEquals(3, before.balances().values().iterator().next().amount());
        assertEquals(64, before.balances().values().iterator().next().capacity());
        assertEquals(0, after.balances().values().iterator().next().amount()); assertNotEquals(before.fingerprint(), after.fingerprint());
    }

    @Test void workWithoutAPreparedPlanStillRequiresExactRecipeIdentityDurationAndEnergy() {
        var recipe = recipe("{\"item\":\"stone\"}", 3, false);
        var state = io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState.RUNNING;
        var matching = new ClassicProcessFrame.Work(recipe.id(), recipe.jsonSignature(), 1, 1, 0, 0, 1, state);
        assertDoesNotThrow(() -> ClassicCheckpointChecks.validateWork(matching, recipe));
        for (var wrong : List.of(
                new ClassicProcessFrame.Work(new ResourceLocation("test:other"), recipe.jsonSignature(), 1, 1, 0, 0, 1, state),
                new ClassicProcessFrame.Work(recipe.id(), "0".repeat(64), 1, 1, 0, 0, 1, state),
                new ClassicProcessFrame.Work(recipe.id(), recipe.jsonSignature(), 2, 1, 0, 0, 1, state),
                new ClassicProcessFrame.Work(recipe.id(), recipe.jsonSignature(), 1, 2, 0, 0, 1, state))) {
            assertThrows(IllegalArgumentException.class, () -> ClassicCheckpointChecks.validateWork(wrong, recipe));
        }
    }

    @Test void planPreflightIsDataOnlyAndPreservesRecordedNativePayloads() {
        CompoundTag raw = rawPlan(); CompoundTag before = raw.copy();
        var plan = ClassicPlanCodec.preflight(raw);
        assertEquals(new UUID(1, 2), plan.transactionId());
        assertEquals(1, plan.entryCount());
        assertEquals("unregistered:recorded", plan.entries().get(0).resourceId());
        assertEquals(3, plan.entries().get(0).before().amount());
        assertEquals(0, plan.entries().get(0).after().amount());
        assertEquals(3, plan.allocations().get(0).amount());
        assertEquals(before, raw);
        // An unregistered ID here demonstrates only structural inspection, not native admission.
    }

    @Test void planPreflightRejectsScalarEnvelopeAndConservationErrorsBeforeNativeResolution() {
        for (java.util.function.Consumer<CompoundTag> change : List.<java.util.function.Consumer<CompoundTag>>of(
                raw -> raw.putIntArray("transaction_id", new int[] {1, 2, 3}),
                raw -> raw.putString("json_signature", "NOT_A_HASH"),
                raw -> raw.putInt("before_revision", 7),
                raw -> raw.putLong("after_revision", 9),
                raw -> raw.putLong("owner_generation", 0),
                raw -> raw.putString("unexpected", "value"),
                raw -> raw.getList("entries", 10).getCompound(0).putInt("slot", 4),
                raw -> raw.getList("entries", 10).getCompound(0).putLong("capacity", 2),
                raw -> raw.getList("entries", 10).getCompound(0).getCompound("before").putInt("Count", 3),
                raw -> raw.getList("entries", 10).getCompound(0).getCompound("after").putString("unexpected", "value"),
                raw -> raw.getList("allocations", 10).getCompound(0).putLong("amount", 2),
                raw -> raw.getList("allocations", 10).getCompound(0).getList("parts", 10)
                        .getCompound(0).putInt("entry_index", 1))) {
            CompoundTag raw = rawPlan(); change.accept(raw); CompoundTag before = raw.copy();
            assertThrows(IllegalArgumentException.class, () -> ClassicPlanCodec.preflight(raw));
            assertEquals(before, raw);
        }
    }

    private static CompoundTag rawPlan() {
        CompoundTag raw = new CompoundTag(); raw.putUUID("transaction_id", new UUID(1, 2));
        raw.putLong("owner_generation", 1); raw.putLong("ordinal", 1);
        raw.putString("recipe_id", "test:recipe"); raw.putString("json_signature", "0".repeat(64));
        raw.putLong("before_revision", 7); raw.putLong("after_revision", 8);
        raw.putString("before_native_hash", "1".repeat(64)); raw.putString("after_native_hash", "2".repeat(64));
        CompoundTag entry = new CompoundTag(); entry.putString("bank_channel", "item_input.0.0.0");
        entry.putString("kind", "item_input"); entry.putString("resource_id", "unregistered:recorded");
        entry.putLong("capacity", 64); entry.putInt("slot", 0);
        CompoundTag payload = new CompoundTag(); payload.putString("id", "unregistered:recorded"); payload.putByte("Count", (byte) 3);
        entry.put("before", payload); entry.put("after", new CompoundTag());
        ListTag entries = new ListTag(); entries.add(entry); raw.put("entries", entries);
        CompoundTag row = new CompoundTag(); row.putString("direction", "input"); row.putString("kind", "item");
        row.putInt("recipe_index", 0); row.putString("resource_id", "unregistered:recorded"); row.putLong("amount", 3);
        CompoundTag part = new CompoundTag(); part.putInt("entry_index", 0); part.putLong("amount", 3);
        ListTag parts = new ListTag(); parts.add(part); row.put("parts", parts);
        ListTag rows = new ListTag(); rows.add(row); raw.put("allocations", rows); return raw;
    }

    private static ClassicValidatedRecipe recipe(String ingredient, int count, boolean output) {
        return ClassicRecipeCodec.decode(new ResourceLocation("test:recipe"),
                "{\"type\":\"advancedrocketrycommunity:lathe\",\"schema_version\":1,\"item_inputs\":[{\"ingredient\":" + ingredient
                        + ",\"count\":" + count + "}],\"item_outputs\":" + (output ? "[{\"item\":\"minecraft:stone\",\"count\":1}]" : "[]")
                        + ",\"fluid_inputs\":[],\"fluid_outputs\":[],\"processing_time\":1,\"energy_per_tick\":1}");
    }

    private static ClassicNativePlan plan(ClassicValidatedRecipe recipe, String id, int amount) {
        var bank = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 0, 0, 0);
        CompoundTag payload = new CompoundTag(); payload.putString("id", id); payload.putByte("Count", (byte) amount);
        var entry = new ClassicNativeEntry(bank, OptionalInt.of(0), id, 64,
                OwnedNativeTag.captureData(payload, ClassicBankKind.ITEM_INPUT),
                OwnedNativeTag.captureData(new CompoundTag(), ClassicBankKind.ITEM_INPUT));
        var row = new ClassicAllocation(true, ProcessResourceKind.ITEM, 0, id, amount, List.of(new ClassicAllocationPart(0, amount)));
        return new ClassicNativePlan(new UUID(1, 2), 1, 1, recipe.id(), recipe.jsonSignature(), 7, 8,
                "0".repeat(64), "1".repeat(64), List.of(entry), List.of(row));
    }
}
