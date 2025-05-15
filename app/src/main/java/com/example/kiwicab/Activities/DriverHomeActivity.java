package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;

import com.example.kiwicab.Model.Carpool;
import com.example.kiwicab.Model.Ride;
import com.example.kiwicab.Model.User;
import com.example.kiwicab.R;
import com.example.kiwicab.Utils.PolylineUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DriverHomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private boolean locationUpdatesActive = false;

    private Button goOnlineBtn, acceptRideBtn, startRideBtn, completeRideBtn;
    private TextView statusTextView, customerNameTextView, pickupAddressTextView, destinationAddressTextView;
    private CardView rideRequestCard;
    private Switch availabilitySwitch;

    private FirebaseAuth mAuth;
    private DatabaseReference driversRef, customersRef, ridesRef, onlineDriversRef;
    private String driverId;
    private Marker driverMarker, pickupMarker, destinationMarker;
    private String currentRideId = null;
    private LatLng driverLocation;
    private boolean isAvailable = false;
    private ValueEventListener rideRequestListener;
    private TextView customerPhoneTextView;
    private Button callCustomerBtn;
    private String customerPhone;
    private ProgressDialog progressDialog;
    private ImageButton toggleStatusBtn, toggleRideRequestBtn;
    private LinearLayout statusContent, rideRequestContent;
    private boolean isStatusExpanded = true;
    private boolean isRideRequestExpanded = true;
    private TextView rideFareTextView;


    private DatabaseReference carpoolsRef;
    private ValueEventListener carpoolRequestListener;
    MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("AutoCab");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        driverId = mAuth.getCurrentUser().getUid();
        driversRef = FirebaseDatabase.getInstance().getReference().child("users").child("drivers");
        customersRef = FirebaseDatabase.getInstance().getReference().child("users").child("customers");
        ridesRef = FirebaseDatabase.getInstance().getReference().child("rides");
        onlineDriversRef = FirebaseDatabase.getInstance().getReference().child("online_drivers");
        carpoolsRef = FirebaseDatabase.getInstance().getReference().child("carpools");

        // Initialize views
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        goOnlineBtn = findViewById(R.id.goOnlineBtn);
        acceptRideBtn = findViewById(R.id.acceptRideBtn);
        startRideBtn = findViewById(R.id.startRideBtn);
        completeRideBtn = findViewById(R.id.completeRideBtn);
        statusTextView = findViewById(R.id.statusTextView);
        customerNameTextView = findViewById(R.id.customerNameTextView);
        pickupAddressTextView = findViewById(R.id.pickupAddressTextView);
        destinationAddressTextView = findViewById(R.id.destinationAddressTextView);
        rideRequestCard = findViewById(R.id.rideRequestCard);
        availabilitySwitch = findViewById(R.id.availabilitySwitch);
        customerPhoneTextView = findViewById(R.id.customerPhoneTextView);
        callCustomerBtn = findViewById(R.id.callCustomerBtn);
        toggleStatusBtn = findViewById(R.id.toggleStatusBtn);
        toggleRideRequestBtn = findViewById(R.id.toggleRideRequestBtn);
        statusContent = findViewById(R.id.statusContent);
        rideRequestContent = findViewById(R.id.rideRequestContent);
        rideFareTextView = findViewById(R.id.rideFareTextView);

        listenForCancelledRides();

// Set click listener for call button
        callCustomerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callCustomer();
            }
        });

        // Initially hide ride request card and ride control buttons
        rideRequestCard.setVisibility(View.GONE);
        acceptRideBtn.setVisibility(View.GONE);
        startRideBtn.setVisibility(View.GONE);
        completeRideBtn.setVisibility(View.GONE);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();

        // Set click listeners
        goOnlineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleOnlineStatus();
            }
        });

        availabilitySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isAvailable = isChecked;
                if (driverLocation != null) {
                    updateDriverAvailability(isChecked);
                }
            }
        });

        acceptRideBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acceptRide();
            }
        });

        startRideBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRide();
            }
        });

        completeRideBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completeRide();
            }
        });

        toggleStatusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStatusExpansion();
            }
        });

        toggleRideRequestBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRideRequestExpansion();
            }
        });

        // Restore the card states if we have them saved
        SharedPreferences prefs = getSharedPreferences("card_prefs", MODE_PRIVATE);
        isStatusExpanded = prefs.getBoolean("is_status_expanded", true);
        isRideRequestExpanded = prefs.getBoolean("is_ride_request_expanded", true);

        // Update card states
        updateStatusCardState();
        updateRideRequestCardState();

        // Check if driver has an active ride
        checkForActiveRide();
        monitorAcceptedRides();
    }

    private void toggleStatusExpansion() {
        isStatusExpanded = !isStatusExpanded;
        updateStatusCardState();

        // Save the state
        SharedPreferences prefs = getSharedPreferences("card_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_status_expanded", isStatusExpanded);
        editor.apply();
    }

    private void toggleRideRequestExpansion() {
        isRideRequestExpanded = !isRideRequestExpanded;
        updateRideRequestCardState();

        // Save the state
        SharedPreferences prefs = getSharedPreferences("card_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_ride_request_expanded", isRideRequestExpanded);
        editor.apply();
    }

    private void updateStatusCardState() {
        if (isStatusExpanded) {
            // Expand with animation
            statusContent.setVisibility(View.VISIBLE);
            statusContent.setAlpha(0f);
            statusContent.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null);
            toggleStatusBtn.setImageResource(R.drawable.ic_arrow_up);
        } else {
            // Collapse with animation
            statusContent.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            statusContent.setVisibility(View.GONE);
                        }
                    });
            toggleStatusBtn.setImageResource(R.drawable.ic_arrow_down);
        }
    }

    private void updateRideRequestCardState() {
        if (isRideRequestExpanded) {
            // Expand with animation
            rideRequestContent.setVisibility(View.VISIBLE);
            rideRequestContent.setAlpha(0f);
            rideRequestContent.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null);
            toggleRideRequestBtn.setImageResource(R.drawable.ic_arrow_up);
        } else {
            // Collapse with animation
            rideRequestContent.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rideRequestContent.setVisibility(View.GONE);
                        }
                    });
            toggleRideRequestBtn.setImageResource(R.drawable.ic_arrow_down);
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
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                startLocationUpdates();
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
                    // Update driver location
                    driverLocation = new LatLng(location.getLatitude(), location.getLongitude());

                    // Update map
                    updateMapWithCurrentLocation();

                    // Update driver location in Firebase if online
                    if (isAvailable) {
                        updateDriverLocation();
                    }
                }
            }
        };
    }
    private void listenForCancelledRides() {
        DatabaseReference cancelledRidesRef = FirebaseDatabase.getInstance().getReference()
                .child("cancelled_rides");

        cancelledRidesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                if (snapshot.exists()) {
                    String cancelledRideId = snapshot.getKey();

                    // Check if this is the ride currently being shown to the driver
                    if (cancelledRideId != null && cancelledRideId.equals(currentRideId)) {
                        // Hide the ride request card
                        runOnUiThread(() -> {
                            rideRequestCard.setVisibility(View.GONE);
                            acceptRideBtn.setVisibility(View.GONE);

                            // Reset current ride ID
                            currentRideId = null;

                            // Reset the map
                            mMap.clear();
                            updateMapWithCurrentLocation();

                            // Show notification to driver
                            Toast.makeText(DriverHomeActivity.this,
                                    "This ride request has been cancelled by the customer",
                                    Toast.LENGTH_SHORT).show();

                            // Resume listening for new ride requests
                            if (isAvailable) {
                                listenForRideRequests();
                            }
                        });
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("CancelledRides", "Failed to listen for cancelled rides: " + error.getMessage());
            }
        });
    }


    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            locationUpdatesActive = true;

            // Get last known location
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(android.location.Location location) {
                            if (location != null) {
                                driverLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                updateMapWithCurrentLocation();

                                if (isAvailable) {
                                    updateDriverLocation();
                                }
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
        if (mMap != null && driverLocation != null) {
            // Clear previous marker
            if (driverMarker != null) {
                driverMarker.remove();
            }

            // Add new marker
            driverMarker = mMap.addMarker(new MarkerOptions()
                    .position(driverLocation)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon)));

            // Move camera to current location
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLocation, 15));
        }
    }

    private void toggleOnlineStatus() {
        if (isAvailable) {
            // Go offline
            isAvailable = false;
            availabilitySwitch.setChecked(false);
            goOnlineBtn.setText("Go Online");
            statusTextView.setText("You are currently offline");

            // Remove from online drivers
            onlineDriversRef.child(driverId).removeValue();

            // Stop listening for ride requests
            if (rideRequestListener != null) {
                ridesRef.removeEventListener(rideRequestListener);
                rideRequestListener = null;
            }
        } else {
            // Check isVerified before allowing to go online
            driversRef.child(driverId).child("isVerified").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean isVerified = snapshot.getValue(Boolean.class);
                    if (isVerified != null && isVerified) {
                        // Go online
                        isAvailable = true;
                        availabilitySwitch.setChecked(true);
                        goOnlineBtn.setText("Go Offline");
                        statusTextView.setText("You are online and available for rides");

                        // Update driver location
                        updateDriverLocation();

                        // Listen for ride requests
                        listenForRideRequests();
                    } else {
                        Toast.makeText(DriverHomeActivity.this,
                                "Your account is not verified. You cannot go online.",
                                Toast.LENGTH_LONG).show();
                        // Ensure UI stays offline
                        isAvailable = false;
                        availabilitySwitch.setChecked(false);
                        goOnlineBtn.setText("Go Online");
                        statusTextView.setText("You are currently offline");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(DriverHomeActivity.this,
                            "Database error: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }


    private void updateDriverLocation() {
        if (driverLocation != null) {
            HashMap<String, Object> driverLocationMap = new HashMap<>();
            driverLocationMap.put("latitude", driverLocation.latitude);
            driverLocationMap.put("longitude", driverLocation.longitude);
            driverLocationMap.put("is_available", isAvailable);

            onlineDriversRef.child(driverId).setValue(driverLocationMap);
        }
    }

    private void updateDriverAvailability(boolean available) {
        if (driverLocation != null) {
            onlineDriversRef.child(driverId).child("is_available").setValue(available);
        }
    }
    private void monitorAcceptedRides() {
        ridesRef.orderByChild("status").equalTo("accepted").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot rideSnapshot : snapshot.getChildren()) {
                    String rideId = rideSnapshot.getKey();
                    String acceptedDriverId = rideSnapshot.child("driverId").getValue(String.class);

                    // If this is the current ride request being shown and another driver accepted it
                    if (rideId != null && rideId.equals(currentRideId) &&
                            acceptedDriverId != null && !acceptedDriverId.equals(driverId)) {
                        // Hide the ride request card
                        rideRequestCard.setVisibility(View.GONE);
                        acceptRideBtn.setVisibility(View.GONE);

                        // Reset current ride ID
                        currentRideId = null;

                        // Notify the driver
                        Toast.makeText(DriverHomeActivity.this,
                                "This ride has been accepted by another driver",
                                Toast.LENGTH_SHORT).show();

                        // Reset the map
                        mMap.clear();
                        updateMapWithCurrentLocation();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("RideAcceptance", "Error monitoring accepted rides: " + error.getMessage());
            }
        });
    }


    private void listenForRideRequests() {
        // Listen for new ride requests
        rideRequestListener = ridesRef.orderByChild("status").equalTo("requested").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Only process if driver is available and not on a ride
                if (isAvailable && currentRideId == null) {
                    boolean foundRide = false;

                    for (DataSnapshot rideSnapshot : snapshot.getChildren()) {
                        Ride ride = rideSnapshot.getValue(Ride.class);

                        if (ride != null && ride.getDriverId() == null) {
                            // Double-check the ride status by making a single-value query
                            // This ensures we have the most up-to-date status
                            ridesRef.child(ride.getId()).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot rideCheck) {
                                    if (rideCheck.exists()) {
                                        String currentStatus = rideCheck.child("status").getValue(String.class);
                                        String currentDriverId = rideCheck.child("driverId").getValue(String.class);

                                        // Only proceed if the ride is still in "requested" status and has no driver
                                        if ("requested".equals(currentStatus) && currentDriverId == null) {
                                            // Calculate distance between driver and pickup location
                                            float[] results = new float[1];
                                            android.location.Location.distanceBetween(
                                                    driverLocation.latitude, driverLocation.longitude,
                                                    ride.getPickupLocation().getLatitude(), ride.getPickupLocation().getLongitude(),
                                                    results);

                                            float distanceInKm = results[0] / 1000;

                                            // Only show requests within 10km
                                            if (distanceInKm <= 10) {
                                                // Show ride request
                                                showRideRequest(ride);
                                            }
                                        } else if ("accepted".equals(currentStatus) && currentRideId != null && currentRideId.equals(ride.getId())) {
                                            // This ride was just accepted by another driver
                                            rideRequestCard.setVisibility(View.GONE);
                                            currentRideId = null;
                                            Toast.makeText(DriverHomeActivity.this,
                                                    "Ride already taken by another driver",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Toast.makeText(DriverHomeActivity.this,
                                            "Database error: " + error.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            });

                            // Only process one ride at a time
                            break;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DriverHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void showRideRequest(final Ride ride) {
        currentRideId = ride.getId();

        // Get customer details
        customersRef.child(ride.getCustomerId()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User customer = snapshot.getValue(User.class);

                    if (customer != null) {
                        // Show ride request card
                        rideRequestCard.setVisibility(View.VISIBLE);
                        acceptRideBtn.setVisibility(View.VISIBLE);
                        //Audio Request
                        mediaPlayer = MediaPlayer.create(DriverHomeActivity.this,R.raw.auto_horn);
                        mediaPlayer.start();

                        // Set customer details
                        customerNameTextView.setText(customer.getName());
                        customerPhoneTextView.setText(customer.getPhone());
                        customerPhone = customer.getPhone(); // Store phone number for call function
                        pickupAddressTextView.setText(ride.getPickupLocation().getAddress());
                        destinationAddressTextView.setText(ride.getDestinationLocation().getAddress());

                        // Set fare amount
                        rideFareTextView.setText(String.format("₹%.2f", ride.getFare()));

                        // Show pickup and destination on map
                        showRideOnMap(ride);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DriverHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void showRideOnMap(Ride ride) {
        // Clear previous markers
        mMap.clear();

        // Add driver marker
        driverMarker = mMap.addMarker(new MarkerOptions()
                .position(driverLocation)
                .title("Your Location")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon)));

        // Add pickup marker
        LatLng pickupLatLng = new LatLng(
                ride.getPickupLocation().getLatitude(),
                ride.getPickupLocation().getLongitude());

        pickupMarker = mMap.addMarker(new MarkerOptions()
                .position(pickupLatLng)
                .title("Pickup Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Add destination marker
        LatLng destinationLatLng = new LatLng(
                ride.getDestinationLocation().getLatitude(),
                ride.getDestinationLocation().getLongitude());

        destinationMarker = mMap.addMarker(new MarkerOptions()
                .position(destinationLatLng)
                .title("Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Show all markers in view
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(driverLocation);
        builder.include(pickupLatLng);
        builder.include(destinationLatLng);
        LatLngBounds bounds = builder.build();

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

        // Draw route from driver to pickup
        drawRoute(driverLocation, pickupLatLng, Color.YELLOW);
    }

    private void drawRoute(LatLng origin, LatLng destination, int color) {
        // Show a loading indicator
        showLoadingIndicator("Getting route...");

        // Build the OSRM API URL
        String url = "https://router.project-osrm.org/route/v1/driving/" +
                origin.longitude + "," + origin.latitude + ";" +
                destination.longitude + "," + destination.latitude +
                "?overview=full&geometries=polyline&steps=true";

        // Create OkHttp client
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Execute the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Handle failure - fall back to simple route
                runOnUiThread(() -> {
                    hideLoadingIndicator();
                    Toast.makeText(DriverHomeActivity.this,
                            "Failed to get route: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    // Fallback to straight line
                    PolylineOptions fallbackOptions = new PolylineOptions()
                            .add(origin)
                            .add(destination)
                            .width(10)
                            .color(color);

                    mMap.addPolyline(fallbackOptions);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
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
                                List<LatLng> decodedPath = PolylineUtils.decode(encodedPolyline);

                                // Update UI on the main thread
                                runOnUiThread(() -> {
                                    hideLoadingIndicator();

                                    // Draw the polyline
                                    PolylineOptions options = new PolylineOptions()
                                            .addAll(decodedPath)
                                            .width(5)
                                            .color(color);
                                    mMap.addPolyline(options);

                                    // Optionally show distance and duration
                                    String distanceText = String.format("%.1f km", distance / 1000);
                                    String durationText = String.format("%d min", (int)(duration / 60));

                                    Toast.makeText(DriverHomeActivity.this,
                                            "Distance: " + distanceText + " | ETA: " + durationText,
                                            Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            // No route found, fall back to straight line
                            runOnUiThread(() -> {
                                hideLoadingIndicator();
                                Toast.makeText(DriverHomeActivity.this,
                                        "No route found",
                                        Toast.LENGTH_SHORT).show();

                                // Fallback to straight line
                                PolylineOptions fallbackOptions = new PolylineOptions()
                                        .add(origin)
                                        .add(destination)
                                        .width(5)
                                        .color(color);

                                mMap.addPolyline(fallbackOptions);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Fall back to simple route on error
                        runOnUiThread(() -> {
                            hideLoadingIndicator();
                            Toast.makeText(DriverHomeActivity.this,
                                    "Error processing route: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();

                            // Fallback to straight line
                            PolylineOptions fallbackOptions = new PolylineOptions()
                                    .add(origin)
                                    .add(destination)
                                    .width(5)
                                    .color(color);

                            mMap.addPolyline(fallbackOptions);
                        });
                    }
                } else {
                    // Handle unsuccessful response
                    runOnUiThread(() -> {
                        hideLoadingIndicator();
                        Toast.makeText(DriverHomeActivity.this,
                                "Server error: " + response.code(),
                                Toast.LENGTH_SHORT).show();

                        // Fallback to straight line
                        PolylineOptions fallbackOptions = new PolylineOptions()
                                .add(origin)
                                .add(destination)
                                .width(5)
                                .color(color);

                        mMap.addPolyline(fallbackOptions);
                    });
                }
            }
        });
    }


    private void showLoadingIndicator(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    private void hideLoadingIndicator() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }


    private void acceptRide() {
        if (currentRideId != null) {
            // Show loading indicator
            final android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
            progressDialog.setMessage("Accepting ride...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            // Use transaction to safely accept the ride
            DatabaseReference rideRef = ridesRef.child(currentRideId);
            rideRef.runTransaction(new Transaction.Handler() {
                @Override
                public Transaction.Result doTransaction(MutableData mutableData) {
                    Ride ride = mutableData.getValue(Ride.class);

                    // Check if ride exists and is still in "requested" status
                    if (ride != null && "requested".equals(ride.getStatus()) && ride.getDriverId() == null) {
                        // Ride is available, update it
                        ride.setStatus("accepted");
                        ride.setDriverId(driverId);
                        mutableData.setValue(ride);
                        return Transaction.success(mutableData);
                    }

                    // Ride is already taken or doesn't exist
                    return Transaction.abort();
                }

                @Override
                public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                    progressDialog.dismiss();

                    if (committed) {
                        // Success! This driver got the ride
                        // Update driver's current ride
                        driversRef.child(driverId).child("currentRideId").setValue(currentRideId);

                        // Update UI
                        acceptRideBtn.setVisibility(View.GONE);
                        startRideBtn.setVisibility(View.VISIBLE);
                        statusTextView.setText("Ride accepted. Navigate to pickup location.");

                        // Listen for ride updates
                        listenForRideUpdates();

                        Toast.makeText(DriverHomeActivity.this,
                                "Ride accepted successfully!",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // Failed to get the ride
                        Toast.makeText(DriverHomeActivity.this,
                                "This ride is no longer available",
                                Toast.LENGTH_SHORT).show();

                        // Reset UI
                        rideRequestCard.setVisibility(View.GONE);
                        currentRideId = null;

                        // Reset the map
                        mMap.clear();
                        updateMapWithCurrentLocation();
                    }
                }
            });
        }
    }


    private void startRide() {
        if (currentRideId != null) {
            // Update ride status
            ridesRef.child(currentRideId).child("status").setValue("ongoing");

            // Update UI
            startRideBtn.setVisibility(View.GONE);
            completeRideBtn.setVisibility(View.VISIBLE);
            statusTextView.setText("Ride in progress. Navigate to destination.");

            // Update map to show route to destination
            ridesRef.child(currentRideId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Ride ride = snapshot.getValue(Ride.class);
                        if (ride != null) {
                            // Clear previous route
                            mMap.clear();

                            // Add driver marker
                            driverMarker = mMap.addMarker(new MarkerOptions()
                                    .position(driverLocation)
                                    .title("Your Location")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon)));

                            // Add destination marker
                            LatLng destinationLatLng = new LatLng(
                                    ride.getDestinationLocation().getLatitude(),
                                    ride.getDestinationLocation().getLongitude());

                            destinationMarker = mMap.addMarker(new MarkerOptions()
                                    .position(destinationLatLng)
                                    .title("Destination")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                            // Show both markers in view
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            builder.include(driverLocation);
                            builder.include(destinationLatLng);
                            LatLngBounds bounds = builder.build();

                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

                            // Draw route to destination
                            drawRoute(driverLocation, destinationLatLng, Color.GREEN);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(DriverHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void completeRide() {
        if (currentRideId != null) {
            // Update ride status
            ridesRef.child(currentRideId).child("status").setValue("completed");

            // Clear driver's current ride
            driversRef.child(driverId).child("currentRideId").removeValue();

            // Reset UI
            rideRequestCard.setVisibility(View.GONE);
            completeRideBtn.setVisibility(View.GONE);
            statusTextView.setText("You are online and available for rides");

            // Reset map
            mMap.clear();
            updateMapWithCurrentLocation();

            // Reset current ride id
            currentRideId = null;

            // Resume listening for ride requests
            listenForRideRequests();

            // Show payment confirmation (in a real app)
            showPaymentConfirmation();
        }
    }

    private void showPaymentConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ride Completed")
                .setMessage("Payment has been initiated successfully.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void listenForRideUpdates() {
        if (currentRideId != null) {
            ridesRef.child(currentRideId).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Ride ride = snapshot.getValue(Ride.class);
                        if (ride != null) {
                            // Update fare display
                            rideFareTextView.setText(String.format("₹%.2f", ride.getFare()));
                            // Check if ride was cancelled by customer
                            if (ride.getStatus().equals("cancelled")) {
                                Toast.makeText(DriverHomeActivity.this, "Ride was cancelled by the customer", Toast.LENGTH_LONG).show();

                                // Reset UI
                                rideRequestCard.setVisibility(View.GONE);
                                acceptRideBtn.setVisibility(View.GONE);
                                startRideBtn.setVisibility(View.GONE);
                                completeRideBtn.setVisibility(View.GONE);
                                statusTextView.setText("You are online and available for rides");

                                // Reset map
                                mMap.clear();
                                updateMapWithCurrentLocation();

                                // Clear driver's current ride
                                driversRef.child(driverId).child("currentRideId").removeValue();

                                // Reset current ride id
                                currentRideId = null;

                                // Resume listening for ride requests
                                listenForRideRequests();
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(DriverHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    private void callCustomer() {
        if (customerPhone != null && !customerPhone.isEmpty()) {
            // Check for call permission
            if (ContextCompat.checkSelfPermission(DriverHomeActivity.this,
                    android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                // Make the call
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + customerPhone));
                startActivity(intent);
            } else {
                // Request permission
                ActivityCompat.requestPermissions(DriverHomeActivity.this,
                        new String[]{android.Manifest.permission.CALL_PHONE}, 2);
            }
        } else {
            Toast.makeText(this, "Customer phone number not available", Toast.LENGTH_SHORT).show();
        }
    }


    private void checkForActiveRide() {
        driversRef.child(driverId).child("currentRideId").addListenerForSingleValueEvent(new ValueEventListener() {
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
                                        // Set fare amount
                                        rideFareTextView.setText(String.format("₹%.2f", ride.getFare()));
                                        // Set UI for active ride
                                        rideRequestCard.setVisibility(View.VISIBLE);

                                        // Get customer details
                                        customersRef.child(ride.getCustomerId()).addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                if (snapshot.exists()) {
                                                    User customer = snapshot.getValue(User.class);
                                                    if (customer != null) {
                                                        customerNameTextView.setText(customer.getName());
                                                        customerPhoneTextView.setText(customer.getPhone());
                                                        customerPhone = customer.getPhone();
                                                    }
                                                }
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                                Toast.makeText(DriverHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                        pickupAddressTextView.setText(ride.getPickupLocation().getAddress());
                                        destinationAddressTextView.setText(ride.getDestinationLocation().getAddress());

                                        // Set appropriate buttons based on ride status
                                        if (ride.getStatus().equals("accepted")) {
                                            startRideBtn.setVisibility(View.VISIBLE);
                                            statusTextView.setText("Ride accepted. Navigate to pickup location.");
                                        } else if (ride.getStatus().equals("ongoing")) {
                                            completeRideBtn.setVisibility(View.VISIBLE);
                                            statusTextView.setText("Ride in progress. Navigate to destination.");
                                        }

                                        // Show ride on map
                                        showRideOnMap(ride);

                                        // Listen for ride updates
                                        listenForRideUpdates();
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(DriverHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DriverHomeActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
    protected void onStop() {
        super.onStop();
        onlineDriversRef.child(driverId).removeValue();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.driver_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_logout) {
            // Sign out
            mAuth.signOut();
            startActivity(new Intent(DriverHomeActivity.this, LoginActivity.class));
            finish();
            return true;
        } else if (id == R.id.action_profile) {
            // Open profile activity
            startActivity(new Intent(DriverHomeActivity.this, DriverProfileActivity.class));
            return true;
        } else if (id == R.id.action_history) {
            // Open ride history activity
            startActivity(new Intent(DriverHomeActivity.this, RideHistoryActivity.class));
            return true;
        }else if (id == R.id.action_earnings) {
            // Open ride history activity
            startActivity(new Intent(DriverHomeActivity.this, DriverEarningsActivity.class));
            return true;
        }else if (id == R.id.action_carpool) {
            // Open ride history activity
            startActivity(new Intent(DriverHomeActivity.this, CarpoolHomeActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}