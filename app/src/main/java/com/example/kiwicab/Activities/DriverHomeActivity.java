package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;
import com.example.kiwicab.Model.Ride;
import com.example.kiwicab.Model.User;
import com.example.kiwicab.R;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("KIWICAB");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        driverId = mAuth.getCurrentUser().getUid();
        driversRef = FirebaseDatabase.getInstance().getReference().child("users").child("drivers");
        customersRef = FirebaseDatabase.getInstance().getReference().child("users").child("customers");
        ridesRef = FirebaseDatabase.getInstance().getReference().child("rides");
        onlineDriversRef = FirebaseDatabase.getInstance().getReference().child("online_drivers");

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

        // Check if driver has an active ride
        checkForActiveRide();
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
            // Go online
            isAvailable = true;
            availabilitySwitch.setChecked(true);
            goOnlineBtn.setText("Go Offline");
            statusTextView.setText("You are online and available for rides");

            // Update driver location
            updateDriverLocation();

            // Listen for ride requests
            listenForRideRequests();
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

    private void listenForRideRequests() {
        // Listen for new ride requests
        rideRequestListener = ridesRef.orderByChild("status").equalTo("requested").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Only process if driver is available and not on a ride
                if (isAvailable && currentRideId == null) {
                    for (DataSnapshot rideSnapshot : snapshot.getChildren()) {
                        Ride ride = rideSnapshot.getValue(Ride.class);

                        if (ride != null && ride.getDriverId() == null) {
                            // Calculate distance between driver and pickup location
                            float[] results = new float[1];
                            android.location.Location.distanceBetween(
                                    driverLocation.latitude, driverLocation.longitude,
                                    ride.getPickupLocation().getLatitude(), ride.getPickupLocation().getLongitude(),
                                    results);

                            float distanceInKm = results[0] / 1000;

                            // Only show requests within 5km
                            if (distanceInKm <= 20) {
                                // Show ride request
                                showRideRequest(ride);
                                break;
                            }
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

                        // Set customer details
                        customerNameTextView.setText(customer.getName());
                        customerPhoneTextView.setText(customer.getPhone());
                        customerPhone = customer.getPhone(); // Store phone number for call function
                        pickupAddressTextView.setText(ride.getPickupLocation().getAddress());
                        destinationAddressTextView.setText(ride.getDestinationLocation().getAddress());

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
        drawRoute(driverLocation, pickupLatLng, Color.BLUE);
    }

    private void drawRoute(LatLng origin, LatLng destination, int color) {
        // Note: In a real app, you would use Google Directions API to get the route
        // This is a simplified version that just draws a straight line
        PolylineOptions polylineOptions = new PolylineOptions()
                .add(origin)
                .add(destination)
                .width(5)
                .color(color);

        mMap.addPolyline(polylineOptions);
    }

    private void acceptRide() {
        if (currentRideId != null) {
            // Update ride with driver id
            ridesRef.child(currentRideId).child("driverId").setValue(driverId);
            ridesRef.child(currentRideId).child("status").setValue("accepted");

            // Update driver's current ride
            driversRef.child(driverId).child("currentRideId").setValue(currentRideId);

            // Update UI
            acceptRideBtn.setVisibility(View.GONE);
            startRideBtn.setVisibility(View.VISIBLE);
            statusTextView.setText("Ride accepted. Navigate to pickup location.");

            // Send notification to customer (in a real app)

            // Listen for ride updates
            listenForRideUpdates();
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
                            drawRoute(driverLocation, destinationLatLng, Color.RED);
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
        } else if (id==R.id.action_earnings) {
            startActivity(new Intent(DriverHomeActivity.this, DriverEarningsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
