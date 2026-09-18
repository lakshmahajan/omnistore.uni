package com.omnistore.model;

import java.util.Objects;

/**
 * Represents a physical fulfillment center or warehouse node in the distribution network.
 */
public class Warehouse {
    private final String warehouseId;
    private final String name;
    private final Location location;
    private volatile boolean active;

    public Warehouse(String warehouseId, String name, Location location, boolean active) {
        this.warehouseId = Objects.requireNonNull(warehouseId, "Warehouse ID cannot be null");
        this.name = Objects.requireNonNull(name, "Warehouse name cannot be null");
        this.location = Objects.requireNonNull(location, "Warehouse location cannot be null");
        this.active = active;
    }

    public Warehouse(String warehouseId, String name, Location location) {
        this(warehouseId, name, location, true);
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Warehouse warehouse = (Warehouse) o;
        return Objects.equals(warehouseId, warehouse.warehouseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId);
    }

    @Override
    public String toString() {
        return String.format("Warehouse[id='%s', name='%s', location=%s, active=%b]",
                warehouseId, name, location, active);
    }
}

