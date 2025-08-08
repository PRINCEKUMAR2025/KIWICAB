package com.freeapp.kiwicab.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;


import com.freeapp.kiwicab.Model.Transaction;
import com.freeapp.kiwicab.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactions;
    private Context context;

    public TransactionAdapter(Context context, List<Transaction> transactions) {
        this.context = context;
        this.transactions = transactions;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);

        // Set transaction type
        if ("payment".equals(transaction.getType())) {
            holder.typeTextView.setText("Payment Received");
            holder.amountTextView.setText(String.format("+₹%.2f", transaction.getAmount()));
            holder.amountTextView.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess));
        } else if ("withdrawal".equals(transaction.getType())) {
            holder.typeTextView.setText("Withdrawal");
            holder.amountTextView.setText(String.format("-₹%.2f", transaction.getAmount()));
            holder.amountTextView.setTextColor(ContextCompat.getColor(context, R.color.colorError));
        }

        // Format and set date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        String formattedDate = sdf.format(new Date(transaction.getTimestamp()));
        holder.dateTextView.setText(formattedDate);

        // Set details
        if (transaction.getRideId() != null && !transaction.getRideId().isEmpty()) {
            holder.detailsTextView.setText("Ride #" + transaction.getRideId());
        } else if (transaction.getDetails() != null && !transaction.getDetails().isEmpty()) {
            holder.detailsTextView.setText(transaction.getDetails());
        } else {
            holder.detailsTextView.setText(transaction.getPaymentMethod());
        }
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    public void updateTransactions(List<Transaction> newTransactions) {
        this.transactions = newTransactions;
        notifyDataSetChanged();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView typeTextView, dateTextView, detailsTextView, amountTextView;

        TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            typeTextView = itemView.findViewById(R.id.transactionTypeTextView);
            dateTextView = itemView.findViewById(R.id.transactionDateTextView);
            detailsTextView = itemView.findViewById(R.id.transactionDetailsTextView);
            amountTextView = itemView.findViewById(R.id.transactionAmountTextView);
        }
    }
}
