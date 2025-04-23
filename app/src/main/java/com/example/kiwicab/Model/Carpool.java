package com.example.kiwicab.Model;

import java.util.HashMap;
import java.util.Map;

public class Carpool {
    private String id;
    private String driverId;

    private String vehicleInformation;
    private Location pickupLocation;
    private Location destinationLocation;
    private double fare;
    private String departureTime;
    private double distance;
    private long timestamp;
    private int passengerCount;
    private String status;
    private Map<String, Boolean> passengers;
    private boolean isFull;

    // Required empty constructor for Firebase
    public Carpool() {
    }

    public Carpool(String id, String driverId,String vehicleInformation, Location pickupLocation,
                   Location destinationLocation, double fare, String departureTime,
                   double distance, long timestamp) {
        this.id = id;
        this.driverId = driverId;
        this.vehicleInformation=vehicleInformation;
        this.pickupLocation = pickupLocation;
        this.destinationLocation = destinationLocation;
        this.fare = fare;
        this.departureTime = departureTime;
        this.distance = distance;
        this.timestamp = timestamp;
        this.passengerCount = 1; // Driver counts as first passenger
        this.status = "active";
        this.passengers = new HashMap<>();
        this.passengers.put(driverId, true); // Add driver as first passenger
        this.isFull = false;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public double getFare() {
        return fare;
    }

    public void setFare(double fare) {
        this.fare = fare;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(int passengerCount) {
        this.passengerCount = passengerCount;
        // Update isFull status if passenger count reaches 4
        this.isFull = (passengerCount >= 4);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Boolean> getPassengers() {
        return passengers;
    }

    public void setPassengers(Map<String, Boolean> passengers) {
        this.passengers = passengers;
    }

    public boolean isFull() {
        return isFull;
    }

    public void setFull(boolean full) {
        isFull = full;
    }
    public String getVehicleInformation() {
        return vehicleInformation;
    }

    public void setVehicleInformation(String vehicleInformation) {
        this.vehicleInformation = vehicleInformation;
    }
}
