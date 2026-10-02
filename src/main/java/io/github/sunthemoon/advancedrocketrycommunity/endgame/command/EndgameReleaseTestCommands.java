package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo.CargoEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameTimings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * C13 packaged-server evidence hooks for v1.7, registered only with
 * {@code -Dadvancedrocketrycommunity.releaseTestHooks=true} (never in normal play). They build a railgun pair, put a
 * payload in, request a launch, report one endpoint or the ledger on one line, persist only the chunks or only the
 * endgame root, halt the JVM for a crash cut, benchmark root flushes on synthetic roots and print the measured
 * endgame work. Every line they print starts with {@code ARCE_RELEASE_TEST}.
 */
public final class EndgameReleaseTestCommands {
    private static final ResourceLocation TARGET = ResourceLocation.tryBuild("advancedrocketrycommunity",
            "laser_target");
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private final EndgameService service;

    public EndgameReleaseTestCommands(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) {
            return;
        }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("endgame")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("railguns").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("owner", UuidArgument.uuid()).executes(this::railguns))))
                        .then(Commands.literal("fill").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(this::fill)))))
                        .then(Commands.literal("launch").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(this::launch)))
                        .then(Commands.literal("endpoint").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(this::endpoint)))
                        .then(Commands.literal("ledger").executes(this::ledger))
                        .then(Commands.literal("persist")
                                .then(Commands.literal("chunks").executes(this::persistChunks))
                                .then(Commands.literal("root").executes(this::persistRoot)))
                        .then(Commands.literal("halt").executes(this::halt))
                        .then(Commands.literal("timing").executes(this::timing))
                        .then(Commands.literal("flushbench")
                                .then(Commands.argument("endpoints", IntegerArgumentType.integer(0, 2048))
                                        .then(Commands.argument("tombstones", IntegerArgumentType.integer(0, 8192))
                                                .then(Commands.argument("records", IntegerArgumentType.integer(0, 256))
                                                        .then(Commands.argument("pairs",
                                                                        IntegerArgumentType.integer(0, 1024))
                                                                .then(Commands.argument("runs",
                                                                                IntegerArgumentType.integer(1, 50))
                                                                        .executes(this::flushbench))))))))));
    }

    /** Two railguns of one owner, 24 blocks apart in z (two chunks), the first selecting the second, both charged. */
    private int railguns(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos source = BlockPosArgument.getBlockPos(context, "pos");
        BlockPos destination = source.offset(0, 0, 24);
        UUID owner = UuidArgument.getUuid(context, "owner");
        RailgunBlockEntity first = build(level, source, owner);
        RailgunBlockEntity second = build(level, destination, owner);
        first.selectForTest(second.deviceId().orElseThrow(), false);
        return report(context, "railguns source=" + first.deviceId().orElseThrow() + " destination="
                + second.deviceId().orElseThrow() + " source_pos=" + source.toShortString() + " destination_pos="
                + destination.toShortString());
    }

    private static RailgunBlockEntity build(ServerLevel level, BlockPos controller, UUID owner) {
        BlockState casing = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = 0; z <= 2; z++) {
                    level.setBlockAndUpdate(controller.offset(x, y, z), casing);
                }
            }
        }
        for (int y = 1; y <= 4; y++) {
            level.setBlockAndUpdate(controller.offset(0, y, 1), Blocks.IRON_BLOCK.defaultBlockState());
        }
        level.setBlockAndUpdate(controller, ModBlocks.RAILGUN.get().defaultBlockState()
                .setValue(RailgunBlock.FACING, Direction.NORTH));
        RailgunBlockEntity railgun = (RailgunBlockEntity) level.getBlockEntity(controller);
        railgun.assignOwner(owner);
        railgun.storage().energy().set(RailgunBlockEntity.ENERGY_CAPACITY);
        return railgun;
    }

    private int fill(CommandContext<CommandSourceStack> context) {
        CargoEndpointBlockEntity endpoint = at(context);
        if (endpoint == null) {
            return report(context, "fill missing");
        }
        ItemStack stack = new ItemStack(ItemArgument.getItem(context, "item").getItem(),
                IntegerArgumentType.getInteger(context, "count"));
        endpoint.storage().input().setStackInSlot(0, stack);
        return report(context, "fill " + describe(endpoint));
    }

    private int launch(CommandContext<CommandSourceStack> context) {
        CargoEndpointBlockEntity endpoint = at(context);
        if (endpoint == null || endpoint.ownerId().isEmpty()) {
            return report(context, "launch missing");
        }
        endpoint.requestLaunch(endpoint.ownerId().get());
        return report(context, "launch requested " + describe(endpoint));
    }

    private int endpoint(CommandContext<CommandSourceStack> context) {
        CargoEndpointBlockEntity endpoint = at(context);
        return report(context, "endpoint " + (endpoint == null ? "missing" : describe(endpoint)));
    }

    /** The cargo endpoint at the position, only when its chunk is loaded (reading it would load it). */
    private static CargoEndpointBlockEntity at(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof CargoEndpointBlockEntity endpoint ? endpoint : null;
    }

    private static String describe(CargoEndpointBlockEntity endpoint) {
        int input = 0;
        int receive = 0;
        for (int slot = 0; slot < endpoint.storage().input().getSlots(); slot++) {
            input += endpoint.storage().input().getStackInSlot(slot).getCount();
        }
        for (int slot = 0; slot < endpoint.storage().receive().getSlots(); slot++) {
            receive += endpoint.storage().receive().getStackInSlot(slot).getCount();
        }
        return "id=" + endpoint.deviceId().map(UUID::toString).orElse("-") + " status=" + endpoint.endpointStatus()
                + " input=" + input + " receive=" + receive + " energy=" + endpoint.storage().energy().energy()
                + " next_seq=" + endpoint.source().nextSeq() + " outbox=" + endpoint.source().outbox().stream()
                .map(entry -> Long.toString(entry.seq())).collect(Collectors.joining(",", "[", "]"))
                + " incoming=" + endpoint.destination().incoming().size() + " receipts="
                + endpoint.destination().receipts().size() + " last=" + endpoint.lastCode();
    }

    /** Every record on one line: key, state, acknowledgement, stub. */
    private int ledger(CommandContext<CommandSourceStack> context) {
        if (service.root().isEmpty()) {
            return report(context, "ledger unavailable");
        }
        EndgameRoot root = service.root().get();
        String records = root.transits().records().stream().map(record -> record.key() + ":" + record.state()
                + (record.acknowledged() ? ":ack" : "") + (record.stub() ? ":stub" : ""))
                .collect(Collectors.joining(",", "[", "]"));
        return report(context, "ledger records=" + root.transits().size() + " save_epoch=" + root.saveEpoch()
                + " pending=" + service.writePending() + " " + records);
    }

    /** Saves every loaded chunk of every Level, and nothing else (no SavedData). */
    private int persistChunks(CommandContext<CommandSourceStack> context) {
        int levels = 0;
        for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
            level.getChunkSource().save(true);
            levels++;
        }
        return report(context, "persist chunks levels=" + levels);
    }

    /** Writes the endgame root now, and nothing else. */
    private int persistRoot(CommandContext<CommandSourceStack> context) {
        service.barrier(root -> null);
        return report(context, "persist root pending=" + service.writePending() + " save_epoch="
                + service.root().map(EndgameRoot::saveEpoch).orElse(-1L));
    }

    /** Ends the JVM at once with the crash-cut status: nothing more is saved. */
    private int halt(CommandContext<CommandSourceStack> context) {
        report(context, "halt status=" + ReleaseTestCommands.HALT_STATUS);
        Runtime.getRuntime().halt(ReleaseTestCommands.HALT_STATUS);
        return 1;
    }

    private int timing(CommandContext<CommandSourceStack> context) {
        EndgameTimings timings = service.timings();
        List<String> parts = new ArrayList<>();
        for (EndgameTimings.Place place : EndgameTimings.Place.values()) {
            EndgameTimings.Summary summary = timings.summary(place);
            parts.add(summary.name() + "=" + String.format(java.util.Locale.ROOT, "%.1f/%.1f", summary.meanMicros(),
                    summary.p99Micros()));
        }
        EndgameTimings.Summary total = timings.total(false);
        EndgameTimings.Summary quiet = timings.total(true);
        return report(context, "timing ticks=" + total.ticks() + " total_mean_us=" + String.format(java.util.Locale.ROOT,
                "%.1f", total.meanMicros()) + " total_p99_quiet_us=" + String.format(java.util.Locale.ROOT, "%.1f",
                quiet.p99Micros()) + " places_mean/p99_us=" + String.join(",", parts) + " "
                + timings.report().get(timings.report().size() - 1));
    }

    /**
     * ADR-054 section 7 flush budget on a synthetic root (never the live one): endpoints, settled tombstones, records
     * with four stacks of about 450 bytes each, pairs and zones, written {@code runs} times through the checked writer
     * to a scratch folder in the world folder, which is deleted afterwards.
     */
    private int flushbench(CommandContext<CommandSourceStack> context) {
        int endpoints = IntegerArgumentType.getInteger(context, "endpoints");
        int tombstones = IntegerArgumentType.getInteger(context, "tombstones");
        int records = Math.min(IntegerArgumentType.getInteger(context, "records"), endpoints);
        int pairs = Math.min(IntegerArgumentType.getInteger(context, "pairs"), endpoints / 2);
        int runs = IntegerArgumentType.getInteger(context, "runs");
        EndgameRoot root = synthetic(endpoints, tombstones, records, pairs);
        CompoundTag encoded = EndgameRootCodec.encode(root, new CompoundTag());
        EndgameSavedData data = EndgameSavedData.load(encoded);
        if (!data.operational()) {
            return report(context, "flushbench refused synthetic_root_decodes=false");
        }
        MinecraftServer server = context.getSource().getServer();
        // The checked writer keeps the type's own file name, so the scratch copy lives in a folder of its own.
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("arce-endgame-flushbench");
        Path file = folder.resolve(EndgameSavedData.DATA_NAME + ".dat");
        long[] nanos = new long[runs];
        long bytes;
        try {
            Files.createDirectories(folder);
            for (int run = 0; run < runs; run++) {
                long start = System.nanoTime();
                data.flush(file);
                nanos[run] = System.nanoTime() - start;
            }
            bytes = Files.size(file);
            Files.deleteIfExists(file);
            Files.deleteIfExists(folder);
        } catch (IOException exception) {
            return report(context, "flushbench failed " + exception.getMessage());
        }
        Arrays.sort(nanos);
        return report(context, "flushbench endpoints=" + root.endpoints().size() + " tombstones="
                + root.settledTombstones().size() + " records=" + root.transits().size() + " pairs="
                + root.pairs().size() + " zones=" + root.zones().size() + " accounted_bytes=" + root.accountedBytes()
                + " file_bytes=" + bytes + " runs=" + runs + " mean_ms=" + millis(Arrays.stream(nanos).sum() / runs)
                + " p50_ms=" + millis(nanos[runs / 2]) + " max_ms=" + millis(nanos[runs - 1]));
    }

    static EndgameRoot synthetic(int endpoints, int tombstones, int records, int pairs) {
        EndgameRoot root = EndgameRoot.create();
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < endpoints; index++) {
            UUID id = new UUID(0x7E57L, index + 1L);
            root.register(id, TARGET, new UUID(0x0E55L, index / 64), LEVEL, BlockPos.asLong(index * 16, 64, 0), false,
                    2048, 64);
            ids.add(id);
        }
        for (int index = 0; index < tombstones; index++) {
            UUID id = new UUID(0x70B5L, index + 1L);
            UUID owner = new UUID(0x0E56L, index / 200);
            root.register(id, TARGET, owner, LEVEL, BlockPos.asLong(index * 16, 64, 16), false, 4096, 4096);
            root.remove(id);
            root.settle(id, ignored -> false);
        }
        byte[] fill = new byte[400];
        Arrays.fill(fill, (byte) 'x');
        List<ItemStack> stacks = new ArrayList<>();
        for (int stack = 0; stack < 4; stack++) {
            ItemStack item = new ItemStack(Items.DIAMOND, 64);
            item.getOrCreateTag().putString("bench", new String(fill, java.nio.charset.StandardCharsets.US_ASCII));
            stacks.add(item);
        }
        TransitPayload payload = TransitPayload.of(stacks).orElseThrow();
        for (int index = 0; index < records; index++) {
            UUID source = ids.get(index);
            UUID destination = ids.get((index + 1) % ids.size());
            root.registerTransit(TransitRecord.registered(source, new OutboxEntry(1L, destination, payload, 1, 20,
                    EndgameSystem.RAILGUN), new UUID(0x0E55L, index / 64), root.saveEpoch(), 0L));
        }
        for (int index = 0; index < pairs; index++) {
            root.pairs().add(new ElevatorPair(new UUID(0x9A1EL, index + 1L), new UUID(0x57A7L, index + 1L),
                    ids.get(2 * index), ids.get(2 * index + 1), ResourceLocation.tryBuild("advancedrocketrycommunity",
                    "moon"), LEVEL, index * 16, 4096, 64, 0L, new UUID(0x0E55L, 0L)));
        }
        if (pairs > 8) {
            for (int index = 0; index < 256; index++) {
                root.addZone(ProtectedZone.of("bench" + index, LEVEL, index * 32, 0, index * 32 + 16, 16, List.of()),
                        256);
            }
        }
        return root;
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", nanos / 1_000_000.0D);
    }

    private static int report(CommandContext<CommandSourceStack> context, String line) {
        context.getSource().sendSuccess(() -> Component.literal("ARCE_RELEASE_TEST " + line), false);
        return 1;
    }
}
