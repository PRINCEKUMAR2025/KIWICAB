package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_home);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("KIWICAB");

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

        // Check if user has an active ride
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

    private void updateRideUI(Ride ride) {
        switch (ride.getStatus()) {
            case "requested":
                statusTextView.setText("Looking for a driver...");
                break;
            case "accepted":
                statusTextView.setText("Confirmed: Driver is coming to pick you up");
                // Get driver details and show on UI
                getDriverDetails(ride.getDriverId());
                if (ride.getDriverId() != null) {
                    trackDriverLocation(ride.getDriverId());
                }
                break;
            case "ongoing":
                statusTextView.setText("Ride in progress");
                break;
            case "completed":
                statusTextView.setText("Ride completed");
                if (driverLocationListener != null && ride.getDriverId() != null) {
                    onlineDriversRef.child(ride.getDriverId()).removeEventListener(driverLocationListener);
                    driverLocationListener = null;
                }
                // Show rating dialog
                showRatingDialog(ride.getDriverId());
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
            ridesRef.child(currentRideId).child("status").setValue("cancelled")
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Reset customer's current ride
                                customersRef.child(customerId).child("currentRideId").removeValue();
                                currentRideId = null;

                                // Reset UI
                                resetRideUI();

                                Toast.makeText(CustomerHomeActivity.this, "Ride cancelled", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(CustomerHomeActivity.this, "Failed to cancel ride: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
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
                            // Animate marker to new position
                            animateMarkerToPosition(driverMarker, driverLatLng);
                        } else {
                            // Create new marker for driver
                            driverMarker = mMap.addMarker(new MarkerOptions()
                                    .position(driverLatLng)
                                    .title("Your Driver")
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon)));
                        }

                        // If pickup and destination markers exist, include all in camera view
                        if (pickupMarker != null && destinationMarker != null) {
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            builder.include(driverLatLng);
                            builder.include(pickupMarker.getPosition());
                            builder.include(destinationMarker.getPosition());
                            LatLngBounds bounds = builder.build();

                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
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

    // Helper method to animate marker movement (smooth transition)
    private void animateMarkerToPosition(final Marker marker, final LatLng targetPosition) {
        final LatLng startPosition = marker.getPosition();
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        final long duration = 500; // Animation duration in ms
        final Interpolator interpolator = new LinearInterpolator();

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

    private void showRatingDialog(final String driverId) {
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
        }

        return super.onOptionsItemSelected(item);
    }
}