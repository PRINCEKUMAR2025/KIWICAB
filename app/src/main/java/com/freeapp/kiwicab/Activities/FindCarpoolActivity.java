package com.freeapp.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.freeapp.kiwicab.Model.Carpool;
import com.freeapp.kiwicab.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FindCarpoolActivity extends AppCompatActivity {

    private EditText destinationEditText;
    private RecyclerView carpoolsRecyclerView;
    private Button searchBtn;
    private ProgressBar progressBar;
    private TextView noResultsTextView;

    private FirebaseAuth mAuth;
    private DatabaseReference carpoolsRef;
    private String userId;
    private LatLng currentLocation;

    private List<Carpool> carpoolList;
    private CarpoolAdapter carpoolAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_carpool);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();
        carpoolsRef = FirebaseDatabase.getInstance().getReference().child("carpools");

        // Initialize views
        destinationEditText = findViewById(R.id.destinationEditText);
        carpoolsRecyclerView = findViewById(R.id.carpoolsRecyclerView);
        searchBtn = findViewById(R.id.searchBtn);
        progressBar = findViewById(R.id.progressBar);
        noResultsTextView = findViewById(R.id.noResultsTextView);

        // Set up RecyclerView
        carpoolList = new ArrayList<>();
        carpoolAdapter = new CarpoolAdapter(this, carpoolList);
        carpoolsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        carpoolsRecyclerView.setAdapter(carpoolAdapter);

        // Get current location from intent
        if (getIntent().hasExtra("currentLat") && getIntent().hasExtra("currentLng")) {
            double lat = getIntent().getDoubleExtra("currentLat", 0);
            double lng = getIntent().getDoubleExtra("currentLng", 0);
            currentLocation = new LatLng(lat, lng);
        }

        // Set click listeners
        destinationEditText.setOnClickListener(v -> {
            // Launch place picker for destination
        });

        searchBtn.setOnClickListener(v -> {
            searchCarpools();
        });

        // Load all active carpools initially
        loadActiveCarpools();
    }

    private void loadActiveCarpools() {
        progressBar.setVisibility(View.VISIBLE);
        noResultsTextView.setVisibility(View.GONE);

        carpoolsRef.orderByChild("status").equalTo("active").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                carpoolList.clear();

                for (DataSnapshot carpoolSnapshot : snapshot.getChildren()) {
                    Carpool carpool = carpoolSnapshot.getValue(Carpool.class);

                    if (carpool != null && carpool.getPassengerCount() < 3) {
                        // Check if user is not already in this carpool
                        if (carpool.getPassengers() == null || !carpool.getPassengers().containsKey(userId)) {
                            carpoolList.add(carpool);
                        }
                    }
                }

                carpoolAdapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);

                if (carpoolList.isEmpty()) {
                    noResultsTextView.setVisibility(View.VISIBLE);
                } else {
                    noResultsTextView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(FindCarpoolActivity.this, "Failed to load carpools: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchCarpools() {
        String destination = destinationEditText.getText().toString().trim();

        if (TextUtils.isEmpty(destination)) {
            destinationEditText.setError("Please enter destination");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        noResultsTextView.setVisibility(View.GONE);

        // Search for carpools with similar destination
        carpoolsRef.orderByChild("status").equalTo("active").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                carpoolList.clear();

                for (DataSnapshot carpoolSnapshot : snapshot.getChildren()) {
                    Carpool carpool = carpoolSnapshot.getValue(Carpool.class);

                    if (carpool != null && carpool.getPassengerCount() < 3) {
                        // Check if destination matches (case insensitive)
                        String carpoolDestination = carpool.getDestinationLocation().getAddress().toLowerCase();
                        if (carpoolDestination.contains(destination.toLowerCase())) {
                            // Check if user is not already in this carpool
                            if (carpool.getPassengers() == null || !carpool.getPassengers().containsKey(userId)) {
                                carpoolList.add(carpool);
                            }
                        }
                    }
                }

                carpoolAdapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);

                if (carpoolList.isEmpty()) {
                    noResultsTextView.setVisibility(View.VISIBLE);
                } else {
                    noResultsTextView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(FindCarpoolActivity.this, "Failed to search carpools: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Adapter for carpools
    private class CarpoolAdapter extends RecyclerView.Adapter<CarpoolAdapter.CarpoolViewHolder> {

        private Context context;
        private List<Carpool> carpools;

        public CarpoolAdapter(Context context, List<Carpool> carpools) {
            this.context = context;
            this.carpools = carpools;
        }

        @NonNull
        @Override
        public CarpoolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_carpool, parent, false);
            return new CarpoolViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CarpoolViewHolder holder, int position) {
            Carpool carpool = carpools.get(position);

            holder.pickupTextView.setText(carpool.getPickupLocation().getAddress());
            holder.destinationTextView.setText(carpool.getDestinationLocation().getAddress());
            holder.fareTextView.setText(String.format("₹%.2f", carpool.getFare()));
            holder.timeTextView.setText(carpool.getDepartureTime());
            holder.seatsTextView.setText(String.format("%d/3 seats", carpool.getPassengerCount()));

            // Get driver details
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(carpool.getDriverId());

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String driverName = snapshot.child("name").getValue(String.class);
                        if (driverName != null) {
                            holder.driverNameTextView.setText(driverName);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error
                }
            });

            // Set click listener for join button
            holder.joinBtn.setOnClickListener(v -> {
                joinCarpool(carpool.getId());
            });

            // Set click listener for view details
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, CarpoolDetailsActivity.class);
                intent.putExtra("carpoolId", carpool.getId());
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return carpools.size();
        }

        class CarpoolViewHolder extends RecyclerView.ViewHolder {
            TextView pickupTextView, destinationTextView, fareTextView, timeTextView, seatsTextView, driverNameTextView;
            Button joinBtn;

            CarpoolViewHolder(@NonNull View itemView) {
                super(itemView);
                pickupTextView = itemView.findViewById(R.id.pickupTextView);
                destinationTextView = itemView.findViewById(R.id.destinationTextView);
                fareTextView = itemView.findViewById(R.id.fareTextView);
                timeTextView = itemView.findViewById(R.id.timeTextView);
                seatsTextView = itemView.findViewById(R.id.seatsTextView);
                driverNameTextView = itemView.findViewById(R.id.driverNameTextView);
                joinBtn = itemView.findViewById(R.id.joinBtn);
            }
        }
    }

    private void joinCarpool(String carpoolId) {
        // Check if carpool is still available
        carpoolsRef.child(carpoolId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Carpool carpool = snapshot.getValue(Carpool.class);

                    if (carpool != null) {
                        int passengerCount = carpool.getPassengerCount();

                        if (passengerCount < 3) {
                            // Add user to passengers
                            carpoolsRef.child(carpoolId).child("passengers").child(userId).setValue(true);

                            // Increment passenger count
                            carpoolsRef.child(carpoolId).child("passengerCount").setValue(passengerCount + 1);

                            // Check if carpool is now full
                            if (passengerCount + 1 >= 3) {
                                carpoolsRef.child(carpoolId).child("isFull").setValue(true);
                            }

                            // Add carpool to user's active carpools
                            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                                    .child("users").child(userId);
                            userRef.child("activeCarpools").child(carpoolId).setValue(true);

                            Toast.makeText(FindCarpoolActivity.this, "Successfully joined carpool", Toast.LENGTH_SHORT).show();

                            // Navigate to carpool details
                            Intent intent = new Intent(FindCarpoolActivity.this, CarpoolDetailsActivity.class);
                            intent.putExtra("carpoolId", carpoolId);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(FindCarpoolActivity.this, "This carpool is already full", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(FindCarpoolActivity.this, "Carpool not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(FindCarpoolActivity.this, "Failed to join carpool: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
