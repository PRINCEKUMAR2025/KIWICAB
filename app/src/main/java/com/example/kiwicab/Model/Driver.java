package com.example.kiwicab.Model;

public class Driver extends User {
    private VehicleDetails vehicleDetails;
    private boolean isAvailable;
    private Location currentLocation;
    private String profileImageUrl;
    public Driver() {
        // Required empty constructor for Firebase
    }

    public Driver(String id, String name, String email, String phone, VehicleDetails vehicleDetails) {
        super(id, name, email, phone, "driver");
        this.vehicleDetails = vehicleDetails;
        this.isAvailable = true;
    }

    // Getters and setters
    public VehicleDetails getVehicleDetails() {
        return vehicleDetails;
    }

    public void setVehicleDetails(VehicleDetails vehicleDetails) {
        this.vehicleDetails = vehicleDetails;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
