package com.omnistore.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a customer purchase order containing one or more line items to be fulfilled.
 */
public class Order {
    private final String orderId;
    private final String customerId;
    private final List<OrderItem> items;
    private final Location deliveryLocation;
    private final Instant createdAt;

    private volatile OrderStatus status;
    private volatile String assignedWarehouseId;
    private volatile Instant completedAt;
    private volatile String statusMessage;

    public Order(String orderId, String customerId, List<OrderItem> items, Location deliveryLocation) {
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID cannot be null");
        Objects.requireNonNull(items, "Items list cannot be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.deliveryLocation = Objects.requireNonNull(deliveryLocation, "Delivery location cannot be null");
        this.createdAt = Instant.now();
        this.status = OrderStatus.PENDING;
        this.statusMessage = "Order created";
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Location getDeliveryLocation() {
        return deliveryLocation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getAssignedWarehouseId() {
        return assignedWarehouseId;
    }

    public synchronized void setAssignedWarehouseId(String assignedWarehouseId) {
        this.assignedWarehouseId = assignedWarehouseId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public synchronized void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public synchronized void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public double getTotalAmount() {
        return items.stream()
                .mapToDouble(OrderItem::getSubtotal)
                .sum();
    }

    public int getTotalQuantity() {
        return items.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return String.format("Order[id='%s', customer='%s', status=%s, warehouse='%s', items=%d, total=$%.2f]",
                orderId, customerId, status,
                assignedWarehouseId != null ? assignedWarehouseId : "UNASSIGNED",
                items.size(), getTotalAmount());
    }
}

