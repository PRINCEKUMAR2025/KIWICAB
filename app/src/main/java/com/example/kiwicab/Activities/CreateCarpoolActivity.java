package com.example.kiwicab.Activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.kiwicab.Model.Carpool;
import com.example.kiwicab.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateCarpoolActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private EditText vehicleInfoEditText,pickupEditText, destinationEditText, fareEditText, departureTimeEditText;
    private Button createCarpoolBtn;
    private LatLng pickupLocation, destinationLocation;
    private String pickupAddress, destinationAddress;
    private FirebaseAuth mAuth;
    private DatabaseReference carpoolsRef, usersRef;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_carpool);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();
        carpoolsRef = FirebaseDatabase.getInstance().getReference().child("carpools");
        usersRef = FirebaseDatabase.getInstance().getReference().child("users");

        // Initialize views
        pickupEditText = findViewById(R.id.pickupEditText);
        vehicleInfoEditText=findViewById(R.id.vehicleInfo);
        destinationEditText = findViewById(R.id.destinationEditText);
        fareEditText = findViewById(R.id.fareEditText);
        departureTimeEditText = findViewById(R.id.departureTimeEditText);
        createCarpoolBtn = findViewById(R.id.createCarpoolBtn);

        // Set up map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        departureTimeEditText.setOnClickListener(v -> {
            showTimePickerDialog();
        });

        createCarpoolBtn.setOnClickListener(v -> {
            // Get the text from EditText fields
            String pickupAddressText = pickupEditText.getText().toString().trim();
            String destinationAddressText = destinationEditText.getText().toString().trim();
            String vehicleInfo=vehicleInfoEditText.getText().toString().trim();

            // Validate input
            if (TextUtils.isEmpty(pickupAddressText)) {
                pickupEditText.setError("Please enter pickup location");
                return;
            }

            if (TextUtils.isEmpty(destinationAddressText)) {
                destinationEditText.setError("Please enter destination location");
                return;
            }
            if (TextUtils.isEmpty(vehicleInfo)) {
                vehicleInfoEditText.setError("Please enter vehicle information");
                return;
            }

            // Geocode the addresses to get coordinates
            geocodeAddress(pickupAddressText, true);
            geocodeAddress(destinationAddressText, false);

            // Check if geocoding was successful
            if (pickupLocation == null || destinationLocation == null) {
                Toast.makeText(this, "Could not find location. Please enter a valid address.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Continue with carpool creation
            createCarpool();
        });


        // Get current location from intent
        if (getIntent().hasExtra("currentLat") && getIntent().hasExtra("currentLng")) {
            double lat = getIntent().getDoubleExtra("currentLat", 0);
            double lng = getIntent().getDoubleExtra("currentLng", 0);
            pickupLocation = new LatLng(lat, lng);
            geocodeLocation(pickupLocation, true);
        }
    }

    private void geocodeAddress(String addressText, boolean isPickup) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressText, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());

                if (isPickup) {
                    pickupLocation = latLng;
                    pickupAddress = addressText;
                } else {
                    destinationLocation = latLng;
                    destinationAddress = addressText;
                }

                // Update map with the new location
                updateMapWithLocations();
            } else {
                Toast.makeText(this, "Location not found: " + addressText, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error finding location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check and request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }

        // Update map if locations are already set
        updateMapWithLocations();
    }

    private void updateMapWithLocations() {
        if (mMap != null) {
            mMap.clear();

            if (pickupLocation != null) {
                mMap.addMarker(new MarkerOptions()
                        .position(pickupLocation)
                        .title("Pickup")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            }

            if (destinationLocation != null) {
                mMap.addMarker(new MarkerOptions()
                        .position(destinationLocation)
                        .title("Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            }

            if (pickupLocation != null && destinationLocation != null) {
                // Draw route between pickup and destination
                drawRoute(pickupLocation, destinationLocation);

                // Show both markers in view
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(pickupLocation);
                builder.include(destinationLocation);
                LatLngBounds bounds = builder.build();

                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } else if (pickupLocation != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLocation, 15));
            }
        }
    }

    private void geocodeLocation(LatLng latLng, boolean isPickup) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addressText = address.getAddressLine(0);

                if (isPickup) {
                    pickupAddress = addressText;
                    pickupEditText.setText(addressText);
                } else {
                    destinationAddress = addressText;
                    destinationEditText.setText(addressText);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showTimePickerDialog() {
        Calendar currentTime = Calendar.getInstance();
        int hour = currentTime.get(Calendar.HOUR_OF_DAY);
        int minute = currentTime.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute1) -> {
                    // Format time as HH:mm
                    String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute1);
                    departureTimeEditText.setText(time);
                }, hour, minute, true);

        timePickerDialog.show();
    }

    private void drawRoute(LatLng origin, LatLng destination) {
        // Use OSRM for route drawing (as implemented in your existing code)
    }

    private void createCarpool() {
        if (pickupLocation == null || destinationLocation == null) {
            String pickupAddressText = pickupEditText.getText().toString().trim();
            String destinationAddressText = destinationEditText.getText().toString().trim();
            String vehicleInfo=vehicleInfoEditText.getText().toString().trim();

            // Validate input
            if (TextUtils.isEmpty(pickupAddressText)) {
                pickupEditText.setError("Please enter pickup location");
                return;
            }

            if (TextUtils.isEmpty(destinationAddressText)) {
                destinationEditText.setError("Please enter destination location");
                return;
            }
            if (TextUtils.isEmpty(vehicleInfo)) {
                vehicleInfoEditText.setError("Please enter vehicle information");
                return;
            }

            // Geocode the addresses to get coordinates
            geocodeAddress(pickupAddressText, true);
            geocodeAddress(destinationAddressText, false);

            // Check if geocoding was successful
            if (pickupLocation == null || destinationLocation == null) {
                Toast.makeText(this, "Could not find location. Please enter a valid address.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String fareStr = fareEditText.getText().toString().trim();
        String departureTime = departureTimeEditText.getText().toString().trim();
        String vehicleInfo=vehicleInfoEditText.getText().toString().trim();

        if (TextUtils.isEmpty(fareStr)) {
            fareEditText.setError("Please enter fare amount");
            return;
        }

        if (TextUtils.isEmpty(departureTime)) {
            departureTimeEditText.setError("Please select departure time");
            return;
        }


        double fare = Double.parseDouble(fareStr);
        String VI=vehicleInfo;

        // Create carpool ID
        String carpoolId = carpoolsRef.push().getKey();

        // Calculate distance
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                pickupLocation.latitude, pickupLocation.longitude,
                destinationLocation.latitude, destinationLocation.longitude,
                results);

        float distanceInKm = results[0] / 1000;

        // Create location objects
        com.example.kiwicab.Model.Location pickupLocationObj =
                new com.example.kiwicab.Model.Location(pickupLocation.latitude, pickupLocation.longitude, pickupAddress);

        com.example.kiwicab.Model.Location destinationLocationObj =
                new com.example.kiwicab.Model.Location(destinationLocation.latitude, destinationLocation.longitude, destinationAddress);

        // Create carpool object
        Carpool carpool = new Carpool(
                carpoolId,
                userId,
                VI,
                pickupLocationObj,
                destinationLocationObj,
                fare,
                departureTime,
                distanceInKm,
                System.currentTimeMillis()
        );

        // Add creator as first passenger
        Map<String, Boolean> passengers = new HashMap<>();
        passengers.put(userId, true);
        carpool.setPassengers(passengers);
        carpool.setPassengerCount(1);
        carpool.setStatus("active");

        // Save to Firebase
        carpoolsRef.child(carpoolId).setValue(carpool)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(CreateCarpoolActivity.this, "Carpool created successfully", Toast.LENGTH_SHORT).show();

                    // Add carpool to user's active carpools
                    usersRef.child(userId).child("activeCarpools").child(carpoolId).setValue(true);

                    // Navigate to carpool details
                    Intent intent = new Intent(CreateCarpoolActivity.this, CarpoolDetailsActivity.class);
                    intent.putExtra("carpoolId", carpoolId);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(CreateCarpoolActivity.this, "Failed to create carpool: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
