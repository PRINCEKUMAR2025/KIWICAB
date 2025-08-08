package com.freeapp.kiwicab.Model;

public class Transaction {
    private String id;
    private String driverId;
    private String customerId;
    private String rideId;
    private double amount;
    private String type; // "payment", "withdrawal"
    private String status; // "completed", "pending", "failed"
    private long timestamp;
    private String paymentMethod;
    private String details;

    // Required empty constructor for Firebase
    public Transaction() {
    }

    public Transaction(String id, String driverId, String customerId, String rideId, double amount,
                       String type, String status, long timestamp, String paymentMethod, String details) {
        this.id = id;
        this.driverId = driverId;
        this.customerId = customerId;
        this.rideId = rideId;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
        this.paymentMethod = paymentMethod;
        this.details = details;
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

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
