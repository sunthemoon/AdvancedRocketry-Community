package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Unit-only session fixture. Bypasses admission gates, not ticket lifetime, validity or work accounting. */
public final class CargoTicketTestFixture implements AutoCloseable {
    private final MinecraftServer server = UnstartedServer.owned();
    private final RocketCargoPreparations session = new RocketCargoPreparations();
    public final RocketCargoPreparations.Ticket ticket;

    public CargoTicketTestFixture(UUID logical, RocketStructureSnapshot before, EscrowCargoPayload delta, boolean load) {
        var level = UnstartedServer.allocate(FixtureLevel.class); level.server = server;
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(Entity.class, rocket, "level", level);
        UnstartedServer.set(Entity.class, rocket, "position", Vec3.ZERO);
        UnstartedServer.set(Entity.class, rocket, "blockPosition", BlockPos.ZERO);
        UnstartedServer.set(Entity.class, rocket, "deltaMovement", Vec3.ZERO);
        UnstartedServer.set(RocketEntity.class, rocket, "snapshot", before);
        UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", logical);
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", UUID.randomUUID());
        UnstartedServer.set(RocketEntity.class, rocket, "escrowSection", RocketEscrowSection.neutral());
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", RocketFlightData.initial(logical, 20_000, 0,
                net.minecraft.resources.ResourceLocation.tryParse("advancedrocketrycommunity:earth"),
                Level.OVERWORLD.location(), before.sourceOrigin(), 0));
        try {
            var constructor = RocketCargoPreparations.Ticket.class.getDeclaredConstructor(RocketCargoPreparations.class,
                    RocketEntity.class, EscrowCargoPayload.class, boolean.class);
            constructor.setAccessible(true);
            ticket = constructor.newInstance(session, rocket, delta, load);
            UnstartedServer.set(RocketCargoPreparations.class, session, "active", ticket);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    public RocketCargoPreparation cursor() { return ticket.cursor(); }
    public RocketCargoWorkBudget budget() { return session.budget(); }
    public void at(long tick) { UnstartedServer.set(MinecraftServer.class, server, "tickCount", Math.toIntExact(tick)); }
    public boolean advance(long tick) { at(tick); return ticket.advance(); }
    @Override public void close() { session.close(); }

    private static final class FixtureLevel extends ServerLevel {
        MinecraftServer server;
        FixtureLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public MinecraftServer getServer() { return server; }
        @Override public boolean hasChunkAt(BlockPos position) { return true; }
    }
}
