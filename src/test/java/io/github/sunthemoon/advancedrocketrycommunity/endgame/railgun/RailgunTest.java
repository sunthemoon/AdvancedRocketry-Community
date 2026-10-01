package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-056 sections 3 and 4 as pure decisions, against the C10 reference vectors ({@code examples.json}). */
final class RailgunTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final ResourceLocation SPACE = ResourceLocation.tryBuild("advancedrocketrycommunity", "space");
    private static final ResourceLocation EARTH = ResourceLocation.tryBuild("advancedrocketrycommunity", "earth");
    private static final ResourceLocation MOON = ResourceLocation.tryBuild("advancedrocketrycommunity", "moon");
    private static final ResourceLocation SOL = ResourceLocation.tryBuild("advancedrocketrycommunity", "sol");
    private static final ResourceLocation CYGNUS = ResourceLocation.tryBuild("advancedrocketrycommunity",
            "cygnus_x1");
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final UUID STRANGER = new UUID(2L, 2L);
    private static final UUID SOURCE = new UUID(0L, 10L);
    private static final UUID TARGET = new UUID(0L, 20L);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static JsonObject examples() {
        try {
            return JsonParser.parseString(Files.readString(Path.of("docs", "work", "v1.7.0-preparation",
                    "examples.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void theQuotesMatchTheReferenceVectors() {
        int cases = 0;
        for (JsonElement element : examples().getAsJsonArray("railgun")) {
            JsonObject vector = element.getAsJsonObject();
            RailgunRoute.Quote quote = RailgunRoute.quote(vector.get("local").getAsBoolean(),
                    vector.get("dx").getAsLong(), vector.get("dz").getAsLong(), vector.get("percent").getAsInt());
            var expected = vector.getAsJsonArray("expected");
            assertEquals(expected.get(0).getAsString(), quote.routeClass().name(), vector.toString());
            assertEquals(expected.get(1).getAsInt(), quote.cost(), vector.toString());
            assertEquals(expected.get(2).getAsInt(), quote.travel(), vector.toString());
            assertTrue(quote.cost() <= RailgunStorage.ENERGY_CAPACITY, "the largest cost fits the buffer");
            cases++;
        }
        assertEquals(9, cases, "the accepted railgun vectors");
        assertThrows(IllegalArgumentException.class, () -> RailgunRoute.quote(true, 0, 0, 9));
        assertThrows(IllegalArgumentException.class, () -> RailgunRoute.quote(false, 0, 0, 401));
        assertThrows(IllegalArgumentException.class, () -> new RailgunSettings(100, 5));
        assertThrows(IllegalArgumentException.class, () -> new RailgunSettings(401, 4));
    }

    @Test
    void theDistanceIsTheIntegerSquareRootIn64Bits() {
        long[][] pairs = {{0, 0}, {1, 1}, {3, 4}, {-1000, -1000}, {60_000_000, 60_000_000}, {59_999_999, -1},
                {Integer.MAX_VALUE, Integer.MIN_VALUE}};
        for (long[] pair : pairs) {
            BigInteger square = BigInteger.valueOf(pair[0]).pow(2).add(BigInteger.valueOf(pair[1]).pow(2));
            assertEquals(square.sqrt().longValueExact(), RailgunRoute.distance(pair[0], pair[1]),
                    pair[0] + "," + pair[1]);
        }
    }

    private static RailgunRoute.Place place(ResourceLocation level, BlockPos pos, ResourceLocation body,
                                            ResourceLocation system) {
        return new RailgunRoute.Place(level, pos.asLong(), Optional.of(body), Optional.of(system));
    }

    @Test
    void theRouteRuleRefusesInItsOrderAndClassifiesTheRoute() {
        RailgunRoute.Place here = place(OVERWORLD, new BlockPos(0, 64, 0), EARTH, SOL);
        RailgunRoute.Place near = place(OVERWORLD, new BlockPos(300, 70, 400), EARTH, SOL);
        RailgunRoute.Place station = place(SPACE, new BlockPos(5_000, 128, 0), EARTH, SOL);
        RailgunRoute.Place moon = place(MOON, new BlockPos(0, 64, 0), MOON, SOL);
        RailgunRoute.Place cygnus = place(SPACE, new BlockPos(9_000, 128, 0), CYGNUS, CYGNUS);
        RailgunRoute.Place nowhere = new RailgunRoute.Place(OVERWORLD, 0L, Optional.empty(), Optional.empty());
        RailgunRoute.Candidate own = new RailgunRoute.Candidate(TARGET, true, true, OWNER);
        assertEquals(EndgameCode.NO_TARGET, RailgunRoute.check(SOURCE, OWNER, here,
                new RailgunRoute.Candidate(TARGET, false, true, OWNER), near, true, false), "not a railgun");
        assertEquals(EndgameCode.NO_TARGET, RailgunRoute.check(SOURCE, OWNER, here,
                new RailgunRoute.Candidate(TARGET, true, false, OWNER), near, true, false), "MISSING");
        assertEquals(EndgameCode.NO_TARGET, RailgunRoute.check(SOURCE, OWNER, here,
                new RailgunRoute.Candidate(SOURCE, true, true, OWNER), here, false, false), "itself");
        assertEquals(EndgameCode.OK, RailgunRoute.check(SOURCE, OWNER, here,
                new RailgunRoute.Candidate(SOURCE, true, true, OWNER), here, false, true), "a redirect home");
        RailgunRoute.Candidate foreign = new RailgunRoute.Candidate(TARGET, true, true, STRANGER);
        assertEquals(EndgameCode.TARGET_FOREIGN, RailgunRoute.check(SOURCE, OWNER, here, foreign, near, false,
                false));
        assertEquals(EndgameCode.OK, RailgunRoute.check(SOURCE, OWNER, here, foreign, near, true, false),
                "an operator selects any owner's railgun");
        assertEquals(EndgameCode.TARGET_FOREIGN, RailgunRoute.check(SOURCE, OWNER, nowhere, foreign, near, false,
                false), "the owner check comes before the bodies");
        assertEquals(EndgameCode.BODY_UNAVAILABLE, RailgunRoute.check(SOURCE, OWNER, nowhere, own, near, false,
                false));
        assertEquals(EndgameCode.BODY_UNAVAILABLE, RailgunRoute.check(SOURCE, OWNER, here, own, nowhere, false,
                false));
        assertEquals(EndgameCode.ROUTE_OUT_OF_SYSTEM, RailgunRoute.check(SOURCE, OWNER, here, own, cygnus, false,
                false));
        assertEquals(EndgameCode.OK, RailgunRoute.check(SOURCE, OWNER, here, own, moon, false, false));
        assertEquals(new RailgunRoute.Quote(RailgunRoute.RouteClass.LOCAL, 30_000, 27),
                RailgunRoute.quote(here, near, 100), "same Level and body");
        assertEquals(RailgunRoute.RouteClass.ORBITAL, RailgunRoute.quote(here, station, 100).routeClass(),
                "a station orbiting the same body is another Level");
        assertEquals(new RailgunRoute.Quote(RailgunRoute.RouteClass.ORBITAL, 250_000, 600),
                RailgunRoute.quote(here, moon, 100));
    }

    @Test
    void thePayloadIsTheFirstStackOfTheMinimumSize() {
        List<ItemStack> input = List.of(ItemStack.EMPTY, new ItemStack(Items.DIRT, 3), new ItemStack(Items.STONE, 64),
                new ItemStack(Items.DIAMOND, 16));
        assertEquals(1, RailgunLaunch.payloadSlot(input, 1));
        assertEquals(2, RailgunLaunch.payloadSlot(input, 4));
        assertEquals(2, RailgunLaunch.payloadSlot(input, 64));
        assertEquals(-1, RailgunLaunch.payloadSlot(List.of(ItemStack.EMPTY, new ItemStack(Items.DIRT, 63)), 64));
        assertEquals(1, RailgunLaunch.minimumStack(1, -16));
        assertEquals(17, RailgunLaunch.minimumStack(1, 16));
        assertEquals(64, RailgunLaunch.minimumStack(60, 16));
        assertTrue(RailgunLaunch.cadenceReady(0L, 20L) && !RailgunLaunch.cadenceReady(0L, 19L));
    }

    @Test
    void theFirstRefusalWins() {
        RailgunLaunch.Facts ready = new RailgunLaunch.Facts(true, true, true, EndgameCode.OK, 0, true, EndgameCode.OK,
                true, true, true, false, EndgameCode.OK);
        assertEquals(EndgameCode.OK, RailgunLaunch.refusal(ready));
        // Each step breaks one more condition; the earliest broken one is reported.
        List<UnaryOperator<RailgunLaunch.Facts>> breaks = new ArrayList<>();
        List<EndgameCode> expected = new ArrayList<>();
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), f.energy(), f.outboxFree(),
                f.escrowBlocked(), EndgameCode.TRANSIT_LIMIT));
        expected.add(EndgameCode.TRANSIT_LIMIT);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), f.energy(), false, f.escrowBlocked(),
                f.admission()));
        expected.add(EndgameCode.OUTBOX_FULL);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), f.energy(), f.outboxFree(), true,
                f.admission()));
        expected.add(EndgameCode.ROOT_BUSY);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), false, f.outboxFree(),
                f.escrowBlocked(), f.admission()));
        expected.add(EndgameCode.INSUFFICIENT_ENERGY);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), false, f.energy(), f.outboxFree(), f.escrowBlocked(),
                f.admission()));
        expected.add(EndgameCode.AWAITING_WORLD_SAVE);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), EndgameCode.ROUTE_OUT_OF_SYSTEM, f.targetDurable(), f.energy(),
                f.outboxFree(), f.escrowBlocked(), f.admission()));
        expected.add(EndgameCode.ROUTE_OUT_OF_SYSTEM);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), false, f.route(), f.targetDurable(), f.energy(), f.outboxFree(), f.escrowBlocked(),
                f.admission()));
        expected.add(EndgameCode.PAYLOAD_TOO_LARGE);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), f.sourceState(), -1,
                f.payloadFits(), f.route(), f.targetDurable(), f.energy(), f.outboxFree(), f.escrowBlocked(),
                f.admission()));
        expected.add(EndgameCode.NO_PAYLOAD);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), f.authorized(), EndgameCode.UNFORMED,
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), f.energy(), f.outboxFree(),
                f.escrowBlocked(), f.admission()));
        expected.add(EndgameCode.UNFORMED);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), f.enabled(), false, f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), f.energy(), f.outboxFree(),
                f.escrowBlocked(), f.admission()));
        expected.add(EndgameCode.UNAUTHORIZED);
        breaks.add(f -> new RailgunLaunch.Facts(f.operational(), false, f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), f.energy(), f.outboxFree(),
                f.escrowBlocked(), f.admission()));
        expected.add(EndgameCode.SYSTEM_DISABLED);
        breaks.add(f -> new RailgunLaunch.Facts(false, f.enabled(), f.authorized(), f.sourceState(),
                f.payloadSlot(), f.payloadFits(), f.route(), f.targetDurable(), f.energy(), f.outboxFree(),
                f.escrowBlocked(), f.admission()));
        expected.add(EndgameCode.ROOT_UNAVAILABLE);
        RailgunLaunch.Facts facts = ready;
        for (int i = 0; i < breaks.size(); i++) {
            facts = breaks.get(i).apply(facts);
            assertEquals(expected.get(i), RailgunLaunch.refusal(facts), "step " + i);
        }
    }
}
