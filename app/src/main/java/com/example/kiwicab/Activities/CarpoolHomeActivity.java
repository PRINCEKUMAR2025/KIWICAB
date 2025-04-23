package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;
import com.example.kiwicab.Model.Carpool;
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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.Manifest;


public class CarpoolHomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Button createCarpoolBtn, findCarpoolBtn;
    private FirebaseAuth mAuth;
    private DatabaseReference carpoolsRef, usersRef;
    private String userId;
    private LatLng currentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_carpool_home);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();
        carpoolsRef = FirebaseDatabase.getInstance().getReference().child("carpools");
        usersRef = FirebaseDatabase.getInstance().getReference().child("users");

        // Initialize views
        createCarpoolBtn = findViewById(R.id.createCarpoolBtn);
        findCarpoolBtn = findViewById(R.id.findCarpoolBtn);

        // Set up map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationCallback();
        startLocationUpdates();

        // Set click listeners
        createCarpoolBtn.setOnClickListener(v -> {
            Intent intent = new Intent(CarpoolHomeActivity.this, CreateCarpoolActivity.class);
            if (currentLocation != null) {
                intent.putExtra("currentLat", currentLocation.latitude);
                intent.putExtra("currentLng", currentLocation.longitude);
            }
            startActivity(intent);
        });

        findCarpoolBtn.setOnClickListener(v -> {
            Intent intent = new Intent(CarpoolHomeActivity.this, FindCarpoolActivity.class);
            if (currentLocation != null) {
                intent.putExtra("currentLat", currentLocation.latitude);
                intent.putExtra("currentLng", currentLocation.longitude);
            }
            startActivity(intent);
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check and request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getLastLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }

        // Load active carpools on map
        loadActiveCarpools();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (android.location.Location location : locationResult.getLocations()) {
                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    updateMapWithCurrentLocation();
                }
            }
        };
    }

    private void getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            updateMapWithCurrentLocation();
                        }
                    });
        }
    }

    private void updateMapWithCurrentLocation() {
        if (mMap != null && currentLocation != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback,
                    Looper.getMainLooper());
        }
    }

    private void loadActiveCarpools() {
        carpoolsRef.orderByChild("status").equalTo("active").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mMap.clear();
                for (DataSnapshot carpoolSnapshot : snapshot.getChildren()) {
                    Carpool carpool = carpoolSnapshot.getValue(Carpool.class);
                    if (carpool != null) {
                        LatLng pickupLocation = new LatLng(
                                carpool.getPickupLocation().getLatitude(),
                                carpool.getPickupLocation().getLongitude());

                        LatLng destinationLocation = new LatLng(
                                carpool.getDestinationLocation().getLatitude(),
                                carpool.getDestinationLocation().getLongitude());

                        // Add markers for pickup and destination
                        mMap.addMarker(new MarkerOptions()
                                .position(pickupLocation)
                                .title("Pickup: " + carpool.getPickupLocation().getAddress())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                        mMap.addMarker(new MarkerOptions()
                                .position(destinationLocation)
                                .title("Destination: " + carpool.getDestinationLocation().getAddress())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                        // Draw route between pickup and destination
                        drawRoute(pickupLocation, destinationLocation);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CarpoolHomeActivity.this, "Failed to load carpools", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawRoute(LatLng origin, LatLng destination) {
        // Use OSRM for route drawing (as implemented in your existing code)
        String url = "https://router.project-osrm.org/route/v1/driving/" +
                origin.longitude + "," + origin.latitude + ";" +
                destination.longitude + "," + destination.latitude +
                "?overview=full&geometries=polyline";

        // Make HTTP request and draw route (implementation omitted for brevity)
    }
}