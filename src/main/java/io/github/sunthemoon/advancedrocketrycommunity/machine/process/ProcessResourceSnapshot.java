package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, revisioned and fingerprinted view of all resources participating in a batch. */
public record ProcessResourceSnapshot(long revision, Map<ProcessResourceKey, ProcessResourceBalance> balances) {
    public static final int MAX_ENTRIES = 64;

    public ProcessResourceSnapshot {
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        Objects.requireNonNull(balances, "balances");
        if (balances.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("resource snapshot exceeds the entry limit");
        }
        TreeMap<ProcessResourceKey, ProcessResourceBalance> copy = new TreeMap<>();
        balances.forEach((key, balance) -> copy.put(
                Objects.requireNonNull(key, "resource key"),
                Objects.requireNonNull(balance, "resource balance")
        ));
        balances = Collections.unmodifiableMap(copy);
    }

    public ProcessResourceBalance balance(ProcessResourceKey key) {
        return balances.getOrDefault(key, new ProcessResourceBalance(0, 0));
    }

    public String fingerprint() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        update(digest, Long.toString(revision));
        for (Map.Entry<ProcessResourceKey, ProcessResourceBalance> entry : balances.entrySet()) {
            ProcessResourceKey key = entry.getKey();
            ProcessResourceBalance balance = entry.getValue();
            update(digest, key.kind().name());
            update(digest, key.channel());
            update(digest, key.resourceId());
            update(digest, Long.toString(balance.amount()));
            update(digest, Long.toString(balance.capacity()));
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
