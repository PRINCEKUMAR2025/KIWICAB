package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;

import com.airbnb.lottie.Lottie;
import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.example.kiwicab.Model.Driver;
import com.example.kiwicab.Model.Location;
import com.example.kiwicab.Model.Ride;
import com.example.kiwicab.Model.User;
import com.example.kiwicab.R;
import com.example.kiwicab.Services.EmergencyAlertService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class CustomerHomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private boolean locationUpdatesActive = false;

    private Button requestRideBtn, cancelRideBtn;
    private EditText destinationEditText;
    private TextView statusTextView;
    private CardView rideDetailsCard;

    private FirebaseAuth mAuth;
    private DatabaseReference customersRef, driversRef, ridesRef, onlineDriversRef;
    private String customerId;
    private Marker pickupMarker, destinationMarker;
    private String currentRideId = null;

    private LatLng pickupLocation, destinationLocation;
    private String destinationAddress;
    private Marker driverMarker;
    private ValueEventListener driverLocationListener;
    private ImageButton toggleCardBtn;
    private LinearLayout cardContent;
    private boolean isCardExpanded = true;
    private List<Polyline> polylines = new ArrayList<>();
    private boolean isPaymentDialogShowing = false;
    private MediaPlayer mediaPlayer;

    private Handler blinkHandler = new Handler();
    private Runnable blinkRunnable;
    private boolean isVisible = true;
    private FloatingActionButton emergencyBtn;
    private DatabaseReference emergencyRequestsRef;
    private LocationCallback emergencyLocationCallback;
    private String activeEmergencyId = null;
    private EmergencyAlertService emergencyAlertService;

    private static final int REQUEST_SMS_PERMISSION = 101;
    private static final int REQUEST_CALL_PERMISSION = 102;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_home);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("AutoCab");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        customerId = mAuth.getCurrentUser().getUid();
        customersRef = FirebaseDatabase.getInstance().getReference().child("users").child("customers");
        driversRef = FirebaseDatabase.getInstance().getReference().child("users").child("drivers");
        ridesRef = FirebaseDatabase.getInstance().getReference().child("rides");
        onlineDriversRef = FirebaseDatabase.getInstance().getReference().child("online_drivers");

        // Initialize views
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        requestRideBtn = findViewById(R.id.requestRideBtn);
        cancelRideBtn = findViewById(R.id.cancelRideBtn);
        destinationEditText = findViewById(R.id.destinationEditText);
        statusTextView = findViewById(R.id.statusTextView);
        rideDetailsCard = findViewById(R.id.rideDetailsCard);

        toggleCardBtn = findViewById(R.id.toggleCardBtn);
        cardContent = findViewById(R.id.cardContent);

        //Emergency Response
        // Initialize emergency references
        emergencyRequestsRef = FirebaseDatabase.getInstance().getReference().child("emergency_requests");
        emergencyBtn = findViewById(R.id.emergencyBtn);

// Set emergency button click listener
        emergencyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerEmergencyRequest();
            }
        });



        // Initially hide ride details card
        rideDetailsCard.setVisibility(View.GONE);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();

        // Set click listeners
        requestRideBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && getCurrentFocus() != null) {
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
                String destination = destinationEditText.getText().toString().trim();
                if (TextUtils.isEmpty(destination)) {
                    destinationEditText.setError("Please enter destination");
                    return;
                }

                // Geocode destination
                geocodeDestination(destination);
            }
        });

        cancelRideBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelRide();
            }
        });

        // Set up the toggle button click listener
        toggleCardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCardExpansion();
            }
        });

        // Restore the card state if we have it saved
        SharedPreferences prefs = getSharedPreferences("card_prefs", MODE_PRIVATE);
        isCardExpanded = prefs.getBoolean("is_card_expanded", true);
        updateCardState();

        // Check if user has an active ride
        checkForActiveRide();
        requestEmergencyPermissions();
        emergencyAlertService = new EmergencyAlertService(this);
    }

    private void toggleCardExpansion() {
        isCardExpanded = !isCardExpanded;
        updateCardState();

        // Save the state
        SharedPreferences prefs = getSharedPreferences("card_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_card_expanded", isCardExpanded);
        editor.apply();
    }

    private void updateCardState() {
        if (isCardExpanded) {
            cardContent.setVisibility(View.VISIBLE);
            toggleCardBtn.setImageResource(R.drawable.ic_arrow_up);
        } else {
            cardContent.setVisibility(View.GONE);
            toggleCardBtn.setImageResource(R.drawable.ic_arrow_down);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check and request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Existing location permission handling
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                startLocationUpdates();
            }
        }

        // New emergency permissions handling
        if (requestCode == REQUEST_SMS_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Emergency permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                // Show explanation dialog
                Toast.makeText(this, "All Permissions Granted", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000); // 10 seconds
        locationRequest.setFastestInterval(5000); // 5 seconds
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                for (android.location.Location location : locationResult.getLocations()) {
                    // Update pickup location
                    pickupLocation = new LatLng(location.getLatitude(), location.getLongitude());

                    // Update map
                    updateMapWithCurrentLocation();

                    // Get address for pickup location
                    getAddressFromLocation(pickupLocation, true);
                }
            }
        };
    }

    private void requestEmergencyPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.SEND_SMS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CALL_PHONE);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_SMS_PERMISSION);
        }
    }


    private void triggerEmergencyRequest() {
        // Show confirmation dialog first
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Alert")
                .setMessage("Are you sure you want to send an emergency alert? This will notify authorities.")
                .setPositiveButton("YES, SEND ALERT", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendEmergencyRequest();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    private void sendEmergencyRequest() {
        if (currentRideId == null) {
            Toast.makeText(this, "No active ride found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending emergency alert...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Get current ride details
        ridesRef.child(currentRideId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Ride ride = snapshot.getValue(Ride.class);
                    if (ride != null && ride.getDriverId() != null) {
                        createEmergencyRequest(ride, progressDialog);
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(CustomerHomeActivity.this, "No driver assigned to this ride", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    progressDialog.dismiss();
                    Toast.makeText(CustomerHomeActivity.this, "Ride details not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(CustomerHomeActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createEmergencyRequest(Ride ride, ProgressDialog progressDialog) {
        String emergencyId = emergencyRequestsRef.push().getKey();
        activeEmergencyId = emergencyId;

        // Get customer details
        customersRef.child(customerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot customerSnapshot) {
                if (customerSnapshot.exists()) {
                    User customer = customerSnapshot.getValue(User.class);

                    // Get driver details
                    driversRef.child(ride.getDriverId()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot driverSnapshot) {
                            if (driverSnapshot.exists()) {
                                User driver = driverSnapshot.getValue(User.class);

                                // Create emergency request object (existing code)
                                Map<String, Object> emergencyRequest = new HashMap<>();
                                emergencyRequest.put("emergencyId", emergencyId);
                                emergencyRequest.put("customerId", customerId);
                                emergencyRequest.put("customerName", customer.getName());
                                emergencyRequest.put("customerPhone", customer.getPhone());

                                // Customer current location
                                Map<String, Object> customerLocation = new HashMap<>();
                                String locationString;
                                if (pickupLocation != null) {
                                    customerLocation.put("latitude", pickupLocation.latitude);
                                    customerLocation.put("longitude", pickupLocation.longitude);
                                    customerLocation.put("timestamp", System.currentTimeMillis());
                                    locationString = "Lat: " + pickupLocation.latitude + ", Lng: " + pickupLocation.longitude;
                                } else {
                                    locationString = "Location not available";
                                }
                                emergencyRequest.put("customerLocation", customerLocation);

                                // Driver details
                                emergencyRequest.put("driverId", ride.getDriverId());
                                emergencyRequest.put("driverName", driver.getName());
                                emergencyRequest.put("driverPhone", driver.getPhone());

                                // Additional details
                                emergencyRequest.put("rideId", currentRideId);
                                emergencyRequest.put("timestamp", System.currentTimeMillis());
                                emergencyRequest.put("status", "active");

                                // Save to Firebase
                                emergencyRequestsRef.child(emergencyId).setValue(emergencyRequest)
                                        .addOnCompleteListener(task -> {
                                            progressDialog.dismiss();
                                            if (task.isSuccessful()) {
                                                // Start real-time location tracking
                                                startEmergencyLocationTracking(emergencyId);

                                                // Send emergency alerts to contacts
                                                sendEmergencyAlertsToContacts(emergencyId, customer, driver, locationString);

                                                Toast.makeText(CustomerHomeActivity.this,
                                                        "Emergency alert sent! Live tracking started and contacts notified.",
                                                        Toast.LENGTH_LONG).show();

                                                showEmergencyConfirmation(emergencyId);
                                            } else {
                                                Toast.makeText(CustomerHomeActivity.this,
                                                        "Failed to send emergency alert",
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            progressDialog.dismiss();
                            Toast.makeText(CustomerHomeActivity.this, "Error getting driver details", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(CustomerHomeActivity.this, "Error getting customer details", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendEmergencyAlertsToContacts(String emergencyId, User customer, User driver, String location) {
        // Get customer's emergency contacts from Firebase
        customersRef.child(customerId).child("emergencyContacts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    List<User.EmergencyContact> emergencyContacts = new ArrayList<>();
                    for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                        User.EmergencyContact contact = contactSnapshot.getValue(User.EmergencyContact.class);
                        if (contact != null) {
                            emergencyContacts.add(contact);
                        }
                    }

                    if (!emergencyContacts.isEmpty()) {
                        // Send alerts to all emergency contacts
                        emergencyAlertService.sendEmergencyAlerts(emergencyId, customer, driver, location, emergencyContacts);

                        Toast.makeText(CustomerHomeActivity.this,
                                "Emergency alerts sent to " + emergencyContacts.size() + " contacts",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(CustomerHomeActivity.this,
                                "No emergency contacts found. Please add emergency contacts in your profile.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("EmergencyAlert", "Failed to get emergency contacts: " + error.getMessage());
            }
        });
    }


    private void startEmergencyLocationTracking(String emergencyId) {
        // Create high-frequency location request for emergency
        LocationRequest emergencyLocationRequest = LocationRequest.create();
        emergencyLocationRequest.setInterval(3000); // 3 seconds for emergency
        emergencyLocationRequest.setFastestInterval(1000); // 1 second
        emergencyLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        emergencyLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && activeEmergencyId != null) {
                    for (android.location.Location location : locationResult.getLocations()) {
                        // Update customer location in emergency request
                        Map<String, Object> locationUpdate = new HashMap<>();
                        locationUpdate.put("latitude", location.getLatitude());
                        locationUpdate.put("longitude", location.getLongitude());
                        locationUpdate.put("timestamp", System.currentTimeMillis());
                        locationUpdate.put("accuracy", location.getAccuracy());
                        locationUpdate.put("speed", location.getSpeed());

                        emergencyRequestsRef.child(emergencyId)
                                .child("customerLocation")
                                .setValue(locationUpdate);

                        Log.d("EmergencyTracking", "Location updated: " + location.getLatitude() + ", " + location.getLongitude());
                    }
                }
            }
        };

        // Start location updates
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(emergencyLocationRequest, emergencyLocationCallback, Looper.getMainLooper());

            // Store emergency ID in SharedPreferences
            SharedPreferences prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("active_emergency_id", emergencyId);
            editor.apply();

            Log.d("EmergencyTracking", "Started emergency location tracking for: " + emergencyId);
        }
    }

    private void showEmergencyConfirmation(String emergencyId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Alert Sent")
                .setMessage("Your emergency alert has been sent successfully.\n\n" +
                        "Emergency ID: " + emergencyId + "\n\n" +
                        "Real-time location tracking is now active. " +
                        "Authorities can track your location in the admin panel.")
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .show();
    }

    private void stopEmergencyLocationTracking() {
        if (emergencyLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(emergencyLocationCallback);
            emergencyLocationCallback = null;
            activeEmergencyId = null;

            // Clear from SharedPreferences
            SharedPreferences prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("active_emergency_id");
            editor.apply();

            Log.d("EmergencyTracking", "Stopped emergency location tracking");
        }
    }



    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            locationUpdatesActive = true;

            // Get last known location
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<android.location.Location>() {
                        @Override
                        public void onSuccess(android.location.Location location) {
                            if (location != null) {
                                pickupLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                updateMapWithCurrentLocation();
                                getAddressFromLocation(pickupLocation, true);
                            }
                        }
                    });
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        locationUpdatesActive = false;
    }

    private void updateMapWithCurrentLocation() {
        if (mMap != null && pickupLocation != null) {
            // Clear previous marker
            if (pickupMarker != null) {
                pickupMarker.remove();
            }

            // Add new marker
            pickupMarker = mMap.addMarker(new MarkerOptions()
                    .position(pickupLocation)
                    .title("Pickup Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

            // Move camera to current location
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLocation, 15));
        }
    }

    private void geocodeDestination(String destinationAddress) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(destinationAddress, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                destinationLocation = new LatLng(address.getLatitude(), address.getLongitude());
                this.destinationAddress = address.getAddressLine(0);

                // Update map with destination marker
                if (destinationMarker != null) {
                    destinationMarker.remove();
                }

                destinationMarker = mMap.addMarker(new MarkerOptions()
                        .position(destinationLocation)
                        .title("Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                // Show both markers in view
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(pickupLocation);
                builder.include(destinationLocation);
                LatLngBounds bounds = builder.build();

                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

                // Calculate distance
                float[] results = new float[1];
                android.location.Location.distanceBetween(
                        pickupLocation.latitude, pickupLocation.longitude,
                        destinationLocation.latitude, destinationLocation.longitude,
                        results);

                float distanceInKm = results[0] / 1000;

                // Request ride
                requestRide(distanceInKm);
            } else {
                Toast.makeText(this, "Destination not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Geocoding error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void getAddressFromLocation(LatLng latLng, boolean isPickup) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addressText = address.getAddressLine(0);

                if (isPickup) {
                    if (pickupMarker != null) {
                        pickupMarker.setTitle(addressText);
                    }
                } else {
                    this.destinationAddress = addressText;
                    if (destinationMarker != null) {
                        destinationMarker.setTitle(addressText);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void requestRide(float distanceInKm) {
        // Create a new ride request
        String rideId = ridesRef.push().getKey();
        currentRideId = rideId;

        // Create location objects for Firebase
        Location pickupLocationObj = new Location(pickupLocation.latitude, pickupLocation.longitude);
        Location destinationLocationObj = new Location(destinationLocation.latitude, destinationLocation.longitude, destinationAddress);

        // Create ride object
        Ride ride = new Ride(rideId, customerId, pickupLocationObj, destinationLocationObj, distanceInKm);

        // Draw route from pickup to destination with blinking animation
        drawRouteFromPickupToDestination(pickupLocation, destinationLocation);
        // Save to Firebase
        ridesRef.child(rideId).setValue(ride)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Update customer's current ride
                            customersRef.child(customerId).child("currentRideId").setValue(rideId);

                            // Show ride details card
                            rideDetailsCard.setVisibility(View.VISIBLE);
                            requestRideBtn.setVisibility(View.GONE);
                            statusTextView.setText("Looking for a driver...");

                            // Listen for ride updates
                            listenForRideUpdates(rideId);
                        } else {
                            Toast.makeText(CustomerHomeActivity.this, "Failed to request ride: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void listenForRideUpdates(String rideId) {
        ridesRef.child(rideId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Ride ride = snapshot.getValue(Ride.class);

                    if (ride != null) {
                        if ("ongoing".equals(ride.getStatus().toString())) {
                            // Get driver's current location and destination
                            if (ride.getDriverId() != null && driverMarker != null) {
                                LatLng driverLocation = driverMarker.getPosition();
                                LatLng destinationLatLng = new LatLng(
                                        ride.getDestinationLocation().getLatitude(),
                                        ride.getDestinationLocation().getLongitude()
                                );

                                // Draw route from driver to destination
                                drawRouteFromDriverToDestination(driverLocation, destinationLatLng);
                            }
                        }
                        updateRideUI(ride);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CustomerHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawRouteFromPickupToDestination(LatLng pickup, LatLng destination) {
        // Build the OSRM API URL
        String url = "https://router.project-osrm.org/route/v1/driving/" +
                pickup.longitude + "," + pickup.latitude + ";" +
                destination.longitude + "," + destination.latitude +
                "?overview=full&geometries=polyline";

        // Create OkHttp client
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                // Handle failure - fall back to straight line
                runOnUiThread(() -> {
                    drawStraightLine(pickup, destination);
                    Log.e("RouteDrawing", "Failed to get route: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);

                        // Check if the route was found
                        String status = jsonObject.getString("code");
                        if ("Ok".equals(status)) {
                            JSONArray routes = jsonObject.getJSONArray("routes");
                            if (routes.length() > 0) {
                                JSONObject route = routes.getJSONObject(0);
                                String encodedPolyline = route.getString("geometry");

                                // Get additional information
                                double distance = route.getDouble("distance"); // in meters
                                double duration = route.getDouble("duration"); // in seconds

                                // Decode the polyline
                                List<LatLng> decodedPath = decodePoly(encodedPolyline);

                                // Update UI on the main thread
                                runOnUiThread(() -> {
                                    // Clear previous polylines
                                    for (Polyline line : polylines) {
                                        line.remove();
                                    }
                                    polylines.clear();

                                    // Draw the polyline with blinking animation
                                    PolylineOptions options = new PolylineOptions()
                                            .addAll(decodedPath)
                                            .width(10)
                                            .color(Color.GRAY)
                                            .geodesic(true);
                                    Polyline polyline = mMap.addPolyline(options);
                                    polylines.add(polyline);

                                    // Start blinking animation
                                    startBlinkingAnimation(polyline);

                                    // Update ETA information
                                    int minutes = (int) (duration / 60);
                                    TextView etaTextView = findViewById(R.id.etaTextView);
                                    if (etaTextView != null) {
                                        etaTextView.setText("ETA: " + minutes + " min");
                                        etaTextView.setVisibility(View.VISIBLE);
                                    }

                                    // Show both markers in the map view
                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                    builder.include(pickup);
                                    builder.include(destination);
                                    LatLngBounds bounds = builder.build();

                                    // Add padding to the bounds
                                    int padding = 100; // offset from edges of the map in pixels
                                    CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                                    mMap.animateCamera(cu);
                                });
                            }
                        } else {
                            // No route found, fall back to straight line
                            runOnUiThread(() -> {
                                drawStraightLine(pickup, destination);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Fall back to simple route on error
                        runOnUiThread(() -> {
                            drawStraightLine(pickup, destination);
                        });
                    }
                } else {
                    // Handle unsuccessful response
                    runOnUiThread(() -> {
                        drawStraightLine(pickup, destination);
                    });
                }
            }
        });
    }



    private void drawRouteFromDriverToCustomer(LatLng driverLocation, LatLng customerLocation) {
        // Build the OSRM API URL
        String url = "https://router.project-osrm.org/route/v1/driving/" +
                driverLocation.longitude + "," + driverLocation.latitude + ";" +
                customerLocation.longitude + "," + customerLocation.latitude +
                "?overview=full&geometries=polyline";

        // Create OkHttp client for making the request
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                // Handle failure - fall back to straight line
                runOnUiThread(() -> {
                    // Draw a simple straight line as fallback
                    drawStraightLine(driverLocation, customerLocation);
                    Log.e("RouteDrawing", "Failed to get route: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);

                        // Check if the route was found
                        String status = jsonObject.getString("code");
                        if ("Ok".equals(status)) {
                            JSONArray routes = jsonObject.getJSONArray("routes");
                            if (routes.length() > 0) {
                                JSONObject route = routes.getJSONObject(0);
                                String encodedPolyline = route.getString("geometry");

                                // Get additional information
                                double distance = route.getDouble("distance"); // in meters
                                double duration = route.getDouble("duration"); // in seconds

                                // Decode the polyline
                                List<LatLng> decodedPath = decodePoly(encodedPolyline);

                                // Update UI on the main thread
                                runOnUiThread(() -> {
                                    // Clear previous polylines
                                    if (mMap != null) {
                                        mMap.clear();

                                        // Re-add all markers
                                        if (pickupMarker != null) {
                                            pickupMarker = mMap.addMarker(new MarkerOptions()
                                                    .position(pickupLocation)
                                                    .title("Pickup Location")
                                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                                        }

                                        if (destinationMarker != null) {
                                            destinationMarker = mMap.addMarker(new MarkerOptions()
                                                    .position(destinationLocation)
                                                    .title("Destination")
                                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                                        }

                                        // Add driver marker
                                        driverMarker = mMap.addMarker(new MarkerOptions()
                                                .position(driverLocation)
                                                .title("Your Driver")
                                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon)));

                                        // Draw the polyline
                                        PolylineOptions options = new PolylineOptions()
                                                .addAll(decodedPath)
                                                .width(10)
                                                .color(Color.YELLOW)
                                                .geodesic(true);
                                        mMap.addPolyline(options);

                                        // Update ETA information
                                        int minutes = (int)(duration / 60);
                                        TextView etaTextView = findViewById(R.id.etaTextView);
                                        if (etaTextView != null) {
                                            etaTextView.setText("Driver ETA: " + minutes + " min");
                                            etaTextView.setVisibility(View.VISIBLE);
                                        }
                                    }
                                });
                            }
                        } else {
                            // No route found, fall back to straight line
                            runOnUiThread(() -> {
                                drawStraightLine(driverLocation, customerLocation);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Fall back to simple route on error
                        runOnUiThread(() -> {
                            drawStraightLine(driverLocation, customerLocation);
                        });
                    }
                } else {
                    // Handle unsuccessful response
                    runOnUiThread(() -> {
                        drawStraightLine(driverLocation, customerLocation);
                    });
                }
            }
        });
    }
    private void drawRouteFromDriverToDestination(LatLng origin, LatLng destination) {
        // Build the OSRM API URL
        String url = "https://router.project-osrm.org/route/v1/driving/" +
                origin.longitude + "," + origin.latitude + ";" +
                destination.longitude + "," + destination.latitude +
                "?overview=full&geometries=polyline";

        // Create OkHttp client
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                // Handle failure - fall back to straight line
                runOnUiThread(() -> {
                    drawStraightLine(origin, destination);
                    Log.e("RouteDrawing", "Failed to get route: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);

                        // Check if the route was found
                        String status = jsonObject.getString("code");
                        if ("Ok".equals(status)) {
                            JSONArray routes = jsonObject.getJSONArray("routes");
                            if (routes.length() > 0) {
                                JSONObject route = routes.getJSONObject(0);
                                String encodedPolyline = route.getString("geometry");

                                // Get additional information
                                double distance = route.getDouble("distance"); // in meters
                                double duration = route.getDouble("duration"); // in seconds

                                // Decode the polyline
                                List<LatLng> decodedPath = decodePoly(encodedPolyline);

                                // Update UI on the main thread
                                runOnUiThread(() -> {
                                    // Clear previous polylines but keep markers
                                    if (mMap != null) {
                                        // Remove only polylines, not markers
                                        for (Polyline line : polylines) {
                                            line.remove();
                                        }
                                        polylines.clear();

                                        // Draw the polyline
                                        PolylineOptions options = new PolylineOptions()
                                                .addAll(decodedPath)
                                                .width(10)
                                                .color(Color.GREEN)
                                                .geodesic(true);
                                        polylines.add(mMap.addPolyline(options));

                                        // Update ETA information
                                        int minutes = (int) (duration / 60);
                                        TextView etaTextView = findViewById(R.id.etaTextView);
                                        if (etaTextView != null) {
                                            etaTextView.setText("ETA: " + minutes + " min");
                                            etaTextView.setVisibility(View.VISIBLE);
                                        }

                                        if (driverMarker != null && destinationMarker != null) {

                                            // Show all relevant points in the map view
                                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                            builder.include(origin);
                                            builder.include(destination);

                                            int numPoints = Math.min(decodedPath.size(), 10);
                                            int step = decodedPath.size() / numPoints;
                                            for (int i = 0; i < decodedPath.size(); i += step) {
                                                builder.include(decodedPath.get(i));
                                            }

                                            LatLngBounds bounds = builder.build();


                                            // Add padding to the bounds
                                            int padding = 100; // offset from edges of the map in pixels
                                            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                                            mMap.animateCamera(cu);
                                        }
                                    }
                                });
                            }
                        } else {
                            // No route found, fall back to straight line
                            runOnUiThread(() -> {
                                drawStraightLine(origin, destination);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Fall back to simple route on error
                        runOnUiThread(() -> {
                            drawStraightLine(origin, destination);
                        });
                    }
                } else {
                    // Handle unsuccessful response
                    runOnUiThread(() -> {
                        drawStraightLine(origin, destination);
                    });
                }
            }
        });
    }

    // Fallback method to draw a straight line
    private void drawStraightLine(LatLng driverLocation, LatLng customerLocation) {
        if (mMap != null) {
            for (Polyline line : polylines) {
                line.remove();
            }
            polylines.clear();

            PolylineOptions options = new PolylineOptions()
                    .add(driverLocation)
                    .add(customerLocation)
                    .width(10)
                    .color(Color.GRAY)
                    .geodesic(true);
            mMap.addPolyline(options);
        }
    }

    // Method to decode polyline points
    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((lat / 1E5), (lng / 1E5));
            poly.add(p);
        }
        return poly;
    }

    private void startBlinkingAnimation(final Polyline polyline) {
        // Stop any existing animation
        stopBlinkingAnimation();

        blinkRunnable = new Runnable() {
            @Override
            public void run() {
                if (polyline != null) {
                    isVisible = !isVisible;
                    polyline.setVisible(isVisible);
                    blinkHandler.postDelayed(this, 600); // Blink every 500ms
                }
            }
        };

        blinkHandler.post(blinkRunnable);
    }

    private void stopBlinkingAnimation() {
        if (blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
            blinkRunnable = null;
        }

        // Make sure all polylines are visible when stopping animation
        for (Polyline line : polylines) {
            line.setVisible(true);
        }
    }

    private void updateRideUI(Ride ride) {
        switch (ride.getStatus()) {
            case "requested":
                statusTextView.setText("Looking for a driver...");
                break;
            case "accepted":
                statusTextView.setText("Confirmed: Driver is coming to pick you up");
                showNotification("Ride Accepted", "Driver is coming!");
                // Stop blinking animation and clear the route
                stopBlinkingAnimation();
                for (Polyline line : polylines) {
                    line.remove();
                }
                polylines.clear();
                // Get driver details and show on UI
                mediaPlayer =MediaPlayer.create(CustomerHomeActivity.this,R.raw.auto_horn);
                mediaPlayer.start();
                getDriverDetails(ride.getDriverId());
                if (ride.getDriverId() != null) {
                    trackDriverLocation(ride.getDriverId());
                }
                break;
            case "ongoing":
                statusTextView.setText("Ride in progress");
                cancelRideBtn.setVisibility(View.GONE);
                Toast.makeText(this, "Heading Towards: "+destinationAddress, Toast.LENGTH_SHORT).show();
                showNotification("Ride Started", "Relax: Your Ride Has Started.");
                break;
            case "completed":
                statusTextView.setText("Ride completed");
                if (driverLocationListener != null && ride.getDriverId() != null) {
                    onlineDriversRef.child(ride.getDriverId()).removeEventListener(driverLocationListener);
                    showNotification("Ride Completed", "Reached: "+destinationAddress);
                    driverLocationListener = null;
                    mMap.clear();
                }
//                showPaymentDialog(currentRideId, ride.getFare());
                SharedPreferences prefs = getSharedPreferences("ride_prefs", MODE_PRIVATE);
                boolean isPaid = prefs.getBoolean("paid_" + ride.getId(), false);
                boolean isRated = prefs.getBoolean("rated_" + ride.getId(), false);

                // Also check Firebase for payment status
                if (!isPaid && (ride.getIsPaid() == null || !ride.getIsPaid())) {
                    showPaymentDialog(currentRideId, ride.getFare());
                }

                // Check if rating is already done
                if (!isRated && (ride.getIsRated() == null || !ride.getIsRated())) {
                    showRatingDialog(ride.getDriverId());
                }

                resetRideUI();
                break;
            case "cancelled":
                statusTextView.setText("Ride cancelled");
                if (driverLocationListener != null && ride.getDriverId() != null) {
                    onlineDriversRef.child(ride.getDriverId()).removeEventListener(driverLocationListener);
                    driverLocationListener = null;
                }
                resetRideUI();
                break;
        }

        // Update fare display with the calculated fare
        TextView fareTextView = findViewById(R.id.fareTextView);
        fareTextView.setText(String.format("₹%.2f", ride.getFare()));

        // You might also want to show the distance
        TextView distanceTextView = findViewById(R.id.estimatedDistance);
        if (distanceTextView != null) {
            distanceTextView.setText(String.format("%.2f km", ride.getDistance()));
        }
    }

    private void showNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "ride_status_channel";

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Ride Status Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.app_logo) // Replace with your icon
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(1, builder.build()); // 1 is the notification ID
    }



    private void getDriverDetails(String driverId) {
        driversRef.child(driverId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Driver driver = snapshot.getValue(Driver.class);
                    if (driver != null) {
                        // Show driver details layout
                        LinearLayout driverDetailsLayout = findViewById(R.id.driverDetailsLayout);
                        driverDetailsLayout.setVisibility(View.VISIBLE);

                        // Set driver details
                        TextView driverNameTextView = findViewById(R.id.driverNameTextView);
                        TextView vehicleDetailsTextView = findViewById(R.id.vehicleDetailsTextView);
                        RatingBar driverRatingBar = findViewById(R.id.driverRatingBar);

                        driverNameTextView.setText(driver.getName());

                        // Set vehicle details
                        if (driver.getVehicleDetails() != null) {
                            String vehicleInfo = driver.getVehicleDetails().getVehicleColor() + " " +
                                    driver.getVehicleDetails().getVehicleModel() + " (" +
                                    driver.getVehicleDetails().getVehicleNumber() + ")";
                            vehicleDetailsTextView.setText(vehicleInfo);
                        }

                        // Set driver rating
                        driverRatingBar.setRating(driver.getRating());

                        // Set up call button
                        ImageButton callDriverBtn = findViewById(R.id.callDriverBtn);
                        callDriverBtn.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // Check for call permission
                                if (ContextCompat.checkSelfPermission(CustomerHomeActivity.this,
                                        android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                    String phoneNumber = driver.getPhone();
                                    Intent intent = new Intent(Intent.ACTION_CALL);
                                    intent.setData(Uri.parse("tel:" + phoneNumber));
                                    startActivity(intent);
                                } else {
                                    ActivityCompat.requestPermissions(CustomerHomeActivity.this,
                                            new String[]{android.Manifest.permission.CALL_PHONE}, 2);
                                }
                            }
                        });

                        // Load driver profile image
                        ImageView driverImageView = findViewById(R.id.driverImageView);
                        if (driver.getProfileImageUrl() != null) {
                            Glide.with(CustomerHomeActivity.this)
                                    .load(driver.getProfileImageUrl())
                                    .placeholder(R.drawable.default_profile)
                                    .error(R.drawable.default_profile)
                                    .circleCrop()
                                    .into(driverImageView);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CustomerHomeActivity.this, "Database error: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cancelRide() {
        if (currentRideId != null) {
            // Create a transaction to update the ride status and create a cancellation notification
            ridesRef.child(currentRideId).runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                    Ride ride = mutableData.getValue(Ride.class);
                    if (ride == null) {
                        return Transaction.abort();
                    }

                    // Update ride status to cancelled
                    ride.setStatus("cancelled");

                    mutableData.setValue(ride);
                    return Transaction.success(mutableData);
                }

                @Override
                public void onComplete(DatabaseError error, boolean committed, DataSnapshot dataSnapshot) {
                    if (committed) {
                        // Create a cancellation notification for all drivers
                        DatabaseReference cancelledRidesRef = FirebaseDatabase.getInstance().getReference()
                                .child("cancelled_rides");

                        Map<String, Object> cancellationData = new HashMap<>();
                        cancellationData.put("rideId", currentRideId);
                        cancellationData.put("timestamp", ServerValue.TIMESTAMP);

                        cancelledRidesRef.child(currentRideId).setValue(cancellationData);

                        // Reset customer's current ride
                        customersRef.child(customerId).child("currentRideId").removeValue();
                        currentRideId = null;

                        // Reset UI
                        resetRideUI();

                        Toast.makeText(CustomerHomeActivity.this, "Ride cancelled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CustomerHomeActivity.this,
                                "Failed to cancel ride: " + (error != null ? error.getMessage() : "Unknown error"),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }


    private void resetRideUI() {
        rideDetailsCard.setVisibility(View.GONE);
        requestRideBtn.setVisibility(View.VISIBLE);
        destinationEditText.setText("");

        LinearLayout driverDetailsLayout = findViewById(R.id.driverDetailsLayout);
        if (driverDetailsLayout != null) {
            driverDetailsLayout.setVisibility(View.GONE);
        }

        // Clear destination marker
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }

        // Reset camera to current location
        if (pickupLocation != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLocation, 15));
        }
    }
    private void trackDriverLocation(String driverId) {
        // Remove any existing listener
        if (driverLocationListener != null) {
            onlineDriversRef.child(driverId).removeEventListener(driverLocationListener);
        }

        // Create a new listener for driver location updates
        driverLocationListener = onlineDriversRef.child(driverId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Get driver location data
                    Double latitude = snapshot.child("latitude").getValue(Double.class);
                    Double longitude = snapshot.child("longitude").getValue(Double.class);

                    if (latitude != null && longitude != null) {
                        LatLng driverLatLng = new LatLng(latitude, longitude);

                        // Update or add driver marker
                        if (driverMarker != null) {
                            // Animate marker to new position instead of recreating
                            animateMarkerToPosition(driverMarker, driverLatLng);
                        } else {
                            // Create new marker for driver
                            driverMarker = mMap.addMarker(new MarkerOptions()
                                    .position(driverLatLng)
                                    .title("Your Driver")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon)));
                        }

                        // Draw route from driver to customer (pickup location) or destination
                        // depending on ride status
                        if (currentRideId != null) {
                            ridesRef.child(currentRideId).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (snapshot.exists()) {
                                        String status = snapshot.child("status").getValue(String.class);
                                        if ("ongoing".equals(status)) {
                                            // If ride is ongoing, draw route to destination
                                            if (destinationLocation != null) {
                                                drawRouteFromDriverToDestination(driverLatLng, destinationLocation);
                                            }
                                        } else if ("accepted".equals(status)) {
                                            // If ride is accepted but not started, draw route to pickup
                                            if (pickupLocation != null) {
                                                drawRouteFromDriverToCustomer(driverLatLng, pickupLocation);
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e("RouteUpdate", "Failed to get ride status: " + error.getMessage());
                                }
                            });
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CustomerHomeActivity.this, "Failed to track driver: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void animateMarkerToPosition(final Marker marker, final LatLng targetPosition) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        final long duration = 500; // Animation duration in ms
        final Interpolator interpolator = new LinearInterpolator();

        final LatLng startPosition = marker.getPosition();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);

                double lng = t * targetPosition.longitude + (1 - t) * startPosition.longitude;
                double lat = t * targetPosition.latitude + (1 - t) * startPosition.latitude;

                marker.setPosition(new LatLng(lat, lng));

                // Repeat until animation is complete
                if (t < 1.0) {
                    handler.postDelayed(this, 16); // 60fps
                }
            }
        });
    }





    private void markRatingComplete(String rideId) {
        // Store in Firebase that this ride has been rated
        Map<String, Object> updates = new HashMap<>();
        updates.put("isRated", true);
        ridesRef.child(rideId).updateChildren(updates);

        // Also store locally
        SharedPreferences prefs = getSharedPreferences("ride_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("rated_" + rideId, true);
        editor.apply();
    }

    private void markPaymentComplete(String rideId) {
        // Store in Firebase that payment is complete
        Map<String, Object> updates = new HashMap<>();
        updates.put("isPaid", true);
        ridesRef.child(rideId).updateChildren(updates);

        // Also store locally
        SharedPreferences prefs = getSharedPreferences("ride_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("paid_" + rideId, true);
        editor.apply();
    }


    private void showPaymentDialog(String rideId, double fareAmount) {
        if (isPaymentDialogShowing) {
            return;
        }
        // Check if already paid
        SharedPreferences prefs = getSharedPreferences("ride_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("paid_" + rideId, false)) {
            return; // Skip showing dialog if already paid
        }
        isPaymentDialogShowing = true;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_payment, null);
        final EditText amountEditText = dialogView.findViewById(R.id.paymentAmountEditText);
        final RadioGroup paymentMethodGroup = dialogView.findViewById(R.id.paymentMethodGroup);

        // Set default fare amount
        amountEditText.setText(String.format("%.2f", fareAmount));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Payment")
                .setView(dialogView)
                .setPositiveButton("Pay", null) // Set to null so we can override later
                .setNegativeButton("Cancel", (dialog, which) -> {
                    isPaymentDialogShowing = false;
                })
                .setOnCancelListener(dialog -> {
                    isPaymentDialogShowing = false;
                });

        final AlertDialog dialog = builder.create();
        dialog.show();

        // Override the positive button to prevent auto-dismiss
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String amountStr = amountEditText.getText().toString().trim();

            if (TextUtils.isEmpty(amountStr)) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedMethodId = paymentMethodGroup.getCheckedRadioButtonId();
            if (selectedMethodId == -1) {
                Toast.makeText(this, "Select a payment method", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedMethodId == R.id.radioCash) {

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                // Show loading indicator
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Processing payment...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                Map<String, Object> paymentData = new HashMap<>();
                paymentData.put("amountPaid", amount);
                paymentData.put("method", "Cash");
                paymentData.put("timestamp", ServerValue.TIMESTAMP);
                paymentData.put("rideID",rideId);

                markPaymentComplete(rideId);
                // Add completion listener to Firebase operation
                FirebaseDatabase.getInstance().getReference()
                        .child("payments")
                        .child(rideId)
                        .setValue(paymentData)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Only mark payment complete and dismiss dialog if Firebase write succeeds
                                markPaymentComplete(rideId);
                                Toast.makeText(this, "Give Cash To Driver", Toast.LENGTH_SHORT).show();

//                                // Dismiss both dialogs
                                progressDialog.dismiss();
                                dialog.dismiss();

                            } else {
                                progressDialog.dismiss();
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                                Toast.makeText(this, "Payment failed: " + task.getException().getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                            isPaymentDialogShowing = false;
                        });
            } else if (selectedMethodId == R.id.radioCard) {

                // Mark as in-progress to prevent duplicate dialogs
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("payment_in_progress_" + rideId, true);
                editor.apply();
                // Navigate to PaymentActivity for Stripe payment
                Intent intent = new Intent(this, PaymentActivity.class);
                intent.putExtra("rideId", rideId);
                intent.putExtra("amountToPay", amount);
                startActivity(intent);
                dialog.dismiss();
                isPaymentDialogShowing = false;

            }
        });
    }


    private void showRatingDialog(final String driverId) {
        SharedPreferences prefs = getSharedPreferences("ride_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("rated_" + currentRideId, false)) {
            return; // Skip showing dialog if already rated
        }
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rating, null);
        final RatingBar ratingBar = dialogView.findViewById(R.id.ratingBar);
        final EditText feedbackEditText = dialogView.findViewById(R.id.feedbackEditText);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rate your driver")
                .setView(dialogView)
                .setPositiveButton("Submit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        float rating = ratingBar.getRating();
                        String feedback = feedbackEditText.getText().toString().trim();

                        // Save rating to driver's profile
                        driversRef.child(driverId).child("ratings").push().setValue(rating);

                        // Calculate average rating
                        driversRef.child(driverId).child("ratings").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    float sum = 0;
                                    int count = 0;

                                    markRatingComplete(currentRideId);
                                    for (DataSnapshot ratingSnapshot : snapshot.getChildren()) {
                                        Float ratingValue = ratingSnapshot.getValue(Float.class);
                                        if (ratingValue != null) {
                                            sum += ratingValue;
                                            count++;
                                        }
                                    }

                                    if (count > 0) {
                                        float averageRating = sum / count;
                                        driversRef.child(driverId).child("rating").setValue(averageRating);
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(CustomerHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });

                        // Save feedback if provided
                        if (!TextUtils.isEmpty(feedback)) {
                            driversRef.child(driverId).child("feedback").push().setValue(feedback);
                        }

                        Toast.makeText(CustomerHomeActivity.this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Skip", null)
                .setCancelable(false)
                .show();
    }

    private void checkForActiveRide() {
        customersRef.child(customerId).child("currentRideId").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String rideId = snapshot.getValue(String.class);
                    if (rideId != null) {
                        currentRideId = rideId;

                        // Get ride details
                        ridesRef.child(rideId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    Ride ride = snapshot.getValue(Ride.class);
                                    if (ride != null) {
                                        // Set UI for active ride
                                        rideDetailsCard.setVisibility(View.VISIBLE);
                                        requestRideBtn.setVisibility(View.GONE);

                                        // Set pickup and destination locations
                                        if (ride.getPickupLocation() != null) {
                                            pickupLocation = new LatLng(
                                                    ride.getPickupLocation().getLatitude(),
                                                    ride.getPickupLocation().getLongitude());

                                            if (pickupMarker != null) {
                                                pickupMarker.remove();
                                            }

                                            pickupMarker = mMap.addMarker(new MarkerOptions()
                                                    .position(pickupLocation)
                                                    .title("Pickup Location")
                                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                                        }

                                        if (ride.getDestinationLocation() != null) {
                                            destinationLocation = new LatLng(
                                                    ride.getDestinationLocation().getLatitude(),
                                                    ride.getDestinationLocation().getLongitude());

                                            if (destinationMarker != null) {
                                                destinationMarker.remove();
                                            }

                                            destinationMarker = mMap.addMarker(new MarkerOptions()
                                                    .position(destinationLocation)
                                                    .title("Destination")
                                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                                            // Show both markers in view
                                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                            builder.include(pickupLocation);
                                            builder.include(destinationLocation);
                                            LatLngBounds bounds = builder.build();

                                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                                        }

                                        // Listen for ride updates
                                        listenForRideUpdates(rideId);
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(CustomerHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CustomerHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopEmergencyLocationTracking();
//Release MediapLayer
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Remove driver location listener if it exists
        if (driverLocationListener != null && currentRideId != null) {
            ridesRef.child(currentRideId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Ride ride = snapshot.getValue(Ride.class);
                        if (ride != null && ride.getDriverId() != null) {
                            onlineDriversRef.child(ride.getDriverId()).removeEventListener(driverLocationListener);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(CustomerHomeActivity.this, "Database error: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!locationUpdatesActive) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationUpdatesActive) {
            stopLocationUpdates();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.customer_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            // Sign out
            mAuth.signOut();
            startActivity(new Intent(CustomerHomeActivity.this, LoginActivity.class));
            finish();
            return true;
        } else if (id == R.id.action_profile) {
            // Open profile activity
            startActivity(new Intent(CustomerHomeActivity.this, CustomerProfileActivity.class));
            return true;
        } else if (id == R.id.action_history) {
            // Open ride history activity
            startActivity(new Intent(CustomerHomeActivity.this, RideHistoryActivity.class));
            return true;
        }else if (id == R.id.action_carpool) {
            // Open ride history activity
            startActivity(new Intent(CustomerHomeActivity.this, CarpoolHomeActivity.class));
            return true;
        }else if (id == R.id.action_emergency_contact) {
            // Open ride history activity
            startActivity(new Intent(CustomerHomeActivity.this, EmergencyContactsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}