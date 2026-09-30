package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Blank-or-bound control chip; all authority remains in server SavedData. */
public final class SatelliteControlChipItem extends Item {
    public SatelliteControlChipItem(Properties properties) {
        super(properties);
    }

    /**
     * ADR-049 section 8: a survey satellite's bound chip used in hand requests an area scan around the player.
     * The server derives the centre; the client sends nothing but the vanilla use.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<SatelliteIdentity> identity = SatelliteItemData.read(stack).identity();
        if (identity.isEmpty() || identity.get().kind() != SatelliteKind.SURVEY) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SatelliteOperationCode code = SatelliteRuntime.requestScan(serverPlayer, identity.get());
            serverPlayer.displayClientMessage(Component.translatable(code == SatelliteOperationCode.SUCCESS
                    ? "status.advancedrocketrycommunity.survey_scan.started" : code.translationKey()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        SatelliteItemData.DecodeResult decoded = SatelliteItemData.read(stack);
        if (decoded.status() == SatelliteItemData.DecodeStatus.EMPTY) {
            tooltip.add(Component.translatable(
                    "tooltip.advancedrocketrycommunity.satellite_chip.blank"
            ).withStyle(ChatFormatting.GRAY));
            return;
        }
        decoded.identity().ifPresentOrElse(identity -> {
            tooltip.add(Component.translatable(
                    "tooltip.advancedrocketrycommunity.satellite_chip.bound",
                    identity.satelliteId().toString().substring(0, 8)
            ).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal(identity.definitionId().toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }, () -> tooltip.add(Component.translatable(
                "tooltip.advancedrocketrycommunity.satellite_item.unsupported"
        ).withStyle(ChatFormatting.RED)));
    }
}
