package com.omnistore.model;

import java.util.Objects;

/**
 * Represents a product available in the OmniStore inventory catalog.
 */
public class Product {
    private final String productId;
    private final String name;
    private final String sku;
    private final double price;

    public Product(String productId, String name, String sku, double price) {
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
        this.name = Objects.requireNonNull(name, "Product name cannot be null");
        this.sku = Objects.requireNonNull(sku, "SKU cannot be null");
        if (price < 0.0) {
            throw new IllegalArgumentException("Product price cannot be negative: " + price);
        }
        this.price = price;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(productId, product.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }

    @Override
    public String toString() {
        return String.format("Product[id='%s', name='%s', sku='%s', price=$%.2f]",
                productId, name, sku, price);
    }
}

