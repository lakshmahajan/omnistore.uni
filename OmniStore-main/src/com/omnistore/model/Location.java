package com.omnistore.model;

import java.util.Objects;

/**
 * Represents geographic coordinates (latitude and longitude) with distance calculation capabilities.
 */
public record Location(double latitude, double longitude) {

    public Location {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees: " + longitude);
        }
    }

    /**
     * Calculates the great-circle distance between two locations using the Haversine formula.
     *
     * @param other The destination location
     * @return Distance in kilometers
     */
    public double distanceTo(Location other) {
        Objects.requireNonNull(other, "Target location cannot be null for distance calculation");

        final double earthRadiusKm = 6371.0;

        double lat1Rad = Math.toRadians(this.latitude);
        double lon1Rad = Math.toRadians(this.longitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double lon2Rad = Math.toRadians(other.longitude);

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double a = Math.sin(deltaLat / 2.0) * Math.sin(deltaLat / 2.0)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2.0) * Math.sin(deltaLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return earthRadiusKm * c;
    }

    @Override
    public String toString() {
        return String.format("(%.4f, %.4f)", latitude, longitude);
    }
}

