package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Operator-only bounded inspection/cancellation/release. ACTIVE pairs still require offline repair. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class ResearchCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var node = Commands.literal("ground-survey").requires(source -> source.hasPermission(2));
        for (String action : new String[]{"inspect", "cancel", "release"}) {
            node.then(Commands.literal(action).then(Commands.argument("job", UuidArgument.uuid()).executes(context -> {
                var source = context.getSource(); UUID id = UuidArgument.getUuid(context, "job");
                var data = SatelliteMissionSavedData.get(source.getServer());
                if (!data.operational()) { source.sendFailure(Component.translatable(ResearchStatus.REGISTRY_BLOCKED.key())); return 0; }
                if (action.equals("inspect")) {
                    var job = data.groundSurveyQuery(jobs -> jobs.job(id)).orElse(null);
                    if (job == null) { source.sendFailure(Component.literal("Ground survey not found")); return 0; }
                    String hint = data.groundSurveyQuery(jobs -> jobs.recoveryHint(id));
                    // R4: an offline-only case prints its repair; any other quarantine names the online release.
                    String recovery = hint != null ? hint : job.phase() == GroundSurveyJob.Phase.QUARANTINED
                            ? "Operator release: /arce ground-survey release " + id : null;
                    source.sendSuccess(() -> Component.literal("job=" + id + " owner=" + job.ownerId() + " instance=" + job.instanceId()
                            + " source=" + job.source() + " phase=" + job.phase() + " survey=" + job.surveyProgress() + "/" + job.surveyTicks()
                            + " analysis=" + job.analysis().state() + ":" + job.analysis().progress() + "/" + job.analysis().ticks()
                            + " claim=" + job.analysis().claim() + (recovery == null ? "" : " recovery=" + recovery)), false); return 1;
                }
                var result = data.groundSurveys(jobs -> action.equals("cancel")
                        ? jobs.cancel(id, source.getEntity() == null ? new UUID(0, 0) : source.getEntity().getUUID(), true,
                                source.getServer().overworld().getGameTime()) : jobs.release(id));
                source.sendSuccess(() -> Component.literal(result.code() + ": " + result.reason()), false);
                AdvancedRocketryCommunity.LOGGER.info("ARCE_GROUND_SURVEY_ADMIN action={} job={} code={}", action, id, result.code());
                return result.changed() ? 1 : 0;
            })));
        }
        event.getDispatcher().register(Commands.literal("arce").then(node));
    }
    private ResearchCommands() { }
}
