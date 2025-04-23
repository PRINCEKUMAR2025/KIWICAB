package com.example.kiwicab.Model;

public class User {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String userType; // "customer" or "driver"
    private float rating;
    private String currentRideId;
    private String profileImageUrl;

    // Constructor, getters, and setters
    public User() {
        // Required empty constructor for Firebase
    }

    public User(String id, String name, String email, String phone, String userType) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.userType = userType;
        this.rating = 5.0f; // Default rating
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getCurrentRideId() {
        return currentRideId;
    }

    public void setCurrentRideId(String currentRideId) {
        this.currentRideId = currentRideId;
    }
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
