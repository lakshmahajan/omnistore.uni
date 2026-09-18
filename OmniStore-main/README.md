# OmniStore: Enterprise Multi-Warehouse Inventory & Order Routing Engine

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Concurrency](https://img.shields.io/badge/Concurrency-Lock--Free%20CAS-purple.svg)]()

**OmniStore** is a high-throughput, thread-safe, enterprise-grade multi-warehouse inventory and intelligent order-routing engine developed in modern Java (JDK 17+ / Java 21). It solves the complex distributed fulfillment challenge by decoupling order fulfillment orchestration from pluggable routing algorithms via the Strategy Pattern, while safeguarding inventory data consistency under extreme multi-threaded concurrency using atomic operations and compensating transactions.

---

## Key Features

- **Decoupled Strategy Pattern**: Routing logic is fully decoupled from the core order lifecycle. Supports runtime plug-and-play strategies (e.g., `NearestWarehouseStrategy` using geodesic distance, `HighestInventoryWarehouseStrategy` for load balancing).
- **Non-Blocking, Thread-Safe Concurrency**: Core inventory levels use `ConcurrentHashMap` combined with `AtomicInteger` lock-free Compare-And-Swap (CAS) loops, preventing race conditions without heavy synchronized blocks.
- **Real-Time Safety Threshold Alerts**: Automated telemetry monitoring emits instant console warnings when warehouse inventory drops to or below pre-configured safety stock levels.
- **Transactional Atomicity & Compensating Rollbacks**: Multi-item orders execute with all-or-nothing guarantees. If stock for any item is exhausted during concurrent fulfillment, previous deductions within that transaction are rolled back cleanly.
- **Geographic Routing Calculations**: Integrates the Haversine great-circle distance formula to accurately determine transit distance between customer delivery points and warehouse coordinates.
- **End-to-End Audit & Reconciliation**: Embedded reconciliation reporting mathematically audits initial inventory vs. final stock and units sold, verifying zero negative inventory and conservation of stock.

---

## System Architecture & Module Layout

```
d:\OmniStore\
├── src\
│   └── com\
│       └── omnistore\
│           ├── exception\
│           │   ├── InsufficientStockException.java   # Raised when requested quantity exceeds stock
│           │   └── WarehouseNotFoundException.java   # Raised when no eligible warehouse fulfills criteria
│           ├── model\
│           │   ├── Location.java                    # Geolocation record with Haversine distance
│           │   ├── Order.java                       # Order lifecycle entity with status audit
│           │   ├── OrderItem.java                   # Line-item record with price & quantity checks
│           │   ├── OrderStatus.java                 # Lifecycle states: PENDING, PROCESSING, FULFILLED, FAILED
│           │   ├── Product.java                     # Catalog entity with SKU and pricing
│           │   └── Warehouse.java                   # Warehouse node with location & availability
│           ├── repository\
│           │   └── InventoryRepository.java         # Thread-safe repository with CAS & safety alerts
│           ├── service\
│           │   └── OrderService.java                # Order coordination, transactions, & rollback
│           ├── strategy\
│           │   ├── RoutingStrategy.java             # Common routing contract
│           │   ├── NearestWarehouseStrategy.java    # Haversine distance-based selection
│           │   └── HighestInventoryWarehouseStrategy.java # Load-balancing inventory capacity selection
│           └── Main.java                            # Concurrency simulation driver & audit suite
├── bin\                                             # Compiled bytecode binaries
├── statement.md                                     # Enterprise problem statement & scope
└── README.md                                        # Documentation & operational guide
```

---

## Technical Stack & Design Patterns

| Layer | Technologies & Implementations |
| :--- | :--- |
| **Language Runtime** | Java 17+ / Java 21 LTS |
| **Concurrency Core** | `java.util.concurrent`, `AtomicInteger`, `ConcurrentHashMap`, `CountDownLatch`, `ExecutorService` |
| **Design Patterns** | **Strategy Pattern** (Routing algorithms), **Repository Pattern** (Inventory access), **Compensating Transaction Pattern** (Rollback on partial stock failure) |
| **Math & Algorithms** | Haversine Great-Circle Distance, CAS (Compare-And-Swap) loops |

---

## Building and Running

### Prerequisites
- Java Development Kit (JDK 17 or later, e.g. OpenJDK / Temurin 21)

### 1. Compile Sources
From the root project folder (`d:\OmniStore`):

```powershell
# Create build directory and compile all Java classes
mkdir -p bin
javac -d bin $(Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
```

*(Or on standard Unix/Linux shells:)*
```bash
mkdir -p bin
find src -name "*.java" > sources.txt
javac -d bin @sources.txt
```

### 2. Execute Driver Simulation
Run the driver application:

```powershell
java -cp bin com.omnistore.Main
```

---

## Simulation & Output Demonstration

Upon running `Main.java`, the system:
1. Registers catalog items (`PROD-100` through `PROD-400`) and regional hubs (New York, Chicago, Los Angeles, Dallas)
2. Sets initial inventory balances and threshold limits across nodes.
3. Fires 20+ concurrent customer orders across an 8-thread worker pool.
4. Triggers real-time alerts when stock drops below configured safety thresholds:
   ```
   [ALERT] LOW STOCK WARNING: Warehouse 'WH-WEST' | Product 'PROD-100' dropped to 2 units (Safety Threshold: 3)
   ```
5. Gracefully handles concurrent conflicts with compensating transaction rollbacks:
   ```
   [FAILED] Order 'ORD-007' | Reason: Race condition / Insufficient stock... (Compensating rollback executed)
   ```
6. Dynamically swaps routing strategies at runtime to `HighestInventoryWarehouseStrategy`.
7. Concludes with a mathematical integrity audit confirming:
   ```
   Baseline Initial Inventory Total : 472 units
   Total Units Fulfilled Across Runs: 137 units
   Current Warehouse Stock Balance  : 335 units
   Reconciliation Sum (Stock + Sold): 472 units
   Zero Negative Stocks Verified    : PASSED [100% CLEAN]
   Atomic Inventory Conservation    : PASSED [100% MATCH]
   ```

