package io.github.sunthemoon.advancedrocketrycommunity.travel.route.model;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class ExactMapCodec {
    private ExactMapCodec() {
    }

    static <A> Codec<A> wrap(Codec<A> delegate, Set<String> expectedKeys, String recordName) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input)
                        .flatMap(map -> {
                            Set<String> keys = new HashSet<>();
                            for (Pair<T, T> entry : map.entries().toList()) {
                                Optional<String> key = ops.getStringValue(entry.getFirst()).result();
                                if (key.isEmpty()) {
                                    return DataResult.error(() -> recordName + " contains a non-string field name");
                                }
                                keys.add(key.get());
                            }
                            if (!keys.equals(expectedKeys)) {
                                return DataResult.error(() -> recordName + " fields must be exactly "
                                        + expectedKeys + "; found " + keys);
                            }
                            return delegate.decode(ops, input);
                        });
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return delegate.encode(input, ops, prefix);
            }
        };
    }
}
