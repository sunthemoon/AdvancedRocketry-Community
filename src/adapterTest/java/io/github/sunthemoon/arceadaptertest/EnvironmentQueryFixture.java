package io.github.sunthemoon.arceadaptertest;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentQueries;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.ServerEnvironmentReadyEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** API-only observer; its command is opt-in and never changes the queried world. */
final class EnvironmentQueryFixture {
    private static volatile EnvironmentQueries queries;

    private EnvironmentQueryFixture() { }

    static void install() {
        MinecraftForge.EVENT_BUS.addListener(EnvironmentQueryFixture::ready);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, EnvironmentQueryFixture::stopping);
        if (Boolean.getBoolean("arce_adapter_test.environmentSmoke")) {
            MinecraftForge.EVENT_BUS.addListener(EnvironmentQueryFixture::commands);
        }
    }

    private static void ready(ServerEnvironmentReadyEvent event) {
        if (queries != null) {
            throw new IllegalStateException("Environment fixture received two live handles");
        }
        queries = event.queries();
        LogUtils.getLogger().info("Environment query handle ready (API {}.{})",
                ApiVersions.current().major(), ApiVersions.current().minor());
    }

    static EnvironmentQueries queries() {
        EnvironmentQueries result = queries;
        if (result == null) { throw new IllegalStateException("Environment fixture has no server handle"); }
        return result;
    }

    private static void stopping(ServerStoppingEvent event) {
        EnvironmentQueries retained = queries;
        queries = null;
        if (retained == null) { return; }
        try {
            retained.at(Level.OVERWORLD, BlockPos.ZERO);
        } catch (IllegalStateException expired) {
            LogUtils.getLogger().info("Environment query handle expired at server stopping");
            return;
        }
        throw new IllegalStateException("Environment fixture handle survived server stopping");
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce_env_probe").requires(source -> source.hasPermission(2))
                .then(Commands.argument("label", StringArgumentType.word())
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context -> {
                                    String label = StringArgumentType.getString(context, "label");
                                    if (!label.matches("[a-z_]{1,32}")) { throw new IllegalArgumentException("Bad probe label"); }
                                    ResourceLocation id = ResourceLocationArgument.getId(context, "dimension");
                                    ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
                                    BlockPos position = BlockPosArgument.getBlockPos(context, "position");
                                    var level = context.getSource().getServer().getLevel(dimension);
                                    boolean before = level != null && level.getChunkSource().getChunkNow(
                                            position.getX() >> 4, position.getZ() >> 4) != null;
                                    var snapshot = queries().at(dimension, position);
                                    boolean after = level != null && level.getChunkSource().getChunkNow(
                                            position.getX() >> 4, position.getZ() >> 4) != null;
                                    JsonObject report = new JsonObject();
                                    report.addProperty("label", label);
                                    report.addProperty("dimension", id.toString());
                                    report.addProperty("resolved", snapshot.isPresent());
                                    report.addProperty("loaded_before", before);
                                    report.addProperty("loaded_after", after);
                                    snapshot.ifPresent(value -> report.add("snapshot", json(value)));
                                    LogUtils.getLogger().info("ARCE_ENV_PROBE {}", report);
                                    return 1;
                                })))));
    }

    private static JsonObject json(EnvironmentSnapshot value) {
        JsonObject result = new JsonObject();
        result.addProperty("body", value.bodyId().toString());
        result.addProperty("locus", value.locus().name());
        value.instanceId().ifPresent(id -> result.addProperty("instance", id.toString()));
        result.addProperty("gravity", value.gravityMultiplier());
        result.addProperty("vacuum", value.vacuum());
        value.atmosphere().ifPresent(profile -> {
            JsonObject atmosphere = new JsonObject();
            atmosphere.addProperty("pressure", profile.pressure());
            atmosphere.addProperty("breathable", profile.breathable());
            atmosphere.addProperty("temperature", profile.temperatureKelvin());
            atmosphere.addProperty("profile", profile.profile().toString());
            result.add("atmosphere", atmosphere);
        });
        return result;
    }
}
