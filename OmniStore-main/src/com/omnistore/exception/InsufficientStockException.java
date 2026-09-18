package com.omnistore.exception;

/**
 * Thrown when an inventory deduction cannot be satisfied due to inadequate available stock.
 */
public class InsufficientStockException extends Exception {
    private final String warehouseId;
    private final String productId;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(String warehouseId, String productId, int requestedQuantity, int availableQuantity) {
        super(String.format("Insufficient stock in warehouse '%s' for product '%s': requested %d, but only %d available.",
                warehouseId, productId, requestedQuantity, availableQuantity));
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public String getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}

