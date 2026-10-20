package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Services;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowItemPlayerHeadTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void stringOwnersAreRefusedWithoutProfileServices() {
        SkullBlockEntity.clear();
        try { refuseStringOwner(); }
        finally { SkullBlockEntity.clear(); }
    }

    @Test void stringOwnersAreRefusedBeforeNativeLoadingWithProfileServices() throws Exception {
        var loader = getClass().getClassLoader();
        var session = (MinecraftSessionService) Proxy.newProxyInstance(loader, new Class<?>[] {MinecraftSessionService.class},
                (proxy, method, args) -> "fillProfileProperties".equals(method.getName()) ? args[0] : null);
        var repository = (GameProfileRepository) Proxy.newProxyInstance(loader, new Class<?>[] {GameProfileRepository.class},
                (proxy, method, args) -> { throw new AssertionError("The cached fixture must not perform a profile lookup"); });
        var cache = new GameProfileCache(repository, temp.resolve("usercache.json").toFile());
        cache.add(new GameProfile(new UUID(0, 110), "W110F")); cache.setExecutor(Runnable::run);
        var mainThread = new LinkedBlockingQueue<Runnable>();
        SkullBlockEntity.setup(new Services(session, null, repository, cache), mainThread::add);
        try {
            // Control: these services exercise the deferred, not the synchronous, native rewrite.
            var saved = stringOwner("W110F"); var nativeStack = ItemStack.of(saved.copy());
            assertEquals(saved, nativeStack.save(new CompoundTag()));
            var rewrite = mainThread.poll(10, TimeUnit.SECONDS);
            assertNotNull(rewrite); rewrite.run();
            assertEquals(Tag.TAG_COMPOUND, nativeStack.getTag().getTagType("SkullOwner"));
            assertNotEquals(saved, nativeStack.save(new CompoundTag()));
            refuseStringOwner();
            assertTrue(mainThread.isEmpty(), "Refusal must not schedule a profile rewrite");
        } finally { SkullBlockEntity.clear(); }
    }

    @Test void canonicalCompoundOwnerRemainsSupported() {
        var saved = new ItemStack(Items.PLAYER_HEAD).save(new CompoundTag());
        var tag = new CompoundTag(); var owner = new CompoundTag();
        owner.putUUID("Id", new UUID(0, 110)); owner.putString("Name", "W110F"); tag.put("SkullOwner", owner); saved.put("tag", tag);
        assertEquals(saved, EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(saved)));
        assertEquals(saved, new EscrowItemCargoPayload(EscrowItemFixtures.cursor(), saved, 0, 0).stack());
    }

    private static void refuseStringOwner() {
        for (String name : java.util.List.of("W110F", "")) {
            var saved = stringOwner(name); var original = saved.copy();
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.validateStructure(saved));
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.validateNative(saved));
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(saved));
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA,
                    () -> new EscrowItemCargoPayload(EscrowItemFixtures.cursor(), saved, 0, 0));
            // setTag itself invokes the item hook; construct the live pre-admission state without that hook.
            var live = new ItemStack(Items.PLAYER_HEAD); live.getOrCreateTag().putString("SkullOwner", name);
            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.encode(live));
            assertEquals(original, saved); assertEquals(original.getCompound("tag"), live.getTag());
        }
    }
    private static CompoundTag stringOwner(String name) {
        var saved = new CompoundTag(); saved.putString("id", "minecraft:player_head"); saved.putByte("Count", (byte) 1);
        var tag = new CompoundTag(); tag.putString("SkullOwner", name); saved.put("tag", tag); return saved;
    }
}
