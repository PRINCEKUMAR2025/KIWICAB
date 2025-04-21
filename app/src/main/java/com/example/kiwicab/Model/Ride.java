package com.example.kiwicab.Model;

public class Ride {
    private String id;
    private String customerId;
    private String driverId;
    private Location pickupLocation;
    private Location destinationLocation;
    private String status; // "requested", "accepted", "ongoing", "completed", "cancelled"
    private double fare;
    private long timestamp;
    private double distance;

    public Ride() {
        // Required empty constructor for Firebase
    }

    public Ride(String id, String customerId, Location pickupLocation, Location destinationLocation, double distance) {
        this.id = id;
        this.customerId = customerId;
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
        this.status = "requested";
        this.timestamp = System.currentTimeMillis();
        this.distance = distance;
        this.fare = calculateFare(distance);
    }

    private double calculateFare(double distance) {
        // Base fare + distance fare (example calculation)
        return 50 + (distance * 10);
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public Location getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(Location pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public Location getDestinationLocation() {
        return destinationLocation;
    }

    public void setDestinationLocation(Location destinationLocation) {
        this.destinationLocation = destinationLocation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getFare() {
        return fare;
    }

    public void setFare(double fare) {
        this.fare = fare;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
}