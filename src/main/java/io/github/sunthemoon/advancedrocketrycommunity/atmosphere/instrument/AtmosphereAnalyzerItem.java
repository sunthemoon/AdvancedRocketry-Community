package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Vanilla hand use only; no packet, capability, damage, NBT inspection or equipment provider. */
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
