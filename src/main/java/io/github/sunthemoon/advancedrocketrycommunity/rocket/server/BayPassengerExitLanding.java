package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import cpw.mods.modlauncher.api.INameMappingService.Domain;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Narrow provenance check, not a damage interceptor. Unknown overrides and truncated stacks refuse. */
final class BayPassengerExitLanding {
    record Signature(Class<?> owner, String name, String descriptor) { }
    private final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private final List<Signature> signatures;
    private Signature attackBoundary;
    private int inspections;

    BayPassengerExitLanding() {
        List<Signature> found;
        try {
            attackBoundary = signature(ServerPlayer.class, "m_6469_", boolean.class, DamageSource.class, float.class);
            found = List.of(signature(LivingEntity.class, "m_142535_", boolean.class, float.class, float.class, DamageSource.class),
                    signature(Block.class, "m_142072_", void.class, Level.class, BlockState.class, BlockPos.class, Entity.class, float.class),
                    signature(Entity.class, "m_7840_", void.class, double.class, boolean.class, BlockState.class, BlockPos.class),
                    signature(ServerPlayer.class, "m_289599_", void.class, double.class, double.class, double.class, boolean.class));
        } catch (ReflectiveOperationException | RuntimeException unavailable) { found = List.of(); }
        signatures = found;
    }
    private static Signature signature(Class<?> owner, String srg, Class<?> result, Class<?>... arguments)
            throws ReflectiveOperationException {
        String name = ObfuscationReflectionHelper.remapName(Domain.METHOD, srg);
        var method = owner.getDeclaredMethod(name, arguments);
        if (method.getReturnType() != result) { throw new NoSuchMethodException(name); }
        return new Signature(owner, name, MethodType.methodType(result, arguments).descriptorString());
    }
    boolean available() { return signatures.size() == 4; }
    void endTick() { inspections = 0; }
    boolean ordinaryStack() {
        return inspect(false);
    }
    boolean attackStack() { return inspect(true); }
    private boolean inspect(boolean attack) {
        if (!available() || inspections++ >= 32) { return false; }
        return walker.walk(stream -> {
            var frames = stream.limit(65).map(frame -> new Signature(frame.getDeclaringClass(), frame.getMethodName(), frame.getDescriptor())).toList();
            return frames.stream().filter(frame -> frame.equals(attackBoundary)).count() == (attack ? 1 : 0)
                    && (!attack || frames.stream().noneMatch(frame -> frame.owner == net.minecraftforge.common.ForgeHooks.class
                    && frame.name.equals("onLivingFall"))) && matches(frames, signatures);
        });
    }
    static boolean matches(List<Signature> frames, List<Signature> required) {
        if (frames.size() > 64 || required.size() != 4) { return false; }
        int next = 0;
        for (var frame : frames) {
            int match = required.indexOf(frame);
            if (match < 0) { continue; }
            if (match != next) { return false; }
            next++;
        }
        return next == required.size();
    }
}
