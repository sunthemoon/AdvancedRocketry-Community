package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.nio.file.Path;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * A real vanilla {@link DimensionDataStorage} over a temporary directory (W44 A0 case a). The data fixer is the
 * identity, because every fixture is written at the current data version.
 */
public final class TestDataStorage {
    private TestDataStorage() {
    }

    public static DimensionDataStorage over(Path dataDirectory) {
        return new DimensionDataStorage(dataDirectory.toFile(), new DataFixer() {
            @Override
            public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
                return input;
            }

            @Override
            public Schema getSchema(int key) {
                throw new UnsupportedOperationException("No schema in the identity fixer");
            }
        });
    }
}
