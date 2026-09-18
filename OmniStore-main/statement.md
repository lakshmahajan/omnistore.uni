# Project Statement: OmniStore Enterprise Multi-Warehouse Engine

## 1. Problem Statement

Modern omnichannel retail enterprises operate distributed fulfillment networks comprising regional distribution centers, urban micro-fulfillment nodes, and local retail stores. Fulfilling online customer orders in such environments poses several critical challenges:

1. **Sub-optimal Routing**: Assigning orders to distant warehouses increases shipping latency, carbon footprint, and carrier expenses.
2. **Concurrency Race Conditions**: High-volume flash sales and concurrent checkouts routinely trigger race conditions where two or more threads attempt to deduct the final available stock simultaneously, causing phantom inventory or negative stock counts.
3. **Partial Order Stockouts**: Multi-item orders that partially succeed before encountering an out-of-stock item leave inventories in inconsistent states without transactional rollback mechanisms.
4. **Stockout Blindspots**: Inability to detect approaching stock depletion in real time leads to delayed reordering, customer disappointment, and stockouts.
5. **Coupled Routing Architectures**: Tightly coupling fulfillment algorithms to core ordering pipelines creates brittle architectures that cannot adapt to changing business rules (e.g., swapping distance-based routing for capacity-load-balanced routing).

**OmniStore** resolves these challenges by providing an enterprise-grade, lock-free, thread-safe inventory repository and routing engine with pluggable strategies and compensating transactional semantics.

---

## 2. Project Scope

### In-Scope:
- **Distributed Catalog & Warehouse Modeling**: Precise representation of products, SKU catalogs, geolocated warehouses, order line items, and order lifecycle states.
- **Pluggable Order Routing Engine**: Strategy Pattern implementation decoupling routing decisions from the fulfillment pipeline. Includes:
  - *Nearest Warehouse Strategy*: Geodesic Haversine calculation selecting closest eligible fulfillment nodes.
  - *Highest Inventory Capacity Strategy*: Inventory load balancing across warehouses.
- **Thread-Safe Inventory Management**: Lock-free Compare-And-Swap (`AtomicInteger`) updates under high concurrent loads.
- **Safety Stock Telemetry**: Real-time console alerting triggered whenever inventory drops to or below a specified safety threshold.
- **Compensating Multi-Item Transactions**: Automatic rollback of previously deducted line items if any item in a multi-item order fails during fulfillment.
- **Audit & Mathematical Reconciliation**: Automated validation verifying exact conservation of inventory without negative stock violations.

### Out-of-Scope (Future Enhancements):
- Direct RDBMS/NoSQL persistence layers (relies on thread-safe in-memory data structures suitable for high-speed routing engines).
- REST API layer / HTTP controller interfaces (architected cleanly as a core service engine ready for Spring Boot or Quarkus integration).
- Multi-carrier logistics provider shipping label generation.

---

## 3. Target Users & Stakeholders

| Stakeholder Role | Value Proposition |
| :--- | :--- |
| **Enterprise Architects** | Clean separation of concerns via design patterns (Strategy, Repository, Compensating Transaction); easily embeddable into larger microservice architectures. |
| **Supply Chain & Logistics Managers** | Drastically reduced transit distances and delivery costs through intelligent distance-based warehouse selection. |
| **Warehouse Operations Teams** | Instant real-time alerts upon reaching safety stock thresholds, enabling timely replenishment before full stockouts occur. |
| **E-Commerce Platform Developers** | Reliable, thread-safe ordering pipeline with zero risk of negative inventory or broken partial order transactions during peak traffic. |

---

## 4. High-Level System Features

1. **Intelligent Node Selection**:
   - Evaluates only active, operational warehouses.
   - Validates multi-item availability across the full order prior to reservation.
   - Computes shortest geodesic route using latitude/longitude spherical geometry.

2. **Non-Blocking Atomic Deductions**:
   - Zero heavyweight locks or global mutexes blocking read operations.
   - Leverages `ConcurrentHashMap` with atomic CAS operations to maximize throughput under extreme concurrency.

3. **Immediate Low-Stock Alerting**:
   - Each inventory SKU is assigned an individual safety threshold.
   - Console telemetry fires instantly upon deduction breaching safety limits.

4. **All-or-Nothing Multi-Item Guarantee**:
   - Atomic reservation across complex multi-item shopping carts.
   - Reverts state seamlessly using reverse compensating operations if concurrent demand depletes an item mid-transaction.

5. **Dynamic Runtime Reconfiguration**:
   - Routing algorithms can be swapped dynamically in flight without restarting the service or disturbing in-flight threads.

---

## 5. Non-Functional Requirements & Design Constraints

- **Thread Safety**: Complete protection against data corruption, lost updates, and race conditions across concurrent threads.
- **Zero Placeholders**: Production-grade code with complete implementations, input validations, and error handling.
- **Language Standard**: Modern Java (JDK 17+ / Java 21) utilizing modern language constructs such as records, stream operations, and concurrency utilities.
- **Portability**: Pure standard Java SE without external third-party library dependencies, ensuring high portability across all execution platforms.

