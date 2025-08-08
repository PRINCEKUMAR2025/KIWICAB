package com.freeapp.kiwicab.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.freeapp.kiwicab.Model.User;
import com.freeapp.kiwicab.R;

import java.util.List;

public class EmergencyContactsAdapter extends RecyclerView.Adapter<EmergencyContactsAdapter.ContactViewHolder> {

    private List<User.EmergencyContact> contacts;
    private OnContactRemoveListener removeListener;

    public interface OnContactRemoveListener {
        void onRemove(int position);
    }

    public EmergencyContactsAdapter(List<User.EmergencyContact> contacts, OnContactRemoveListener removeListener) {
        this.contacts = contacts;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        User.EmergencyContact contact = contacts.get(position);
        holder.nameTextView.setText(contact.getName());
        holder.phoneTextView.setText(contact.getPhone());
        holder.emailTextView.setText(contact.getEmail().isEmpty() ? "No email" : contact.getEmail());
        holder.relationshipTextView.setText(contact.getRelationship());

        holder.removeBtn.setOnClickListener(v -> removeListener.onRemove(position));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, phoneTextView, emailTextView, relationshipTextView;
        ImageButton removeBtn;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            phoneTextView = itemView.findViewById(R.id.phoneTextView);
            emailTextView = itemView.findViewById(R.id.emailTextView);
            relationshipTextView = itemView.findViewById(R.id.relationshipTextView);
            removeBtn = itemView.findViewById(R.id.removeBtn);
        }
    }
}
