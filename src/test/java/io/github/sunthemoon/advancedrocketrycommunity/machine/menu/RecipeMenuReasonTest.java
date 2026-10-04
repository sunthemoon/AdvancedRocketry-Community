package io.github.sunthemoon.advancedrocketrycommunity.machine.menu;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import org.junit.jupiter.api.Test;

class RecipeMenuReasonTest {
    @Test
    void whitelistIdsAndSubjectsAreExplicitAndDisplayOnly() {
        String[] subjects = {"", "recipe_missing", "recipe_changed", "recipe_tags_invalid",
                "signature_migration_pending", "signature_migration_unproven", "retained_plan_invalid"};
        for (int id = 0; id < subjects.length; id++) {
            RecipeMenuReason reason = RecipeMenuReason.fromNetworkId(id);
            assertEquals(id, reason.networkId());
            ProcessFailure failure = id == 0 ? ProcessFailure.NONE
                    : new ProcessFailure(ProcessFailureCode.INVALID_RECIPE, subjects[id]);
            assertEquals(reason, RecipeMenuReason.fromFailure(failure));
            assertEquals(subjects[id], failure.subject());
            assertEquals(id == 0 ? ProcessFailureCode.NONE : ProcessFailureCode.INVALID_RECIPE, failure.code());
        }
    }

    @Test
    void arbitrarySubjectAndEveryOtherSignedShortUseGenericFallback() {
        assertEquals(RecipeMenuReason.NONE, RecipeMenuReason.fromFailure(ProcessFailure.NONE));
        for (String subject : new String[] {"Recipe_missing", " recipe_missing", "recipe_missing ",
                "minecraft:secret", "signature_migration_unproven-extra", ""}) {
            assertEquals(RecipeMenuReason.NONE, RecipeMenuReason.fromFailure(
                    new ProcessFailure(ProcessFailureCode.RECOVERY_DIVERGED, subject)));
        }
        for (int id = Short.MIN_VALUE; id <= Short.MAX_VALUE; id++) {
            if (id < 1 || id > 6) {
                assertEquals(RecipeMenuReason.NONE, RecipeMenuReason.fromNetworkId(id));
            }
        }
        assertEquals(RecipeMenuReason.NONE, RecipeMenuReason.fromNetworkId(Integer.MAX_VALUE));
    }
}
