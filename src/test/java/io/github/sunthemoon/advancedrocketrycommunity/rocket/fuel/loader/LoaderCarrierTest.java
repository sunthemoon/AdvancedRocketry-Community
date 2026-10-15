package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoaderCarrierTest {
    private static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static LoaderTank tank() {
        CompoundTag tag = new CompoundTag();
        tag.putString("batch", "kept");
        return LoaderTank.of(WATER, tag, 1_251);
    }

    @Test void carriersRoundTripWithAndWithoutAnOwner() {
        var owned = new LoaderCarrier(UUID.randomUUID(), tank());
        assertEquals(Set.of("schema_version", "owner", "tank"), owned.encode().getAllKeys());
        assertEquals(owned, LoaderCarrier.decode(owned.encode()));
        var unowned = new LoaderCarrier(null, LoaderTank.EMPTY);
        assertEquals(Set.of("schema_version", "tank"), unowned.encode().getAllKeys());
        assertEquals(unowned, LoaderCarrier.decode(unowned.encode()));
        assertThrows(NullPointerException.class, () -> new LoaderCarrier(UUID.randomUUID(), null));
    }

    @Test void aDropKeepsOnlyTheOwnerAndTheTank() {
        UUID owner = UUID.randomUUID();
        var binding = new LoaderBinding(UUID.randomUUID(), ResourceLocation.tryParse("minecraft:overworld"), UUID.randomUUID(),
                BlockPos.ZERO, null);
        var retired = new LoaderEscrowSection(UUID.randomUUID(), 3, List.of());
        var root = new LoaderRoot(owner, UUID.randomUUID(), binding, tank(), retired);
        var carrier = LoaderCarrier.drop(root);
        assertEquals(new LoaderCarrier(owner, tank()), carrier);
        assertEquals(Set.of("schema_version", "owner", "tank"), carrier.encode().getAllKeys());
        // An unclaimed device drops a carrier with no owner, so ownership follows the item for non-operators.
        assertNull(LoaderCarrier.drop(LoaderRoot.placed(null, UUID.randomUUID(), tank())).owner());
        var locked = new LoaderRoot(owner, UUID.randomUUID(), binding, tank(),
                new LoaderEscrowSection(UUID.randomUUID(), 1, List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, "SOURCE", 1))));
        invalid(() -> LoaderCarrier.drop(locked));
    }

    @Test void identityBindingAndMarkersCannotRideACarrier() {
        CompoundTag valid = new LoaderCarrier(UUID.randomUUID(), tank()).encode();
        schemaVersions(valid, LoaderCarrier::decode);
        CompoundTag device = valid.copy(); device.putUUID("device", UUID.randomUUID());
        invalid(() -> LoaderCarrier.decode(device));
        CompoundTag binding = valid.copy(); binding.put("binding", new CompoundTag());
        invalid(() -> LoaderCarrier.decode(binding));
        CompoundTag escrow = valid.copy(); escrow.put("resource_escrow", LoaderEscrowSection.neutral().encode());
        invalid(() -> LoaderCarrier.decode(escrow));
        CompoundTag missing = valid.copy(); missing.remove("tank");
        invalid(() -> LoaderCarrier.decode(missing));
        CompoundTag owner = valid.copy(); owner.putString("owner", "Steve");
        invalid(() -> LoaderCarrier.decode(owner));
        CompoundTag amount = valid.copy(); amount.getCompound("tank").putInt("Amount", LoaderTank.CAPACITY + 1);
        invalid(() -> LoaderCarrier.decode(amount));
        CompoundTag absent = valid.copy(); absent.getCompound("tank").putString("FluidName", "absentmod:fuel");
        assertEquals(LoaderDataException.Reason.UNREGISTERED_FLUID, refusal(() -> LoaderCarrier.decode(absent)));
        CompoundTag futureTankless = valid.copy(); futureTankless.putInt("schema_version", 2); futureTankless.remove("tank");
        assertEquals(LoaderDataException.Reason.FUTURE, refusal(() -> LoaderCarrier.decode(futureTankless)));
    }

    @Test void envelopeIsTwelveKibibytesDepthEighteenAndOneThousandTwentyFourNodes() {
        CompoundTag valid = new LoaderCarrier(UUID.randomUUID(), tank()).encode();
        bounds(valid, LoaderCarrier::bounded, 12_288, 18, 1_024);
        invalid(() -> LoaderCarrier.decode(padTo(valid, "pad", 12_289)));
    }
}
