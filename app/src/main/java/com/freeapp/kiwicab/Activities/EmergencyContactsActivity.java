package com.freeapp.kiwicab.Activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.freeapp.kiwicab.Adapters.EmergencyContactsAdapter;
import com.freeapp.kiwicab.Model.User;
import com.freeapp.kiwicab.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class EmergencyContactsActivity extends AppCompatActivity {

    private EditText nameEditText, phoneEditText, emailEditText, relationshipEditText;
    private Button addContactBtn, saveContactsBtn;
    private RecyclerView contactsRecyclerView;
    private EmergencyContactsAdapter adapter;
    private List<User.EmergencyContact> emergencyContacts;

    private DatabaseReference usersRef;
    private String currentUserId;
    private static final int MAX_CONTACTS = 5; // Limit to 5 emergency contacts

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        initializeViews();
        initializeFirebase();
        setupRecyclerView();
        loadExistingContacts();

        addContactBtn.setOnClickListener(v -> addContact());
        saveContactsBtn.setOnClickListener(v -> saveContactsToDatabase());
    }

    private void initializeViews() {
        nameEditText = findViewById(R.id.nameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        emailEditText = findViewById(R.id.emailEditText);
        relationshipEditText = findViewById(R.id.relationshipEditText);
        addContactBtn = findViewById(R.id.addContactBtn);
        saveContactsBtn = findViewById(R.id.saveContactsBtn);
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);

        emergencyContacts = new ArrayList<>();
    }

    private void initializeFirebase() {
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        usersRef = FirebaseDatabase.getInstance().getReference().child("users").child("customers");
    }

    private void setupRecyclerView() {
        adapter = new EmergencyContactsAdapter(emergencyContacts, this::removeContact);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(adapter);
    }

    private void addContact() {
        // Check if maximum contacts reached
        if (emergencyContacts.size() >= MAX_CONTACTS) {
            Toast.makeText(this, "Maximum " + MAX_CONTACTS + " emergency contacts allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String relationship = relationshipEditText.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty() || relationship.isEmpty()) {
            Toast.makeText(this, "Please fill required fields (Name, Phone, Relationship)", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate phone number format
        if (!phone.matches("^[+]?[0-9]{10,15}$")) {
            Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        User.EmergencyContact contact = new User.EmergencyContact(name, phone, email, relationship);
        emergencyContacts.add(contact);
        adapter.notifyItemInserted(emergencyContacts.size() - 1);

        clearInputFields();
        Toast.makeText(this, "Contact added. Don't forget to save!", Toast.LENGTH_SHORT).show();
    }

    private void removeContact(int position) {
        emergencyContacts.remove(position);
        adapter.notifyItemRemoved(position);
    }

    private void clearInputFields() {
        nameEditText.setText("");
        phoneEditText.setText("");
        emailEditText.setText("");
        relationshipEditText.setText("");
    }

    private void loadExistingContacts() {
        usersRef.child(currentUserId).child("emergencyContacts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                emergencyContacts.clear();
                for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                    User.EmergencyContact contact = contactSnapshot.getValue(User.EmergencyContact.class);
                    if (contact != null) {
                        emergencyContacts.add(contact);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EmergencyContactsActivity.this, "Failed to load contacts", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveContactsToDatabase() {
        if (emergencyContacts.isEmpty()) {
            Toast.makeText(this, "Please add at least one emergency contact", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update the user's emergency contacts in Firebase
        usersRef.child(currentUserId).child("emergencyContacts").setValue(emergencyContacts)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(EmergencyContactsActivity.this, "Emergency contacts saved successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(EmergencyContactsActivity.this, "Failed to save contacts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}