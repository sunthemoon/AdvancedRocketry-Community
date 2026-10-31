package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6PublicationProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactPublicationCasesReachTheRealLoaderFlightOnlyWithHooksEnabled() {
        for (var cut : List.of("LF_S6_PUBLICATION_RETURN", "LF_S6_PUBLICATION_HELD")) {
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, cut));
            assertTrue(EscrowLoaderS6Probe.selected(true, cut));
            assertFalse(EscrowLoaderS6FlightProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6FlightProbe.selected(true, cut + "_OTHER"));
        }
    }

    @Test void vetoRequiresExactReleaseRoundIdentityPhaseAndANewJoin() {
        var id = UUID.randomUUID(); var other = UUID.randomUUID();
        for (var cut : List.of(EscrowLoaderS6PublicationProbe.RETURN, EscrowLoaderS6PublicationProbe.HELD)) {
            int round = cut.equals(EscrowLoaderS6PublicationProbe.RETURN) ? 0 : 1;
            var phase = round == 0 ? RocketTransferPhase.DESTINATION_SPAWNED : RocketTransferPhase.PASSENGERS_TRANSFERRED;
            assertTrue(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(false, true, cut, round, false, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, false, cut, round, false, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, true, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, other, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, id, null, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut + "_OTHER", round, false, id, id, phase));
            for (int wrong : List.of(-1, 2, 3, 1 - round)) {
                assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, wrong, false, id, id, phase));
            }
            for (var wrong : RocketTransferPhase.values()) {
                if (wrong != phase) {
                    assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, id, id, wrong));
                }
            }
        }
    }

    @Test void registeredRocketHasNoNullableFactoryOrExperimentalFeatureRequirement() throws Exception {
        var source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/registry/ModEntities.java"));
        assertTrue(source.contains("EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)"));
        assertFalse(source.contains("requiredFeatures("));
        var type = net.minecraft.world.entity.EntityType.Builder
                .<io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity>of(
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity::new,
                        net.minecraft.world.entity.MobCategory.MISC).build("arce_test:w220_factory");
        assertEquals(net.minecraft.world.flag.FeatureFlags.VANILLA_SET, type.requiredFeatures());
        assertTrue(type.isEnabled(net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
        assertFalse(type.isEnabled(net.minecraft.world.flag.FeatureFlagSet.of()));
    }
}
