package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import com.example.kiwicab.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SplashActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkUserLoggedIn();
            }
        }, 2000); // 2 seconds delay
    }

    private void checkUserLoggedIn() {
        if (mAuth.getCurrentUser() != null) {
            // User is already logged in, check user type
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child("customers").child(mAuth.getCurrentUser().getUid());

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // User is a customer
                        startActivity(new Intent(SplashActivity.this, CustomerHomeActivity.class));
                    } else {
                        // Check if user is a driver
                        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                                .child("users").child("drivers").child(mAuth.getCurrentUser().getUid());

                        driverRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    // User is a driver
                                    startActivity(new Intent(SplashActivity.this, DriverHomeActivity.class));
                                } else {
                                    // User type not found, log out and go to login
                                    mAuth.signOut();
                                    startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                                }
                                finish();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(SplashActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                                finish();
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(SplashActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                    finish();
                }
            });
        } else {
            // User is not logged in
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }
    }
}