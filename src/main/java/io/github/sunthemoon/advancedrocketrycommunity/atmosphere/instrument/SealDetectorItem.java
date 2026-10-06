package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Objects;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/** Native clicked-face use only; no packet, air-use measurement, owned NBT or equipment capability. */
public final class SealDetectorItem extends Item {
    public static final int COOLDOWN_TICKS = 2;
    private final BooleanSupplier enabled;
    private final Function<UseOnContext, SealDetectorReading> readings;

    public SealDetectorItem(Properties properties, BooleanSupplier enabled) {
        this(properties, enabled, SealDetectorRuntime::read);
    }

    SealDetectorItem(Properties properties, BooleanSupplier enabled, Function<UseOnContext, SealDetectorReading> readings) {
        super(properties.stacksTo(1));
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.readings = Objects.requireNonNull(readings, "readings");
    }

    /** Root registers this exact item instance's method on both logical sides. */
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() == this) {
            event.setUseBlock(Event.Result.DENY);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.advancedrocketrycommunity.seal_detector.measurement_only"));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!heldMatches(context, this)) { return InteractionResult.FAIL; }
        if (context.getLevel().isClientSide) { return InteractionResult.sidedSuccess(true); }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)
                || !level.getServer().isSameThread() || player.serverLevel() != level
                || !AtmosphereAnalyzerService.admitted(level.getServer(), player)) {
            return InteractionResult.FAIL;
        }
        if (player.getCooldowns().isOnCooldown(this)) { return InteractionResult.FAIL; }
        SealDetectorReading reading = response(enabled, readings, context);
        player.sendSystemMessage(SealDetectorFeedback.message(reading));
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResult.sidedSuccess(false);
    }

    static SealDetectorReading response(BooleanSupplier enabled, Function<UseOnContext, SealDetectorReading> reader,
                                        UseOnContext context) {
        return enabled.getAsBoolean() ? Objects.requireNonNull(reader.apply(context), "reading") : SealDetectorReading.disabled();
    }

    /** Identity/count only: arbitrary held NBT is neither inspected nor copied. */
    static boolean heldMatches(UseOnContext context, SealDetectorItem expected) {
        if (context == null || context.getPlayer() == null || context.getHand() == null) { return false; }
        ItemStack actual = context.getPlayer().getItemInHand(context.getHand());
        return context.getItemInHand() == actual && actual.getCount() == 1
                && actual.getItem() instanceof SealDetectorItem && (expected == null || actual.getItem() == expected);
    }
}
