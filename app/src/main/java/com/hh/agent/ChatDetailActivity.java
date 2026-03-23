package com.hh.agent;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hh.agent.mockim.MockChatRepository;
import com.hh.agent.mockim.adapter.ChatMessageAdapter;
import com.hh.agent.mockim.debug.MockChatProbeRunner;
import com.hh.agent.mockim.model.ChatConversation;

public class ChatDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_detail);

        String conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        ChatConversation conversation = MockChatRepository.findConversationById(conversationId);
        if (conversation == null) {
            finish();
            return;
        }

        ImageView backButton = findViewById(R.id.backButton);
        TextView titleView = findViewById(R.id.chatTitleView);
        ListView messageListView = findViewById(R.id.messageListView);
        TextView sendButton = findViewById(R.id.sendButton);

        backButton.setOnClickListener(v -> finish());
        titleView.setText(conversation.getTitle());
        messageListView.setAdapter(new ChatMessageAdapter(this, conversation.getMessages()));
        sendButton.setOnLongClickListener(v -> {
            MockChatProbeRunner.runObservationBoundGestureProbe(
                    this,
                    "Chat Detail Observation Probe",
                    "发送",
                    960,
                    2200);
            return true;
        });
    }
}
