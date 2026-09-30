package io.github.sunthemoon.advancedrocketrycommunity.celestial.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.PlanetaryDiscoveryPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.StarSystemKnowledge;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Bounded server diagnostics and operator-only fixed-destination travel. */
public final class CelestialCommands {
    private static final String BODY_ARGUMENT = "body";

    private final CelestialCatalogManager catalogs;
    private final SafeCelestialTravel travel;

    public CelestialCommands(CelestialCatalogManager catalogs, SafeCelestialTravel travel) {
        this.catalogs = catalogs;
        this.travel = travel;
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce")
                .then(Commands.literal("celestial")
                        .then(Commands.literal("validate").executes(this::validate))
                        .then(Commands.literal("list").executes(this::list))
                        .then(Commands.literal("systems").executes(this::systems))
                        .then(Commands.literal("goto")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument(BODY_ARGUMENT, StringArgumentType.word())
                                        .suggests((context, builder) -> catalogs.current()
                                                .map(CelestialCatalog::definitions)
                                                .stream()
                                                .flatMap(java.util.Collection::stream)
                                                .map(CelestialBodyDefinition::id)
                                                .map(id -> ModIdentity.MOD_ID.equals(id.getNamespace())
                                                        ? id.getPath()
                                                        : id.toString())
                                                .collect(java.util.stream.Collectors.collectingAndThen(
                                                        java.util.stream.Collectors.toList(),
                                                        values -> SharedSuggestionProvider.suggest(values, builder)
                                                )))
                                        .executes(this::goTo)))));
    }

    /** ADR-043 star systems (root trees) with shared knowledge state; at most 16 lines. */
    private int systems(CommandContext<CommandSourceStack> context) {
        CelestialCatalog catalog = catalogs.current().orElse(null);
        if (catalog == null) {
            context.getSource().sendFailure(Component.literal("Celestial catalog unavailable"));
            return 0;
        }
        var progress = CelestialSavedData.get(context.getSource().getServer());
        Predicate<ResourceLocation> discovered =
                body -> progress.isWritableSchema() && progress.get(body).isPresent();
        for (ResourceLocation system : catalog.systems()) {
            var bodies = catalog.systemBodies(system);
            long known = bodies.stream().filter(body -> PlanetaryDiscoveryPolicy.allows(body, discovered)).count();
            boolean systemKnown = StarSystemKnowledge.systemKnown(catalog, system, discovered);
            context.getSource().sendSuccess(() -> Component.literal(
                    "system=" + system + " known=" + systemKnown + " bodies=" + bodies.size()
                            + " known_bodies=" + known), false);
        }
        return catalog.systems().size();
    }

    private int validate(CommandContext<CommandSourceStack> context) {
        CelestialCatalogManager.ReloadStatus status = catalogs.status();
        if (!status.ready()) {
            context.getSource().sendFailure(Component.literal(
                    "Celestial catalog unavailable: " + status.message()
            ));
            return 0;
        }
        if (!status.lastReloadAccepted()) {
            context.getSource().sendFailure(Component.literal(
                    "Celestial catalog generation " + status.generation()
                            + " retained after rejected reload: " + status.message()
            ));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Celestial catalog generation " + status.generation()
                                + " is valid with " + status.bodyCount() + " bodies"
                ),
                false
        );
        return status.bodyCount();
    }

    private int list(CommandContext<CommandSourceStack> context) {
        Optional<CelestialCatalog> current = catalogs.current();
        if (current.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Celestial catalog is not loaded"));
            return 0;
        }

        CelestialCatalog catalog = current.orElseThrow();
        context.getSource().sendSuccess(
                () -> Component.literal("Celestial bodies: " + catalog.size()),
                false
        );
        for (CelestialBodyDefinition definition : catalog.definitions()) {
            context.getSource().sendSuccess(
                    () -> Component.literal(
                            definition.id() + " -> " + definition.levelKey()
                                    .map(level -> level.location().toString()).orElse("unmapped")
                                    + " gravity=" + definition.gravityMultiplier()
                                    + " atmosphere=" + definition.atmosphere().profile()
                                    + " landable=" + definition.capabilities().landable()
                                    + " orbitable=" + definition.capabilities().orbitable()
                                    + " gas_giant=" + definition.capabilities().gasGiant()
                                    + " solar=" + definition.solarIntensity()
                                    + " radiation=" + definition.radiation()
                    ),
                    false
            );
        }
        return catalog.size();
    }

    private int goTo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String rawBody = StringArgumentType.getString(context, BODY_ARGUMENT);
        ResourceLocation bodyId = parseBodyId(rawBody);
        if (bodyId == null) {
            source.sendFailure(Component.literal("Invalid celestial body id"));
            return 0;
        }

        Optional<CelestialBodyDefinition> definition = catalogs.current()
                .flatMap(catalog -> catalog.get(bodyId));
        if (definition.isEmpty()) {
            source.sendFailure(Component.literal("Unknown celestial body: " + bodyId));
            return 0;
        }
        if (!travel.isAllowedBody(bodyId)) {
            source.sendFailure(Component.literal("Body is not a fixed v0.3 travel destination: " + bodyId));
            return 0;
        }

        ServerLevel target = definition.orElseThrow().levelKey()
                .map(source.getServer()::getLevel).orElse(null);
        if (target == null) {
            source.sendFailure(Component.literal("Target has no loaded Level: " + bodyId));
            return 0;
        }

        SafeCelestialTravel.Destination destination = travel.prepare(target, bodyId);
        travel.teleport(player, target, destination);
        source.sendSuccess(
                () -> Component.literal("Teleported to " + bodyId + " at fixed safe destination"),
                true
        );
        AdvancedRocketryCommunity.LOGGER.info(
                "Operator {} teleported to fixed celestial destination {}",
                player.getGameProfile().getName(),
                bodyId
        );
        return 1;
    }

    private static ResourceLocation parseBodyId(String raw) {
        if (raw.length() > BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS) {
            return null;
        }
        if (!raw.contains(":")) {
            return ResourceLocation.tryBuild(ModIdentity.MOD_ID, raw);
        }
        return ResourceLocation.tryParse(raw);
    }
}
