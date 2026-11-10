package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * W346 (records/W346-DESIGN.md section 4): a gated release-test control for W27A B6.5's native crash state, in which the
 * source Level has saved the discard of S and the destination Level has not saved the new rocket. Registered with the
 * other bay release-test commands only when the release-test property is set; each entry needs permission 2 and passes
 * the complete {@link EscrowTestEndpoints} gate before it resolves a dimension or reads anything. No production code
 * calls it, and it changes no cut, admission rule, budget or deadline:
 * <ul>
 *   <li>{@code save-boundary hold <dimension>}: sets that Level's {@code noSave}, as {@code /save-off} does for every
 *       Level, and remembers it; refused, changing nothing, when {@code noSave} is already set;</li>
 *   <li>{@code save-boundary autosave <logical>}: once the transfer journal has acknowledged COMMITTED for L and L's
 *       destination Level is held here, reports the destination's in-memory state and calls the periodic autosave's own
 *       {@code saveEverything(true, false, false)}, which skips every Level whose {@code noSave} is set, and reports the
 *       skipped and saved Levels; otherwise a WAIT line and no save;</li>
 *   <li>{@code save-boundary restore <dimension>}: clears {@code noSave} on a Level held here, for an aborted run.</li>
 * </ul>
 * The held Levels live in memory only, keyed weakly by the server, and nothing else reads them. A clean stop clears
 * {@code noSave} on every Level and saves them all (vanilla {@code stopServer}), so only a hard kill keeps the gap.
 */
final class BaySaveBoundaryReleaseTestCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** One bookkeeping set per server; owner-thread use only, weakly keyed so it ends with its server. */
    private static final Map<MinecraftServer, Holds> HOLDS = Collections.synchronizedMap(new WeakHashMap<>());

    private BaySaveBoundaryReleaseTestCommands() { }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("release-test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("save-boundary")
                        .then(Commands.literal("hold").then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(context -> hold(context))))
                        .then(Commands.literal("autosave").then(Commands.argument("logical", UuidArgument.uuid())
                                .executes(context -> autosave(context.getSource(), UuidArgument.getUuid(context, "logical")))))
                        .then(Commands.literal("restore").then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(context -> restore(context))))))));
    }

    /** The held Levels' bookkeeping, apart from the Level objects so that unit tests need no world. */
    static final class Holds {
        private final Set<String> held = new HashSet<>();

        /** Holds a Level whose saves are on; false, changing nothing, when they are already off. */
        boolean hold(String level, boolean saveOff, Runnable turnSavesOff) {
            if (saveOff) { return false; }
            turnSavesOff.run();
            held.add(level);
            return true;
        }

        /** Restores a Level held here; false, changing nothing, for any other Level. */
        boolean restore(String level, Runnable turnSavesOn) {
            if (!held.remove(level)) { return false; }
            turnSavesOn.run();
            return true;
        }

        boolean holds(String level) { return held.contains(level); }
    }

    /** Read-only, for the gate test: the Levels this server's command holds, or an empty set. */
    static Set<String> heldFor(MinecraftServer server) {
        Holds holds = HOLDS.get(server);
        return holds == null ? Set.of() : Set.copyOf(holds.held);
    }

    private static int hold(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        EscrowTestEndpoints.requireEnabled(server);
        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
        String id = level.dimension().location().toString();
        boolean held = HOLDS.computeIfAbsent(server, ignored -> new Holds()).hold(id, level.noSave(), () -> level.noSave = true);
        if (held) {
            LOGGER.info("ARCE_RELEASE_SAVE_BOUNDARY_HELD level={} noSave={}", id, level.noSave());
        } else {
            LOGGER.info("ARCE_RELEASE_SAVE_BOUNDARY_REFUSED action=hold level={} reason=ALREADY_SAVE_OFF", id);
        }
        context.getSource().sendSuccess(() -> Component.literal("Save boundary hold reported"), false);
        return held ? 1 : 0;
    }

    private static int restore(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        EscrowTestEndpoints.requireEnabled(server);
        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
        String id = level.dimension().location().toString();
        Holds holds = HOLDS.get(server);
        boolean restored = holds != null && holds.restore(id, () -> level.noSave = false);
        if (restored) {
            LOGGER.info("ARCE_RELEASE_SAVE_BOUNDARY_RESTORED level={} noSave={}", id, level.noSave());
        } else {
            LOGGER.info("ARCE_RELEASE_SAVE_BOUNDARY_REFUSED action=restore level={} reason=NOT_HELD", id);
        }
        context.getSource().sendSuccess(() -> Component.literal("Save boundary restore reported"), false);
        return restored ? 1 : 0;
    }

    /** The check and the save run in one owner-thread call, so nothing can change between them. */
    private static int autosave(CommandSourceStack source, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var journal = RocketTransferSavedData.get(server);
        var record = journal.findByLogicalRocket(logical).orElse(null);
        boolean acknowledged = journal.logicalAcknowledged(logical);
        ServerLevel destinationLevel = record == null ? null
                : RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        Holds holds = HOLDS.get(server);
        boolean held = destinationLevel != null && destinationLevel.noSave() && holds != null
                && holds.holds(destinationLevel.dimension().location().toString());
        if (record == null || record.phase() != RocketTransferPhase.COMMITTED || !acknowledged || !held) {
            LOGGER.info("ARCE_RELEASE_SAVE_BOUNDARY_WAIT logical={} transfer={} phase={} acknowledged={} destination_held={}", logical,
                    record == null ? "NONE" : record.transferId(), record == null ? "NONE" : record.phase(), acknowledged, held);
            source.sendSuccess(() -> Component.literal("Save boundary autosave waiting"), false);
            return 0;
        }
        UUID destinationId = record.destinationEntityId().orElse(null);
        var destination = destinationId == null ? null : destinationLevel.getEntity(destinationId);
        boolean present = destination instanceof RocketEntity && !destination.isRemoved();
        boolean chunkLoaded = present && destinationLevel.areEntitiesLoaded(destination.chunkPosition().toLong());
        int sources = RocketTransferEntities.loadedMatches(server, record, false).size();
        List<String> skipped = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            (level.noSave() ? skipped : saved).add(level.dimension().location().toString());
        }
        // The periodic autosave's own call (MinecraftServer.tickServer): flush false, forced false.
        boolean result = server.saveEverything(true, false, false);
        LOGGER.info("ARCE_RELEASE_SAVE_BOUNDARY_AUTOSAVE logical={} transfer={} phase={} acknowledged={} destination_level={} "
                        + "destination_entity={} destination_present={} destination_chunk_loaded={} source_count={} result={} "
                        + "skipped=[{}] saved=[{}]", logical, record.transferId(), record.phase(), acknowledged,
                destinationLevel.dimension().location(), destinationId == null ? "NONE" : destinationId, present, chunkLoaded,
                sources, result, String.join(",", skipped), String.join(",", saved));
        source.sendSuccess(() -> Component.literal("Save boundary autosave reported"), false);
        return 1;
    }
}
