package com.example.kiwicab.Services;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.example.kiwicab.Model.User;
import java.util.List;
import java.util.ArrayList;

public class EmergencyAlertService {
    private static final String TAG = "EmergencyAlertService";
    private Context context;

    public EmergencyAlertService(Context context) {
        this.context = context;
    }

    public void sendEmergencyAlerts(String emergencyId, User customer, User driver,
                                    String customerLocation, List<User.EmergencyContact> emergencyContacts) {

        String alertMessage = createAlertMessage(emergencyId, customer, driver, customerLocation);

        for (User.EmergencyContact contact : emergencyContacts) {
            // Send SMS
            sendSMS(contact.getPhone(), alertMessage);

            // Send Email via Intent (device's email app)
            sendEmailViaIntent(contact.getEmail(), contact.getName(), alertMessage, emergencyId);
        }

        // Show call options
        showCallDialog(emergencyContacts);
    }

    private String createAlertMessage(String emergencyId, User customer, User driver, String location) {
        return "🚨 EMERGENCY ALERT 🚨\n\n" +
                "Emergency ID: " + emergencyId + "\n" +
                "Customer: " + customer.getName() + "\n" +
                "Phone: " + customer.getPhone() + "\n" +
                "Driver: " + driver.getName() + "\n" +
                "Driver Phone: " + driver.getPhone() + "\n" +
                "Location: " + location + "\n" +
                "Time: " + new java.util.Date() + "\n\n" +
                "Please contact authorities immediately if needed.\n" +
                "This is an automated emergency alert from KiwiCab.";
    }

    // Send SMS using device's SMS service
    private void sendSMS(String phoneNumber, String message) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {

            try {
                SmsManager smsManager = SmsManager.getDefault();

                // Split message if too long (SMS limit is 160 characters)
                if (message.length() > 160) {
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                }

                Log.d(TAG, "SMS sent to: " + phoneNumber);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS: " + e.getMessage());
            }
        }
    }

    // Send Email using device's email client
    private void sendEmailViaIntent(String email, String contactName, String message, String emergencyId) {
        if (email == null || email.isEmpty()) return;

        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "🚨 EMERGENCY ALERT - KiwiCab Emergency ID: " + emergencyId);
        emailIntent.putExtra(Intent.EXTRA_TEXT, message);
        emailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(Intent.createChooser(emailIntent, "Send Emergency Email to " + contactName));
        } catch (Exception e) {
            Log.e(TAG, "No email app available");
        }
    }

    // Show dialog to select which contact to call
    private void showCallDialog(List<User.EmergencyContact> emergencyContacts) {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;

            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
            builder.setTitle("Call Emergency Contact?");

            String[] contactNames = new String[emergencyContacts.size()];
            for (int i = 0; i < emergencyContacts.size(); i++) {
                contactNames[i] = emergencyContacts.get(i).getName() + " (" + emergencyContacts.get(i).getRelationship() + ")";
            }

            builder.setItems(contactNames, (dialog, which) -> {
                User.EmergencyContact selectedContact = emergencyContacts.get(which);
                makeEmergencyCall(selectedContact.getPhone());
            });

            builder.setNegativeButton("Cancel", null);
            builder.show();
        }
    }

    // Make emergency call
    public void makeEmergencyCall(String phoneNumber) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {

            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                context.startActivity(callIntent);
                Log.d(TAG, "Emergency call initiated to: " + phoneNumber);
            } catch (Exception e) {
                Log.e(TAG, "Failed to make call: " + e.getMessage());
            }
        }
    }
}
