package com.freeapp.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.freeapp.kiwicab.Adapters.TransactionAdapter;
import com.freeapp.kiwicab.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class DriverEarningsActivity extends AppCompatActivity {

    private TextView balanceTextView, emptyTransactionsText;
    private Button withdrawButton;
    private RecyclerView transactionsRecyclerView;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private DatabaseReference driversRef, transactionsRef, withdrawalsRef;
    private String driverId;

    private TransactionAdapter adapter;
    private List<com.freeapp.kiwicab.Model.Transaction> transactionList;
    private double currentBalance = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_earnings);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        driverId = mAuth.getCurrentUser().getUid();
        driversRef = FirebaseDatabase.getInstance().getReference().child("users").child("drivers");
        transactionsRef = FirebaseDatabase.getInstance().getReference().child("transactions");
        withdrawalsRef = FirebaseDatabase.getInstance().getReference().child("withdrawals");

        balanceTextView = findViewById(R.id.balanceTextView);
        withdrawButton = findViewById(R.id.withdrawButton);
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView);
        emptyTransactionsText = findViewById(R.id.emptyTransactionsText);
        progressBar = findViewById(R.id.progressBar);

        // Set up RecyclerView
        transactionList = new ArrayList<>();
        adapter = new TransactionAdapter(this, transactionList);
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionsRecyclerView.setAdapter(adapter);

        // Set click listener for withdraw button
        withdrawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWithdrawDialog();
            }
        });

        // Load driver balance and transaction history
        loadDriverBalance();
        loadTransactionHistory();
    }

    private void loadDriverBalance() {
        showProgressBar();

        driversRef.child(driverId).child("balance").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                hideProgressBar();

                if (snapshot.exists()) {
                    currentBalance = snapshot.getValue(Double.class);
                    balanceTextView.setText(String.format("₹%.2f", currentBalance));
                } else {
                    // If no balance exists yet, initialize it to 0
                    driversRef.child(driverId).child("balance").setValue(0.0);
                    balanceTextView.setText("₹0.00");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideProgressBar();
                Toast.makeText(DriverEarningsActivity.this,
                        "Failed to load balance: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTransactionHistory() {
        showProgressBar();

        // Query transactions related to this driver
        Query query = transactionsRef.orderByChild("driverId").equalTo(driverId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                transactionList.clear();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    com.freeapp.kiwicab.Model.Transaction transaction =
                            dataSnapshot.getValue(com.freeapp.kiwicab.Model.Transaction.class);

                    if (transaction != null) {
                        transactionList.add(transaction);
                    }
                }

                // Also get withdrawals
                Query withdrawalsQuery = withdrawalsRef.orderByChild("driverId").equalTo(driverId);
                withdrawalsQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            com.freeapp.kiwicab.Model.Transaction withdrawal =
                                    dataSnapshot.getValue(com.freeapp.kiwicab.Model.Transaction.class);

                            if (withdrawal != null) {
                                transactionList.add(withdrawal);
                            }
                        }

                        // Sort by timestamp descending (newest first)
                        Collections.sort(transactionList, new Comparator<com.freeapp.kiwicab.Model.Transaction>() {
                            @Override
                            public int compare(com.freeapp.kiwicab.Model.Transaction t1, com.freeapp.kiwicab.Model.Transaction t2) {
                                return Long.compare(t2.getTimestamp(), t1.getTimestamp());
                            }
                        });

                        adapter.updateTransactions(transactionList);
                        hideProgressBar();

                        // Show empty view if no transactions
                        if (transactionList.isEmpty()) {
                            emptyTransactionsText.setVisibility(View.VISIBLE);
                            transactionsRecyclerView.setVisibility(View.GONE);
                        } else {
                            emptyTransactionsText.setVisibility(View.GONE);
                            transactionsRecyclerView.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        hideProgressBar();
                        Toast.makeText(DriverEarningsActivity.this,
                                "Failed to load withdrawals: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                hideProgressBar();
                Toast.makeText(DriverEarningsActivity.this,
                        "Failed to load transactions: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWithdrawDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_withdraw, null);

        final TextView availableBalanceTextView = dialogView.findViewById(R.id.availableBalanceTextView);
        final EditText withdrawAmountEditText = dialogView.findViewById(R.id.withdrawAmountEditText);
        final AutoCompleteTextView paymentMethodDropdown = dialogView.findViewById(R.id.paymentMethodDropdown);
        final EditText accountDetailsEditText = dialogView.findViewById(R.id.accountDetailsEditText);

        // Set available balance
        availableBalanceTextView.setText(String.format("Available Balance: ₹%.2f", currentBalance));

        // Set up payment method dropdown
        String[] paymentMethods = {"Bank Transfer", "UPI", "PayTM"};
        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, paymentMethods);
        paymentMethodDropdown.setAdapter(methodAdapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView)
                .setPositiveButton("Withdraw", null) // We'll override this below
                .setNegativeButton("Cancel", null);

        final AlertDialog dialog = builder.create();
        dialog.show();

        // Override the positive button to prevent auto-dismiss on error
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String amountStr = withdrawAmountEditText.getText().toString().trim();
                String paymentMethod = paymentMethodDropdown.getText().toString().trim();
                String accountDetails = accountDetailsEditText.getText().toString().trim();

                if (TextUtils.isEmpty(amountStr)) {
                    withdrawAmountEditText.setError("Please enter an amount");
                    return;
                }

                if (TextUtils.isEmpty(paymentMethod)) {
                    paymentMethodDropdown.setError("Please select a payment method");
                    return;
                }

                if (TextUtils.isEmpty(accountDetails)) {
                    accountDetailsEditText.setError("Please enter account details");
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    withdrawAmountEditText.setError("Invalid amount");
                    return;
                }

                // Check if amount is valid
                if (amount <= 0) {
                    withdrawAmountEditText.setError("Amount must be greater than 0");
                    return;
                }

                if (amount > currentBalance) {
                    withdrawAmountEditText.setError("Insufficient balance");
                    return;
                }

                // All validation passed, process withdrawal
                processWithdrawal(amount, paymentMethod, accountDetails);
                dialog.dismiss();
            }
        });
    }

    private void processWithdrawal(final double amount, String paymentMethod, String accountDetails) {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Processing withdrawal request...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Use a transaction to safely update the balance
        DatabaseReference balanceRef = driversRef.child(driverId).child("balance");
        balanceRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                Double currentBalance = mutableData.getValue(Double.class);
                if (currentBalance == null || currentBalance < amount) {
                    return Transaction.abort();
                }

                // Subtract the withdrawal amount from the balance
                mutableData.setValue(currentBalance - amount);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                if (committed) {
                    // Create withdrawal record
                    String withdrawalId = withdrawalsRef.push().getKey();

                    if (withdrawalId != null) {
                        Map<String, Object> withdrawalData = new HashMap<>();
                        withdrawalData.put("id", withdrawalId);
                        withdrawalData.put("driverId", driverId);
                        withdrawalData.put("amount", amount);
                        withdrawalData.put("type", "withdrawal");
                        withdrawalData.put("paymentMethod", paymentMethod);
                        withdrawalData.put("details", accountDetails);
                        withdrawalData.put("status", "pending");
                        withdrawalData.put("timestamp", ServerValue.TIMESTAMP);

                        withdrawalsRef.child(withdrawalId).setValue(withdrawalData)
                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        progressDialog.dismiss();

                                        if (task.isSuccessful()) {
                                            Toast.makeText(DriverEarningsActivity.this,
                                                    "Withdrawal request submitted successfully",
                                                    Toast.LENGTH_SHORT).show();
                                        } else {
                                            // Revert the balance change if withdrawal record creation fails
                                            balanceRef.runTransaction(new Transaction.Handler() {
                                                @NonNull
                                                @Override
                                                public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                                                    Double balance = mutableData.getValue(Double.class);
                                                    if (balance != null) {
                                                        mutableData.setValue(balance + amount);
                                                    }
                                                    return Transaction.success(mutableData);
                                                }

                                                @Override
                                                public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                                                    // Nothing to do here
                                                }
                                            });

                                            Toast.makeText(DriverEarningsActivity.this,
                                                    "Failed to submit withdrawal request: " + task.getException().getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(DriverEarningsActivity.this,
                                "Failed to generate withdrawal ID",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    progressDialog.dismiss();
                    Toast.makeText(DriverEarningsActivity.this,
                            "Failed to update balance: " + (databaseError != null ? databaseError.getMessage() : "Unknown error"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgressBar() {
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
