package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ResourceMissionRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkDataEvent;

/**
 * ADR-051 section 5: the only persistence signal for a terminal ID or receipt is its presence in the
 * terminal's entry of a {@code ChunkDataEvent.Load} or {@code ChunkDataEvent.Save} tag. Save marks the live
 * terminal directly (main thread, before the asynchronous file write); Load records an observation that the
 * terminal consumes once it is in the level. Nothing else ({@code BlockEntity.load}, carried roots,
 * {@code /data}, structure capture) marks anything persisted.
 */
public final class TerminalChunkEvents {
    static final String BLOCK_ENTITIES = "block_entities";
    private final String blockEntityId;

    public TerminalChunkEvents(String blockEntityId) {
        this.blockEntityId = blockEntityId;
    }

    public void onLoad(ChunkDataEvent.Load event) {
        ResourceMissionRuntime.service().ifPresent(service -> entries(event.getData(), blockEntityId)
                .forEach(entry -> service.observations().record(entry.terminalId(), entry.pos(), entry.receipts())));
    }

    public void onSave(ChunkDataEvent.Save event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        for (Entry entry : entries(event.getData(), blockEntityId)) {
            BlockEntity blockEntity = chunk.getBlockEntities().get(entry.pos());
            if (blockEntity instanceof SatelliteTerminalBlockEntity terminal) {
                terminal.observedPersisted(entry.terminalId(), entry.receipts());
            }
        }
    }

    /** One terminal entry of a chunk tag: position, terminal ID and the receipts it held. */
    record Entry(BlockPos pos, UUID terminalId, Set<UUID> receipts) {
    }

    /** The well-formed schema-2 terminal entries of a chunk tag; anything malformed is skipped. */
    static List<Entry> entries(CompoundTag chunk, String blockEntityId) {
        List<Entry> entries = new ArrayList<>();
        if (!(chunk.get(BLOCK_ENTITIES) instanceof ListTag list) || list.getElementType() != Tag.TAG_COMPOUND) {
            return entries;
        }
        for (int index = 0; index < list.size(); index++) {
            CompoundTag blockEntity = list.getCompound(index);
            if (!blockEntityId.equals(blockEntity.getString("id"))
                    || !blockEntity.contains(SatelliteTerminalBlockEntity.DATA_KEY, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag data = blockEntity.getCompound(SatelliteTerminalBlockEntity.DATA_KEY);
            try {
                TerminalDelivery delivery = TerminalDelivery.read(data);
                entries.add(new Entry(new BlockPos(blockEntity.getInt("x"), blockEntity.getInt("y"),
                        blockEntity.getInt("z")), delivery.terminalId(), delivery.receiptIds()));
            } catch (RuntimeException malformed) {
                // A quarantined or older root carries no persistable delivery section.
            }
        }
        return entries;
    }
}
