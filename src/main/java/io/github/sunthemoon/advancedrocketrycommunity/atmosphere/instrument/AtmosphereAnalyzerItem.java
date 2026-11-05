package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/** Vanilla hand/blocked-activation use; no packet, capability, damage, NBT inspection or equipment provider. */
public final class AtmosphereAnalyzerItem extends Item {
    public static final int COOLDOWN_TICKS = 2;
    private final BooleanSupplier enabled;
    private final Function<ServerPlayer, AnalyzerReading> readings;

    public AtmosphereAnalyzerItem(Properties properties, BooleanSupplier enabled) {
        this(properties, enabled, AtmosphereAnalyzerRuntime::read);
    }

    public AtmosphereAnalyzerItem(Properties properties, BooleanSupplier enabled, Function<ServerPlayer, AnalyzerReading> readings) {
        super(properties.stacksTo(1));
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.readings = Objects.requireNonNull(readings, "readings");
    }

    /** Registered on both sides before atmosphere interaction invalidation; measurement never activates the target. */
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() == this) {
            event.setUseBlock(Event.Result.DENY);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context == null || context.getPlayer() == null || context.getHand() == null
                || context.getItemInHand() != context.getPlayer().getItemInHand(context.getHand())) {
            return InteractionResult.FAIL;
        }
        // Same admission, eye-cell query and cooldown as air-use, without a second fallback measurement.
        return use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == null) { return InteractionResultHolder.fail(ItemStack.EMPTY); }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() != this || held.getCount() != 1) { return InteractionResultHolder.fail(held); }
        if (level.isClientSide) { return InteractionResultHolder.sidedSuccess(held, true); }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || !serverLevel.getServer().isSameThread()
                || serverPlayer.serverLevel() != level || !AtmosphereAnalyzerService.admitted(level.getServer(), serverPlayer)) {
            return InteractionResultHolder.fail(held);
        }
        if (player.getCooldowns().isOnCooldown(this)) { return InteractionResultHolder.fail(held); }
        AnalyzerReading reading = enabled.getAsBoolean() ? Objects.requireNonNull(readings.apply(serverPlayer), "reading")
                : AnalyzerReading.disabled();
        player.sendSystemMessage(AtmosphereAnalyzerFeedback.message(reading));
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(held, false);
    }
}
