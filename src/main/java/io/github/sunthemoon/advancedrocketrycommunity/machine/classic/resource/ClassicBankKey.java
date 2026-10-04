package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import java.util.Objects;

/** Absolute coordinates within the owner's Level, never packed or rotation-relative. */
public record ClassicBankKey(ClassicBankKind kind, int x, int y, int z) {
    public ClassicBankKey {
        Objects.requireNonNull(kind, "kind");
    }

    public String channel() { return kind.id() + "." + x + "." + y + "." + z; }

    public static ClassicBankKey parse(String channel) {
        Objects.requireNonNull(channel, "channel");
        if (channel.length() > ProcessResourceKey.MAX_CHANNEL_CHARS) {
            throw new IllegalArgumentException("Classic bank channel exceeds shared limit");
        }
        String[] parts = channel.split("\\.", -1);
        if (parts.length != 4) { throw new IllegalArgumentException("Invalid classic bank channel"); }
        try {
            ClassicBankKey key = new ClassicBankKey(ClassicBankKind.parse(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            if (!key.channel().equals(channel)) {
                throw new IllegalArgumentException("Noncanonical classic bank channel");
            }
            return key;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid classic bank coordinate", invalid);
        }
    }
}
