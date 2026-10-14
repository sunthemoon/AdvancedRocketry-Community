package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.DeferredRoomBoundary;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge adapter for the torch rules; every decision reads server state, never a client-supplied value. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class TorchEvents {
    private static final String MESSAGE = "message." + ModIdentity.MOD_ID + ".torch.";

    private TorchEvents() {
    }

    @SubscribeEvent
    public static void reloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TorchPairReloadListener());
    }

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        CombustionTorches.start();
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        CombustionTorches.stop();
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CombustionTorches.service().ifPresent(service -> service.unload(level));
        }
    }

    /** After the atmosphere's own server tick (normal priority), so dirty cells are already being rescanned. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CombustionTorches.service().ifPresent(service -> service.tick(event.getServer()));
        }
    }

    /** Placement admission runs last, on placements no protection has cancelled. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof DeferredRoomBoundary || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Optional<TorchCombustionService> service = CombustionTorches.service();
        if (service.isEmpty()) {
            return;
        }
        List<BlockSnapshot> snapshots = event instanceof BlockEvent.EntityMultiPlaceEvent multiple
                ? multiple.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        for (BlockSnapshot snapshot : snapshots) {
            BlockState placed = snapshots.size() == 1 ? event.getPlacedBlock() : snapshot.getCurrentBlock();
            Optional<TorchCombustionService.Refusal> refusal = service.get().admitPlacement(level, snapshot.getPos(), placed);
            if (refusal.isPresent()) {
                event.setCanceled(true);
                tell(event.getEntity(), refusal.get().key);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void neighbourNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CombustionTorches.service().ifPresent(service -> {
            service.reconsider(level, event.getPos());
            for (Direction side : event.getNotifiedSides()) {
                service.reconsider(level, event.getPos().relative(side));
            }
        });
    }

    /**
     * A fire starter is suppressed, spending nothing, where the adjacent loaded cell is noncombustible or pending. On
     * an unlit torch the server relights it only with combustion air, a resolvable root and normal permissions.
     */
    @SubscribeEvent
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || !event.getItemStack().is(CombustionTorches.FIRE_STARTERS)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!CombustionAir.loaded(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof UnlitTorchBlock || state.getBlock() instanceof UnlitWallTorchBlock) {
            event.setCanceled(true);
            event.setCancellationResult(relight(level, player, pos, state, event.getItemStack(), event.getHand(),
                    event.getFace()) ? InteractionResult.SUCCESS : InteractionResult.FAIL);
            return;
        }
        Direction face = event.getFace();
        BlockPos target = face == null ? null : pos.relative(face);
        if (target == null || !CombustionAir.loaded(level, target)) {
            return;
        }
        CombustionAir air = CombustionAir.at(level, target);
        if (air == CombustionAir.NONCOMBUSTIBLE || air == CombustionAir.PENDING) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            tell(player, "no_combustion");
        }
    }

    private static boolean relight(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState unlit,
                                   ItemStack stack, InteractionHand hand, Direction face) {
        if (!(level.getBlockEntity(pos) instanceof UnlitTorchBlockEntity torch)) {
            return false;
        }
        if (torch.protectedData()) {
            tell(player, "quarantined");
            return false;
        }
        Optional<BlockState> lit = torch.litState();
        if (lit.isEmpty()) {
            tell(player, "unknown");
            return false;
        }
        if (CombustionAir.at(level, pos) != CombustionAir.COMBUSTIBLE) {
            tell(player, "no_combustion");
            return false;
        }
        if (player.isSpectator() || !player.mayUseItemAt(pos, face == null ? Direction.UP : face, stack)
                || TorchWorldChanges.relight(player, level, pos, unlit, lit.get()) != TorchWorldChanges.Result.CHANGED) {
            return false;
        }
        boolean charge = stack.is(Items.FIRE_CHARGE);
        if (!player.getAbilities().instabuild) {
            if (stack.isDamageableItem()) {
                stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
            } else if (charge) {
                stack.shrink(1);
            }
        }
        level.playSound(null, pos, charge ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE,
                SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        return true;
    }

    private static void tell(Entity entity, String key) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(MESSAGE + key), true);
        }
    }
}
