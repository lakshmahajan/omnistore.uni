package com.omnistore;

import com.omnistore.exception.InsufficientStockException;
import com.omnistore.exception.WarehouseNotFoundException;
import com.omnistore.model.Location;
import com.omnistore.model.Order;
import com.omnistore.model.OrderItem;
import com.omnistore.model.OrderStatus;
import com.omnistore.model.Product;
import com.omnistore.model.Warehouse;
import com.omnistore.repository.InventoryRepository;
import com.omnistore.service.OrderService;
import com.omnistore.strategy.HighestInventoryWarehouseStrategy;
import com.omnistore.strategy.NearestWarehouseStrategy;
import com.omnistore.strategy.RoutingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Driver execution class demonstrating the OmniStore Enterprise Multi-Warehouse
 * Inventory & Order Routing Engine under high-concurrency multi-threaded workloads.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=========================================================================================");
        System.out.println("     OMNISTORE: ENTERPRISE MULTI-WAREHOUSE INVENTORY & ORDER ROUTING ENGINE             ");
        System.out.println("     Java Runtime: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("=========================================================================================\n");

        // 1. Initialize Repository
        InventoryRepository repository = new InventoryRepository();

        // 2. Register Catalog Products
        Product p100 = new Product("PROD-100", "Omni UltraBook 16-Inch", "SKU-ULTRA-16", 1499.00);
        Product p200 = new Product("PROD-200", "Omni 4K HDR Monitor 32-Inch", "SKU-MON-4K", 599.00);
        Product p300 = new Product("PROD-300", "Omni Pro Wireless Keyboard", "SKU-KEY-WL", 129.00);
        Product p400 = new Product("PROD-400", "Omni Precision Ergonomic Mouse", "SKU-MOU-ERG", 79.00);

        repository.addProduct(p100);
        repository.addProduct(p200);
        repository.addProduct(p300);
        repository.addProduct(p400);

        // 3. Register Strategic Regional Warehouses (Geographical coordinates)
        Warehouse whEast = new Warehouse("WH-EAST", "New York Fulfillment Hub", new Location(40.7128, -74.0060));
        Warehouse whMidwest = new Warehouse("WH-MIDWEST", "Chicago Logistics Center", new Location(41.8781, -87.6298));
        Warehouse whWest = new Warehouse("WH-WEST", "Los Angeles Distribution Node", new Location(34.0522, -118.2437));
        Warehouse whSouth = new Warehouse("WH-SOUTH", "Dallas Central Depot", new Location(32.7767, -96.7970));

        repository.addWarehouse(whEast);
        repository.addWarehouse(whMidwest);
        repository.addWarehouse(whWest);
        repository.addWarehouse(whSouth);

        // 4. Seed Initial Stocks and Safety Thresholds
        // WH-EAST: High stock on monitors, limited laptops with low safety threshold
        repository.setStock("WH-EAST", "PROD-100", 12, 4); // Alert at <= 4
        repository.setStock("WH-EAST", "PROD-200", 25, 8);
        repository.setStock("WH-EAST", "PROD-300", 40, 10);
        repository.setStock("WH-EAST", "PROD-400", 50, 15);

        // WH-MIDWEST: Balanced stock
        repository.setStock("WH-MIDWEST", "PROD-100", 10, 3);
        repository.setStock("WH-MIDWEST", "PROD-200", 18, 5);
        repository.setStock("WH-MIDWEST", "PROD-300", 35, 8);
        repository.setStock("WH-MIDWEST", "PROD-400", 45, 10);

        // WH-WEST: Tight laptop inventory to trigger threshold alerts quickly
        repository.setStock("WH-WEST", "PROD-100", 8, 3);
        repository.setStock("WH-WEST", "PROD-200", 15, 5);
        repository.setStock("WH-WEST", "PROD-300", 30, 8);
        repository.setStock("WH-WEST", "PROD-400", 40, 10);

        // WH-SOUTH: Generous stock
        repository.setStock("WH-SOUTH", "PROD-100", 14, 4);
        repository.setStock("WH-SOUTH", "PROD-200", 20, 6);
        repository.setStock("WH-SOUTH", "PROD-300", 50, 12);
        repository.setStock("WH-SOUTH", "PROD-400", 60, 15);

        System.out.println(">> Initial Inventory Baseline Configured:");
        repository.printInventorySnapshot();

        // 5. Initialize Order Routing Engine with Nearest Warehouse Strategy
        RoutingStrategy nearestStrategy = new NearestWarehouseStrategy();
        OrderService orderService = new OrderService(repository, nearestStrategy);

        System.out.println("\n>> Initialized OrderService with Strategy: " + orderService.getRoutingStrategy().getStrategyName());

        // Calculate initial total inventory across all warehouses for later reconciliation
        int initialTotalStock = calculateTotalStock(repository);

        // 6. Create Concurrent Orders from Various Customer Locations
        List<Order> batch1Orders = createTestOrders();

        System.out.println("\n>> Launching Phase 1: High-Concurrency Order Simulation (Nearest Strategy)...");
        System.out.println(">> Submitting " + batch1Orders.size() + " concurrent orders across 8 worker threads...\n");

        int threadPoolSize = 8;
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(batch1Orders.size());

        for (Order order : batch1Orders) {
            executorService.submit(() -> {
                try {
                    orderService.processOrder(order);
                } catch (InsufficientStockException | WarehouseNotFoundException e) {
                    // Handled internally by orderService and marked as FAILED
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Execution interrupted while awaiting order processing.");
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        // 7. Dynamic Strategy Swap Demonstration
        System.out.println("\n-----------------------------------------------------------------------------------------");
        System.out.println(">> Dynamic Strategy Pattern Swap: Switching to HighestInventoryWarehouseStrategy");
        System.out.println("-----------------------------------------------------------------------------------------");
        RoutingStrategy inventoryStrategy = new HighestInventoryWarehouseStrategy();
        orderService.setRoutingStrategy(inventoryStrategy);
        System.out.println(">> Active Strategy is now: " + orderService.getRoutingStrategy().getStrategyName());

        // Process a second targeted batch under the new strategy
        List<Order> batch2Orders = List.of(
                new Order("ORD-SWAP-01", "CUST-901",
                        List.of(new OrderItem("PROD-100", 2, 1499.00), new OrderItem("PROD-300", 5, 129.00)),
                        new Location(39.7392, -104.9903)), // Denver (equidistant between Midwest, West, South)
                new Order("ORD-SWAP-02", "CUST-902",
                        List.of(new OrderItem("PROD-200", 3, 599.00), new OrderItem("PROD-400", 4, 79.00)),
                        new Location(29.7604, -95.3698))   // Houston
        );

        for (Order order : batch2Orders) {
            try {
                orderService.processOrder(order);
            } catch (Exception e) {
                // Handled
            }
        }

        // 8. Output Audits and Integrity Verification
        orderService.printSummaryReport();
        repository.printInventorySnapshot();

        performIntegrityReconciliation(repository, orderService, initialTotalStock);
    }

    private static List<Order> createTestOrders() {
        List<Order> orders = new ArrayList<>();

        // Customer Locations:
        // Boston: 42.3601, -71.0589 (Near NY)
        // Philadelphia: 39.9526, -75.1652 (Near NY)
        // Milwaukee: 43.0389, -87.9065 (Near Chicago)
        // Indianapolis: 39.7684, -86.1581 (Near Chicago)
        // San Francisco: 37.7749, -122.4194 (Near LA)
        // San Diego: 32.7157, -117.1611 (Near LA)
        // Austin: 30.2672, -97.7431 (Near Dallas)
        // Oklahoma City: 35.4676, -97.5164 (Near Dallas)
        // Seattle: 47.6062, -122.3321 (West coast)
        // Miami: 25.7617, -80.1918 (Southeast)

        orders.add(new Order("ORD-001", "CUST-101",
                List.of(new OrderItem("PROD-100", 3, 1499.00), new OrderItem("PROD-300", 2, 129.00)),
                new Location(42.3601, -71.0589))); // Boston -> Expect WH-EAST

        orders.add(new Order("ORD-002", "CUST-102",
                List.of(new OrderItem("PROD-100", 4, 1499.00), new OrderItem("PROD-400", 3, 79.00)),
                new Location(39.9526, -75.1652))); // Philly -> Expect WH-EAST

        orders.add(new Order("ORD-003", "CUST-103",
                List.of(new OrderItem("PROD-100", 4, 1499.00)),
                new Location(40.7128, -74.0060))); // NYC -> Expect WH-EAST (May trigger safety threshold!)

        orders.add(new Order("ORD-004", "CUST-104",
                List.of(new OrderItem("PROD-100", 2, 1499.00)),
                new Location(41.8781, -87.6298))); // Chicago -> Expect WH-MIDWEST

        orders.add(new Order("ORD-005", "CUST-105",
                List.of(new OrderItem("PROD-200", 4, 599.00), new OrderItem("PROD-400", 5, 79.00)),
                new Location(43.0389, -87.9065))); // Milwaukee -> Expect WH-MIDWEST

        orders.add(new Order("ORD-006", "CUST-106",
                List.of(new OrderItem("PROD-100", 3, 1499.00), new OrderItem("PROD-300", 4, 129.00)),
                new Location(37.7749, -122.4194))); // SF -> Expect WH-WEST

        orders.add(new Order("ORD-007", "CUST-107",
                List.of(new OrderItem("PROD-100", 4, 1499.00)),
                new Location(32.7157, -117.1611))); // San Diego -> Expect WH-WEST (Depletes/triggers threshold!)

        orders.add(new Order("ORD-008", "CUST-108",
                List.of(new OrderItem("PROD-100", 3, 1499.00)),
                new Location(34.0522, -118.2437))); // LA -> Expect WH-WEST or fallback

        orders.add(new Order("ORD-009", "CUST-109",
                List.of(new OrderItem("PROD-200", 5, 599.00), new OrderItem("PROD-300", 6, 129.00)),
                new Location(30.2672, -97.7431))); // Austin -> Expect WH-SOUTH

        orders.add(new Order("ORD-010", "CUST-110",
                List.of(new OrderItem("PROD-100", 3, 1499.00), new OrderItem("PROD-400", 4, 79.00)),
                new Location(35.4676, -97.5164))); // OKC -> Expect WH-SOUTH

        orders.add(new Order("ORD-011", "CUST-111",
                List.of(new OrderItem("PROD-300", 10, 129.00)),
                new Location(25.7617, -80.1918))); // Miami -> Expect WH-SOUTH or WH-EAST

        orders.add(new Order("ORD-012", "CUST-112",
                List.of(new OrderItem("PROD-200", 2, 599.00), new OrderItem("PROD-400", 2, 79.00)),
                new Location(47.6062, -122.3321))); // Seattle -> Expect WH-WEST

        orders.add(new Order("ORD-013", "CUST-113",
                List.of(new OrderItem("PROD-100", 5, 1499.00)),
                new Location(42.3314, -83.0458))); // Detroit -> Expect WH-MIDWEST

        orders.add(new Order("ORD-014", "CUST-114",
                List.of(new OrderItem("PROD-400", 12, 79.00)),
                new Location(39.7684, -86.1581))); // Indianapolis -> Expect WH-MIDWEST

        orders.add(new Order("ORD-015", "CUST-115",
                List.of(new OrderItem("PROD-100", 4, 1499.00), new OrderItem("PROD-200", 2, 599.00)),
                new Location(32.7767, -96.7970))); // Dallas -> Expect WH-SOUTH

        orders.add(new Order("ORD-016", "CUST-116",
                List.of(new OrderItem("PROD-300", 15, 129.00)),
                new Location(40.7128, -74.0060))); // NYC -> Expect WH-EAST

        orders.add(new Order("ORD-017", "CUST-117",
                List.of(new OrderItem("PROD-100", 6, 1499.00)),
                new Location(36.1627, -86.7816))); // Nashville -> Expect WH-SOUTH or WH-MIDWEST

        orders.add(new Order("ORD-018", "CUST-118",
                List.of(new OrderItem("PROD-200", 10, 599.00)),
                new Location(33.7490, -84.3880))); // Atlanta -> Expect WH-SOUTH

        orders.add(new Order("ORD-019", "CUST-119",
                List.of(new OrderItem("PROD-100", 8, 1499.00)),
                new Location(38.9072, -77.0369))); // Washington DC -> tests bulk quantity

        orders.add(new Order("ORD-020", "CUST-120",
                List.of(new OrderItem("PROD-100", 25, 1499.00)),
                new Location(40.7128, -74.0060))); // Intentionally excessive quantity to test graceful rejection

        return orders;
    }

    private static int calculateTotalStock(InventoryRepository repository) {
        int total = 0;
        for (Warehouse wh : repository.getAllWarehouses()) {
            for (Product prod : repository.getAllProducts()) {
                total += repository.getStock(wh.getWarehouseId(), prod.getProductId());
            }
        }
        return total;
    }

    private static void performIntegrityReconciliation(InventoryRepository repository,
                                                       OrderService orderService,
                                                       int initialTotalStock) {
        System.out.println("=========================================================================================");
        System.out.println("                       CONCURRENCY & DATA INTEGRITY AUDIT                               ");
        System.out.println("=========================================================================================");

        int remainingStock = calculateTotalStock(repository);

        int totalUnitsFulfilled = orderService.getOrderAuditTrail().stream()
                .filter(o -> o.getStatus() == OrderStatus.FULFILLED)
                .mapToInt(Order::getTotalQuantity)
                .sum();

        int reconciledTotal = remainingStock + totalUnitsFulfilled;
        boolean matchesBaseline = (reconciledTotal == initialTotalStock);

        // Check for any negative stock violations
        boolean hasNegativeStock = false;
        for (Warehouse wh : repository.getAllWarehouses()) {
            for (Product prod : repository.getAllProducts()) {
                if (repository.getStock(wh.getWarehouseId(), prod.getProductId()) < 0) {
                    hasNegativeStock = true;
                    System.err.printf("[VIOLATION] Negative stock detected: WH: %s, Prod: %s, Stock: %d%n",
                            wh.getWarehouseId(), prod.getProductId(),
                            repository.getStock(wh.getWarehouseId(), prod.getProductId()));
                }
            }
        }

        System.out.printf("Baseline Initial Inventory Total : %d units%n", initialTotalStock);
        System.out.printf("Total Units Fulfilled Across Runs: %d units%n", totalUnitsFulfilled);
        System.out.printf("Current Warehouse Stock Balance  : %d units%n", remainingStock);
        System.out.printf("Reconciliation Sum (Stock + Sold): %d units%n", reconciledTotal);
        System.out.printf("Zero Negative Stocks Verified    : %s%n", !hasNegativeStock ? "PASSED [100% CLEAN]" : "FAILED");
        System.out.printf("Atomic Inventory Conservation    : %s%n", matchesBaseline ? "PASSED [100% MATCH]" : "FAILED [DISCREPANCY]");
        System.out.println("=========================================================================================\n");

        if (matchesBaseline && !hasNegativeStock) {
            System.out.println(">> CONCURRENCY VERIFICATION SUCCESSFUL: Zero race condition anomalies detected.");
            System.out.println(">> Thread-safe CAS loops and compensating transactions maintained absolute data integrity.\n");
        } else {
            System.err.println(">> CONCURRENCY VERIFICATION FAILED: Discrepancy detected in stock totals.");
        }
    }
}

