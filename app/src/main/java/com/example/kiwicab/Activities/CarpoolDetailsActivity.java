package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.kiwicab.Model.Carpool;
import com.example.kiwicab.Model.User;
import com.example.kiwicab.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class CarpoolDetailsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private TextView pickupTextView, destinationTextView, fareTextView, timeTextView, seatsTextView, driverNameTextView,vehicleInfoTextView;
    private Button actionBtn, chatBtn;
    private RecyclerView passengersRecyclerView;

    private FirebaseAuth mAuth;
    private DatabaseReference carpoolsRef, usersRef;
    private String userId, carpoolId;
    private Carpool carpool;
    private Polyline currentRoutePolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_carpool_details);

        // Get carpool ID from intent
        carpoolId = getIntent().getStringExtra("carpoolId");
        if (carpoolId == null) {
            Toast.makeText(this, "Carpool not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();
        carpoolsRef = FirebaseDatabase.getInstance().getReference().child("carpools");
        usersRef = FirebaseDatabase.getInstance().getReference().child("users");

        // Initialize views
        pickupTextView = findViewById(R.id.pickupTextView);
        destinationTextView = findViewById(R.id.destinationTextView);
        fareTextView = findViewById(R.id.fareTextView);
        timeTextView = findViewById(R.id.timeTextView);
        vehicleInfoTextView=findViewById(R.id.vehicleInfo);
        seatsTextView = findViewById(R.id.seatsTextView);
        actionBtn = findViewById(R.id.actionBtn);
        chatBtn = findViewById(R.id.chatBtn);
        passengersRecyclerView = findViewById(R.id.passengersRecyclerView);

        // Set up map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Set up RecyclerView
        passengersRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Load carpool details
        loadCarpoolDetails();

        // Set click listeners
        chatBtn.setOnClickListener(v -> {
            // Open chat activity
            Toast.makeText(this, "Chat Coming Soon", Toast.LENGTH_SHORT).show();
        });
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

        // If carpool is already loaded, update map
        if (carpool != null) {
            updateMapWithRoute();
        }
    }

    private void loadCarpoolDetails() {
        carpoolsRef.child(carpoolId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    carpool = snapshot.getValue(Carpool.class);

                    if (carpool != null) {
                        updateUI();
                        loadPassengers();

                        if (mMap != null) {
                            updateMapWithRoute();
                        }
                    }
                } else {
                    Toast.makeText(CarpoolDetailsActivity.this, "Carpool not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CarpoolDetailsActivity.this, "Failed to load carpool details: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI() {
        pickupTextView.setText(carpool.getPickupLocation().getAddress());
        destinationTextView.setText(carpool.getDestinationLocation().getAddress());
        fareTextView.setText(String.format("₹%.2f", carpool.getFare()));
        vehicleInfoTextView.setText(carpool.getVehicleInformation().toString());
        timeTextView.setText(carpool.getDepartureTime());
        seatsTextView.setText(String.format("%d/4 seats", carpool.getPassengerCount()));


        // Set action button based on user role
        if (userId.equals(carpool.getDriverId())) {
            // User is the driver
            actionBtn.setText("Cancel Carpool");
            actionBtn.setOnClickListener(v -> {
                cancelCarpool();
            });
        } else if (carpool.getPassengers() != null && carpool.getPassengers().containsKey(userId)) {
            // User is a passenger
            actionBtn.setText("Leave Carpool");
            actionBtn.setOnClickListener(v -> {
                leaveCarpool();
            });
        } else {
            // User is not part of the carpool
            if (carpool.getPassengerCount() < 4) {
                actionBtn.setText("Join Carpool");
                actionBtn.setOnClickListener(v -> {
                    joinCarpool();
                });
            } else {
                actionBtn.setText("Carpool Full");
                actionBtn.setEnabled(false);
            }
        }
    }

    private void loadPassengers() {
        if (carpool.getPassengers() != null) {
            List<String> passengerIds = new ArrayList<>(carpool.getPassengers().keySet());
            List<User> passengers = new ArrayList<>();

            for (String passengerId : passengerIds) {
                usersRef.child(passengerId).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User passenger = snapshot.getValue(User.class);
                            if (passenger != null) {
                                passengers.add(passenger);

                                // Update adapter when all passengers are loaded
                                if (passengers.size() == passengerIds.size()) {
                                    PassengerAdapter adapter = new PassengerAdapter(CarpoolDetailsActivity.this, passengers);
                                    passengersRecyclerView.setAdapter(adapter);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });
            }
        }
    }

    private void updateMapWithRoute() {
        if (mMap != null && carpool != null) {
            mMap.clear();

            LatLng pickupLocation = new LatLng(
                    carpool.getPickupLocation().getLatitude(),
                    carpool.getPickupLocation().getLongitude());

            LatLng destinationLocation = new LatLng(
                    carpool.getDestinationLocation().getLatitude(),
                    carpool.getDestinationLocation().getLongitude());

            // Add markers
            mMap.addMarker(new MarkerOptions()
                    .position(pickupLocation)
                    .title("Pickup")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

            mMap.addMarker(new MarkerOptions()
                    .position(destinationLocation)
                    .title("Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            // Draw route
            drawRoute(pickupLocation, destinationLocation);

            // Show both markers in view
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(pickupLocation);
            builder.include(destinationLocation);
            LatLngBounds bounds = builder.build();

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
        }
    }

    private void drawRoute(LatLng origin, LatLng destination) {
        // Build the OSRM API URL
        String url = "https://router.project-osrm.org/route/v1/driving/"
                + origin.longitude + "," + origin.latitude + ";"
                + destination.longitude + "," + destination.latitude
                + "?overview=full&geometries=polyline";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(CarpoolDetailsActivity.this, "Failed to get route", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseData);

                        if ("Ok".equals(jsonObject.getString("code"))) {
                            JSONArray routes = jsonObject.getJSONArray("routes");
                            if (routes.length() > 0) {
                                JSONObject route = routes.getJSONObject(0);
                                String encodedPolyline = route.getString("geometry");
                                double distance = route.getDouble("distance"); // meters
                                double duration = route.getDouble("duration"); // seconds

                                List<LatLng> decodedPath = decodePoly(encodedPolyline);

                                runOnUiThread(() -> {
                                    // Remove previous route if exists
                                    if (currentRoutePolyline != null) {
                                        currentRoutePolyline.remove();
                                    }
                                    // Draw the polyline
                                    PolylineOptions options = new PolylineOptions()
                                            .addAll(decodedPath)
                                            .width(8)
                                            .color(getResources().getColor(R.color.colorPrimary, null))
                                            .geodesic(true);
                                    currentRoutePolyline = mMap.addPolyline(options);

                                    // Show distance
                                    TextView distanceTextView = findViewById(R.id.distanceTextView);
                                    if (distanceTextView != null) {
                                        distanceTextView.setText(String.format("%.1f km, %d min",
                                                distance / 1000, (int) (duration / 60)));
                                    }

                                    // Adjust camera to fit route
                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                    for (LatLng point : decodedPath) builder.include(point);
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                                });
                            }
                        } else {
                            runOnUiThread(() ->
                                    Toast.makeText(CarpoolDetailsActivity.this, "No route found", Toast.LENGTH_SHORT).show()
                            );
                        }
                    } catch (Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(CarpoolDetailsActivity.this, "Error parsing route", Toast.LENGTH_SHORT).show()
                        );
                    }
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(CarpoolDetailsActivity.this, "Route request failed", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }
    // Polyline decoder (Google/OSRM format)
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

    private void joinCarpool() {
        // Check if carpool is still available
        carpoolsRef.child(carpoolId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Carpool carpool = snapshot.getValue(Carpool.class);

                    if (carpool != null) {
                        int passengerCount = carpool.getPassengerCount();

                        if (passengerCount < 4) {
                            // Add user to passengers
                            carpoolsRef.child(carpoolId).child("passengers").child(userId).setValue(true);

                            // Increment passenger count
                            carpoolsRef.child(carpoolId).child("passengerCount").setValue(passengerCount + 1);

                            // Check if carpool is now full
                            if (passengerCount + 1 >= 4) {
                                carpoolsRef.child(carpoolId).child("isFull").setValue(true);
                            }

                            // Add carpool to user's active carpools
                            usersRef.child(userId).child("activeCarpools").child(carpoolId).setValue(true);

                            Toast.makeText(CarpoolDetailsActivity.this, "Successfully joined carpool", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CarpoolDetailsActivity.this, "This carpool is already full", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(CarpoolDetailsActivity.this, "Carpool not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CarpoolDetailsActivity.this, "Failed to join carpool: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void leaveCarpool() {
        // Remove user from passengers
        carpoolsRef.child(carpoolId).child("passengers").child(userId).removeValue();

        // Decrement passenger count
        carpoolsRef.child(carpoolId).child("passengerCount").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Integer count = snapshot.getValue(Integer.class);
                    if (count != null && count > 0) {
                        carpoolsRef.child(carpoolId).child("passengerCount").setValue(count - 1);

                        // Update isFull flag
                        carpoolsRef.child(carpoolId).child("isFull").setValue(false);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });

        // Remove carpool from user's active carpools
        usersRef.child(userId).child("activeCarpools").child(carpoolId).removeValue();

        Toast.makeText(this, "You have left the carpool", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void cancelCarpool() {
        // Only the driver can cancel the carpool
        if (userId.equals(carpool.getDriverId())) {
            // Delete the carpool node completely
            carpoolsRef.child(carpoolId).removeValue();

            // Remove carpool from all passengers' activeCarpools
            if (carpool.getPassengers() != null) {
                for (String passengerId : carpool.getPassengers().keySet()) {
                    usersRef.child(passengerId).child("activeCarpools").child(carpoolId).removeValue();
                    // Send notification to passenger (in a real app)
                }
            }

            Toast.makeText(this, "Carpool deleted", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Only the driver can cancel the carpool", Toast.LENGTH_SHORT).show();
        }
    }

    // Adapter for passengers
    private class PassengerAdapter extends RecyclerView.Adapter<PassengerAdapter.PassengerViewHolder> {

        private Context context;
        private List<User> passengers;

        public PassengerAdapter(Context context, List<User> passengers) {
            this.context = context;
            this.passengers = passengers;
        }

        @NonNull
        @Override
        public PassengerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_passenger, parent, false);
            return new PassengerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PassengerViewHolder holder, int position) {
            User passenger = passengers.get(position);

            holder.nameTextView.setText(passenger.getName());

            // Load profile image if available
            if (passenger.getProfileImageUrl() != null) {
                Glide.with(context)
                        .load(passenger.getProfileImageUrl())
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .circleCrop()
                        .into(holder.profileImageView);
            }
        }

        @Override
        public int getItemCount() {
            return passengers.size();
        }

        class PassengerViewHolder extends RecyclerView.ViewHolder {
            ImageView profileImageView;
            TextView nameTextView;

            PassengerViewHolder(@NonNull View itemView) {
                super(itemView);
                profileImageView = itemView.findViewById(R.id.profileImageView);
                nameTextView = itemView.findViewById(R.id.nameTextView);
            }
        }
    }
}
