package com.omnistore.service;

import com.omnistore.exception.InsufficientStockException;
import com.omnistore.exception.WarehouseNotFoundException;
import com.omnistore.model.Order;
import com.omnistore.model.OrderItem;
import com.omnistore.model.OrderStatus;
import com.omnistore.model.Warehouse;
import com.omnistore.repository.InventoryRepository;
import com.omnistore.strategy.RoutingStrategy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Enterprise service coordinating order routing and transactional stock deduction.
 * Guarantees atomicity across multi-item orders using compensating rollback actions on concurrent failure.
 */
public class OrderService {
    private final InventoryRepository inventoryRepository;
    private volatile RoutingStrategy routingStrategy;

    // Metrics and audit tracking
    private final AtomicInteger totalOrders = new AtomicInteger(0);
    private final AtomicInteger successfulOrders = new AtomicInteger(0);
    private final AtomicInteger failedOrders = new AtomicInteger(0);
    private final DoubleAdder totalRevenueFulfilled = new DoubleAdder();
    private final ConcurrentLinkedQueue<Order> orderAuditTrail = new ConcurrentLinkedQueue<>();

    public OrderService(InventoryRepository inventoryRepository, RoutingStrategy routingStrategy) {
        this.inventoryRepository = Objects.requireNonNull(inventoryRepository, "InventoryRepository cannot be null");
        this.routingStrategy = Objects.requireNonNull(routingStrategy, "RoutingStrategy cannot be null");
    }

    public RoutingStrategy getRoutingStrategy() {
        return routingStrategy;
    }

    public void setRoutingStrategy(RoutingStrategy routingStrategy) {
        this.routingStrategy = Objects.requireNonNull(routingStrategy, "RoutingStrategy cannot be null");
    }

    /**
     * Processes a single order end-to-end:
     * 1. Evaluates eligible candidate warehouses via the decoupled RoutingStrategy.
     * 2. Reserves and deducts required quantities atomically.
     * 3. Performs automatic rollback of partially deducted items if a race condition stockout occurs.
     * 4. Updates order lifecycle status and logs audit results.
     *
     * @param order The order to fulfill
     * @return The updated Order instance
     * @throws WarehouseNotFoundException if no warehouse can fulfill the request
     * @throws InsufficientStockException if concurrent depletion prevents full multi-item fulfillment
     */
    public Order processOrder(Order order) throws WarehouseNotFoundException, InsufficientStockException {
        Objects.requireNonNull(order, "Order cannot be null");
        totalOrders.incrementAndGet();

        order.setStatus(OrderStatus.PROCESSING);
        order.setStatusMessage("Selecting optimal warehouse via " + routingStrategy.getStrategyName());

        List<Warehouse> candidates = inventoryRepository.getActiveWarehouses();
        Optional<Warehouse> selectedWarehouseOpt = routingStrategy.selectWarehouse(order, candidates, inventoryRepository);

        if (selectedWarehouseOpt.isEmpty()) {
            order.setStatus(OrderStatus.FAILED);
            order.setCompletedAt(Instant.now());
            order.setStatusMessage("No active warehouse with sufficient stock found.");
            failedOrders.incrementAndGet();
            orderAuditTrail.add(order);
            throw new WarehouseNotFoundException(order.getOrderId(),
                    "No eligible warehouse found matching inventory requirements using " + routingStrategy.getStrategyName());
        }

        Warehouse warehouse = selectedWarehouseOpt.get();
        String warehouseId = warehouse.getWarehouseId();
        List<OrderItem> successfullyDeducted = new ArrayList<>();

        try {
            // Attempt transactional multi-item deduction
            for (OrderItem item : order.getItems()) {
                inventoryRepository.deductStock(warehouseId, item.getProductId(), item.getQuantity());
                successfullyDeducted.add(item);
            }

            // Transaction Succeeded
            order.setStatus(OrderStatus.FULFILLED);
            order.setAssignedWarehouseId(warehouseId);
            order.setCompletedAt(Instant.now());
            double distanceKm = order.getDeliveryLocation().distanceTo(warehouse.getLocation());
            order.setStatusMessage(String.format("Fulfilled by %s (%s, %.1f km away)",
                    warehouse.getName(), warehouseId, distanceKm));

            successfulOrders.incrementAndGet();
            totalRevenueFulfilled.add(order.getTotalAmount());
            orderAuditTrail.add(order);

            System.out.printf("[SUCCESS] Order '%s' | Customer '%s' | Warehouse '%s' (%.1f km) | Items: %d | Total: $%.2f%n",
                    order.getOrderId(), order.getCustomerId(), warehouseId, distanceKm,
                    order.getTotalQuantity(), order.getTotalAmount());

            return order;

        } catch (InsufficientStockException | WarehouseNotFoundException e) {
            // Compensating transaction: Rollback any items deducted in this order prior to failure
            for (OrderItem deductedItem : successfullyDeducted) {
                inventoryRepository.restoreStock(warehouseId, deductedItem.getProductId(), deductedItem.getQuantity());
            }

            order.setStatus(OrderStatus.FAILED);
            order.setCompletedAt(Instant.now());
            order.setStatusMessage("Stockout encountered during atomic deduction; transaction rolled back. " + e.getMessage());
            failedOrders.incrementAndGet();
            orderAuditTrail.add(order);

            System.err.printf("[FAILED] Order '%s' | Reason: Race condition / %s (Compensating rollback executed)%n",
                    order.getOrderId(), e.getMessage());

            if (e instanceof InsufficientStockException) {
                throw (InsufficientStockException) e;
            } else {
                throw (WarehouseNotFoundException) e;
            }
        }
    }

    public int getTotalOrders() {
        return totalOrders.get();
    }

    public int getSuccessfulOrders() {
        return successfulOrders.get();
    }

    public int getFailedOrders() {
        return failedOrders.get();
    }

    public double getTotalRevenueFulfilled() {
        return totalRevenueFulfilled.sum();
    }

    public List<Order> getOrderAuditTrail() {
        return Collections.unmodifiableList(new ArrayList<>(orderAuditTrail));
    }

    /**
     * Prints a formatted summary of orders processed.
     */
    public void printSummaryReport() {
        System.out.println("\n=========================================================================================");
        System.out.println("                        ORDER PROCESSING SERVICE AUDIT REPORT                            ");
        System.out.println("=========================================================================================");
        System.out.printf("Total Orders Received   : %d%n", totalOrders.get());
        System.out.printf("Successfully Fulfilled  : %d%n", successfulOrders.get());
        System.out.printf("Failed / Rejected       : %d%n", failedOrders.get());
        System.out.printf("Total Revenue Fulfilled : $%.2f%n", totalRevenueFulfilled.sum());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("%-10s | %-12s | %-12s | %-10s | %-10s | %-32s%n",
                "Order ID", "Customer", "Warehouse", "Status", "Amount", "Details");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (Order o : orderAuditTrail) {
            System.out.printf("%-10s | %-12s | %-12s | %-10s | $%-9.2f | %-32s%n",
                    o.getOrderId(),
                    o.getCustomerId(),
                    o.getAssignedWarehouseId() != null ? o.getAssignedWarehouseId() : "NONE",
                    o.getStatus(),
                    o.getTotalAmount(),
                    o.getStatusMessage() != null && o.getStatusMessage().length() > 32
                            ? o.getStatusMessage().substring(0, 29) + "..."
                            : o.getStatusMessage());
        }
        System.out.println("=========================================================================================\n");
    }
}

