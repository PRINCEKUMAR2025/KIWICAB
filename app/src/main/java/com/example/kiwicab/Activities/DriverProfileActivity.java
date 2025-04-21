package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.kiwicab.Model.Driver;
import com.example.kiwicab.Model.VehicleDetails;
import com.example.kiwicab.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;

public class DriverProfileActivity extends AppCompatActivity {

    private EditText nameEditText, phoneEditText, vehicleTypeEditText, vehicleNumberEditText,
            vehicleModelEditText, vehicleColorEditText;
    private Button updateProfileBtn;
    private ImageView profileImageView;
    private TextView emailTextView, ratingTextView;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private DatabaseReference driversRef;
    private StorageReference storageRef;
    private String driverId;
    private Uri imageUri;
    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        driverId = mAuth.getCurrentUser().getUid();
        driversRef = FirebaseDatabase.getInstance().getReference().child("users").child("drivers").child(driverId);
        storageRef = FirebaseStorage.getInstance().getReference().child("profile_images").child(driverId);

        // Initialize views
        nameEditText = findViewById(R.id.profileNameEditText);
        phoneEditText = findViewById(R.id.profilePhoneEditText);
        emailTextView = findViewById(R.id.profileEmailTextView);
        ratingTextView = findViewById(R.id.profileRatingTextView);
        vehicleTypeEditText = findViewById(R.id.vehicleTypeEditText);
        vehicleNumberEditText = findViewById(R.id.vehicleNumberEditText);
        vehicleModelEditText = findViewById(R.id.vehicleModelEditText);
        vehicleColorEditText = findViewById(R.id.vehicleColorEditText);
        profileImageView = findViewById(R.id.profileImageView);
        updateProfileBtn = findViewById(R.id.updateProfileBtn);
        progressBar = findViewById(R.id.progressBar);

        // Load user data
        loadUserData();

        // Set click listeners
        profileImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileChooser();
            }
        });

        updateProfileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateProfile();
            }
        });
    }

    private void loadUserData() {
        progressBar.setVisibility(View.VISIBLE);

        driversRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Driver driver = snapshot.getValue(Driver.class);
                    if (driver != null) {
                        nameEditText.setText(driver.getName());
                        phoneEditText.setText(driver.getPhone());
                        emailTextView.setText(driver.getEmail());
                        ratingTextView.setText(String.format("%.1f", driver.getRating()));

                        // Set vehicle details
                        if (driver.getVehicleDetails() != null) {
                            vehicleTypeEditText.setText(driver.getVehicleDetails().getVehicleType());
                            vehicleNumberEditText.setText(driver.getVehicleDetails().getVehicleNumber());
                            vehicleModelEditText.setText(driver.getVehicleDetails().getVehicleModel());
                            vehicleColorEditText.setText(driver.getVehicleDetails().getVehicleColor());
                        }

                        // Load profile image
                        storageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                Glide.with(DriverProfileActivity.this)
                                        .load(uri)
                                        .placeholder(R.drawable.default_profile)
                                        .error(R.drawable.default_profile)
                                        .circleCrop()
                                        .into(profileImageView);
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Use default image
                                profileImageView.setImageResource(R.drawable.default_profile);
                            }
                        });
                    }
                }
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DriverProfileActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            imageUri = data.getData();
            Glide.with(this)
                    .load(imageUri)
                    .circleCrop()
                    .into(profileImageView);
        }
    }

    private void updateProfile() {
        final String name = nameEditText.getText().toString().trim();
        final String phone = phoneEditText.getText().toString().trim();
        final String vehicleType = vehicleTypeEditText.getText().toString().trim();
        final String vehicleNumber = vehicleNumberEditText.getText().toString().trim();
        final String vehicleModel = vehicleModelEditText.getText().toString().trim();
        final String vehicleColor = vehicleColorEditText.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("Name is required");
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            phoneEditText.setError("Phone number is required");
            return;
        }

        if (TextUtils.isEmpty(vehicleType) || TextUtils.isEmpty(vehicleNumber) ||
                TextUtils.isEmpty(vehicleModel) || TextUtils.isEmpty(vehicleColor)) {
            Toast.makeText(this, "Please fill all vehicle details", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Create vehicle details object
        VehicleDetails vehicleDetails = new VehicleDetails(vehicleType, vehicleNumber, vehicleModel, vehicleColor);

        // Update user data
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("vehicleDetails", vehicleDetails);

        driversRef.updateChildren(updates).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    // Upload image if selected
                    if (imageUri != null) {
                        uploadImage();
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(DriverProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(DriverProfileActivity.this, "Failed to update profile: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void uploadImage() {
        if (imageUri != null) {
            // Create a reference to the file location
            StorageReference fileRef = storageRef;

            // Upload file
            fileRef.putFile(imageUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            // Get download URL
                            fileRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    // Save URL to user profile
                                    driversRef.child("profileImageUrl").setValue(uri.toString())
                                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task) {
                                                    progressBar.setVisibility(View.GONE);
                                                    if (task.isSuccessful()) {
                                                        Toast.makeText(DriverProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                                                    } else {
                                                        Toast.makeText(DriverProfileActivity.this, "Failed to update profile image URL", Toast.LENGTH_SHORT).show();
                                                    }
                                                }
                                            });
                                }
                            });
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(DriverProfileActivity.this, "Failed to upload image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
