package com.omnistore.model;

import java.util.Objects;

/**
 * Represents a single line item within an order.
 */
public class OrderItem {
    private final String productId;
    private final int quantity;
    private final double unitPrice;

    public OrderItem(String productId, int quantity, double unitPrice) {
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero: " + quantity);
        }
        if (unitPrice < 0.0) {
            throw new IllegalArgumentException("Unit price cannot be negative: " + unitPrice);
        }
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getSubtotal() {
        return quantity * unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return quantity == orderItem.quantity &&
                Double.compare(orderItem.unitPrice, unitPrice) == 0 &&
                Objects.equals(productId, orderItem.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, quantity, unitPrice);
    }

    @Override
    public String toString() {
        return String.format("OrderItem[productId='%s', qty=%d, unitPrice=$%.2f, subtotal=$%.2f]",
                productId, quantity, unitPrice, getSubtotal());
    }
}

