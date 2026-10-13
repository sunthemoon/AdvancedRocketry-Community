package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The W32 extension point: a closed, signed {@code special} field on chemical reactor recipes only. */
class ClassicSpecialRecipeTest {
    private static final ResourceLocation ID = ResourceLocation.tryParse("test:special");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static String json(String type, String special, String subject, int subjectCount, int outputs) {
        String specialField = special == null ? "" : ",\"special\":\"" + special + "\"";
        String rows = String.join(",", java.util.Collections.nCopies(outputs, "{\"item\":\"minecraft:charcoal\",\"count\":1}"));
        return "{\"type\":\"advancedrocketrycommunity:" + type + "\",\"schema_version\":1" + specialField
                + ",\"item_inputs\":[{\"ingredient\":" + subject + ",\"count\":" + subjectCount + "},"
                + "{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"count\":4}],"
                + "\"item_outputs\":[" + rows + "],\"fluid_inputs\":[],\"fluid_outputs\":[],"
                + "\"processing_time\":100,\"energy_per_tick\":10}";
    }

    private static String chest(String special) {
        return json("chemical_reacting", special, "{\"item\":\"minecraft:iron_chestplate\"}", 1, 0);
    }

    @Test void runtimeBindingsCoverExactlyTheCodecNames() {
        assertEquals(ClassicRecipeCodec.SPECIALS, Arrays.stream(ClassicSpecialRecipe.values())
                .map(ClassicSpecialRecipe::id).collect(Collectors.toSet()));
        for (String id : ClassicRecipeCodec.SPECIALS) { assertEquals(id, ClassicSpecialRecipe.of(id).orElseThrow().id()); }
        assertTrue(ClassicSpecialRecipe.of("space_breathing").isEmpty());
    }

    @Test void theSpecialNameIsPartOfTheCanonicalJsonAndItsSignature() {
        var plain = ClassicRecipeCodec.decode(ID, chest(null));
        var sealed = ClassicRecipeCodec.decode(ID, chest("space_breathing_chest"));
        assertNull(plain.special());
        assertEquals("space_breathing_chest", sealed.special());
        assertTrue(sealed.canonicalJson().contains("\"special\":\"space_breathing_chest\""));
        assertNotEquals(plain.jsonSignature(), sealed.jsonSignature(), "A process cannot resume across a special change");
        assertNotEquals(sealed.jsonSignature(), ClassicRecipeCodec.decode(ID, chest("space_breathing_head")).jsonSignature());
        assertEquals(sealed.jsonSignature(), ClassicRecipeCodec.decode(ID, sealed.canonicalJson()).jsonSignature());
        assertEquals(ClassicSpecialRecipe.SPACE_BREATHING_CHEST, new ClassicMachineRecipe(sealed).special().orElseThrow());
        assertTrue(new ClassicMachineRecipe(plain).special().isEmpty());
    }

    @Test void onlyAKnownStringOnTheChemicalReactorWithOneSubjectUnitAndRoomForItDecodes() {
        String subject = "{\"item\":\"minecraft:iron_chestplate\"}";
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, chest("space_breathing")));
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, chest("SPACE_BREATHING_CHEST")));
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID,
                chest("space_breathing_chest").replace("\"space_breathing_chest\"", "1")));
        for (String type : List.of("lathe", "arc_furnace", "cutting", "crystallizing", "laser_etching", "centrifuging")) {
            assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID,
                    json(type, "carbon_cartridge_refresh", subject, 1, 0)), type);
        }
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID,
                json("chemical_reacting", "space_breathing_chest", subject, 2, 0)), "The subject is one unit");
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID,
                json("chemical_reacting", "carbon_cartridge_refresh", subject, 1, 4)), "The subject needs an output slot");
        assertEquals(3, ClassicRecipeCodec.decode(ID, json("chemical_reacting", "carbon_cartridge_refresh", subject, 1, 3))
                .itemOutputs().size());
        String noItems = "{\"type\":\"advancedrocketrycommunity:chemical_reacting\",\"schema_version\":1,"
                + "\"special\":\"carbon_cartridge_refresh\",\"item_inputs\":[],\"item_outputs\":[],"
                + "\"fluid_inputs\":[{\"fluid\":\"minecraft:water\",\"amount\":10}],\"fluid_outputs\":[],"
                + "\"processing_time\":40,\"energy_per_tick\":20}";
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, noItems), "A special needs a subject");
    }

    @Test void theKernelDefinitionRetainsTheSubjectLikeTheEtcherLens() {
        var recipe = new ClassicMachineRecipe(ClassicRecipeCodec.decode(ID, chest("space_breathing_chest")));
        var definition = recipe.definition();
        var subject = definition.inputs().get(0);
        assertEquals(List.of("minecraft:iron_chestplate"), subject.alternatives());
        assertEquals(1, definition.outputs().size());
        var retained = definition.outputs().get(0);
        assertEquals(subject.key(subject.alternatives().get(0)), retained.key());
        assertEquals(ProcessResourceKind.ITEM, retained.key().kind());
        assertEquals(1, retained.amount());
        var plain = new ClassicMachineRecipe(ClassicRecipeCodec.decode(ID, chest(null))).definition();
        assertTrue(plain.outputs().isEmpty(), "An ordinary recipe gains no retained output");
    }

    @Test void aSubjectThatAnotherRowCouldDebitDisablesTheRecipe() {
        String overlap = json("chemical_reacting", "space_breathing_chest", "{\"item\":\"minecraft:iron_ingot\"}", 1, 0);
        var recipe = new ClassicMachineRecipe(ClassicRecipeCodec.decode(ID, overlap));
        assertThrows(IllegalArgumentException.class, recipe::definition);
        String partial = json("chemical_reacting", "space_breathing_chest",
                "[{\"item\":\"minecraft:iron_chestplate\"},{\"item\":\"minecraft:iron_ingot\"}]", 1, 0);
        assertThrows(IllegalArgumentException.class, new ClassicMachineRecipe(ClassicRecipeCodec.decode(ID, partial))::definition);
    }
}
