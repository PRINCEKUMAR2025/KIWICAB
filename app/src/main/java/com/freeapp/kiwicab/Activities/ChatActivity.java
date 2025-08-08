package com.freeapp.kiwicab.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.freeapp.kiwicab.Model.Message;
import com.freeapp.kiwicab.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private Button sendButton;
    private String carpoolId, userId, userName;
    private DatabaseReference messagesRef;
    private List<Message> messagesList = new ArrayList<>();
    private MessageAdapter messageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get carpool ID from intent
        carpoolId = getIntent().getStringExtra("carpoolId");
        userName = getIntent().getStringExtra("userName");
        if (carpoolId == null) {
            Toast.makeText(this, "Chat room not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        messagesRef = FirebaseDatabase.getInstance().getReference()
                .child("carpool_chats").child(carpoolId);

        // Initialize views
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        // Set up RecyclerView
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(this, messagesList, userId);
        messagesRecyclerView.setAdapter(messageAdapter);

        // Load messages
        loadMessages();

        // Set up send button
        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void loadMessages() {
        messagesRef.orderByChild("timestamp").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message != null) {
                    messagesList.add(message);
                    messageAdapter.notifyItemInserted(messagesList.size() - 1);
                    messagesRecyclerView.scrollToPosition(messagesList.size() - 1);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this, "Failed to load messages: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (!text.isEmpty()) {
            // Get user name
            String senderName = userName != null ? userName : "User " + userId.substring(0, 5);

            // Create message
            Message message = new Message(userId, senderName, text, System.currentTimeMillis());

            // Push to Firebase
            messagesRef.push().setValue(message);

            // Clear input
            messageInput.setText("");
        }
    }

    // Message adapter
    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
        private static final int VIEW_TYPE_MY_MESSAGE = 1;
        private static final int VIEW_TYPE_OTHER_MESSAGE = 2;

        private Context context;
        private List<Message> messages;
        private String currentUserId;

        public MessageAdapter(Context context, List<Message> messages, String currentUserId) {
            this.context = context;
            this.messages = messages;
            this.currentUserId = currentUserId;
        }

        @Override
        public int getItemViewType(int position) {
            Message message = messages.get(position);
            if (message.getSenderId().equals(currentUserId)) {
                return VIEW_TYPE_MY_MESSAGE;
            } else {
                return VIEW_TYPE_OTHER_MESSAGE;
            }
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_MY_MESSAGE) {
                View view = LayoutInflater.from(context).inflate(R.layout.item_message_mine, parent, false);
                return new MessageViewHolder(view);
            } else {
                View view = LayoutInflater.from(context).inflate(R.layout.item_message_other, parent, false);
                return new MessageViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            Message message = messages.get(position);
            holder.messageText.setText(message.getText());
            holder.senderName.setText(message.getSenderName());
            holder.timeText.setText(formatTime(message.getTimestamp()));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        private String formatTime(long timestamp) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }

        static class MessageViewHolder extends RecyclerView.ViewHolder {
            TextView messageText, senderName, timeText;

            MessageViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.messageText);
                senderName = itemView.findViewById(R.id.senderName);
                timeText = itemView.findViewById(R.id.timeText);
            }
        }
    }
}
