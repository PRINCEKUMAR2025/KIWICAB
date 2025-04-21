package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.example.kiwicab.Model.Ride;
import com.example.kiwicab.Model.User;
import com.example.kiwicab.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.google.firebase.database.Query;


public class RideHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RideAdapter adapter;
    private List<Ride> rideList;
    private ProgressBar progressBar;
    private TextView emptyView;

    private FirebaseAuth mAuth;
    private DatabaseReference ridesRef;
    private String userId;
    private boolean isDriver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_history);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();
        ridesRef = FirebaseDatabase.getInstance().getReference().child("rides");

        // Check if user is driver or customer
        DatabaseReference driversRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child("drivers").child(userId);

        driversRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                isDriver = snapshot.exists();
                loadRideHistory();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RideHistoryActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        rideList = new ArrayList<>();
        adapter = new RideAdapter(this, rideList);
        recyclerView.setAdapter(adapter);
    }

    private void loadRideHistory() {
        progressBar.setVisibility(View.VISIBLE);

        // Query rides based on user type
        Query query;
        if (isDriver) {
            query = ridesRef.orderByChild("driverId").equalTo(userId);
        } else {
            query = ridesRef.orderByChild("customerId").equalTo(userId);
        }

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                rideList.clear();

                for (DataSnapshot rideSnapshot : snapshot.getChildren()) {
                    Ride ride = rideSnapshot.getValue(Ride.class);
                    if (ride != null) {
                        // Only add completed or cancelled rides
                        if (ride.getStatus().equals("completed") || ride.getStatus().equals("cancelled")) {
                            rideList.add(ride);
                        }
                    }
                }

                // Sort rides by timestamp (newest first)
                Collections.sort(rideList, new Comparator<Ride>() {
                    @Override
                    public int compare(Ride r1, Ride r2) {
                        return Long.compare(r2.getTimestamp(), r1.getTimestamp());
                    }
                });

                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);

                // Show empty view if no rides
                if (rideList.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(RideHistoryActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Adapter for ride history
    private class RideAdapter extends RecyclerView.Adapter<RideAdapter.RideViewHolder> {

        private Context context;
        private List<Ride> rides;
        private DatabaseReference usersRef;

        public RideAdapter(Context context, List<Ride> rides) {
            this.context = context;
            this.rides = rides;
            this.usersRef = FirebaseDatabase.getInstance().getReference().child("users");
        }

        @NonNull
        @Override
        public RideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_ride_history, parent, false);
            return new RideViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RideViewHolder holder, int position) {
            Ride ride = rides.get(position);

            // Format date
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            String dateString = sdf.format(new Date(ride.getTimestamp()));

            // Set ride details
            holder.dateTextView.setText(dateString);
            holder.statusTextView.setText(ride.getStatus());
            holder.fareTextView.setText(String.format("₹%.2f", ride.getFare()));
            holder.pickupTextView.setText(ride.getPickupLocation().getAddress());
            holder.destinationTextView.setText(ride.getDestinationLocation().getAddress());

            // Set status color
            if (ride.getStatus().equals("completed")) {
                holder.statusTextView.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess));
            } else {
                holder.statusTextView.setTextColor(ContextCompat.getColor(context, R.color.colorError));
            }

            // Get other user's details (driver for customer, customer for driver)
            String otherUserId = isDriver ? ride.getCustomerId() : ride.getDriverId();
            if (otherUserId != null) {
                String userType = isDriver ? "customers" : "drivers";
                usersRef.child(userType).child(otherUserId).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                holder.userNameTextView.setText(user.getName());
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });
            } else {
                holder.userNameTextView.setText("Unknown");
            }

            // Set click listener for details
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRideDetails(ride);
                }
            });
        }

        @Override
        public int getItemCount() {
            return rides.size();
        }

        private void showRideDetails(Ride ride) {
            // Create dialog to show ride details
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_ride_details, null);

            // Initialize dialog views
            TextView dateTextView = dialogView.findViewById(R.id.detailsDateTextView);
            TextView statusTextView = dialogView.findViewById(R.id.detailsStatusTextView);
            TextView fareTextView = dialogView.findViewById(R.id.detailsFareTextView);
            TextView pickupTextView = dialogView.findViewById(R.id.detailsPickupTextView);
            TextView destinationTextView = dialogView.findViewById(R.id.detailsDestinationTextView);
            TextView distanceTextView = dialogView.findViewById(R.id.detailsDistanceTextView);
            TextView userNameTextView = dialogView.findViewById(R.id.detailsUserNameTextView);
            TextView userTypeTextView = dialogView.findViewById(R.id.detailsUserTypeTextView);

            // Format date
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.getDefault());
            String dateString = sdf.format(new Date(ride.getTimestamp()));

            // Set ride details
            dateTextView.setText(dateString);
            statusTextView.setText(ride.getStatus());
            fareTextView.setText(String.format("₹%.2f", ride.getFare()));
            pickupTextView.setText(ride.getPickupLocation().getAddress());
            destinationTextView.setText(ride.getDestinationLocation().getAddress());
            distanceTextView.setText(String.format("%.2f km", ride.getDistance()));

            // Set user type label
            userTypeTextView.setText(isDriver ? "Customer" : "Driver");

            // Get other user's details
            String otherUserId = isDriver ? ride.getCustomerId() : ride.getDriverId();
            if (otherUserId != null) {
                String userType = isDriver ? "customers" : "drivers";
                usersRef.child(userType).child(otherUserId).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                userNameTextView.setText(user.getName());
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });
            } else {
                userNameTextView.setText("Unknown");
            }

            builder.setView(dialogView)
                    .setTitle("Ride Details")
                    .setPositiveButton("Close", null)
                    .show();
        }

        class RideViewHolder extends RecyclerView.ViewHolder {
            TextView dateTextView, statusTextView, fareTextView, pickupTextView, destinationTextView, userNameTextView;

            RideViewHolder(@NonNull View itemView) {
                super(itemView);
                dateTextView = itemView.findViewById(R.id.dateTextView);
                statusTextView = itemView.findViewById(R.id.statusTextView);
                fareTextView = itemView.findViewById(R.id.fareTextView);
                pickupTextView = itemView.findViewById(R.id.pickupTextView);
                destinationTextView = itemView.findViewById(R.id.destinationTextView);
                userNameTextView = itemView.findViewById(R.id.userNameTextView);
            }
        }
    }
}
