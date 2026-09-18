package com.omnistore.exception;

/**
 * Thrown when no eligible warehouse can be identified to fulfill an order based on routing strategies and inventory availability.
 */
public class WarehouseNotFoundException extends Exception {
    private final String orderId;

    public WarehouseNotFoundException(String orderId, String message) {
        super(String.format("Warehouse routing failed for order '%s': %s", orderId, message));
        this.orderId = orderId;
    }

    public WarehouseNotFoundException(String orderId) {
        this(orderId, "No active warehouse found with sufficient inventory to fulfill all requested items.");
    }

    public String getOrderId() {
        return orderId;
    }
}

