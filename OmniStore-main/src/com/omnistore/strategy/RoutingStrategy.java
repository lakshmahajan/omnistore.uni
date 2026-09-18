package com.omnistore.strategy;

import com.omnistore.model.Order;
import com.omnistore.model.Warehouse;
import com.omnistore.repository.InventoryRepository;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface defining the contract for warehouse order routing algorithms.
 * Encapsulates the routing algorithm so it can be swapped dynamically at runtime
 * without altering OrderService.
 */
public interface RoutingStrategy {

    /**
     * Evaluates candidate warehouses and selects the optimal warehouse capable of fulfilling the order.
     *
     * @param order The purchase order to be fulfilled
     * @param candidateWarehouses List of candidate warehouses
     * @param inventoryRepository The inventory repository for stock availability checks
     * @return An Optional containing the selected Warehouse, or empty if no warehouse can fulfill
     */
    Optional<Warehouse> selectWarehouse(Order order,
                                        List<Warehouse> candidateWarehouses,
                                        InventoryRepository inventoryRepository);

    /**
     * Returns the human-readable name of the strategy.
     */
    String getStrategyName();
}

