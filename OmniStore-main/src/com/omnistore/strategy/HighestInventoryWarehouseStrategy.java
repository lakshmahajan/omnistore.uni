package com.omnistore.strategy;

import com.omnistore.model.Order;
import com.omnistore.model.OrderItem;
import com.omnistore.model.Warehouse;
import com.omnistore.repository.InventoryRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Alternative routing strategy that selects the warehouse with the highest aggregate remaining stock
 * for the requested items, optimizing for warehouse load balancing.
 */
public class HighestInventoryWarehouseStrategy implements RoutingStrategy {

    @Override
    public Optional<Warehouse> selectWarehouse(Order order,
                                                List<Warehouse> candidateWarehouses,
                                                InventoryRepository inventoryRepository) {
        Objects.requireNonNull(order, "Order cannot be null");
        Objects.requireNonNull(candidateWarehouses, "Candidate warehouses cannot be null");
        Objects.requireNonNull(inventoryRepository, "InventoryRepository cannot be null");

        return candidateWarehouses.stream()
                .filter(Warehouse::isActive)
                .filter(wh -> inventoryRepository.canFulfillOrder(wh.getWarehouseId(), order.getItems()))
                .max(Comparator.comparingInt(wh -> calculateAggregateStock(wh.getWarehouseId(), order.getItems(), inventoryRepository)));
    }

    private int calculateAggregateStock(String warehouseId, List<OrderItem> items, InventoryRepository repo) {
        return items.stream()
                .mapToInt(item -> repo.getStock(warehouseId, item.getProductId()))
                .sum();
    }

    @Override
    public String getStrategyName() {
        return "Highest Inventory Capacity Strategy";
    }
}

