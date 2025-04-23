package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
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

    public interface CarpoolCallback {
        void onActiveCarpoolFound(Carpool carpool);
    }

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

        checkUserType();



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
            checkExistingCarpoolAndProceed(
                    () -> {
                        // No active carpool, allow creation
                        startActivity(new Intent(this, CreateCarpoolActivity.class));
                    },
                    carpool -> {
                        // Already in a carpool, show details instead
                        Intent intent = new Intent(this, CarpoolDetailsActivity.class);
                        intent.putExtra("carpoolId", carpool.getId());
                        startActivity(intent);
                        Toast.makeText(this, "You are already in a carpool.", Toast.LENGTH_LONG).show();
                    }
            );
        });


        findCarpoolBtn.setOnClickListener(v -> {
            checkExistingCarpoolAndProceed(
                    () -> {
                        // No active carpool, allow finding
                        startActivity(new Intent(this, FindCarpoolActivity.class));
                    },
                    carpool -> {
                        // Already in a carpool, show details instead
                        Intent intent = new Intent(this, CarpoolDetailsActivity.class);
                        intent.putExtra("carpoolId", carpool.getId());
                        startActivity(intent);
                        Toast.makeText(this, "You are already in a carpool.", Toast.LENGTH_LONG).show();
                    }
            );
        });
    }

    private void checkUserType() {
        DatabaseReference customerRef = usersRef.child("customers").child(userId);

        customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    handleUserType("customer");
                } else {
                    DatabaseReference driverRef = usersRef.child("drivers").child(userId);
                    driverRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                handleUserType("driver");
                            } else {
                                Toast.makeText(CarpoolHomeActivity.this, "User role not recognized", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(CarpoolHomeActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CarpoolHomeActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleUserType(String userType) {
        if (userType.equals("driver")) {
            createCarpoolBtn.setVisibility(View.VISIBLE);
            findCarpoolBtn.setVisibility(View.GONE);
        } else if (userType.equals("customer")) {
            createCarpoolBtn.setVisibility(View.GONE);
            findCarpoolBtn.setVisibility(View.VISIBLE);
        }
    }





    private void checkExistingCarpoolAndProceed(Runnable onNoActiveCarpool, CarpoolCallback onActiveCarpoolFound) {
        DatabaseReference userActiveCarpoolsRef = FirebaseDatabase.getInstance()
                .getReference().child("users").child(userId).child("activeCarpools");

        userActiveCarpoolsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // No active carpools at all
                    onNoActiveCarpool.run();
                    return;
                }
                // Check each carpoolId for active status
                for (DataSnapshot carpoolSnap : snapshot.getChildren()) {
                    String carpoolId = carpoolSnap.getKey();
                    DatabaseReference carpoolRef = FirebaseDatabase.getInstance()
                            .getReference().child("carpools").child(carpoolId);
                    carpoolRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot carpoolSnapshot) {
                            if (carpoolSnapshot.exists()) {
                                String status = carpoolSnapshot.child("status").getValue(String.class);
                                if ("active".equals(status)) {
                                    // Block: already in an active carpool
                                    Carpool carpool = carpoolSnapshot.getValue(Carpool.class);
                                    onActiveCarpoolFound.onActiveCarpoolFound(carpool);
                                } else {
                                    // Remove stale reference
                                    userActiveCarpoolsRef.child(carpoolId).removeValue();
                                    onNoActiveCarpool.run();
                                }
                            } else {
                                // Remove stale reference
                                userActiveCarpoolsRef.child(carpoolId).removeValue();
                                onNoActiveCarpool.run();
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            onNoActiveCarpool.run();
                        }
                    });
                    // Only check the first found carpoolId
                    break;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onNoActiveCarpool.run();
            }
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