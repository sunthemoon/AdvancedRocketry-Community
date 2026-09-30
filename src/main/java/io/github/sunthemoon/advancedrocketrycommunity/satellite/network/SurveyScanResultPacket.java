package io.github.sunthemoon.advancedrocketrycommunity.satellite.network;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-049 section 8: one finished survey area scan. Transient display data that grants nothing: per cell the
 * ore share of non-air blocks (0..65,535) and a biome palette index, {@code OTHER} or {@code UNKNOWN}.
 */
public record SurveyScanResultPacket(int centreX, int centreZ, int radius, int cell,
                                     List<ResourceLocation> palette, List<Cell> cells) {
    public static final int MAX_BYTES = 8 * 1024;
    public static final int MAX_PALETTE = 16;
    public static final int MAX_ID_CHARS = 128;

    public SurveyScanResultPacket {
        palette = List.copyOf(palette);
        cells = List.copyOf(cells);
        if (radius < SatelliteLimits.MIN_SCAN_RADIUS || radius > SatelliteLimits.MAX_SCAN_RADIUS
                || (cell != 4 && cell != 8 && cell != 16) || radius % cell != 0) {
            throw new IllegalArgumentException("Survey scan geometry is invalid");
        }
        int side = 2 * radius / cell;
        if (cells.size() != side * side || palette.size() > MAX_PALETTE
                || new HashSet<>(palette).size() != palette.size()
                || palette.stream().anyMatch(id -> id.toString().length() > MAX_ID_CHARS)) {
            throw new IllegalArgumentException("Survey scan result is outside its bounds");
        }
        for (Cell value : cells) {
            if (value.biome() >= palette.size() && value.biome() != Cell.OTHER && value.biome() != Cell.UNKNOWN) {
                throw new IllegalArgumentException("Survey scan cell names a missing palette entry");
            }
        }
    }

    public int side() {
        return 2 * radius / cell;
    }

    public static void encode(SurveyScanResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.centreX());
        buffer.writeInt(packet.centreZ());
        buffer.writeByte(packet.radius());
        buffer.writeByte(packet.cell());
        buffer.writeByte(packet.palette().size());
        packet.palette().forEach(id -> buffer.writeUtf(id.toString(), MAX_ID_CHARS));
        for (Cell value : packet.cells()) {
            buffer.writeShort(value.ratio());
            buffer.writeByte(value.biome());
        }
    }

    public static SurveyScanResultPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("Survey scan result exceeds " + MAX_BYTES + " bytes");
        }
        try {
            int centreX = buffer.readInt();
            int centreZ = buffer.readInt();
            int radius = buffer.readUnsignedByte();
            int cell = buffer.readUnsignedByte();
            if (radius < SatelliteLimits.MIN_SCAN_RADIUS || radius > SatelliteLimits.MAX_SCAN_RADIUS
                    || (cell != 4 && cell != 8 && cell != 16) || radius % cell != 0) {
                throw new DecoderException("Survey scan geometry is invalid");
            }
            int paletteSize = buffer.readUnsignedByte();
            if (paletteSize > MAX_PALETTE) {
                throw new DecoderException("Survey scan palette exceeds its bound");
            }
            List<ResourceLocation> palette = new ArrayList<>(paletteSize);
            for (int index = 0; index < paletteSize; index++) {
                ResourceLocation id = ResourceLocation.tryParse(buffer.readUtf(MAX_ID_CHARS));
                if (id == null) {
                    throw new DecoderException("Invalid survey scan biome ID");
                }
                palette.add(id);
            }
            int side = 2 * radius / cell;
            List<Cell> cells = new ArrayList<>(side * side);
            for (int index = 0; index < side * side; index++) {
                cells.add(new Cell(buffer.readUnsignedShort(), buffer.readUnsignedByte()));
            }
            if (buffer.isReadable()) {
                throw new DecoderException("Trailing bytes after the survey scan result");
            }
            return new SurveyScanResultPacket(centreX, centreZ, radius, cell, palette, cells);
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw new DecoderException("Malformed survey scan result", exception);
        }
    }

    public static void handle(SurveyScanResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            SurveyScanResultCache.accept(packet);
        }
        context.setPacketHandled(true);
    }

    /** One grid cell: the ore share and a palette index, {@link #OTHER} or {@link #UNKNOWN}. */
    public record Cell(int ratio, int biome) {
        public static final int OTHER = 0xFE;
        public static final int UNKNOWN = 0xFF;
        public static final Cell UNKNOWN_CELL = new Cell(0, UNKNOWN);

        public Cell {
            if (ratio < 0 || ratio > 65_535 || biome < 0 || biome > UNKNOWN
                    || biome >= MAX_PALETTE && biome != OTHER && biome != UNKNOWN
                    || biome == UNKNOWN && ratio != 0) {
                throw new IllegalArgumentException("Survey scan cell is outside its bounds");
            }
        }

        public boolean unknown() {
            return biome == UNKNOWN;
        }
    }
}
