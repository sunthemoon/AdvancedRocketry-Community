package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerReport;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketIssueFeedbackGameTests {
    private RocketIssueFeedbackGameTests() { }

    @GameTest(template = "empty", batch = "rocket_feedback", timeoutTicks = 100)
    public static void entityNameUsesTheRegisteredEntityTranslationAndHonorsCustomNames(GameTestHelper helper) {
        var rocket = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(rocket != null, "Registered rocket factory returned null");
        helper.assertTrue(rocket.getDisplayName().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("entity." + ModIdentity.MOD_ID + ".rocket"),
                "Rocket still uses the menu title as its entity name");
        helper.assertTrue(rocket.getDisplayName().getString().equals("Assembled Rocket"), "Entity name has no English translation");
        rocket.setCustomName(Component.literal("Surveyor"));
        helper.assertTrue(rocket.getDisplayName().getString().equals("Surveyor"), "Entity name ignores a custom name");
        rocket.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "rocket_feedback", timeoutTicks = 100)
    public static void failureReportKeepsSchemaAndOldValuesButSeparatesNewDiagnostics(GameTestHelper helper) {
        var assembler = new RocketAssemblerBlockEntity(BlockPos.ZERO, ModBlocks.ROCKET_ASSEMBLER.get().defaultBlockState());
        var issue = new RocketValidationIssue(RocketValidationCode.TARGET_OCCUPIED, new RocketPosition(1, 2, 3),
                Map.of("coordinate_space", "absolute", "detail", "occupied fixture"));
        String reason = RocketInteraction.issueDetail(issue);
        helper.assertTrue(reason.equals("Rocket disassembly target is occupied"), "New report is not a translated reason");
        RocketInteraction.update(assembler, issue.code(), null, reason, helper.getLevel());
        assembler.setOperatorDetail(RocketInteraction.diagnosticDetail(issue));
        var saved = assembler.saveWithoutMetadata();
        var data = saved.getCompound("RocketAssemblerData");
        helper.assertTrue(data.getInt("schema_version") == 1 && data.getAllKeys().equals(
                Set.of("schema_version", "code", "detail", "updated_at_game_time")), "Report format changed");
        helper.assertTrue(data.getString("detail").equals(reason), "Saved report still includes the diagnostic map");
        helper.assertTrue(!data.toString().contains("coordinate_space"), "Operator diagnostic leaked into NBT");
        helper.assertTrue(assembler.operatorDetail().contains("coordinate_space=absolute"), "Operator lost the diagnostic map");

        String oldDetail = "target_occupied at 1,2,3 {coordinate_space=absolute, detail=old}";
        data.putString("detail", oldDetail);
        assembler.load(saved);
        helper.assertTrue(assembler.saveWithoutMetadata().getCompound("RocketAssemblerData").getString("detail").equals(oldDetail),
                "Existing schema-1 report was migrated or rewritten");
        helper.assertTrue(assembler.operatorDetail().isEmpty(), "Loading another report retained a stale diagnostic");
        RocketInteraction.update(assembler, RocketValidationCode.REGION_BUSY, null,
                "unfinished transaction owns the region", helper.getLevel());
        helper.assertTrue(assembler.report().detail().equals("Rocket region is busy")
                        && assembler.operatorDetail().equals("unfinished transaction owns the region"),
                "A preflight failure bypassed translated reports or lost its operator detail");
        assembler.setOperatorDetail("x".repeat(2_000));
        helper.assertTrue(assembler.operatorDetail().length() == 1_024, "Transient diagnostic is unbounded");
        assembler.setReport(new RocketAssemblerReport(issue.code(), null, "x".repeat(300), 0));
        helper.assertTrue(assembler.report().detail().length() == 256 && assembler.operatorDetail().isEmpty(),
                "Report bound or diagnostic lifetime changed");

        Component[] received = {null};
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "rocketWording")) {
            @Override public void displayClientMessage(Component message, boolean overlay) { received[0] = message; }
        };
        RocketInteraction.notify(player, issue.code(), RocketInteraction.diagnosticDetail(issue));
        helper.assertTrue(received[0] != null && received[0].getContents() instanceof TranslatableContents text
                        && text.getKey().equals(issue.translationKey()) && received[0].getSiblings().isEmpty(),
                "Player message lost localization or appended developer diagnostics");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "rocket_feedback", timeoutTicks = 100)
    public static void nativeScanFailureSeparatesReportAndOperatorDetails(GameTestHelper helper) {
        var position = new BlockPos(1, 2, 1);
        helper.setBlock(position, ModBlocks.ROCKET_ASSEMBLER.get());
        helper.setBlock(position.above(), Blocks.BEDROCK);
        var assembler = (RocketAssemblerBlockEntity) helper.getBlockEntity(position);
        var scans = new RocketAssemblerScanService(new RocketTransactionExecutor(RocketBlockEntityAdapters.defaults()),
                RocketComponentCatalog.empty());
        for (boolean assemble : new boolean[]{false, true}) {
            helper.assertTrue(scans.requestAdminAssembler(helper.getLevel(), helper.absolutePos(position), UUID.randomUUID(),
                    assemble) == RocketValidationCode.SCAN_IN_PROGRESS, "Failure scan was not queued");
            scans.tick(helper.getLevel().getServer());
            helper.assertTrue(scans.pendingScans() == 0, "One-block failure scan did not finish");
            helper.assertTrue(assembler.report().detail().equals(Component.translatable(assembler.report().code().translationKey()).getString()),
                    "Failure service stored developer details instead of the reason");
            helper.assertTrue(assembler.operatorDetail().contains("coordinate_space=absolute"), "Failure service lost operator diagnostics");
        }
        helper.succeed();
    }
}
