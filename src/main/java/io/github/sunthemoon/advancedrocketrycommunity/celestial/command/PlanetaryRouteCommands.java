package io.github.sunthemoon.advancedrocketrycommunity.celestial.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Operator diagnostics for a single route in one captured generation; never resolves or loads a Level. */
public final class PlanetaryRouteCommands {
    private final PlanetaryCatalogManager catalogs;

    public PlanetaryRouteCommands(PlanetaryCatalogManager catalogs) {
        this.catalogs = catalogs;
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("celestial")
                .then(Commands.literal("route").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("route", StringArgumentType.word()).executes(this::inspect)))));
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "route");
        ResourceLocation id = raw.length() > RouteLimits.MAX_RESOURCE_LOCATION_CHARS ? null
                : ResourceLocation.tryParse(raw.contains(":") ? raw : ModIdentity.MOD_ID + ":" + raw);
        var captured = catalogs.capture().orElse(null);
        if (id == null || captured == null) {
            context.getSource().sendFailure(Component.literal("Invalid route ID or planetary catalog unavailable"));
            return 0;
        }
        var route = captured.catalog().routes().definitions().stream().filter(value -> value.id().equals(id)).findFirst();
        if (route.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown planetary route: " + id));
            return 0;
        }
        var value = route.orElseThrow();
        context.getSource().sendSuccess(() -> Component.literal("Planetary route generation=" + captured.generation()
                + " id=" + value.id() + " from=" + value.from().typeId() + "/" + value.from().bodyId()
                + " to=" + value.to().typeId() + "/" + value.to().bodyId()
                + " distance=" + value.distanceUnits() + " bidirectional=" + value.bidirectional()), false);
        return 1;
    }
}
