package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;

/** Shared strictness of the {@code classic_controls} codecs. */
final class ClassicCodecs {
    private ClassicCodecs() {
    }

    /**
     * A decoded frame must be byte-for-byte its own canonical encoding, so an overlong VarInt string length or any
     * other second spelling of the same message is refused. {@code start} is the reader index before decoding.
     */
    static <T> void requireCanonical(FriendlyByteBuf buffer, int start, T decoded, BiConsumer<T, FriendlyByteBuf> encoder,
                                     String what) {
        int length = buffer.readerIndex() - start;
        FriendlyByteBuf canonical = new FriendlyByteBuf(Unpooled.buffer(length));
        try {
            encoder.accept(decoded, canonical);
            if (canonical.readableBytes() != length || !ByteBufUtil.equals(canonical, 0, buffer, start, length)) {
                throw new DecoderException("A non-canonical " + what);
            }
        } finally {
            canonical.release();
        }
    }
}
