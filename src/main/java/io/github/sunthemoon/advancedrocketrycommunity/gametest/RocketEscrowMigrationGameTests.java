package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketEscrowMigrationGameTests {
    private RocketEscrowMigrationGameTests() { }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void genuineSchemaTwoMigratesOnlyItsWrapperAndNeutralSection(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var before = ResourceEscrowSavedData.existing(server);
        UUID ledgerBefore = before == null ? null : before.ledger().id();
        BlockPos origin = new BlockPos(3, 2, 3);
        RocketFlightGameTestFixtures.placeLegalRocket(helper, origin);
        var snapshot = RocketFlightGameTestFixtures.successfulSnapshot(helper, origin);
        RocketEntity original = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(original != null, "No assembly target");
        original.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        CompoundTag current = original.saveWithoutId(new CompoundTag());
        var expected = current.getCompound("RocketEntityData").copy();
        helper.assertTrue(expected.getInt("schema_version") == 3
                && expected.getCompound("resource_escrow").equals(RocketEscrowSection.neutral().encode()), "New assembly is not neutral");
        var legacy = current.copy(); var data = legacy.getCompound("RocketEntityData");
        data.putInt("schema_version", 2); data.remove("resource_escrow");
        for (int restart = 0; restart < 3; restart++) {
            RocketEntity restored = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(restored != null, "No restore target"); restored.load(legacy);
            helper.assertTrue(restored.operational(), "Schema-2 or repeated schema-3 load failed");
            legacy = restored.saveWithoutId(new CompoundTag());
            helper.assertTrue(expected.equals(legacy.getCompound("RocketEntityData")), "Migration changed identity, resources, snapshot or flight data");
        }
        RocketEntity transferred = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(transferred != null, "No transfer target");
        transferred.initializeTransferred(snapshot, original.assemblyTransactionId().orElseThrow(), original.ownerId().orElseThrow(), original.flightData().orElseThrow());
        helper.assertTrue(transferred.escrowSection().equals(RocketEscrowSection.neutral()), "New transferred identity is not neutral");
        var after = ResourceEscrowSavedData.existing(server);
        helper.assertTrue(java.util.Objects.equals(ledgerBefore, after == null ? null : after.ledger().id()), "Neutral migration created or replaced a ledger");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void invalidSectionAndUnknownWrapperAreQuarantinedVerbatim(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        RocketFlightGameTestFixtures.placeLegalRocket(helper, origin);
        var snapshot = RocketFlightGameTestFixtures.successfulSnapshot(helper, origin);
        RocketEntity original = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(original != null, "No fixture target"); original.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        for (boolean section : new boolean[]{true, false}) {
            var saved = original.saveWithoutId(new CompoundTag()); var raw = saved.getCompound("RocketEntityData");
            if (section) { raw.getCompound("resource_escrow").putLong("resource_epoch", 1); }
            else { raw.putString("unknown_wrapper_field", "retained"); }
            RocketEntity invalid = ModEntities.ROCKET.get().create(helper.getLevel());
            helper.assertTrue(invalid != null, "No invalid fixture target"); invalid.load(saved);
            helper.assertTrue(!invalid.operational(), "Invalid envelope remained operational");
            helper.assertTrue(raw.equals(invalid.saveWithoutId(new CompoundTag()).getCompound("RocketEntityData")), "Quarantine altered the raw protected wrapper");
        }
        helper.succeed();
    }
}
