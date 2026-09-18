package com.omnistore.repository;

import com.omnistore.exception.InsufficientStockException;
import com.omnistore.exception.WarehouseNotFoundException;
import com.omnistore.model.OrderItem;
import com.omnistore.model.Product;
import com.omnistore.model.Warehouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe multi-warehouse inventory repository leveraging ConcurrentHashMap and atomic operations
 * to guarantee high-concurrency correctness, non-blocking reads, and isolated atomic updates.
 */
public class InventoryRepository {

    /**
     * Inner record-like structure managing concurrency-safe stock counters and thresholds.
     */
    public static class StockLevel {
        private final AtomicInteger availableStock;
        private final int safetyThreshold;
        private final int initialStock;
        private final AtomicInteger totalDeductions;

        public StockLevel(int initialStock, int safetyThreshold) {
            if (initialStock < 0) {
                throw new IllegalArgumentException("Initial stock cannot be negative: " + initialStock);
            }
            if (safetyThreshold < 0) {
                throw new IllegalArgumentException("Safety threshold cannot be negative: " + safetyThreshold);
            }
            this.initialStock = initialStock;
            this.availableStock = new AtomicInteger(initialStock);
            this.safetyThreshold = safetyThreshold;
            this.totalDeductions = new AtomicInteger(0);
        }

        public int getAvailableStock() {
            return availableStock.get();
        }

        public int getSafetyThreshold() {
            return safetyThreshold;
        }

        public int getInitialStock() {
            return initialStock;
        }

        public int getTotalDeductions() {
            return totalDeductions.get();
        }

        /**
         * Atomically decrements the stock using a CAS (Compare-And-Swap) loop.
         * Triggers a real-time console warning if remaining stock reaches or dips below the safety threshold.
         */
        public int deduct(int quantity, String warehouseId, String productId) throws InsufficientStockException {
            while (true) {
                int current = availableStock.get();
                if (current < quantity) {
                    throw new InsufficientStockException(warehouseId, productId, quantity, current);
                }
                int updated = current - quantity;
                if (availableStock.compareAndSet(current, updated)) {
                    totalDeductions.addAndGet(quantity);
                    if (updated <= safetyThreshold) {
                        System.out.printf("[ALERT] LOW STOCK WARNING: Warehouse '%s' | Product '%s' dropped to %d units (Safety Threshold: %d)%n",
                                warehouseId, productId, updated, safetyThreshold);
                    }
                    return updated;
                }
            }
        }

        /**
         * Restores stock atomically (e.g., during order compensation / rollback).
         */
        public int restore(int quantity) {
            totalDeductions.addAndGet(-quantity);
            return availableStock.addAndGet(quantity);
        }

        /**
         * Adds new stock replenishments.
         */
        public int replenish(int quantity) {
            return availableStock.addAndGet(quantity);
        }
    }

    private final ConcurrentHashMap<String, Warehouse> warehouseMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Product> productMap = new ConcurrentHashMap<>();

    // Map: warehouseId -> (productId -> StockLevel)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, StockLevel>> warehouseInventory = new ConcurrentHashMap<>();

    public InventoryRepository() {
    }

    public void addWarehouse(Warehouse warehouse) {
        Objects.requireNonNull(warehouse, "Warehouse cannot be null");
        warehouseMap.put(warehouse.getWarehouseId(), warehouse);
        warehouseInventory.putIfAbsent(warehouse.getWarehouseId(), new ConcurrentHashMap<>());
    }

    public void addProduct(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        productMap.put(product.getProductId(), product);
    }

    public void setStock(String warehouseId, String productId, int quantity, int safetyThreshold) {
        if (!warehouseMap.containsKey(warehouseId)) {
            throw new IllegalArgumentException("Warehouse '" + warehouseId + "' not registered in repository.");
        }
        if (!productMap.containsKey(productId)) {
            throw new IllegalArgumentException("Product '" + productId + "' not registered in repository.");
        }

        warehouseInventory.computeIfAbsent(warehouseId, k -> new ConcurrentHashMap<>())
                .put(productId, new StockLevel(quantity, safetyThreshold));
    }

    public int getStock(String warehouseId, String productId) {
        ConcurrentHashMap<String, StockLevel> stockMap = warehouseInventory.get(warehouseId);
        if (stockMap == null) return 0;
        StockLevel level = stockMap.get(productId);
        return level != null ? level.getAvailableStock() : 0;
    }

    public int getSafetyThreshold(String warehouseId, String productId) {
        ConcurrentHashMap<String, StockLevel> stockMap = warehouseInventory.get(warehouseId);
        if (stockMap == null) return 0;
        StockLevel level = stockMap.get(productId);
        return level != null ? level.getSafetyThreshold() : 0;
    }

    public boolean hasStock(String warehouseId, String productId, int quantity) {
        return getStock(warehouseId, productId) >= quantity;
    }

    /**
     * Checks if a warehouse has sufficient stock for all items in an order.
     */
    public boolean canFulfillOrder(String warehouseId, List<OrderItem> items) {
        Warehouse warehouse = warehouseMap.get(warehouseId);
        if (warehouse == null || !warehouse.isActive()) {
            return false;
        }
        for (OrderItem item : items) {
            if (!hasStock(warehouseId, item.getProductId(), item.getQuantity())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Atomically deducts inventory for a product in a specified warehouse.
     */
    public int deductStock(String warehouseId, String productId, int quantity)
            throws InsufficientStockException, WarehouseNotFoundException {
        if (!warehouseMap.containsKey(warehouseId)) {
            throw new WarehouseNotFoundException("N/A", "Warehouse '" + warehouseId + "' does not exist.");
        }

        ConcurrentHashMap<String, StockLevel> stockMap = warehouseInventory.get(warehouseId);
        if (stockMap == null) {
            throw new InsufficientStockException(warehouseId, productId, quantity, 0);
        }

        StockLevel stockLevel = stockMap.get(productId);
        if (stockLevel == null) {
            throw new InsufficientStockException(warehouseId, productId, quantity, 0);
        }

        return stockLevel.deduct(quantity, warehouseId, productId);
    }

    /**
     * Compensating transaction: restores deducted stock in case of multi-item order failure.
     */
    public void restoreStock(String warehouseId, String productId, int quantity) {
        ConcurrentHashMap<String, StockLevel> stockMap = warehouseInventory.get(warehouseId);
        if (stockMap != null) {
            StockLevel stockLevel = stockMap.get(productId);
            if (stockLevel != null) {
                stockLevel.restore(quantity);
            }
        }
    }

    public Optional<Warehouse> getWarehouse(String warehouseId) {
        return Optional.ofNullable(warehouseMap.get(warehouseId));
    }

    public Optional<Product> getProduct(String productId) {
        return Optional.ofNullable(productMap.get(productId));
    }

    public List<Warehouse> getAllWarehouses() {
        return Collections.unmodifiableList(new ArrayList<>(warehouseMap.values()));
    }

    public List<Warehouse> getActiveWarehouses() {
        return warehouseMap.values().stream()
                .filter(Warehouse::isActive)
                .toList();
    }

    public List<Product> getAllProducts() {
        return Collections.unmodifiableList(new ArrayList<>(productMap.values()));
    }

    public StockLevel getStockLevel(String warehouseId, String productId) {
        ConcurrentHashMap<String, StockLevel> stockMap = warehouseInventory.get(warehouseId);
        if (stockMap == null) return null;
        return stockMap.get(productId);
    }

    /**
     * Prints a formatted snapshot table of current warehouse stocks across all registered products.
     */
    public void printInventorySnapshot() {
        System.out.println("=========================================================================================");
        System.out.println("                       CURRENT INVENTORY SNAPSHOT                                       ");
        System.out.println("=========================================================================================");
        System.out.printf("%-14s | %-20s | %-16s | %-8s | %-9s | %-9s%n",
                "Warehouse ID", "Warehouse Name", "Product SKU", "Initial", "Available", "Threshold");
        System.out.println("-----------------------------------------------------------------------------------------");

        for (Warehouse warehouse : warehouseMap.values()) {
            ConcurrentHashMap<String, StockLevel> items = warehouseInventory.get(warehouse.getWarehouseId());
            if (items != null) {
                for (Map.Entry<String, StockLevel> entry : items.entrySet()) {
                    Product prod = productMap.get(entry.getKey());
                    String sku = prod != null ? prod.getSku() : entry.getKey();
                    StockLevel level = entry.getValue();
                    System.out.printf("%-14s | %-20s | %-16s | %-8d | %-9d | %-9d%n",
                            warehouse.getWarehouseId(),
                            warehouse.getName(),
                            sku,
                            level.getInitialStock(),
                            level.getAvailableStock(),
                            level.getSafetyThreshold());
                }
            }
        }
        System.out.println("=========================================================================================");
    }
}

