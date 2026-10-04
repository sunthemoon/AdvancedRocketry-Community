package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.EndgameProtection;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class PumpProtectionChecksTest {
    static { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = new UUID(1, 17);
    private static final BlockPos TARGET = new BlockPos(7, 70, 9);
    private static final class View implements EndgameProtection.View {
        final List<String> calls = new ArrayList<>(); int failure; EndgameEffectEvent event;
        View(int failure) { this.failure = failure; }
        public boolean chunksFull(BlockPos min, BlockPos max) { calls.add("loaded"); return failure != 1; }
        public boolean insideWorld(BlockPos min, BlockPos max) { calls.add("bounds"); return failure != 2; }
        public Collection<ProtectedZone> zones() {
            calls.add("zones");
            return failure == 3 ? List.of(ProtectedZone.of("test", Level.OVERWORLD.location(), 0, 0, 10, 10, List.of())) : List.of();
        }
        public boolean spaceLevel() { return true; }
        public boolean stationsAllow(UUID owner, BlockPos min, BlockPos max) { calls.add("station"); return failure != 4; }
        public Optional<EndgameProtection.SpawnSquare> spawnSquare() {
            calls.add("spawn"); return failure == 5 ? Optional.of(new EndgameProtection.SpawnSquare(0, 0, 16)) : Optional.empty();
        }
        public boolean cancelled(EndgameEffectEvent posted) { calls.add("public"); event = posted; return failure == 6; }
    }
    @Test void allSevenLayersAreOrderedAndEveryFirstFailureStopsLaterCallbacks() {
        List<String> order = List.of("loaded", "bounds", "zones", "station", "spawn", "public", "break");
        for (int failure = 1; failure <= 7; failure++) {
            View view = new View(failure);
            PumpCode result = PumpProtectionChecks.check(OWNER, Level.OVERWORLD, TARGET, view, () -> { view.calls.add("break"); return view.failure == 7; });
            assertEquals(failure == 1 ? PumpCode.TARGET_UNLOADED : failure == 2 ? PumpCode.TARGET_OUT_OF_BOUNDS : PumpCode.TARGET_PROTECTED, result);
            assertEquals(order.subList(0, failure), view.calls);
        }
    }
    @Test void effectUsesActualPumpIdentityAndOwnerNotEnumAliasOrNearbyPlayer() {
        View view = new View(0);
        assertEquals(PumpCode.SOURCE_READY, PumpProtectionChecks.check(OWNER, Level.OVERWORLD, TARGET, view, () -> false));
        assertEquals(ModIdentity.id("pump"), view.event.systemId()); assertEquals(OWNER, view.event.ownerId());
        assertTrue(view.event.actorId().isEmpty()); assertEquals(TARGET, view.event.min()); assertEquals(TARGET, view.event.max());
        View absent = new View(0);
        assertEquals(PumpCode.NO_OWNER, PumpProtectionChecks.check(null, Level.OVERWORLD, TARGET, absent, () -> fail("ownerless break")));
        assertTrue(absent.calls.isEmpty());
    }
    @Test void postCallbackRegionRecheckObservesChangedAuthorityWithoutReposting() {
        View view = new View(0);
        assertEquals(PumpCode.SOURCE_READY, PumpProtectionChecks.check(OWNER, Level.OVERWORLD, TARGET, view, () -> false));
        view.calls.clear(); view.failure = 3;
        assertEquals(PumpCode.TARGET_PROTECTED, PumpProtectionChecks.region(OWNER, Level.OVERWORLD, TARGET, view));
        assertEquals(List.of("loaded", "bounds", "zones"), view.calls);
    }
}
