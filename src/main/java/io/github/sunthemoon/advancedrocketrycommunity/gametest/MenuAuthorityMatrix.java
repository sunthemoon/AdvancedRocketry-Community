package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.GameType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * W179 (VDOC 12.1, docs/17 section 6): the menu-authority matrix. For every menu type the mod registers, a GameTest
 * builds the menu's real holder, lets an admitted owner open it through the server's own interaction path, and then
 * checks that the server refuses the menu for five conditions:
 * <ul>
 * <li>{@link Check#DISTANCE}: the owner moves out of reach and the open menu is no longer valid;</li>
 * <li>{@link Check#SPECTATOR}: the owner, as a spectator, cannot keep the open menu, and the holder builds no menu
 * for it at all (W182-R2), so no open packet and no slot sync reach a spectator's client;</li>
 * <li>{@link Check#FOREIGN_OWNER}: another player cannot open it, keep it valid, or have an action accepted;</li>
 * <li>{@link Check#RATE}: within one tick, a burst of the owner's identical actions meets a refusal;</li>
 * <li>{@link Check#UNLOADED}: once the holder is unloaded, the open menu is no longer valid.</li>
 * </ul>
 * A menu that is not valid is closed by the server at the player's next tick, and vanilla drops every slot click
 * and button click for it; the {@code classic_controls} targets check the same validity. Every cell is preceded by a
 * positive control, the owner's valid menu, so a holder that refuses everybody cannot pass a cell.
 *
 * <p>A cell that no probe runs needs an entry in {@value #EXEMPTIONS} under {@code src/test/resources}, one reason
 * each. A {@code gap:<finding>} entry names a real gap found by this matrix: its cell still runs and must still show
 * the gap, so the entry has to go when the gap is fixed.
 */
public final class MenuAuthorityMatrix {
    static final String EXEMPTIONS = "menu-authority-exemptions.txt";
    /** A run from another directory may name the file directly. */
    static final String EXEMPTIONS_PROPERTY = "advancedrocketrycommunity.menuAuthorityExemptions";
    /** Every menu reach of the mod is at most 8 blocks; the owner steps this far away. */
    static final int AWAY_BLOCKS = 16;
    private static final int MIN_REASON_CHARS = 24;
    /** A one-tick burst of this many identical actions must meet a refusal; the largest window admits 8. */
    static final int MAX_BURST = 16;

    /** The five refusals, in the order a probe runs them; {@link #UNLOADED} goes last because it ends the holder. */
    public enum Check { DISTANCE, SPECTATOR, FOREIGN_OWNER, RATE, UNLOADED }

    /** What one action of a menu came to on the server. */
    public enum Outcome { ACCEPTED, RATE_LIMITED, REFUSED }

    /**
     * One exemption: a menu, one check or {@code ALL}, its kind and its reason. Kinds: {@code no-holder} (the menu
     * has no holder at all), {@code not-applicable} (the condition cannot arise for this menu), {@code elsewhere}
     * (a named test covers it and a GameTest cannot set it up) and {@code gap:<finding>} (see the class comment).
     */
    record Exemption(ResourceLocation menu, Optional<Check> check, String kind, String reason, int line) {
        boolean gap() {
            return kind.startsWith("gap:");
        }

        boolean covers(Check candidate) {
            return check.isEmpty() || check.get() == candidate;
        }
    }

    private MenuAuthorityMatrix() {
    }

    /** The exemption file, from the working directory upwards (Gradle runs GameTests in {@code build/gametest}). */
    static Path exemptionFile() {
        String named = System.getProperty(EXEMPTIONS_PROPERTY, "");
        if (!named.isBlank()) {
            return Path.of(named);
        }
        Path directory = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && directory != null; up++, directory = directory.getParent()) {
            Path candidate = directory.resolve("src").resolve("test").resolve("resources").resolve(EXEMPTIONS);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No src/test/resources/" + EXEMPTIONS + " above " + Path.of("").toAbsolutePath()
                + "; set -D" + EXEMPTIONS_PROPERTY);
    }

    /** Parses the exemption file; a malformed line is a defect, never skipped. */
    static List<Exemption> exemptions(List<String> defects) {
        List<String> lines;
        try {
            lines = Files.readAllLines(exemptionFile(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        List<Exemption> entries = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\s+", 4);
            ResourceLocation menu = fields.length == 4 ? ResourceLocation.tryParse(fields[0]) : null;
            Optional<Check> check = fields.length == 4 ? check(fields[1]) : null;
            String kind = fields.length == 4 ? fields[2] : "";
            boolean known = kind.equals("no-holder") || kind.equals("not-applicable") || kind.equals("elsewhere")
                    || kind.matches("gap:W179-F\\d+");
            if (menu == null || check == null || !known || fields[3].length() < MIN_REASON_CHARS
                    || kind.equals("no-holder") != check.isEmpty()) {
                defects.add(EXEMPTIONS + " line " + (index + 1) + " is malformed: " + line);
                continue;
            }
            entries.add(new Exemption(menu, check, kind, fields[3], index + 1));
        }
        return entries;
    }

    @Nullable
    private static Optional<Check> check(String field) {
        if (field.equals("ALL")) {
            return Optional.empty();
        }
        for (Check check : Check.values()) {
            if (check.name().equals(field)) {
                return Optional.of(check);
            }
        }
        return null;
    }

    /** The mod's registered menu types. */
    static Map<ResourceLocation, MenuType<?>> registered() {
        Map<ResourceLocation, MenuType<?>> menus = new java.util.TreeMap<>();
        for (var entry : ForgeRegistries.MENU_TYPES.getEntries()) {
            if (entry.getKey().location().getNamespace().equals(ModIdentity.MOD_ID)) {
                menus.put(entry.getKey().location(), entry.getValue());
            }
        }
        return menus;
    }

    /**
     * A connected survival player over an embedded channel, as a real client's server player: unlike
     * {@link ConnectedTestPlayers} it really becomes a spectator in spectator mode, and its permission level comes
     * from the server's ops list. Every chat and action-bar message is recorded as its code token.
     */
    public static final class MatrixPlayer extends ServerPlayer {
        public final List<String> messages = new ArrayList<>();

        private MatrixPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile);
        }

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(ConnectedTestPlayers.token(message));
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            messages.add(ConnectedTestPlayers.token(message));
        }

        /** Whether a message since {@code mark} (a size of {@link #messages}) has the code token {@code token}. */
        public boolean saidSince(int mark, String token) {
            return messages.subList(Math.min(mark, messages.size()), messages.size()).contains(token);
        }
    }

    static MatrixPlayer join(MinecraftServer server, UUID id, String name, ServerLevel level, BlockPos at) {
        MatrixPlayer player = new MatrixPlayer(server, level, new GameProfile(id, name));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.SURVIVAL);
        move(player, level, at);
        return player;
    }
    static void move(ServerPlayer player, ServerLevel level, BlockPos at) {
        player.teleportTo(level, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
    }

    /** The menu of {@code type} that the server opened for {@code player} through the fixture, or null. */
    @Nullable
    static AbstractContainerMenu open(MenuAuthorityFixture fixture, MenuType<?> type, ServerPlayer player) {
        player.closeContainer();
        fixture.open(player);
        AbstractContainerMenu menu = player.containerMenu;
        return menu != player.inventoryMenu && menu.getType() == type ? menu : null;
    }

    /**
     * Runs the probe's checks on a ready fixture and returns, for each check, whether the server refused, with the
     * reason in {@code notes}. A failed positive control is a defect, never a refusal.
     */
    static Map<Check, Boolean> run(MenuAuthorityProbe probe, MenuAuthorityFixture fixture, MenuType<?> type,
                                   ServerPlayer owner, ServerPlayer stranger, List<String> notes,
                                   List<String> defects) {
        Map<Check, Boolean> refused = new EnumMap<>(Check.class);
        Function<String, AbstractContainerMenu> control = cell -> {
            MenuAuthorityMatrix.move(owner, fixture.level(), fixture.stand());
            AbstractContainerMenu menu = open(fixture, type, owner);
            if (menu == null || !menu.stillValid(owner)) {
                defects.add(cell + ": the owner could not open a valid menu (" + owner.containerMenu.getClass()
                        .getSimpleName() + ")");
                return null;
            }
            return menu;
        };
        for (Check check : Check.values()) {
            if (!probe.checks().contains(check)) {
                continue;
            }
            switch (check) {
                case DISTANCE -> {
                    AbstractContainerMenu menu = control.apply("DISTANCE");
                    if (menu == null) {
                        continue;
                    }
                    move(owner, fixture.level(), fixture.stand().offset(AWAY_BLOCKS, 0, 0));
                    refused.put(check, !menu.stillValid(owner));
                    move(owner, fixture.level(), fixture.stand());
                    owner.closeContainer();
                }
                case SPECTATOR -> {
                    AbstractContainerMenu menu = control.apply("SPECTATOR");
                    if (menu == null) {
                        continue;
                    }
                    owner.setGameMode(GameType.SPECTATOR);
                    boolean kept = menu.stillValid(owner);
                    // W182-R2: a menu the server builds is refused, even one that stillValid would close next tick.
                    boolean built = open(fixture, type, owner) != null;
                    owner.closeContainer();
                    owner.setGameMode(GameType.SURVIVAL);
                    refused.put(check, !kept && !built);
                    notes.add("SPECTATOR kept=" + kept + " built=" + built);
                }
                case FOREIGN_OWNER -> {
                    if (control.apply("FOREIGN_OWNER") == null) {
                        continue;
                    }
                    owner.closeContainer();
                    move(stranger, fixture.level(), fixture.stand());
                    AbstractContainerMenu foreign = open(fixture, type, stranger);
                    if (foreign == null || !foreign.stillValid(stranger)) {
                        refused.put(check, true);
                        notes.add("FOREIGN_OWNER refused at open");
                    } else if (probe.acts()) {
                        Outcome outcome = fixture.act(stranger, foreign);
                        refused.put(check, outcome != Outcome.ACCEPTED);
                        notes.add("FOREIGN_OWNER opened; its action was " + outcome);
                        if (!probe.checks().contains(Check.RATE)) {
                            AbstractContainerMenu own = control.apply("FOREIGN_OWNER");
                            if (own != null && fixture.act(owner, own) != Outcome.ACCEPTED) {
                                defects.add("FOREIGN_OWNER: the owner's own action was refused");
                            }
                        }
                    } else {
                        refused.put(check, false);
                        notes.add("FOREIGN_OWNER opened a valid menu and the menu has no action");
                    }
                    stranger.closeContainer();
                    owner.closeContainer();
                }
                case RATE -> {
                    AbstractContainerMenu menu = control.apply("RATE");
                    if (menu == null) {
                        continue;
                    }
                    Outcome first = fixture.act(owner, menu);
                    if (first != Outcome.ACCEPTED) {
                        defects.add("RATE: the owner's first action was " + first);
                    }
                    Outcome later = Outcome.ACCEPTED;
                    int sent = 1;
                    while (later == Outcome.ACCEPTED && sent < MAX_BURST) {
                        later = fixture.act(owner, menu);
                        sent++;
                    }
                    refused.put(check, later != Outcome.ACCEPTED);
                    notes.add("RATE first=" + first + " then " + later + " at action " + sent);
                    owner.closeContainer();
                }
                case UNLOADED -> {
                    AbstractContainerMenu menu = control.apply("UNLOADED");
                    if (menu == null) {
                        continue;
                    }
                    fixture.unload();
                    refused.put(check, !menu.stillValid(owner));
                    owner.closeContainer();
                }
            }
        }
        return refused;
    }
}
