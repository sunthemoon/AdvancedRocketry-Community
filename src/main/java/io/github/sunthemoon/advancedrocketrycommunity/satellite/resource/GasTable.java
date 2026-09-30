package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** ADR-052 section 2: the gas products of one gas-giant body, with the version of its raw resource bytes. */
public record GasTable(ResourceLocation id, ResourceLocation body, List<Product> products, String tableVersion) {
    public static final int MAX_PRODUCTS = 8;

    public GasTable {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(tableVersion, "tableVersion");
        products = List.copyOf(products);
        if (products.isEmpty() || products.size() > MAX_PRODUCTS
                || products.stream().map(Product::item).distinct().count() != products.size()) {
            throw new IllegalArgumentException("Gas table " + id + " needs 1..8 unique products");
        }
        if (!MissionPayload.HEX16.matcher(tableVersion).matches()) {
            throw new IllegalArgumentException("Gas table version must be 16 hex characters");
        }
    }

    /** One product and its base amount per 1,000 ticks. */
    public record Product(ResourceLocation item, int amountPer1000Ticks) {
        public Product {
            Objects.requireNonNull(item, "item");
            if (amountPer1000Ticks < 1 || amountPer1000Ticks > 64) {
                throw new IllegalArgumentException("Gas amount per 1,000 ticks is outside 1..64");
            }
        }
    }
}
