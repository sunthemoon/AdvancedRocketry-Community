package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * ADR-058 section 5 consent (review R2-M1): the owners whose fields outside stations affect a player, chosen by that
 * player only. At most 32 UUIDs, stored as {@code {schema_version: 1, owners: [...]}} (review C11R-L6) under
 * {@code Player.PERSISTED_NBT_TAG} in the player's persistent data, which Forge keeps across death. A bare list, the
 * unversioned development format of C11, is read as schema 1 and rewritten in the new form on the next change. A
 * cache keeps the per-tick lookup cheap; it is filled from the player on first use and dropped at logout.
 */
public final class GravityTrust {
    public static final String KEY = "advancedrocketrycommunity_field_trust";
    public static final String OWNERS = "owners";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TRUSTED = 32;

    private final Map<UUID, Set<UUID>> cache = new HashMap<>();

    public Set<UUID> trusted(ServerPlayer player) {
        return cache.computeIfAbsent(player.getUUID(), ignored -> Set.copyOf(read(player)));
    }

    public EndgameCode trust(ServerPlayer player, UUID owner) {
        LinkedHashSet<UUID> owners = read(player);
        if (owners.contains(owner)) {
            return EndgameCode.OK;
        }
        if (owners.size() >= MAX_TRUSTED) {
            return EndgameCode.TRUST_LIMIT;
        }
        owners.add(owner);
        write(player, owners);
        return EndgameCode.OK;
    }

    public EndgameCode untrust(ServerPlayer player, UUID owner) {
        LinkedHashSet<UUID> owners = read(player);
        if (owners.remove(owner)) {
            write(player, owners);
        }
        return EndgameCode.OK;
    }

    public List<UUID> list(ServerPlayer player) {
        return new ArrayList<>(read(player));
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    public void clear() {
        cache.clear();
    }

    /**
     * Unreadable entries, and a list of another schema, are skipped; nothing is rewritten until the player changes the
     * list.
     */
    private static LinkedHashSet<UUID> read(ServerPlayer player) {
        LinkedHashSet<UUID> owners = new LinkedHashSet<>();
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = null;
        if (persisted.get(KEY) instanceof CompoundTag stored && stored.getInt("schema_version") == SCHEMA_VERSION
                && stored.get(OWNERS) instanceof ListTag versioned) {
            list = versioned;
        } else if (persisted.get(KEY) instanceof ListTag unversioned) {
            list = unversioned;
        }
        if (list != null && list.getElementType() == Tag.TAG_INT_ARRAY) {
            for (int i = 0; i < list.size() && owners.size() < MAX_TRUSTED; i++) {
                if (list.getIntArray(i).length == 4) {
                    owners.add(NbtUtils.loadUUID(list.get(i)));
                }
            }
        }
        return owners;
    }

    private void write(ServerPlayer player, LinkedHashSet<UUID> owners) {
        ListTag list = new ListTag();
        owners.forEach(owner -> list.add(NbtUtils.createUUID(owner)));
        CompoundTag stored = new CompoundTag();
        stored.putInt("schema_version", SCHEMA_VERSION);
        stored.put(OWNERS, list);
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, stored);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        cache.put(player.getUUID(), Set.copyOf(owners));
    }
}
