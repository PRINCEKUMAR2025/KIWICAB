package com.example.kiwicab.Model;

public class VehicleDetails {
    private String vehicleType;
    private String vehicleNumber;
    private String vehicleModel;
    private String vehicleColor;

    public VehicleDetails() {
        // Required empty constructor for Firebase
    }

    public VehicleDetails(String vehicleType, String vehicleNumber, String vehicleModel, String vehicleColor) {
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.vehicleModel = vehicleModel;
        this.vehicleColor = vehicleColor;
    }

    // Getters and setters
    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public void setVehicleColor(String vehicleColor) {
        this.vehicleColor = vehicleColor;
    }
}
