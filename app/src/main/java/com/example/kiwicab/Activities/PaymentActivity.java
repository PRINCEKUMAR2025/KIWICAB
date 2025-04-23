package com.example.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.kiwicab.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    TextView tvAmountToPay,DriverWallet;
    String AmountToPay;
    PaymentSheet paymentSheet;
    FirebaseAuth auth;
    FirebaseFirestore firestore;
    String customerID;
    String EmphericalKey;
    String ClientSecret;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        auth=FirebaseAuth.getInstance();
        firestore=FirebaseFirestore.getInstance();


        tvAmountToPay=findViewById(R.id.amountToPay);
        DriverWallet=findViewById(R.id.DriverWalletAddress);

        Intent intent = getIntent();
        String rideId = intent.getStringExtra("rideId");

        DatabaseReference rideRef = FirebaseDatabase.getInstance().getReference()
                .child("rides").child(rideId);

        rideRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String driverId = snapshot.child("driverId").getValue(String.class);
                    DriverWallet.setText("Sending Rs: "+AmountToPay +" to driver");
                    Log.d("RideInfo", "Driver UID: " + driverId);

                    // you can use driverId now for payment, UI, messages, etc
                } else {
                    Log.e("RideInfo", "Ride not found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("RideInfo", "Database error: " + error.getMessage());
            }
        });


        double amount = intent.getDoubleExtra("amountToPay", 0.0);
        tvAmountToPay.setText("Rs: "+amount);
        AmountToPay = String.valueOf(amount);

        PaymentConfiguration.init(this,PUBLISH_KEY);
        paymentSheet=new PaymentSheet(this, this::onPaymentResult);

        StringRequest stringRequest=new StringRequest(Request.Method.POST
                , "https://api.stripe.com/v1/customers", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject object=new JSONObject(response);
                    customerID=object.getString("id");
                    Log.e("payment info",customerID);
                    getEmphericalKey(customerID);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String,String> header=new HashMap<>();
                header.put("Authorization","Bearer "+SECRET_KEY);
                return header;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(PaymentActivity.this);
        requestQueue.add(stringRequest);


    }

    private void getEmphericalKey(String customerID) {

        StringRequest stringRequest=new StringRequest(Request.Method.POST
                , "https://api.stripe.com/v1/ephemeral_keys", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject object=new JSONObject(response);
                    EmphericalKey=object.getString("id");
                    Log.e("payment info",EmphericalKey);
                    getClientSecret(customerID,EmphericalKey);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String,String> header=new HashMap<>();
                header.put("Stripe-Version","2020-08-27");
                header.put("Authorization","Bearer "+SECRET_KEY);
                return header;
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params=new HashMap<>();
                params.put("customer",customerID);
                return params;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(PaymentActivity.this);
        requestQueue.add(stringRequest);

    }

    private void getClientSecret(String customerID, String emphericalKey) {
        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                "https://api.stripe.com/v1/payment_intents",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject object = new JSONObject(response);
                            ClientSecret = object.getString("client_secret");
                            Log.e("payment info", ClientSecret);

                            // ✅ Call payment flow now that everything is ready
                            PaymentFlow();
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(PaymentActivity.this, "Error fetching client secret", Toast.LENGTH_SHORT).show();
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> header = new HashMap<>();
                header.put("Authorization", "Bearer " + SECRET_KEY);
                return header;
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("customer", customerID);
                params.put("amount", String.valueOf((int)(Double.parseDouble(AmountToPay) * 100))); // Stripe expects amount in paise
                params.put("currency", "inr");
                params.put("automatic_payment_methods[enabled]", "true");
                return params;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(PaymentActivity.this);
        requestQueue.add(stringRequest);
    }


    private void PaymentFlow() {

        paymentSheet.presentWithPaymentIntent(
                ClientSecret,new PaymentSheet.Configuration("Shopit" ,
                        new PaymentSheet.CustomerConfiguration(customerID,EmphericalKey))
        );
    }

    private void creditDriverBalance(String rideId, double amount) {
        // First, get the driver ID from the ride
        DatabaseReference rideRef = FirebaseDatabase.getInstance().getReference()
                .child("rides").child(rideId);

        rideRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String driverId = snapshot.child("driverId").getValue(String.class);

                    if (driverId != null) {
                        // Reference to driver's balance
                        DatabaseReference driverBalanceRef = FirebaseDatabase.getInstance().getReference()
                                .child("users").child("drivers").child(driverId).child("balance");

                        // Use a transaction to safely update the balance
                        driverBalanceRef.runTransaction(new Transaction.Handler() {
                            @NonNull
                            @Override
                            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                                Double currentBalance = mutableData.getValue(Double.class);
                                if (currentBalance == null) {
                                    // If no balance exists yet, initialize it
                                    mutableData.setValue(amount);
                                } else {
                                    // Add the payment amount to existing balance
                                    mutableData.setValue(currentBalance + amount);
                                }
                                return Transaction.success(mutableData);
                            }

                            @Override
                            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                                if (committed) {
                                    // Record the transaction in payment history
                                    recordPaymentTransaction(rideId, driverId, amount);

                                    // Update ride status to paid
                                    updateRidePaymentStatus(rideId);

                                    // Show success message with updated balance
                                    Double newBalance = dataSnapshot.getValue(Double.class);
                                    Toast.makeText(PaymentActivity.this,
                                            "Sent ₹" + amount,
                                            Toast.LENGTH_LONG).show();

                                    // Finish activity and return to previous screen
                                    finish();
                                } else {
                                    Toast.makeText(PaymentActivity.this,
                                            "Failed to update driver balance: " + databaseError.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        Toast.makeText(PaymentActivity.this, "Driver ID not found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(PaymentActivity.this, "Ride not found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PaymentActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void recordPaymentTransaction(String rideId, String driverId, double amount) {
        DatabaseReference paymentsRef = FirebaseDatabase.getInstance().getReference().child("payments");
        String paymentId = paymentsRef.push().getKey();

        if (paymentId != null) {
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("rideId", rideId);
            paymentData.put("driverId", driverId);
            paymentData.put("customerId", auth.getCurrentUser().getUid());
            paymentData.put("amount", amount);
            paymentData.put("method", "Stripe");
            paymentData.put("status", "completed");
            paymentData.put("timestamp", ServerValue.TIMESTAMP);
            paymentData.put("stripeCustomerId", customerID);

            paymentsRef.child(paymentId).setValue(paymentData)
                    .addOnSuccessListener(aVoid -> Log.d("Payment", "Payment transaction recorded successfully"))
                    .addOnFailureListener(e -> Log.e("Payment", "Failed to record payment transaction", e));
        }
    }

    private void updateRidePaymentStatus(String rideId) {
        DatabaseReference rideRef = FirebaseDatabase.getInstance().getReference()
                .child("rides").child(rideId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("isPaid", true);

        rideRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d("Payment", "Ride payment status updated successfully"))
                .addOnFailureListener(e -> Log.e("Payment", "Failed to update ride payment status", e));
    }



    private void onPaymentResult(PaymentSheetResult paymentSheetResult) {
        if (paymentSheetResult instanceof PaymentSheetResult.Canceled) {
            Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show();
        }
        else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
            Toast.makeText(this, ((PaymentSheetResult.Failed)paymentSheetResult).getError().getMessage(), Toast.LENGTH_SHORT).show();
        }
        else if (paymentSheetResult instanceof PaymentSheetResult.Completed) {
            Toast.makeText(this, "Payment Success", Toast.LENGTH_SHORT).show();

            // Get ride ID from intent
            String rideId = getIntent().getStringExtra("rideId");
            double amount = getIntent().getDoubleExtra("amountToPay", 0.0);

            // Credit the payment to driver's balance
            creditDriverBalance(rideId, amount);
        }
    }


}