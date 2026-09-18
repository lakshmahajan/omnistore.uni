package com.omnistore.strategy;

import com.omnistore.model.Order;
import com.omnistore.model.Warehouse;
import com.omnistore.repository.InventoryRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Routing strategy that selects the geographically closest warehouse to the order's delivery location
 * that has sufficient inventory to satisfy all order line items.
 */
public class NearestWarehouseStrategy implements RoutingStrategy {

    @Override
    public Optional<Warehouse> selectWarehouse(Order order,
                                                List<Warehouse> candidateWarehouses,
                                                InventoryRepository inventoryRepository) {
        Objects.requireNonNull(order, "Order cannot be null");
        Objects.requireNonNull(candidateWarehouses, "Candidate warehouses cannot be null");
        Objects.requireNonNull(inventoryRepository, "InventoryRepository cannot be null");

        return candidateWarehouses.stream()
                .filter(Warehouse::isActive)
                .filter(warehouse -> inventoryRepository.canFulfillOrder(warehouse.getWarehouseId(), order.getItems()))
                .min(Comparator.comparingDouble(warehouse ->
                        order.getDeliveryLocation().distanceTo(warehouse.getLocation())
                ));
    }

    @Override
    public String getStrategyName() {
        return "Nearest Geographical Warehouse Strategy";
    }
}

