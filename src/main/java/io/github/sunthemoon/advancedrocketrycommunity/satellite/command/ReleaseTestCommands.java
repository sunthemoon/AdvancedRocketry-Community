package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * C9 packaged-server evidence hooks, registered only with {@code -Dadvancedrocketrycommunity.releaseTestHooks=true}
 * (never in normal play). They drive the production terminal and registry: a logical-clock advance, flush and
 * tick timings, a terminal-bound asteroid mission set up through real terminal buttons, and crash cuts that halt
 * the JVM right after one store became durable and before the other (ADR-051 section 11). Every line they print
 * starts with {@code ARCE_RELEASE_TEST}.
 */
public final class ReleaseTestCommands {
    public static final String PROPERTY = "advancedrocketrycommunity.releaseTestHooks";
    /** The process exit status of a deliberate crash cut. */
    public static final int HALT_STATUS = 75;
    private static final String KEY = "SatelliteTerminal";

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("satellite")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("advance")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 10_000_000))
                                        .executes(this::advance)))
                        .then(Commands.literal("perf")
                                .then(Commands.literal("flush")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                                .executes(this::flushTiming)))
                                .then(Commands.literal("ticks").executes(this::ticks))
                                .then(Commands.literal("worst-case").executes(this::worstCase)))
                        .then(Commands.literal("terminal")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.literal("setup")
                                                .then(Commands.argument("owner", UuidArgument.uuid())
                                                        .executes(this::setup)))
                                        .then(Commands.literal("inspect").executes(this::inspect))
                                        .then(Commands.literal("cut")
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .executes(this::cut))))))));
    }

    /** Advances the registry's logical clock by {@code ticks} (ADR-010 monotonic clock), for backlogs. */
    private int advance(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        long before = data.logicalGameTime();
        data.completeDue(server.overworld().getGameTime() + ticks);
        return report(context, "advance before=" + before + " after=" + data.logicalGameTime());
    }

    /** Times {@code count} barrier flushes of the current registry (ADR-050 section 2 cost). */
    private int flushTiming(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int count = IntegerArgumentType.getInteger(context, "count");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        long[] nanos = new long[count];
        for (int index = 0; index < count; index++) {
            long started = System.nanoTime();
            data.flush(server);
            nanos[index] = System.nanoTime() - started;
        }
        Arrays.sort(nanos);
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(SatelliteMissionSavedData.DATA_NAME + ".dat");
        long bytes;
        try {
            bytes = Files.size(file);
        } catch (IOException exception) {
            bytes = -1L;
        }
        return report(context, "flush count=" + count + " missions=" + data.missions().size()
                + " file_bytes=" + bytes + " mean_ms=" + millis(Arrays.stream(nanos).sum() / count)
                + " p50_ms=" + millis(nanos[count / 2]) + " max_ms=" + millis(nanos[count - 1]));
    }

    /**
     * The last 100 server ticks: vanilla tick time plus the satellite post-tick work of the same tick (Forge fires
     * the post-tick event after vanilla records the tick time, so vanilla MSPT alone would omit the coalesced
     * flush), and that post-tick work alone.
     */
    private int ticks(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        long[] vanilla = server.tickTimes.clone();
        long[] post = io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime.postTickNanos();
        long[] total = new long[vanilla.length];
        for (int index = 0; index < total.length; index++) {
            total[index] = vanilla[index] + post[index];
        }
        Arrays.sort(total);
        Arrays.sort(post);
        return report(context, "ticks window=" + total.length + " tick=" + server.getTickCount()
                + " mean_ms=" + millis(Arrays.stream(total).sum() / total.length) + " p50_ms=" + millis(total[49])
                + " p95_ms=" + millis(total[94]) + " p99_ms=" + millis(total[98]) + " max_ms=" + millis(total[99])
                + " satellite_post_p99_ms=" + millis(post[98]) + " satellite_post_max_ms=" + millis(post[99]) + " "
                + SatelliteMissionSavedData.get(server).diagnostics());
    }

    /**
     * ADR-050 section 2 worst-case cost: the current root is widened, by cloning finished missions, idle
     * satellites and settled instances with fresh IDs, to the load bounds (8,192 missions, 4,096 satellites,
     * 2,048 instances). It is loaded as a separate, uninstalled registry and flushed three times to a scratch file;
     * the world's own registry is not touched.
     */
    private int worstCase(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        CompoundTag root = SatelliteMissionSavedData.get(server).save(new CompoundTag());
        int missions = widen(root.getList("missions", Tag.TAG_COMPOUND), "mission_id",
                tag -> List.of("claimed", "cancelled").contains(tag.getString("status")), 8_192);
        int satellites = widen(root.getList("satellites", Tag.TAG_COMPOUND), "satellite_id",
                tag -> !tag.contains("current_mission_id"), 4_096);
        int instances = widen(root.getList("instances", Tag.TAG_COMPOUND), "instance_id",
                tag -> List.of("available", "depleted", "expired").contains(tag.getString("state")), 2_048);
        SatelliteMissionSavedData widened = SatelliteMissionSavedData.load(root);
        if (!widened.operational()) {
            return report(context, "worst-case refused loaded=false");
        }
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("arce-c9-worst-case");
        Path file = directory.resolve(SatelliteMissionSavedData.DATA_NAME + ".dat");
        long[] nanos = new long[3];
        long bytes = -1L;
        try {
            Files.createDirectories(directory);
            for (int index = 0; index < nanos.length; index++) {
                long started = System.nanoTime();
                widened.flush(file);
                nanos[index] = System.nanoTime() - started;
            }
            bytes = Files.size(file);
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            return report(context, "worst-case refused io=" + exception.getClass().getSimpleName());
        }
        Arrays.sort(nanos);
        return report(context, "worst-case missions=" + missions + " satellites=" + satellites
                + " instances=" + instances + " nbt_bytes="
                + io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteNbtSize
                .uncompressedBytes(widened.save(new CompoundTag())) + " file_bytes=" + bytes
                + " mean_ms=" + millis(Arrays.stream(nanos).sum() / nanos.length) + " max_ms=" + millis(nanos[2]));
    }

    /** Clones the first record that {@code template} accepts, with fresh IDs, until the list holds {@code target}. */
    private static int widen(net.minecraft.nbt.ListTag list, String idKey,
                             java.util.function.Predicate<CompoundTag> template, int target) {
        CompoundTag source = null;
        for (Tag tag : list) {
            if (template.test((CompoundTag) tag)) {
                source = (CompoundTag) tag;
                break;
            }
        }
        while (source != null && list.size() < target) {
            CompoundTag copy = source.copy();
            copy.putUUID(idKey, UUID.randomUUID());
            list.add(copy);
        }
        return list.size();
    }

    /**
     * A terminal at {@code pos} (its chunk is loaded for this command only), an owner, a survey satellite and an
     * asteroid miner; a survey started and claimed through the terminal buttons, the terminal ID persisted by a
     * chunk save, and an asteroid mission started, made durable and brought to READY.
     */
    private int setup(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel level = server.overworld();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        UUID owner = UuidArgument.getUuid(context, "owner");
        level.getChunk(pos);
        level.setBlock(pos, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH), 3);
        SatelliteTerminalBlockEntity terminal = (SatelliteTerminalBlockEntity) level.getBlockEntity(pos);
        FakePlayer player = player(level, pos, owner);
        terminal.setOwner(owner);
        terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow()
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        UUID survey = craft(data, server, owner, SatelliteKind.SURVEY, ModIdentity.id("survey_satellite"));
        UUID miner = craft(data, server, owner, SatelliteKind.ASTEROID_MINER, ModIdentity.id("asteroid_miner"));
        chip(terminal, survey, owner, SatelliteKind.SURVEY, ModIdentity.id("survey_satellite"));
        String launch = press(terminal, player, SatelliteTerminalMenu.BUTTON_LAUNCH);
        data.completeDue(server.overworld().getGameTime() + 6_000L);
        String claim = press(terminal, player, SatelliteTerminalMenu.BUTTON_CLAIM);
        chip(terminal, miner, owner, SatelliteKind.ASTEROID_MINER, ModIdentity.id("asteroid_miner"));
        level.getChunkSource().save(true);
        String start = press(terminal, player, SatelliteTerminalMenu.BUTTON_LAUNCH);
        MissionState mission = data.satellite(miner).flatMap(SatelliteState::currentMissionId)
                .flatMap(data::mission).orElse(null);
        data.flush(server);
        data.completeDue(server.overworld().getGameTime() + 72_000L);
        data.flush(server);
        level.getChunkSource().save(true);
        return report(context, "setup pos=" + pos.toShortString().replace(" ", "") + " survey=" + launch + "/" + claim
                + " start=" + start + " terminal=" + terminal.terminalId() + " persisted=" + terminal.terminalIdPersisted()
                + " miner=" + miner + " mission=" + (mission == null ? "none" : mission.missionId())
                + " instance=" + (mission == null ? "none" : mission.instanceId().map(UUID::toString).orElse("none"))
                + " status=" + (mission == null ? "none" : data.mission(mission.missionId()).orElseThrow().status()));
    }

    private int inspect(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = context.getSource().getServer().overworld();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !(level.getBlockEntity(pos) instanceof SatelliteTerminalBlockEntity terminal)) {
            return report(context, "inspect pos=" + pos.toShortString().replace(" ", "") + " loaded=false");
        }
        CompoundTag data = terminal.saveWithoutMetadata().getCompound(KEY);
        int items = 0;
        for (Tag entry : data.getList("reward_buffer", Tag.TAG_COMPOUND)) {
            items += ((CompoundTag) entry).getInt("count");
        }
        return report(context, "inspect pos=" + pos.toShortString().replace(" ", "") + " loaded=true terminal="
                + terminal.terminalId() + " persisted=" + terminal.terminalIdPersisted() + " buffer_items=" + items
                + " receipts=" + data.getList("receipts", Tag.TAG_INT_ARRAY).size()
                + " loaded_chunks=" + level.getChunkSource().getLoadedChunksCount()
                + " forced_chunks=" + level.getForcedChunks().size());
    }

    /**
     * ADR-051 section 11 cut: claims through the terminal, makes the chosen store durable, then halts the JVM
     * without shutdown. {@code chunk}: the chunk is saved, the registry is not; {@code registry}: the registry is
     * flushed, the chunk is not; {@code both}: both.
     */
    private int cut(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel level = server.overworld();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        String mode = StringArgumentType.getString(context, "mode");
        if (!List.of("chunk", "registry", "both").contains(mode)) {
            return report(context, "cut refused mode=" + mode);
        }
        level.getChunk(pos);
        SatelliteTerminalBlockEntity terminal = (SatelliteTerminalBlockEntity) level.getBlockEntity(pos);
        UUID owner = terminal.saveWithoutMetadata().getCompound(KEY).getUUID("owner_id");
        String claim = press(terminal, player(level, pos, owner), SatelliteTerminalMenu.BUTTON_CLAIM);
        if (!claim.equals(SatelliteOperationCode.SUCCESS.name())) {
            return report(context, "cut refused claim=" + claim);
        }
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        if (!mode.equals("chunk")) {
            data.flush(server);
        }
        if (!mode.equals("registry")) {
            level.getChunkSource().save(true);
        }
        String line = "ARCE_RELEASE_TEST cut mode=" + mode + " claim=" + claim + " save_epoch=" + data.saveEpoch();
        AdvancedRocketryCommunity.LOGGER.info(line);
        System.out.println(line);
        System.out.flush();
        Runtime.getRuntime().halt(HALT_STATUS);
        return 1;
    }

    private static FakePlayer player(ServerLevel level, BlockPos pos, UUID owner) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(owner, "arce_c9_owner"));
        player.setPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        return player;
    }

    private static String press(SatelliteTerminalBlockEntity terminal, FakePlayer player, int button) {
        int last = SatelliteOperationCode.RECONCILING.ordinal();
        for (int attempt = 0; attempt < 16 && last == SatelliteOperationCode.RECONCILING.ordinal(); attempt++) {
            terminal.handleButton(player, button);
            last = terminal.saveWithoutMetadata().getCompound(KEY).getInt("last_result");
        }
        return SatelliteOperationCode.values()[last].name();
    }

    private static void chip(SatelliteTerminalBlockEntity terminal, UUID satellite, UUID owner, SatelliteKind kind,
                             ResourceLocation definition) {
        terminal.menuInventory().extractItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 64, false);
        ItemStack chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get());
        SatelliteItemData.write(chip, new SatelliteIdentity(satellite, owner, definition, kind, components(kind)));
        terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, chip, false);
    }

    private static UUID craft(SatelliteMissionSavedData data, MinecraftServer server, UUID owner, SatelliteKind kind,
                              ResourceLocation definition) {
        UUID satelliteId = UUID.randomUUID();
        SatelliteKindState state = kind == SatelliteKind.SURVEY
                ? new SatelliteKindState.Survey(0L, 0L, 1_000, 32, 8) : new SatelliteKindState.Plain(kind);
        SatelliteBlueprint blueprint = new SatelliteBlueprint(components(kind), false,
                new SatelliteStats(4, 10_720, 1_000, 9, 10));
        data.launchIdle(time -> SatelliteState.launchIdle(satelliteId, definition, owner, time, CelestialIds.EARTH_ID,
                blueprint, state), server.overworld().getGameTime());
        return satelliteId;
    }

    private static List<ResourceLocation> components(SatelliteKind kind) {
        Item primary = kind == SatelliteKind.SURVEY ? ModItems.SURVEY_SCANNER_MODULE.get()
                : ModItems.ASTEROID_DRILL_MODULE.get();
        return List.of(key(ModItems.SATELLITE_CHASSIS.get()), key(primary), key(ModItems.SATELLITE_CARGO_HOLD.get()));
    }

    private static ResourceLocation key(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    /** One line: console command feedback is logged by the server itself. */
    private static int report(CommandContext<CommandSourceStack> context, String text) {
        String line = "ARCE_RELEASE_TEST " + text;
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }
}
