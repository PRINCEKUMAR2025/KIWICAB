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
    private Boolean isPaid;
    private Boolean isRated;

    private static final double BASE_FARE = 40.0;
    private static final double PRICE_PER_KM = 13.0;
    private static final double PRICE_PER_MINUTE = 2.5;
    private static final double BOOKING_FEE = 20.0;

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
        this.fare = calculateFare();
    }

    private double calculateFare() {
        // Calculate estimated time in minutes (assuming average speed of 30 km/h)
        double estimatedTimeInMinutes = (distance / 30.0) * 60.0;

        // Calculate fare components
        double distanceFare = distance * PRICE_PER_KM;
        double timeFare = estimatedTimeInMinutes * PRICE_PER_MINUTE;

        // Total fare = base fare + distance fare + time fare + booking fee
        double totalFare = BASE_FARE + distanceFare + timeFare + BOOKING_FEE;

        // Round to nearest whole number
        return Math.round(totalFare);
    }

    public void recalculateFare() {
        this.fare = calculateFare();
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
        // Recalculate fare when distance changes
        recalculateFare();
    }

    public Boolean getIsPaid() {
        return isPaid;
    }

    public void setIsPaid(Boolean isPaid) {
        this.isPaid = isPaid;
    }

    public Boolean getIsRated() {
        return isRated;
    }

    public void setIsRated(Boolean isRated) {
        this.isRated = isRated;
    }
}