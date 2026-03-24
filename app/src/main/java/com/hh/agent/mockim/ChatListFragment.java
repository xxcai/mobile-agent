package com.hh.agent.mockim;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.hh.agent.ChatDetailActivity;
import com.hh.agent.R;
import com.hh.agent.mockim.adapter.ChatConversationAdapter;
import com.hh.agent.mockim.debug.MockChatProbeRunner;
import com.hh.agent.mockim.model.ChatConversation;

import java.util.List;

public class ChatListFragment extends Fragment {

    public static ChatListFragment newInstance() {
        return new ChatListFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ListView listView = view.findViewById(R.id.chatListView);
        List<ChatConversation> conversations = MockChatRepository.getConversations();
        listView.setAdapter(new ChatConversationAdapter(requireContext(), conversations));
        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            ChatConversation conversation = conversations.get(position);
            Intent intent = new Intent(requireContext(), ChatDetailActivity.class);
            intent.putExtra(ChatDetailActivity.EXTRA_CONVERSATION_ID, conversation.getId());
            startActivity(intent);
        });
        listView.setOnItemLongClickListener((parent, itemView, position, id) -> {
            ChatConversation conversation = conversations.get(position);
            MockChatProbeRunner.runObservationBoundGestureProbe(
                    (AppCompatActivity) requireActivity(),
                    "Chat List Observation Probe",
                    conversation.getTitle(),
                    200,
                    420);
            return true;
        });
    }
}
