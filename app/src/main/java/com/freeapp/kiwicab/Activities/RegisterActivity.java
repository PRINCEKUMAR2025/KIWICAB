package com.freeapp.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.freeapp.kiwicab.Model.Driver;
import com.freeapp.kiwicab.Model.User;
import com.freeapp.kiwicab.Model.VehicleDetails;
import com.freeapp.kiwicab.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameEditText, emailEditText, phoneEditText, passwordEditText;
    private RadioGroup userTypeRadioGroup;
    private Button registerBtn;
    private TextView moveToLogin;
    private LinearLayout vehicleDetailsLayout;
    private EditText vehicleTypeEditText, vehicleNumberEditText, vehicleModelEditText, vehicleColorEditText;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private ProgressDialog loadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference().child("users");
        loadingBar = new ProgressDialog(this);

        // Initialize views
        nameEditText = findViewById(R.id.registerNameEditText);
        emailEditText = findViewById(R.id.registerEmailEditText);
        phoneEditText = findViewById(R.id.registerPhoneEditText);
        passwordEditText = findViewById(R.id.registerPasswordEditText);
        userTypeRadioGroup = findViewById(R.id.userTypeRadioGroup);
        registerBtn = findViewById(R.id.registerBtn);
        moveToLogin = findViewById(R.id.moveToLoginTextView);
        vehicleDetailsLayout = findViewById(R.id.vehicleDetailsLayout);

        vehicleTypeEditText = findViewById(R.id.vehicleTypeEditText);
        vehicleNumberEditText = findViewById(R.id.vehicleNumberEditText);
        vehicleModelEditText = findViewById(R.id.vehicleModelEditText);
        vehicleColorEditText = findViewById(R.id.vehicleColorEditText);

        // Initially hide vehicle details
        vehicleDetailsLayout.setVisibility(View.GONE);

        // Set listeners
        userTypeRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.driverRadioButton) {
                    vehicleDetailsLayout.setVisibility(View.VISIBLE);
                } else {
                    vehicleDetailsLayout.setVisibility(View.GONE);
                }
            }
        });

        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });

        moveToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            }
        });
    }

    private void registerUser() {
        final String name = nameEditText.getText().toString().trim();
        final String email = emailEditText.getText().toString().trim();
        final String phone = phoneEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        int selectedId = userTypeRadioGroup.getCheckedRadioButtonId();
        final boolean isDriver = selectedId == R.id.driverRadioButton;

        // Validate inputs
        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("Name is required");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            phoneEditText.setError("Phone number is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            return;
        }

        // Validate vehicle details for drivers
        if (isDriver) {
            String vehicleType = vehicleTypeEditText.getText().toString().trim();
            String vehicleNumber = vehicleNumberEditText.getText().toString().trim();
            String vehicleModel = vehicleModelEditText.getText().toString().trim();
            String vehicleColor = vehicleColorEditText.getText().toString().trim();

            if (TextUtils.isEmpty(vehicleType) || TextUtils.isEmpty(vehicleNumber) ||
                    TextUtils.isEmpty(vehicleModel) || TextUtils.isEmpty(vehicleColor)) {
                Toast.makeText(this, "Please fill all vehicle details", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        loadingBar.setTitle("Registration");
        loadingBar.setMessage("Please wait while we register your account");
        loadingBar.setCanceledOnTouchOutside(false);
        loadingBar.show();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            String userId = mAuth.getCurrentUser().getUid();
                            saveUserData(userId, name, email, phone, isDriver);
                        } else {
                            loadingBar.dismiss();
                            Toast.makeText(RegisterActivity.this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void saveUserData(String userId, String name, String email, String phone, boolean isDriver) {
        if (isDriver) {
            // Save driver data
            String vehicleType = vehicleTypeEditText.getText().toString().trim();
            String vehicleNumber = vehicleNumberEditText.getText().toString().trim();
            String vehicleModel = vehicleModelEditText.getText().toString().trim();
            String vehicleColor = vehicleColorEditText.getText().toString().trim();

            VehicleDetails vehicleDetails = new VehicleDetails(vehicleType, vehicleNumber, vehicleModel, vehicleColor);
            Driver driver = new Driver(userId, name, email, phone, vehicleDetails);

            usersRef.child("drivers").child(userId).setValue(driver)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            loadingBar.dismiss();
                            if (task.isSuccessful()) {
                                Toast.makeText(RegisterActivity.this, "Registration successful", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RegisterActivity.this, DriverHomeActivity.class));
                                finish();
                            } else {
                                Toast.makeText(RegisterActivity.this, "Failed to save user data: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                mAuth.getCurrentUser().delete();
                            }
                        }
                    });
        } else {
            // Save customer data
            User customer = new User(userId, name, email, phone, "customer");

            usersRef.child("customers").child(userId).setValue(customer)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            loadingBar.dismiss();
                            if (task.isSuccessful()) {
                                Toast.makeText(RegisterActivity.this, "Registration successful", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RegisterActivity.this, CustomerHomeActivity.class));
                                finish();
                            } else {
                                Toast.makeText(RegisterActivity.this, "Failed to save user data: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                mAuth.getCurrentUser().delete();
                            }
                        }
                    });
        }
    }
}